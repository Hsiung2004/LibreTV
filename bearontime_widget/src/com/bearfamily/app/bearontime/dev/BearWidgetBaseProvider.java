package com.bearfamily.app.bearontime.dev;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.AlarmClock;
import android.util.TypedValue;
import android.widget.RemoteViews;

abstract class BearWidgetBaseProvider extends AppWidgetProvider {
    protected abstract String layoutName();
    protected abstract String sizeName();

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        if (ids == null) return;
        for (int id : ids) updateOne(context, manager, id);
    }

    @Override public void onEnabled(Context context) { updateAll(context); }

    @Override public void onReceive(Context context, Intent intent) {
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

        // TIME AREA: always Android/Samsung system clock alarm list, never BearOnTime MainActivity.
        if (open != 0) {
            rv.setOnClickPendingIntent(open, alarmIntent(context, 1200 + appWidgetId));
            try {
                float sp = "large".equals(sizeName()) ? 38f : ("medium".equals(sizeName()) ? 32f : 30f);
                rv.setTextViewTextSize(open, TypedValue.COMPLEX_UNIT_SP, sp);
            } catch (Throwable ignored) { }
        }

        if ("small".equals(sizeName())) {
            if (refresh != 0) {
                rv.setTextViewText(refresh, "🌤");
                rv.setOnClickPendingIntent(refresh, appPage(context, "weather", 2200 + appWidgetId));
            }
        } else if ("medium".equals(sizeName())) {
            if (line1 != 0) {
                rv.setTextViewText(line1, "📅 日曆");
                rv.setOnClickPendingIntent(line1, appPage(context, "calendar", 2300 + appWidgetId));
            }
            if (line2 != 0) {
                rv.setTextViewText(line2, "💰 記帳");
                rv.setOnClickPendingIntent(line2, appPage(context, "expense", 2400 + appWidgetId));
            }
            if (refresh != 0) {
                rv.setTextViewText(refresh, "＋ 快速新增");
                rv.setOnClickPendingIntent(refresh, appPage(context, "quickadd", 2500 + appWidgetId));
            }
        } else {
            // LARGE WIDGET = four independent zones:
            // 1. time -> system alarm, 2. quick add -> quick add prompt,
            // 3. calendar -> calendar, 4. expense -> accounting.
            if (line1 != 0) rv.setTextViewText(line1, "四區快捷 · 點時間開手機鬧鐘");
            if (line2 != 0) rv.setTextViewText(line2, "下方三區各自獨立，不會共用首頁動作");
            if (line3 != 0) rv.setTextViewText(line3, "");
            if (next != 0) rv.setTextViewText(next, "熊正點報時 · 大型桌面工具");

            if (quick != 0) {
                rv.setTextViewText(quick, "＋ 快速新增");
                rv.setOnClickPendingIntent(quick, appPage(context, "quickadd", 2800 + appWidgetId));
            }
            if (calendar != 0) {
                rv.setTextViewText(calendar, "📅 日曆");
                rv.setOnClickPendingIntent(calendar, appPage(context, "calendar", 2900 + appWidgetId));
            }
            if (expense != 0) {
                rv.setTextViewText(expense, "💰 記帳");
                rv.setOnClickPendingIntent(expense, appPage(context, "expense", 3000 + appWidgetId));
            }
            if (refresh != 0) {
                rv.setTextViewText(refresh, "↻");
                rv.setOnClickPendingIntent(refresh, appPage(context, "widgets", 3100 + appWidgetId));
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

    private static PendingIntent alarmIntent(Context c, int requestCode) {
        Intent i = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        // Samsung: explicitly target Samsung Clock so the click cannot resolve back into BearOnTime.
        if (Build.MANUFACTURER != null && Build.MANUFACTURER.toLowerCase().contains("samsung")) {
            i.setPackage("com.sec.android.app.clockpackage");
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent appPage(Context c, String page, int requestCode) {
        Intent i = new Intent();
        i.setClassName(c.getPackageName(), c.getPackageName() + ".MainActivity");
        i.setAction(c.getPackageName() + ".WIDGET_" + page.toUpperCase());
        i.putExtra("openPage", page);
        i.setData(Uri.parse("bearontime://widget/" + page + "/" + requestCode));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int id(Context c, String name) { return res(c, "id", name); }
    private static int res(Context c, String type, String name) {
        try { return c.getResources().getIdentifier(name, type, c.getPackageName()); }
        catch (Throwable t) { return 0; }
    }
}
