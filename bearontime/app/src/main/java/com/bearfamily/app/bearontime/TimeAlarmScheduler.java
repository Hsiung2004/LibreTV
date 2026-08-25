package com.bearfamily.app.bearontime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class TimeAlarmScheduler {
    public static final String EVENT_HOUR = "hour";
    public static final String EVENT_HALF = "half";

    private TimeAlarmScheduler() {}

    public static void scheduleNext(Context context) {
        SettingsStore settings = new SettingsStore(context);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (!settings.isEnabled()) { cancel(context); return; }

        Next n = next(settings.isHalfHourEnabled());
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_TIME_ALARM);
        intent.putExtra(VoiceAnnounceService.EXTRA_EVENT_TYPE, n.type);
        PendingIntent pi = PendingIntent.getBroadcast(context, 2001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.when, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.when, pi);
            }
        } catch (Exception ex) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.when, pi);
        }
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_TIME_ALARM);
        PendingIntent pi = PendingIntent.getBroadcast(context, 2001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    public static String nextTriggerSummary(boolean includeHalf) {
        Next n = next(includeHalf);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.TAIWAN).format(new Date(n.when));
        return time + (EVENT_HALF.equals(n.type) ? "（半點提示）" : "（整點報時）");
    }

    private static Next next(boolean includeHalf) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int minute = c.get(Calendar.MINUTE);
        if (includeHalf) {
            if (minute < 30) {
                c.set(Calendar.MINUTE, 30);
                return new Next(c.getTimeInMillis(), EVENT_HALF);
            }
            c.add(Calendar.HOUR_OF_DAY, 1);
            c.set(Calendar.MINUTE, 0);
            return new Next(c.getTimeInMillis(), EVENT_HOUR);
        }
        c.add(Calendar.HOUR_OF_DAY, 1);
        c.set(Calendar.MINUTE, 0);
        return new Next(c.getTimeInMillis(), EVENT_HOUR);
    }

    private static final class Next {
        final long when; final String type;
        Next(long when, String type) { this.when = when; this.type = type; }
    }
}
