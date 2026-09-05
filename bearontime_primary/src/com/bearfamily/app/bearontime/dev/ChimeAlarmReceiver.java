package com.bearfamily.app.bearontime.dev;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.PowerManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Locale;

public final class ChimeAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(final Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final PendingResult result = goAsync();
        NativeChimeScheduler.scheduleNext(app);
        new Thread(new Runnable() {
            @Override public void run() {
                PowerManager.WakeLock wake = null;
                try {
                    PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                    if (pm != null) {
                        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BearOnTime:Chime");
                        wake.acquire(15000L);
                    }
                    SharedPreferences p = app.getSharedPreferences(NativeChimeScheduler.PREF, Context.MODE_PRIVATE);
                    Calendar cal = Calendar.getInstance();
                    int minute = cal.get(Calendar.MINUTE);
                    if (!(minute == 0 || minute == 30) || !NativeChimeScheduler.allowedNow(p, cal)) return;
                    String key = String.format(Locale.US, "%04d-%02d-%02d-%02d-%02d",
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                            cal.get(Calendar.HOUR_OF_DAY), minute);
                    if (key.equals(p.getString("lastKey", ""))) return;
                    p.edit().putString("lastKey", key).putLong("lastFire", System.currentTimeMillis())
                            .putString("lastKind", minute == 0 ? "hour" : "half").apply();
                    play(app, p, cal, result, wake);
                    return;
                } catch (Throwable t) {
                    app.getSharedPreferences(NativeChimeScheduler.PREF, Context.MODE_PRIVATE).edit()
                            .putString("lastError", "receiver: " + NativeChimeScheduler.msg(t)).apply();
                }
                try { if (wake != null && wake.isHeld()) wake.release(); } catch (Throwable ignored) { }
                try { result.finish(); } catch (Throwable ignored) { }
            }
        }, "BearNativeChime").start();
    }

    private static void play(final Context c, final SharedPreferences p, Calendar cal,
                             final PendingResult result, final PowerManager.WakeLock wake) throws Exception {
        final AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) throw new IllegalStateException("AudioManager unavailable");
        final NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        final int savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM);
        final int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_ALARM));
        int volume = Math.max(0, Math.min(100, p.getInt("volume", 80)));
        int target = volume <= 0 ? 0 : Math.max(1, Math.round(max * (volume / 100f)));
        am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);

        final int[] savedFilter = new int[]{-1};
        if (p.getBoolean("bypassDnd", false) && Build.VERSION.SDK_INT >= 23 && nm != null
                && nm.isNotificationPolicyAccessGranted() && c.getApplicationInfo().targetSdkVersion < 35) {
            try {
                savedFilter[0] = nm.getCurrentInterruptionFilter();
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            } catch (Throwable ignored) { savedFilter[0] = -1; }
        }

        final AudioFocusRequest[] focus = new AudioFocusRequest[]{null};
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build();
                focus[0] = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                            @Override public void onAudioFocusChange(int focusChange) { }
                        }).build();
                am.requestAudioFocus(focus[0]);
            } else {
                am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
        } catch (Throwable ignored) { }

        int minute = cal.get(Calendar.MINUTE);
        String first;
        String second = "";
        if (minute == 0) {
            first = "asset:audio/bearontime_hour_cue.mp3";
            second = resolveVoice(c, p.getString("voice", "bear_default"), cal.get(Calendar.HOUR_OF_DAY));
        } else {
            first = "asset:audio/" + halfFile(p.getString("halfTone", "classic"));
        }
        playOne(c, first, new Runnable() {
            @Override public void run() {
                String secondPath = secondHolder;
            }
        });
        // play sequence using holder to keep Java 8 anonymous classes simple
        playSequence(c, first, second, new Runnable() {
            @Override public void run() {
                restore(c, am, nm, savedVolume, savedFilter[0], focus[0], p, "");
                try { if (wake != null && wake.isHeld()) wake.release(); } catch (Throwable ignored) { }
                try { result.finish(); } catch (Throwable ignored) { }
            }
        }, new ErrorDone() {
            @Override public void done(String error) {
                restore(c, am, nm, savedVolume, savedFilter[0], focus[0], p, error);
                try { if (wake != null && wake.isHeld()) wake.release(); } catch (Throwable ignored) { }
                try { result.finish(); } catch (Throwable ignored) { }
            }
        });
    }

    // Dummy field referenced only to avoid accidental capture rewrite in old javac; never used.
    private static String secondHolder = "";

    private interface ErrorDone { void done(String error); }

    private static void playSequence(final Context c, final String first, final String second,
                                     final Runnable ok, final ErrorDone err) {
        playOne(c, first, new Runnable() {
            @Override public void run() {
                if (second == null || second.isEmpty()) ok.run();
                else playOne(c, second, ok, err);
            }
        }, err);
    }

    private static void playOne(Context c, String source, Runnable ok) { playOne(c, source, ok, new ErrorDone(){@Override public void done(String e){}}); }

    private static void playOne(final Context c, final String source, final Runnable ok, final ErrorDone err) {
        try {
            final MediaPlayer mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_ALARM);
            String path = materialize(c, source);
            mp.setDataSource(path);
            mp.setVolume(1f, 1f);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer p) { p.start(); }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer p) {
                    try { p.reset(); } catch (Throwable ignored) { }
                    try { p.release(); } catch (Throwable ignored) { }
                    ok.run();
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer p, int what, int extra) {
                    try { p.reset(); } catch (Throwable ignored) { }
                    try { p.release(); } catch (Throwable ignored) { }
                    err.done("MediaPlayer " + what + "/" + extra + " source=" + source);
                    return true;
                }
            });
            mp.prepareAsync();
        } catch (Throwable t) {
            err.done("play: " + NativeChimeScheduler.msg(t) + " source=" + source);
        }
    }

    private static String materialize(Context c, String source) throws Exception {
        if (source != null && source.startsWith("asset:")) {
            String asset = source.substring(6);
            File out = new File(c.getCacheDir(), "native_chime_" + Math.abs(asset.hashCode()) + ext(asset));
            if (!out.isFile() || out.length() == 0) {
                InputStream in = c.getAssets().open(asset);
                FileOutputStream fos = new FileOutputStream(out, false);
                try {
                    byte[] b = new byte[8192]; int n;
                    while ((n = in.read(b)) > 0) fos.write(b, 0, n);
                    fos.flush();
                } finally {
                    try { fos.close(); } catch (Throwable ignored) { }
                    try { in.close(); } catch (Throwable ignored) { }
                }
            }
            return out.getAbsolutePath();
        }
        return source;
    }

    private static String resolveVoice(Context c, String moduleId, int hour) {
        int h = ((hour % 24) + 24) % 24;
        if (moduleId != null && !moduleId.isEmpty() && !"bear_default".equals(moduleId)) {
            String clean = moduleId.replaceAll("[^A-Za-z0-9_-]", "");
            File dir = new File(new File(c.getFilesDir(), "voice_modules"), clean);
            File[] files = dir.listFiles();
            if (files != null) {
                String prefix = String.format(Locale.US, "%02d_00.", h);
                for (File f : files) if (f != null && f.isFile() && f.getName().toLowerCase(Locale.US).startsWith(prefix)) return f.getAbsolutePath();
            }
        }
        return "asset:audio/voices/" + String.format(Locale.US, "%02d_00.mp3", h);
    }

    private static String halfFile(String tone) {
        if ("chime".equals(tone)) return "bearontime_half_chime.wav";
        if ("cuckoo".equals(tone)) return "bearontime_half_cuckoo.wav";
        if ("electronic".equals(tone)) return "bearontime_half_electronic.wav";
        return "bearontime_hour_cue.mp3";
    }

    private static String ext(String path) {
        int i = path == null ? -1 : path.lastIndexOf('.');
        return i >= 0 ? path.substring(i) : ".bin";
    }

    private static void restore(Context c, AudioManager am, NotificationManager nm, int volume,
                                int filter, AudioFocusRequest focus, SharedPreferences p, String error) {
        try { am.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0); } catch (Throwable ignored) { }
        if (Build.VERSION.SDK_INT >= 23 && nm != null && filter >= 0) {
            try { if (nm.isNotificationPolicyAccessGranted()) nm.setInterruptionFilter(filter); } catch (Throwable ignored) { }
        }
        if (Build.VERSION.SDK_INT >= 26 && focus != null) {
            try { am.abandonAudioFocusRequest(focus); } catch (Throwable ignored) { }
        } else {
            try { am.abandonAudioFocus(null); } catch (Throwable ignored) { }
        }
        p.edit().putString("lastError", error == null ? "" : error).apply();
    }
}
