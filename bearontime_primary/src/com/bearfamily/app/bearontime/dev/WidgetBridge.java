package com.bearfamily.app.bearontime.dev;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.webkit.JavascriptInterface;

public final class WidgetBridge {
    private final Activity activity;
    private String lastError = "";

    WidgetBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface public boolean isAvailable() {
        return true;
    }

    @JavascriptInterface public boolean isPinSupported() {
        try {
            return Build.VERSION.SDK_INT >= 26
                    && AppWidgetManager.getInstance(activity).isRequestPinAppWidgetSupported();
        } catch (Throwable t) {
            lastError = msg(t);
            return false;
        }
    }

    @JavascriptInterface public String getLastError() {
        return lastError == null ? "" : lastError;
    }

    @JavascriptInterface public boolean requestPinWidget(String size) {
        if (Build.VERSION.SDK_INT < 26) {
            lastError = "Android 版本不支援 App 內直接加入桌面小工具";
            return false;
        }
        try {
            String provider = providerName(size);
            ComponentName component = new ComponentName(activity.getPackageName(), provider);
            AppWidgetManager manager = AppWidgetManager.getInstance(activity);
            if (!manager.isRequestPinAppWidgetSupported()) {
                lastError = "目前桌面啟動器不支援直接釘選 Widget";
                return false;
            }
            boolean ok = manager.requestPinAppWidget(component, null, null);
            if (!ok) lastError = "桌面啟動器未接受 Widget 加入要求";
            else lastError = "";
            return ok;
        } catch (Throwable t) {
            lastError = msg(t);
            return false;
        }
    }

    @JavascriptInterface public boolean refreshWidgets() {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(activity);
            for (String provider : new String[]{
                    activity.getPackageName() + ".BearWidgetSmallProvider",
                    activity.getPackageName() + ".BearWidgetMediumProvider",
                    activity.getPackageName() + ".BearWidgetLargeProvider"}) {
                ComponentName cn = new ComponentName(activity.getPackageName(), provider);
                int[] ids = manager.getAppWidgetIds(cn);
                if (ids == null || ids.length == 0) continue;
                Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                i.setComponent(cn);
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                activity.sendBroadcast(i);
            }
            lastError = "";
            return true;
        } catch (Throwable t) {
            lastError = msg(t);
            return false;
        }
    }

    private String providerName(String size) {
        String s = size == null ? "" : size.trim().toLowerCase();
        if ("small".equals(s)) return activity.getPackageName() + ".BearWidgetSmallProvider";
        if ("large".equals(s)) return activity.getPackageName() + ".BearWidgetLargeProvider";
        return activity.getPackageName() + ".BearWidgetMediumProvider";
    }

    private static String msg(Throwable t) {
        if (t == null) return "unknown";
        String m = t.getMessage();
        return (m == null || m.trim().isEmpty()) ? t.toString() : m;
    }
}
