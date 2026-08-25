package com.bearfamily.app.bearontime;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private boolean firstResume = true;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashGuard.run(this, "首頁啟動", () -> {
            new VoiceModuleManager(this).ensureBuiltinInstalled();
            buildUi();
            requestNotificationPermission();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
        } else {
            CrashGuard.run(this, "首頁重新整理", this::buildUi);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = UiKit.pageRoot(this);
        scroll.addView(root);

        root.addView(UiKit.title(this, "🐻 " + Brand.APP_NAME, 30f));
        root.addView(UiKit.body(this, "穩定優先的離線整點報時、待辦與提醒工具"));
        root.addView(UiKit.gap(this, 12));

        SettingsStore settings = new SettingsStore(this);
        LinearLayout status = UiKit.card(this);
        status.addView(UiKit.title(this, "目前狀態", 20f));
        status.addView(UiKit.body(this,
                "整點報時：" + (settings.isEnabled() ? "已啟用" : "已停用") +
                "\n目前語音：" + new VoiceModuleManager(this).getModuleName(settings.getSelectedVoiceModuleId()) +
                "\n主題：" + ThemeManager.find(settings.getThemeKey()).name));
        root.addView(status);

        addNav(root, "🕐 整點與半點報時", "時段、音量、半點提示與立即試播", ChimeSettingsActivity.class);
        addNav(root, "🔊 報時聲音組", "列出、勾選、試聽、匯入 ZIP 與刪除自訂語音", VoiceModulesActivity.class);
        addNav(root, "📅 我的日曆與待辦", "依日期新增、完成、編輯與刪除待辦", PlannerActivity.class);
        addNav(root, "🎨 介面主題", "晴空藍、黃色、綠色、紅色、紫色等多種主題", ThemeActivity.class);
        addNav(root, "💾 備份與還原", "設定、待辦、工作日與自訂語音一併備份", BackupActivity.class);
        addNav(root, "🗓 工作日與請假日", "手動指定補班日、休假日與清除覆寫", WorkdayActivity.class);
        addNav(root, "⚙ 系統行為與權限", "通知、精準鬧鐘、勿擾權限狀態", SystemSettingsActivity.class);
        addNav(root, "🧪 系統診斷", "語音完整性、權限、排程與崩潰紀錄", DiagnosticsActivity.class);

        TextView version = UiKit.body(this, "版本：" + Brand.VERSION);
        version.setPadding(0, UiKit.dp(this, 8), 0, 0);
        root.addView(version);
        root.addView(UiKit.centerFooter(this));
        setContentView(scroll);
    }

    private void addNav(LinearLayout root, String title, String desc, Class<?> target) {
        LinearLayout card = UiKit.card(this);
        card.addView(UiKit.title(this, title, 20f));
        card.addView(UiKit.body(this, desc));
        android.widget.Button b = UiKit.button(this, "進入設定");
        b.setOnClickListener(v -> startActivity(new Intent(this, target)));
        card.addView(UiKit.gap(this, 8));
        card.addView(b);
        root.addView(card);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }
}
