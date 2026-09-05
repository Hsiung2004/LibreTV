package com.bearfamily.app.bearontime.dev;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.AlarmClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

abstract class BearWidgetBaseProvider extends AppWidgetProvider {
    private static final String ACTION_TIME = "com.bearfamily.app.bearontime.dev.widget.TIME_V18122";
    private static final String ACTION_QUICK_ADD = "com.bearfamily.app.bearontime.dev.widget.QUICK_ADD_V18122";
    private static final String ACTION_CALENDAR = "com.bearfamily.app.bearontime.dev.widget.CALENDAR_V18122";
    private static final String ACTION_EXPENSE = "com.bearfamily.app.bearontime.dev.widget.EXPENSE_V18122";
    private static final String ACTION_WEATHER = "com.bearfamily.app.bearontime.dev.widget.WEATHER_V18122";

    protected abstract String layoutName();
    protected abstract String sizeName();

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        if (ids == null) return;
        for (int id : ids) updateOne(context, manager, id);
    }

    @Override public void onEnabled(Context context) { updateAll(context); }

    @Override public void onReceive(Context context, Intent intent) {
        if (context != null && intent != null) {
            String action = intent.getAction();
            try {
                if (ACTION_TIME.equals(action)) { openSystemAlarm(context); return; }
                if (ACTION_QUICK_ADD.equals(action)) { openBearPage(context, "quickadd"); return; }
                if (ACTION_CALENDAR.equals(action)) { openBearPage(context, "calendar"); return; }
                if (ACTION_EXPENSE.equals(action)) { openBearPage(context, "expense"); return; }
                if (ACTION_WEATHER.equals(action)) { openBearPage(context, "weather"); return; }
            } catch (Throwable ignored) { }
        }
        super.onReceive(context, intent);
        if (intent != null && AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())) updateAll(context);
    }

    protected void updateOne(Context context, AppWidgetManager manager, int appWidgetId) {
        int layout = res(context, "layout", layoutName());
        if (layout == 0) return;
        RemoteViews rv = new RemoteViews(context.getPackageName(), layout);

        int open = id(context, "widget_open");
        int line1 = id(context, "widget_line1");
        int line2 = id(context, "widget_line2");
        int line3 = id(context, "widget_line3");
        int quick = id(context, "widget_quick");
        int calendar = id(context, "widget_calendar");
        int expense = id(context, "widget_expense");
        int refresh = id(context, "widget_refresh");
        int next = id(context, "widget_next");

        // Top time area has its own broadcast action. It never opens BearOnTime.
        if (open != 0) rv.setOnClickPendingIntent(open,
                widgetAction(context, ACTION_TIME, 51200 + appWidgetId));

        if ("small".equals(sizeName())) {
            if (refresh != 0) {
                rv.setTextViewText(refresh, "🌤");
                rv.setOnClickPendingIntent(refresh,
                        widgetAction(context, ACTION_WEATHER, 52200 + appWidgetId));
            }
        } else if ("medium".equals(sizeName())) {
            if (line1 != 0) {
                rv.setTextViewText(line1, "📅 日曆");
                rv.setOnClickPendingIntent(line1,
                        widgetAction(context, ACTION_CALENDAR, 52300 + appWidgetId));
            }
            if (line2 != 0) {
                rv.setTextViewText(line2, "💰 記帳");
                rv.setOnClickPendingIntent(line2,
                        widgetAction(context, ACTION_EXPENSE, 52400 + appWidgetId));
            }
            if (refresh != 0) {
                rv.setTextViewText(refresh, "＋ 快速新增");
                rv.setOnClickPendingIntent(refresh,
                        widgetAction(context, ACTION_QUICK_ADD, 52500 + appWidgetId));
            }
        } else {
            // LARGE WIDGET = exactly four visible zones:
            // top: time; bottom: quick add / calendar / expense.
            if (line1 != 0) rv.setViewVisibility(line1, View.GONE);
            if (line2 != 0) rv.setViewVisibility(line2, View.GONE);
            if (line3 != 0) rv.setViewVisibility(line3, View.GONE);
            if (next != 0) rv.setViewVisibility(next, View.GONE);
            if (refresh != 0) {
                rv.setViewVisibility(refresh, View.GONE);
                rv.setOnClickPendingIntent(refresh, null);
            }

            if (quick != 0) {
                rv.setTextViewText(quick, "＋ 快速新增");
                rv.setTextViewTextSize(quick, TypedValue.COMPLEX_UNIT_SP, 15f);
                rv.setOnClickPendingIntent(quick,
                        widgetAction(context, ACTION_QUICK_ADD, 52800 + appWidgetId));
            }
            if (calendar != 0) {
                rv.setTextViewText(calendar, "📅 日曆");
                rv.setTextViewTextSize(calendar, TypedValue.COMPLEX_UNIT_SP, 15f);
                rv.setOnClickPendingIntent(calendar,
                        widgetAction(context, ACTION_CALENDAR, 52900 + appWidgetId));
            }
            if (expense != 0) {
                rv.setTextViewText(expense, "💰 記帳");
                rv.setTextViewTextSize(expense, TypedValue.COMPLEX_UNIT_SP, 15f);
                rv.setOnClickPendingIntent(expense,
                        widgetAction(context, ACTION_EXPENSE, 53000 + appWidgetId));
            }
        }

        manager.updateAppWidget(appWidgetId, rv);
    }

    protected void updateAll(Context context) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(context);
            ComponentName cn = new ComponentName(context, getClass());
            int[] ids = m.getAppWidgetIds(cn);
            if (ids != null) for (int id : ids) updateOne(context, m, id);
        } catch (Throwable ignored) { }
    }

    private PendingIntent widgetAction(Context c, String action, int requestCode) {
        Intent i = new Intent(c, getClass());
        i.setAction(action);
        i.setData(Uri.parse("bearontime://widget-action/v18_12_2/" + action.substring(action.lastIndexOf('.') + 1)
                + "/" + requestCode));
        return PendingIntent.getBroadcast(c, requestCode, i,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void openSystemAlarm(Context context) {
        Intent samsung = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        samsung.setPackage("com.sec.android.app.clockpackage");
        samsung.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { context.startActivity(samsung); return; } catch (Throwable ignored) { }

        Intent generic = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { context.startActivity(generic); return; } catch (Throwable ignored) { }

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
        i.setAction(context.getPackageName() + ".WIDGET_OPEN_" + page.toUpperCase() + "_V18122");
        i.putExtra("openPage", page);
        i.setData(Uri.parse("bearontime://open/" + page + "?source=widget18_12_2"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(i);
    }

    private static int id(Context c, String name) { return res(c, "id", name); }
    private static int res(Context c, String type, String name) {
        try { return c.getResources().getIdentifier(name, type, c.getPackageName()); }
        catch (Throwable t) { return 0; }
    }
}
