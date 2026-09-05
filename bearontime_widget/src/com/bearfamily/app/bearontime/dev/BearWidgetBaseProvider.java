package com.bearfamily.app.bearontime.dev;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
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

        if (open != 0) rv.setOnClickPendingIntent(open,
                widgetAction(context, WidgetActionReceiver.ACTION_TIME, 41200 + appWidgetId));

        if ("small".equals(sizeName())) {
            if (refresh != 0) {
                rv.setTextViewText(refresh, "🌤");
                rv.setOnClickPendingIntent(refresh,
                        widgetAction(context, WidgetActionReceiver.ACTION_WEATHER, 42200 + appWidgetId));
            }
        } else if ("medium".equals(sizeName())) {
            if (line1 != 0) {
                rv.setTextViewText(line1, "📅 日曆");
                rv.setOnClickPendingIntent(line1,
                        widgetAction(context, WidgetActionReceiver.ACTION_CALENDAR, 42300 + appWidgetId));
            }
            if (line2 != 0) {
                rv.setTextViewText(line2, "💰 記帳");
                rv.setOnClickPendingIntent(line2,
                        widgetAction(context, WidgetActionReceiver.ACTION_EXPENSE, 42400 + appWidgetId));
            }
            if (refresh != 0) {
                rv.setTextViewText(refresh, "＋ 快速新增");
                rv.setOnClickPendingIntent(refresh,
                        widgetAction(context, WidgetActionReceiver.ACTION_QUICK_ADD, 42500 + appWidgetId));
            }
        } else {
            // LARGE = exactly four main action zones:
            // top time -> system alarm; bottom: quick add / calendar / expense.
            if (line1 != 0) rv.setTextViewText(line1, "📌 快捷工具");
            if (line2 != 0) rv.setTextViewText(line2, "點下方區塊直接進入功能");
            if (line3 != 0) rv.setTextViewText(line3, "");
            if (next != 0) rv.setTextViewText(next, "熊正點報時 · 大型桌面工具");

            if (quick != 0) {
                rv.setTextViewText(quick, "＋ 快速新增");
                rv.setOnClickPendingIntent(quick,
                        widgetAction(context, WidgetActionReceiver.ACTION_QUICK_ADD, 42800 + appWidgetId));
            }
            if (calendar != 0) {
                rv.setTextViewText(calendar, "📅 日曆");
                rv.setOnClickPendingIntent(calendar,
                        widgetAction(context, WidgetActionReceiver.ACTION_CALENDAR, 42900 + appWidgetId));
            }
            if (expense != 0) {
                rv.setTextViewText(expense, "💰 記帳");
                rv.setOnClickPendingIntent(expense,
                        widgetAction(context, WidgetActionReceiver.ACTION_EXPENSE, 43000 + appWidgetId));
            }
            if (refresh != 0) {
                // Large widget is deliberately four-zone only. Hide the old fifth refresh target.
                rv.setViewVisibility(refresh, View.GONE);
                rv.setOnClickPendingIntent(refresh, null);
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

    private static PendingIntent widgetAction(Context c, String action, int requestCode) {
        Intent i = new Intent(c, WidgetActionReceiver.class);
        i.setAction(action);
        i.setData(Uri.parse("bearontime://widget-action/v18_12_2/" + action.substring(action.lastIndexOf('.') + 1)
                + "/" + requestCode));
        return PendingIntent.getBroadcast(c, requestCode, i,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int id(Context c, String name) { return res(c, "id", name); }
    private static int res(Context c, String type, String name) {
        try { return c.getResources().getIdentifier(name, type, c.getPackageName()); }
        catch (Throwable t) { return 0; }
    }
}
