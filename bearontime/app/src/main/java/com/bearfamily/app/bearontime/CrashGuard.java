package com.bearfamily.app.bearontime;

import android.app.Activity;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class CrashGuard {
    private CrashGuard() {}

    public static void run(Activity activity, String area, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            CrashLogger.recordNonFatal(activity, area, t);
            showFallback(activity, area, t);
        }
    }

    public static void showFallback(Activity activity, String area, Throwable t) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(activity, 20);
        root.setPadding(p,p,p,p);
        root.setBackgroundColor(Color.rgb(250,247,243));
        scroll.addView(root);

        TextView title = new TextView(activity);
        title.setText("熊正點報時：此功能發生錯誤");
        title.setTextSize(24f);
        title.setTextColor(Color.rgb(60,45,37));
        root.addView(title);

        TextView info = new TextView(activity);
        String msg = t == null ? "未知錯誤" : (t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        info.setText("功能：" + area + "\n\n" + msg + "\n\n錯誤已記錄。可從首頁 → 系統診斷匯出詳細紀錄。\n目前畫面已切換為安全模式，不讓 App 直接秒退。");
        info.setTextSize(16f);
        info.setTextColor(Color.DKGRAY);
        info.setPadding(0,dp(activity,16),0,dp(activity,16));
        root.addView(info);

        Button close = new Button(activity);
        close.setText("返回上一頁");
        close.setAllCaps(false);
        close.setOnClickListener(v -> activity.finish());
        root.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        activity.setContentView(scroll);
    }

    private static int dp(Activity a, int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }
}
