package com.bearfamily.app.bearontime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_TIME_ALARM = "com.bearfamily.app.bearontime.ACTION_TIME_ALARM";

    @Override public void onReceive(Context context, Intent intent) {
        try {
            SettingsStore settings = new SettingsStore(context);
            if (settings.isEnabled() && !(settings.isSkipHolidays() && HolidayRepository.isHolidayToday(context))) {
                Intent service = new Intent(context, VoiceAnnounceService.class);
                service.setAction(VoiceAnnounceService.ACTION_EVENT);
                service.putExtra(VoiceAnnounceService.EXTRA_EVENT_TYPE,
                        intent == null ? TimeAlarmScheduler.EVENT_HOUR : intent.getStringExtra(VoiceAnnounceService.EXTRA_EVENT_TYPE));
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service); else context.startService(service);
            }
        } finally {
            TimeAlarmScheduler.scheduleNext(context);
        }
    }
}
