package com.bearfamily.app.bearontime.dev;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public final class NativeChimeScheduler {
    static final String PREF = "bear_native_chime";
    static final int REQUEST_CODE = 23100;
    private final Context context;

    NativeChimeScheduler(Context context) {
        this.context = context.getApplicationContext();
    }

    @JavascriptInterface public boolean isAvailable() { return true; }

    @JavascriptInterface public boolean syncChimeSettings(String json) {
        try {
            JSONObject root = new JSONObject(json == null ? "{}" : json);
            SharedPreferences.Editor e = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit();
            e.putBoolean("configured", true);
            e.putBoolean("hourly", root.optBoolean("hourly", true));
            e.putBoolean("half", root.optBoolean("half", true));
            e.putInt("volume", clamp(root.optInt("volume", 80), 0, 100));
            e.putBoolean("bypassDnd", root.optBoolean("bypassDnd", false));
            e.putString("voice", safe(root.optString("voice", "bear_default"), "bear_default"));
            e.putString("halfTone", safe(root.optString("halfTone", "classic"), "classic"));
            e.putString("days", csv(root.optJSONArray("days"), 0, 6, "0,1,2,3,4,5,6"));
            e.putString("hours", csv(root.optJSONArray("hours"), 0, 23, defaultHours()));
            e.apply();
            scheduleNext(context);
            return true;
        } catch (Throwable t) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                    .putString("lastError", "sync: " + msg(t)).apply();
            return false;
        }
    }

    @JavascriptInterface public void ensureScheduled() { scheduleNext(context); }

    @JavascriptInterface public String debugState() {
        try {
            SharedPreferences p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            boolean exact = Build.VERSION.SDK_INT < 31 || am == null || am.canScheduleExactAlarms();
            return "nativeScheduler=true,exact=" + exact
                    + ",next=" + p.getLong("nextAt", 0)
                    + ",lastFire=" + p.getLong("lastFire", 0)
                    + ",lastKind=" + p.getString("lastKind", "")
                    + ",lastError=" + p.getString("lastError", "");
        } catch (Throwable t) {
            return "scheduler-debug-error=" + msg(t);
        }
    }

    static void scheduleNext(Context c) {
        try {
            Context app = c.getApplicationContext();
            SharedPreferences p = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            if (!p.getBoolean("configured", false)) return;
            long when = findNext(p, System.currentTimeMillis());
            AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
            if (am == null) throw new IllegalStateException("AlarmManager unavailable");
            PendingIntent pi = pendingIntent(app);
            am.cancel(pi);
            if (when <= 0) {
                p.edit().putLong("nextAt", 0).apply();
                return;
            }
            if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            else am.setExact(AlarmManager.RTC_WAKEUP, when, pi);
            p.edit().putLong("nextAt", when).putString("lastError", "").apply();
        } catch (Throwable t) {
            c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                    .putString("lastError", "schedule: " + msg(t)).apply();
        }
    }

    static boolean allowedNow(SharedPreferences p, Calendar cal) {
        int day = cal.get(Calendar.DAY_OF_WEEK) - 1;
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        if (!containsCsv(p.getString("days", "0,1,2,3,4,5,6"), day)) return false;
        if (!containsCsv(p.getString("hours", defaultHours()), hour)) return false;
        if (minute == 0) return p.getBoolean("hourly", true);
        if (minute == 30) return p.getBoolean("half", true);
        return false;
    }

    private static long findNext(SharedPreferences p, long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now + 1500);
        int m = cal.get(Calendar.MINUTE);
        if (m < 30) cal.set(Calendar.MINUTE, 30);
        else {
            cal.add(Calendar.HOUR_OF_DAY, 1);
            cal.set(Calendar.MINUTE, 0);
        }
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 24 * 2 * 9; i++) {
            if (allowedNow(p, cal)) return cal.getTimeInMillis();
            cal.add(Calendar.MINUTE, 30);
        }
        return 0;
    }

    private static PendingIntent pendingIntent(Context c) {
        Intent i = new Intent(c, ChimeAlarmReceiver.class);
        i.setAction("com.bearfamily.app.bearontime.dev.NATIVE_CHIME");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, REQUEST_CODE, i, flags);
    }

    private static String csv(JSONArray a, int min, int max, String fallback) {
        if (a == null) return fallback;
        Set<Integer> set = new HashSet<>();
        for (int i = 0; i < a.length(); i++) {
            int v = a.optInt(i, Integer.MIN_VALUE);
            if (v >= min && v <= max) set.add(v);
        }
        if (set.isEmpty()) return "";
        StringBuilder s = new StringBuilder();
        for (int v = min; v <= max; v++) if (set.contains(v)) {
            if (s.length() > 0) s.append(',');
            s.append(v);
        }
        return s.toString();
    }

    private static boolean containsCsv(String csv, int v) {
        if (csv == null || csv.isEmpty()) return false;
        String[] a = csv.split(",");
        for (String x : a) try { if (Integer.parseInt(x.trim()) == v) return true; } catch (Throwable ignored) { }
        return false;
    }

    private static String defaultHours() {
        StringBuilder s = new StringBuilder();
        for (int i = 7; i <= 23; i++) { if (s.length() > 0) s.append(','); s.append(i); }
        return s.toString();
    }

    private static String safe(String s, String fallback) {
        String v = s == null ? "" : s.trim();
        return v.isEmpty() ? fallback : v;
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    static String msg(Throwable t) { String m = t == null ? "" : t.getMessage(); return m == null || m.isEmpty() ? String.valueOf(t) : m; }
}
