package com.bearfamily.app.bearontime.dev;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class NativeChimeBridge {
    public static final int REQ_VOICE_ZIP = 1909;
    private static final long MAX_IMPORT_BYTES = 120L * 1024L * 1024L;
    private static final int MAX_ZIP_ENTRIES = 500;

    private final Activity activity;
    private final WebView webView;
    private final AudioManager audioManager;
    private final NotificationManager notificationManager;

    private AudioFocusRequest focusRequest;
    private Integer savedAlarmVolume;
    private Integer savedInterruptionFilter;
    private MediaPlayer activePlayer;
    private String activeCallbackId = "";
    private String lastError = "";

    NativeChimeBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        this.notificationManager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @JavascriptInterface public boolean isAvailable() { return true; }
    @JavascriptInterface public int getSdkInt() { return Build.VERSION.SDK_INT; }
    @JavascriptInterface public String getLastError() { return lastError == null ? "" : lastError; }
    @JavascriptInterface public int getRingerMode() { return audioManager.getRingerMode(); }

    @JavascriptInterface public int getAlarmVolumePercent() {
        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM));
        return Math.round(audioManager.getStreamVolume(AudioManager.STREAM_ALARM) * 100f / max);
    }

    @JavascriptInterface public boolean hasDndAccess() {
        return Build.VERSION.SDK_INT < 23 || notificationManager.isNotificationPolicyAccessGranted();
    }

    @JavascriptInterface public void openDndAccessSettings() {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (Build.VERSION.SDK_INT >= 23) {
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                    } catch (Throwable t) {
                        lastError = messageOf(t);
                    }
                }
            }
        });
    }

    @JavascriptInterface public String debugState() {
        try {
            int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM));
            int now = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            int filter = Build.VERSION.SDK_INT >= 23 ? notificationManager.getCurrentInterruptionFilter() : -1;
            return "sdk=" + Build.VERSION.SDK_INT
                    + ",target=" + activity.getApplicationInfo().targetSdkVersion
                    + ",ringer=" + audioManager.getRingerMode()
                    + ",alarm=" + now + "/" + max
                    + ",dnd=" + hasDndAccess()
                    + ",filter=" + filter
                    + ",voiceModules=" + importedModuleCount()
                    + ",err=" + getLastError();
        } catch (Throwable t) {
            return "debug-error=" + messageOf(t);
        }
    }

    @JavascriptInterface public void openVoiceModuleImport() {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/zip");
                    i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream"
                    });
                    activity.startActivityForResult(i, REQ_VOICE_ZIP);
                } catch (Throwable t) {
                    lastError = messageOf(t);
                    notifyVoiceImport(false, "", "", 0, lastError);
                }
            }
        });
    }

    boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_VOICE_ZIP) return false;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            notifyVoiceImport(false, "", "", 0, "已取消匯入");
            return true;
        }
        final Uri uri = data.getData();
        new Thread(new Runnable() {
            @Override public void run() {
                importVoiceZip(uri);
            }
        }, "BearVoiceImport").start();
        return true;
    }

    @JavascriptInterface public String listVoiceModules() {
        JSONArray arr = new JSONArray();
        try {
            JSONObject built = new JSONObject();
            built.put("id", "bear_default");
            built.put("name", "內建原版語音");
            built.put("builtIn", true);
            built.put("count", 24);
            arr.put(built);

            File root = modulesRoot();
            File[] dirs = root.listFiles();
            if (dirs != null) {
                for (File d : dirs) {
                    if (d == null || !d.isDirectory() || d.getName().startsWith(".")) continue;
                    JSONObject o = new JSONObject();
                    o.put("id", d.getName());
                    o.put("name", readModuleName(d));
                    o.put("builtIn", false);
                    o.put("count", countHours(d));
                    arr.put(o);
                }
            }
        } catch (Throwable t) {
            lastError = messageOf(t);
        }
        return arr.toString();
    }

    @JavascriptInterface public String getVoicePath(String moduleId, int hour) {
        int h = ((hour % 24) + 24) % 24;
        if (moduleId == null || moduleId.trim().isEmpty() || "bear_default".equals(moduleId)) {
            return builtInVoicePath(h);
        }
        File dir = moduleDir(moduleId);
        File f = findHourFile(dir, h);
        return f != null ? f.getAbsolutePath() : builtInVoicePath(h);
    }

    @JavascriptInterface public boolean renameVoiceModule(String moduleId, String newName) {
        if ("bear_default".equals(moduleId)) return false;
        try {
            File d = moduleDir(moduleId);
            if (!d.isDirectory()) return false;
            writeText(new File(d, "name.txt"), safeDisplayName(newName, d.getName()));
            return true;
        } catch (Throwable t) {
            lastError = messageOf(t);
            return false;
        }
    }

    @JavascriptInterface public boolean deleteVoiceModule(String moduleId) {
        if ("bear_default".equals(moduleId)) return false;
        try {
            File d = moduleDir(moduleId);
            if (!d.isDirectory()) return false;
            return deleteRecursive(d);
        } catch (Throwable t) {
            lastError = messageOf(t);
            return false;
        }
    }

    private void importVoiceZip(Uri uri) {
        File outDir = null;
        try {
            String display = displayName(uri);
            String fallbackName = stripZip(display);
            String id = "voice_" + System.currentTimeMillis();
            outDir = new File(modulesRoot(), id);
            if (!outDir.mkdirs() && !outDir.isDirectory()) throw new Exception("無法建立語音模組資料夾");

            boolean[] hours = new boolean[24];
            int count = 0;
            int entries = 0;
            long total = 0;
            String manifestName = "";

            InputStream raw = activity.getContentResolver().openInputStream(uri);
            if (raw == null) throw new Exception("無法開啟 ZIP");
            ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw));
            try {
                ZipEntry e;
                byte[] buf = new byte[8192];
                while ((e = zin.getNextEntry()) != null) {
                    if (++entries > MAX_ZIP_ENTRIES) throw new Exception("ZIP 檔案項目過多");
                    if (e.isDirectory()) { zin.closeEntry(); continue; }

                    String base = new File(e.getName()).getName();
                    if ("manifest.json".equalsIgnoreCase(base)) {
                        byte[] small = readEntryBytes(zin, 128 * 1024);
                        try {
                            JSONObject m = new JSONObject(new String(small, StandardCharsets.UTF_8));
                            manifestName = m.optString("name", "").trim();
                        } catch (Throwable ignored) { }
                        zin.closeEntry();
                        continue;
                    }

                    HourFile hf = parseHourFile(base);
                    if (hf == null) { zin.closeEntry(); continue; }

                    File target = new File(outDir, String.format(Locale.US, "%02d_00.%s", hf.hour, hf.ext));
                    BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(target, false));
                    try {
                        int n;
                        long one = 0;
                        while ((n = zin.read(buf)) > 0) {
                            one += n;
                            total += n;
                            if (one > 20L * 1024L * 1024L) throw new Exception("單一語音檔過大");
                            if (total > MAX_IMPORT_BYTES) throw new Exception("語音模組總容量超過限制");
                            bout.write(buf, 0, n);
                        }
                        bout.flush();
                    } finally {
                        try { bout.close(); } catch (Throwable ignored) { }
                    }
                    if (!hours[hf.hour]) {
                        hours[hf.hour] = true;
                        count++;
                    }
                    zin.closeEntry();
                }
            } finally {
                try { zin.close(); } catch (Throwable ignored) { }
                try { raw.close(); } catch (Throwable ignored) { }
            }

            if (count == 0) throw new Exception("ZIP 內找不到整點語音。請放入 00_00～23_00 的 mp3/wav/ogg/m4a/aac");
            String name = safeDisplayName(manifestName, safeDisplayName(fallbackName, "自訂語音"));
            writeText(new File(outDir, "name.txt"), name);
            notifyVoiceImport(true, id, name, count,
                    count == 24 ? "已匯入完整 24 小時語音" : "已匯入 " + count + "/24；缺少時段會自動使用內建語音");
        } catch (Throwable t) {
            lastError = messageOf(t);
            if (outDir != null) deleteRecursive(outDir);
            notifyVoiceImport(false, "", "", 0, lastError);
        }
    }

    private static byte[] readEntryBytes(ZipInputStream zin, int max) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n, total = 0;
        while ((n = zin.read(b)) > 0) {
            total += n;
            if (total > max) throw new Exception("manifest.json 過大");
            out.write(b, 0, n);
        }
        return out.toByteArray();
    }

    private void notifyVoiceImport(boolean ok, String id, String name, int count, String message) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", ok);
            o.put("id", id == null ? "" : id);
            o.put("name", name == null ? "" : name);
            o.put("count", count);
            o.put("message", message == null ? "" : message);
            final String js = "window.__bearVoiceImportDone&&window.__bearVoiceImportDone(" + o.toString() + ")";
            webView.post(new Runnable() {
                @Override public void run() {
                    try { webView.evaluateJavascript(js, null); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private File modulesRoot() {
        File r = new File(activity.getFilesDir(), "voice_modules");
        if (!r.exists()) r.mkdirs();
        return r;
    }

    private int importedModuleCount() {
        File[] a = modulesRoot().listFiles();
        int n = 0;
        if (a != null) for (File f : a) if (f != null && f.isDirectory() && !f.getName().startsWith(".")) n++;
        return n;
    }

    private File moduleDir(String moduleId) {
        String id = moduleId == null ? "" : moduleId.replaceAll("[^A-Za-z0-9_-]", "");
        return new File(modulesRoot(), id);
    }

    private String readModuleName(File d) {
        File n = new File(d, "name.txt");
        if (!n.isFile()) return d.getName();
        try {
            FileReader r = new FileReader(n);
            StringBuilder s = new StringBuilder();
            char[] b = new char[256];
            int c;
            while ((c = r.read(b)) > 0 && s.length() < 200) s.append(b, 0, c);
            r.close();
            return safeDisplayName(s.toString(), d.getName());
        } catch (Throwable ignored) {
            return d.getName();
        }
    }

    private static void writeText(File f, String text) throws Exception {
        FileWriter w = new FileWriter(f, false);
        try { w.write(text == null ? "" : text); }
        finally { try { w.close(); } catch (Throwable ignored) { } }
    }

    private static String safeDisplayName(String s, String fallback) {
        String v = s == null ? "" : s.trim().replaceAll("[\\r\\n\\t]+", " ");
        if (v.isEmpty()) v = fallback == null ? "自訂語音" : fallback;
        return v.length() > 40 ? v.substring(0, 40) : v;
    }

    private String displayName(Uri uri) {
        Cursor c = null;
        try {
            c = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) try { c.close(); } catch (Throwable ignored) { }
        }
        String p = uri == null ? "" : uri.getLastPathSegment();
        return p == null ? "自訂語音.zip" : p;
    }

    private static String stripZip(String name) {
        String s = name == null ? "" : name.trim();
        if (s.toLowerCase(Locale.US).endsWith(".zip")) s = s.substring(0, s.length() - 4);
        return s;
    }

    private static class HourFile {
        final int hour;
        final String ext;
        HourFile(int hour, String ext) { this.hour = hour; this.ext = ext; }
    }

    private static HourFile parseHourFile(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.US);
        int dot = lower.lastIndexOf('.');
        if (dot <= 0) return null;
        String ext = lower.substring(dot + 1);
        if (!("mp3".equals(ext) || "wav".equals(ext) || "ogg".equals(ext) || "m4a".equals(ext) || "aac".equals(ext))) return null;

        String stem = lower.substring(0, dot);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([0-9]{1,2})(?:_00)?$").matcher(stem);
        if (!m.matches()) return null;
        int h;
        try { h = Integer.parseInt(m.group(1)); } catch (Throwable t) { return null; }
        return h >= 0 && h <= 23 ? new HourFile(h, ext) : null;
    }

    private File findHourFile(File dir, int hour) {
        if (dir == null || !dir.isDirectory()) return null;
        String p = String.format(Locale.US, "%02d_00.", hour);
        File[] a = dir.listFiles();
        if (a == null) return null;
        for (File f : a) {
            if (f != null && f.isFile() && f.getName().toLowerCase(Locale.US).startsWith(p)) return f;
        }
        return null;
    }

    private static int countHours(File dir) {
        boolean[] seen = new boolean[24];
        int n = 0;
        File[] a = dir == null ? null : dir.listFiles();
        if (a != null) for (File f : a) {
            if (f == null || !f.isFile()) continue;
            HourFile hf = parseHourFile(f.getName());
            if (hf != null && !seen[hf.hour]) { seen[hf.hour] = true; n++; }
        }
        return n;
    }

    private static boolean deleteRecursive(File f) {
        if (f == null || !f.exists()) return true;
        if (f.isDirectory()) {
            File[] a = f.listFiles();
            if (a != null) for (File c : a) deleteRecursive(c);
        }
        return f.delete();
    }

    private static String builtInVoicePath(int hour) {
        return String.format(Locale.US, "audio/voices/%02d_00.mp3", hour);
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
                lastError = "";
                try {
                    if (alarmChannel) prepareAlarmEnvironment(volume, bypassDnd);
                    requestAlarmFocus();
                    playSource(firstAsset, new Runnable() {
                        @Override public void run() {
                            if (secondAsset != null && !secondAsset.trim().isEmpty()) {
                                playSource(secondAsset, new Runnable() {
                                    @Override public void run() { finish(activeCallbackId, true, ""); }
                                });
                            } else {
                                finish(activeCallbackId, true, "");
                            }
                        }
                    });
                } catch (Throwable t) {
                    lastError = messageOf(t);
                    finish(activeCallbackId, false, lastError);
                }
            }
        });
    }

    @JavascriptInterface public void stop() {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { stopInternal(true, true); }
        });
    }

    private void prepareAlarmEnvironment(int volumePercent, boolean bypassDnd) {
        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM));
        if (savedAlarmVolume == null) savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        int target = volumePercent <= 0 ? 0 : Math.max(1, Math.round(max * (volumePercent / 100f)));
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);

        if (bypassDnd && Build.VERSION.SDK_INT >= 23
                && activity.getApplicationInfo().targetSdkVersion < 35
                && notificationManager.isNotificationPolicyAccessGranted()) {
            savedInterruptionFilter = notificationManager.getCurrentInterruptionFilter();
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
        }
    }

    private void requestAlarmFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                        @Override public void onAudioFocusChange(int c) { }
                    }).build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }
    }

    private void playSource(String path, final Runnable done) {
        if (path == null || path.trim().isEmpty()) { done.run(); return; }
        final MediaPlayer player = new MediaPlayer();
        activePlayer = player;
        try {
            player.setAudioStreamType(AudioManager.STREAM_ALARM);
            setDataSource(player, path);
            player.setVolume(1f, 1f);
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
                    String m = "MediaPlayer error " + what + "/" + extra;
                    lastError = m;
                    safeRelease(mp);
                    if (activePlayer == mp) activePlayer = null;
                    finish(activeCallbackId, false, m);
                    return true;
                }
            });
            player.prepareAsync();
        } catch (Throwable t) {
            lastError = messageOf(t);
            safeRelease(player);
            if (activePlayer == player) activePlayer = null;
            throw new RuntimeException(t);
        }
    }

    private void setDataSource(MediaPlayer player, String path) throws Exception {
        File direct = path == null ? null : new File(path);
        if (direct != null && direct.isAbsolute() && direct.isFile()) {
            player.setDataSource(direct.getAbsolutePath());
            return;
        }

        android.content.res.AssetFileDescriptor afd = null;
        try {
            afd = activity.getAssets().openFd(path);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            return;
        } catch (Throwable x) {
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
                @Override public void run() {
                    try { webView.evaluateJavascript(js, null); } catch (Throwable ignored) { }
                }
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
                @Override public void run() {
                    try { webView.evaluateJavascript(js, null); } catch (Throwable ignored) { }
                }
            });
        }
    }

    private void restoreEnvironment() {
        if (savedAlarmVolume != null) {
            try { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0); } catch (Throwable ignored) { }
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
        String m = t == null ? "" : t.getMessage();
        return (m == null || m.trim().isEmpty()) ? String.valueOf(t) : m;
    }

    private static String jsSafe(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\r", " ").replace("\n", " ");
    }
}
