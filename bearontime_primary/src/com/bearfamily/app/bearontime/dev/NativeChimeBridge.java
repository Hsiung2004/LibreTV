package com.bearfamily.app.bearontime.dev;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class NativeChimeBridge {
    private final Activity activity;
    private final WebView webView;
    private final AudioManager audioManager;
    private final NotificationManager notificationManager;
    private AudioFocusRequest focusRequest;
    private Integer savedAlarmVolume;
    private Integer savedInterruptionFilter;
    private MediaPlayer activePlayer;
    private String activeCallbackId = "";
    private boolean activeAlarmChannel = true;
    private int activeVolumePercent = 80;

    NativeChimeBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        this.notificationManager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @JavascriptInterface public boolean isAvailable() { return true; }
    @JavascriptInterface public int getSdkInt() { return Build.VERSION.SDK_INT; }
    @JavascriptInterface public boolean hasDndAccess() {
        return Build.VERSION.SDK_INT < 23 || notificationManager.isNotificationPolicyAccessGranted();
    }
    @JavascriptInterface public void openDndAccessSettings() {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (Build.VERSION.SDK_INT >= 23) {
                    try { activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); }
                    catch (Throwable ignored) { }
                }
            }
        });
    }
    @JavascriptInterface public int getAlarmVolumePercent() {
        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM));
        int now = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        return Math.round(now * 100f / max);
    }

    @JavascriptInterface public void playSingle(String asset, int volumePercent, boolean alarmChannel,
                                                 boolean bypassDnd, String callbackId) {
        playSequence(asset, "", volumePercent, alarmChannel, bypassDnd, callbackId);
    }

    @JavascriptInterface public void playSequence(final String firstAsset, final String secondAsset,
                                                   int volumePercent, final boolean alarmChannel,
                                                   final boolean bypassDnd, final String callbackId) {
        final int volume = Math.max(0, Math.min(100, volumePercent));
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                stopInternal(true, false);
                activeCallbackId = callbackId == null ? "" : callbackId;
                activeAlarmChannel = alarmChannel;
                activeVolumePercent = volume;
                try {
                    if (alarmChannel) prepareAlarmEnvironment(bypassDnd);
                    requestFocus(alarmChannel);
                    playAsset(firstAsset, new Runnable() {
                        @Override public void run() {
                            if (secondAsset != null && !secondAsset.trim().isEmpty()) {
                                playAsset(secondAsset, new Runnable() {
                                    @Override public void run() { finish(activeCallbackId, true, ""); }
                                });
                            } else {
                                finish(activeCallbackId, true, "");
                            }
                        }
                    });
                } catch (Throwable t) {
                    finish(activeCallbackId, false, messageOf(t));
                }
            }
        });
    }

    @JavascriptInterface public void stop() {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { stopInternal(true, true); }
        });
    }

    private void prepareAlarmEnvironment(boolean bypassDnd) {
        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM));
        savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        try { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0); } catch (Throwable ignored) { }

        if (bypassDnd && Build.VERSION.SDK_INT >= 23
                && activity.getApplicationInfo().targetSdkVersion < 35
                && notificationManager.isNotificationPolicyAccessGranted()) {
            try {
                savedInterruptionFilter = notificationManager.getCurrentInterruptionFilter();
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            } catch (Throwable ignored) {
                savedInterruptionFilter = null;
            }
        }
    }

    private AudioAttributes audioAttributes(boolean alarmChannel) {
        return new AudioAttributes.Builder()
                .setUsage(alarmChannel ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    private void requestFocus(boolean alarmChannel) {
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(audioAttributes(alarmChannel))
                    .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                        @Override public void onAudioFocusChange(int focusChange) { }
                    })
                    .build();
            try { audioManager.requestAudioFocus(focusRequest); } catch (Throwable ignored) { }
        } else {
            try {
                audioManager.requestAudioFocus(null,
                        alarmChannel ? AudioManager.STREAM_ALARM : AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            } catch (Throwable ignored) { }
        }
    }

    private void playAsset(String path, final Runnable done) {
        if (path == null || path.trim().isEmpty()) { done.run(); return; }
        final MediaPlayer player = new MediaPlayer();
        activePlayer = player;
        try {
            player.setAudioAttributes(audioAttributes(activeAlarmChannel));
            setDataSourceFromAsset(player, path);
            float v = activeVolumePercent / 100f;
            player.setVolume(v, v);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer mp) { mp.start(); }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) {
                    safeRelease(mp);
                    if (activePlayer == mp) activePlayer = null;
                    done.run();
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                    safeRelease(mp);
                    if (activePlayer == mp) activePlayer = null;
                    finish(activeCallbackId, false, "MediaPlayer error " + what + "/" + extra);
                    return true;
                }
            });
            player.prepareAsync();
        } catch (Throwable t) {
            safeRelease(player);
            if (activePlayer == player) activePlayer = null;
            throw new RuntimeException(t);
        }
    }

    private void setDataSourceFromAsset(MediaPlayer player, String path) throws Exception {
        AssetFileDescriptor afd = null;
        try {
            afd = activity.getAssets().openFd(path);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            return;
        } catch (Throwable compressedOrMissing) {
            InputStream in = null;
            FileOutputStream out = null;
            try {
                in = activity.getAssets().open(path);
                File tmp = new File(activity.getCacheDir(), "bear_chime_" + Math.abs(path.hashCode()) + ".bin");
                out = new FileOutputStream(tmp, false);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
                player.setDataSource(tmp.getAbsolutePath());
                return;
            } finally {
                if (out != null) try { out.close(); } catch (Throwable ignored) { }
                if (in != null) try { in.close(); } catch (Throwable ignored) { }
            }
        } finally {
            if (afd != null) try { afd.close(); } catch (Throwable ignored) { }
        }
    }

    private void finish(String callbackId, boolean ok, String message) {
        String cb = callbackId == null ? "" : callbackId;
        activeCallbackId = "";
        restoreEnvironment();
        if (!cb.isEmpty()) {
            final String js = "window.__bearNativeDone&&window.__bearNativeDone('" + jsSafe(cb) + "',"
                    + (ok ? "true" : "false") + ",'" + jsSafe(message == null ? "" : message) + "')";
            webView.post(new Runnable() {
                @Override public void run() { try { webView.evaluateJavascript(js, null); } catch (Throwable ignored) { } }
            });
        }
    }

    private void stopInternal(boolean restore, boolean notify) {
        if (activePlayer != null) {
            try { activePlayer.stop(); } catch (Throwable ignored) { }
            safeRelease(activePlayer);
            activePlayer = null;
        }
        String cb = activeCallbackId;
        activeCallbackId = "";
        if (restore) restoreEnvironment();
        if (notify && cb != null && !cb.isEmpty()) {
            final String js = "window.__bearNativeDone&&window.__bearNativeDone('" + jsSafe(cb) + "',false,'stopped')";
            webView.post(new Runnable() {
                @Override public void run() { try { webView.evaluateJavascript(js, null); } catch (Throwable ignored) { } }
            });
        }
    }

    private void restoreEnvironment() {
        if (savedAlarmVolume != null) {
            try { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0); }
            catch (Throwable ignored) { }
            savedAlarmVolume = null;
        }
        if (savedInterruptionFilter != null && Build.VERSION.SDK_INT >= 23) {
            try {
                if (notificationManager.isNotificationPolicyAccessGranted()) {
                    notificationManager.setInterruptionFilter(savedInterruptionFilter);
                }
            } catch (Throwable ignored) { }
            savedInterruptionFilter = null;
        }
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
            try { audioManager.abandonAudioFocusRequest(focusRequest); } catch (Throwable ignored) { }
            focusRequest = null;
        } else {
            try { audioManager.abandonAudioFocus(null); } catch (Throwable ignored) { }
        }
    }

    private static void safeRelease(MediaPlayer mp) {
        if (mp == null) return;
        try { mp.reset(); } catch (Throwable ignored) { }
        try { mp.release(); } catch (Throwable ignored) { }
    }
    private static String messageOf(Throwable t) {
        String m = t.getMessage(); return m == null ? t.toString() : m;
    }
    private static String jsSafe(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\r", " ").replace("\n", " ");
    }
}
