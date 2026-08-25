package com.bearfamily.app.bearontime;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

public class ThemeActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) { super.onCreate(savedInstanceState); CrashGuard.run(this, "介面主題", this::buildUi); }

    private void buildUi() {
        SettingsStore settings = new SettingsStore(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = UiKit.pageRoot(this);
        scroll.addView(root);
        root.addView(UiKit.title(this, "🎨 介面主題", 26f));
        root.addView(UiKit.body(this, "選擇後立即保存；回到首頁會自動套用。"));
        root.addView(UiKit.gap(this, 12));
        for (ThemeManager.ThemeSpec t : ThemeManager.all()) {
            LinearLayout card = UiKit.card(this);
            String mark = t.key.equals(settings.getThemeKey()) ? "✓ " : "";
            android.widget.Button b = new android.widget.Button(this);
            b.setText(mark + t.name);
            b.setAllCaps(false);
            b.setTextColor(t.accentText);
            b.setBackgroundTintList(ColorStateList.valueOf(t.accent));
            b.setMinHeight(UiKit.dp(this, 54));
            b.setOnClickListener(v -> {
                settings.setThemeKey(t.key);
                Toast.makeText(this, "已套用「" + t.name + "」", Toast.LENGTH_SHORT).show();
                buildUi();
            });
            card.addView(b);
            card.addView(UiKit.body(this, t.key.equals("sky_blue") ? "清爽淺藍，適合作為日常主題。" : "可隨時切換，不影響資料與報時設定。"));
            root.addView(card);
        }
        setContentView(scroll);
    }
}
