package com.bearfamily.app.bearontime;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ScrollView;

public class SystemSettingsActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) { super.onCreate(savedInstanceState); CrashGuard.run(this, "系統行為與權限", this::buildUi); }
    @Override protected void onResume() { super.onResume(); if (getWindow() != null) CrashGuard.run(this, "系統權限重新整理", this::buildUi); }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); LinearLayout root = UiKit.pageRoot(this); scroll.addView(root);
        root.addView(UiKit.title(this, "⚙ 系統行為與權限", 26f));
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean dnd = Build.VERSION.SDK_INT < 23 || nm.isNotificationPolicyAccessGranted();
        boolean notif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        root.addView(UiKit.body(this, "通知：" + yes(notif) + "\n精準鬧鐘：" + yes(exact) + "\n勿擾政策存取：" + yes(dnd)));
        root.addView(UiKit.gap(this,12));
        android.widget.Button n = UiKit.button(this, "設定通知權限");
        n.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 701);
            else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }); root.addView(n);
        root.addView(UiKit.gap(this,6));
        android.widget.Button e = UiKit.button(this, "設定精準鬧鐘權限");
        e.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 31) startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
        }); root.addView(e);
        root.addView(UiKit.gap(this,6));
        android.widget.Button d = UiKit.button(this, "設定勿擾／模式存取權");
        d.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))); root.addView(d);
        setContentView(scroll);
    }
    private String yes(boolean v) { return v ? "已允許" : "尚未允許"; }
}
