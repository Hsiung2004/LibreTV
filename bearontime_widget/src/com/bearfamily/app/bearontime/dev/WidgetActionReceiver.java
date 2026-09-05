package com.bearfamily.app.bearontime.dev;

import android.app.AlarmClockInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;

public final class WidgetActionReceiver extends BroadcastReceiver {
    public static final String ACTION_TIME = "com.bearfamily.app.bearontime.dev.widget.TIME";
    public static final String ACTION_QUICK = "com.bearfamily.app.bearontime.dev.widget.QUICK";
    public static final String ACTION_CALENDAR = "com.bearfamily.app.bearontime.dev.widget.CALENDAR";
    public static final String ACTION_EXPENSE = "com.bearfamily.app.bearontime.dev.widget.EXPENSE";

    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String a = intent.getAction();
        if (ACTION_TIME.equals(a)) {
            openSystemAlarm(context);
        } else if (ACTION_QUICK.equals(a)) {
            openApp(context, "quickadd");
        } else if (ACTION_CALENDAR.equals(a)) {
            openApp(context, "calendar");
        } else if (ACTION_EXPENSE.equals(a)) {
            openApp(context, "expense");
        }
    }

    private static void openSystemAlarm(Context c) {
        Intent i = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            if (android.os.Build.MANUFACTURER != null && android.os.Build.MANUFACTURER.toLowerCase().contains("samsung")) {
                i.setPackage("com.sec.android.app.clockpackage");
                c.startActivity(i);
                return;
            }
        } catch (Throwable ignored) { }
        try {
            i.setPackage(null);
            c.startActivity(i);
            return;
        } catch (Throwable ignored) { }
        try {
            Intent launch = c.getPackageManager().getLaunchIntentForPackage("com.sec.android.app.clockpackage");
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                c.startActivity(launch);
            }
        } catch (Throwable ignored) { }
    }

    private static void openApp(Context c, String page) {
        try {
            Intent i = new Intent();
            i.setClassName(c.getPackageName(), c.getPackageName() + ".MainActivity");
            i.putExtra("openPage", page);
            i.setAction(c.getPackageName() + ".WIDGET_OPEN_" + page.toUpperCase());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            c.startActivity(i);
        } catch (Throwable ignored) { }
    }
}
