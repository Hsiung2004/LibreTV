package com.bearfamily.app.bearontime.dev;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.AlarmClock;

public final class WidgetActionReceiver extends BroadcastReceiver {
    public static final String ACTION_TIME = "com.bearfamily.app.bearontime.dev.widget.TIME";
    public static final String ACTION_QUICK_ADD = "com.bearfamily.app.bearontime.dev.widget.QUICK_ADD";
    public static final String ACTION_CALENDAR = "com.bearfamily.app.bearontime.dev.widget.CALENDAR";
    public static final String ACTION_EXPENSE = "com.bearfamily.app.bearontime.dev.widget.EXPENSE";
    public static final String ACTION_WEATHER = "com.bearfamily.app.bearontime.dev.widget.WEATHER";
    public static final String ACTION_WIDGETS = "com.bearfamily.app.bearontime.dev.widget.WIDGETS";

    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        try {
            if (ACTION_TIME.equals(action)) {
                openSystemAlarm(context);
                return;
            }
            if (ACTION_QUICK_ADD.equals(action)) {
                openBearPage(context, "quickadd");
                return;
            }
            if (ACTION_CALENDAR.equals(action)) {
                openBearPage(context, "calendar");
                return;
            }
            if (ACTION_EXPENSE.equals(action)) {
                openBearPage(context, "expense");
                return;
            }
            if (ACTION_WEATHER.equals(action)) {
                openBearPage(context, "weather");
                return;
            }
            if (ACTION_WIDGETS.equals(action)) {
                openBearPage(context, "widgets");
            }
        } catch (Throwable ignored) { }
    }

    private static void openSystemAlarm(Context context) {
        Intent samsung = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        samsung.setPackage("com.sec.android.app.clockpackage");
        samsung.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(samsung);
            return;
        } catch (Throwable ignored) { }

        Intent generic = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(generic);
            return;
        } catch (Throwable ignored) { }

        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.sec.android.app.clockpackage");
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(launch);
            }
        } catch (Throwable ignored) { }
    }

    private static void openBearPage(Context context, String page) {
        Intent i = new Intent();
        i.setClassName(context.getPackageName(), context.getPackageName() + ".MainActivity");
        i.setAction(context.getPackageName() + ".WIDGET_OPEN_" + page.toUpperCase());
        i.putExtra("openPage", page);
        i.setData(Uri.parse("bearontime://open/" + page + "?source=widget18_12_2"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(i);
    }
}
