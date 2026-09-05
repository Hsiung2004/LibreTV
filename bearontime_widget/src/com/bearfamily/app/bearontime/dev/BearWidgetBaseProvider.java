package com.bearfamily.app.bearontime.dev;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.AlarmClock;
import android.widget.RemoteViews;

abstract class BearWidgetBaseProvider extends AppWidgetProvider {
    protected abstract String layoutName();
    protected abstract String sizeName();

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        if (ids == null) return;
        for (int id : ids) updateOne(context, manager, id);
    }

    @Override public void onEnabled(Context context) {
        updateAll(context);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())) {
            updateAll(context);
        }
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

        if (open != 0) rv.setOnClickPendingIntent(open, alarmIntent(context, 1200 + appWidgetId));

        if ("small".equals(sizeName())) {
            if (refresh != 0) {
                rv.setTextViewText(refresh, "🌤");
                rv.setOnClickPendingIntent(refresh, appPage(context, "weather", 2200 + appWidgetId));
            }
        } else if ("medium".equals(sizeName())) {
            if (line1 != 0) {
                rv.setTextViewText(line1, "🌤  天氣預報");
                rv.setOnClickPendingIntent(line1, appPage(context, "weather", 2300 + appWidgetId));
            }
            if (line2 != 0) {
                rv.setTextViewText(line2, "💰  記帳");
                rv.setOnClickPendingIntent(line2, appPage(context, "expense", 2400 + appWidgetId));
            }
            if (refresh != 0) {
                rv.setTextViewText(refresh, "↻ 更新");
                rv.setOnClickPendingIntent(refresh, appPage(context, "widgets", 2500 + appWidgetId));
            }
        } else {
            if (line1 != 0) {
                rv.setTextViewText(line1, "🌤 天氣預報");
                rv.setOnClickPendingIntent(line1, appPage(context, "weather", 2600 + appWidgetId));
            }
            if (line2 != 0) {
                rv.setTextViewText(line2, "💰 今日記帳");
                rv.setOnClickPendingIntent(line2, appPage(context, "expense", 2700 + appWidgetId));
            }
            if (line3 != 0) rv.setTextViewText(line3, "點下方區塊快速進入功能");
            if (next != 0) rv.setTextViewText(next, "熊正點報時 · 桌面快捷");
            if (quick != 0) {
                rv.setTextViewText(quick, "⏰ 系統鬧鐘");
                rv.setOnClickPendingIntent(quick, alarmIntent(context, 2800 + appWidgetId));
            }
            if (calendar != 0) {
                rv.setTextViewText(calendar, "🌤 天氣");
                rv.setOnClickPendingIntent(calendar, appPage(context, "weather", 2900 + appWidgetId));
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
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent appPage(Context c, String page, int requestCode) {
        Intent i = new Intent();
        i.setClassName(c.getPackageName(), c.getPackageName() + ".MainActivity");
        i.putExtra("openPage", page);
        i.setData(Uri.parse("bearontime://open/" + page + "/" + requestCode));
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
