package com.bearfamily.app.bearontime;

import android.content.Context;
import android.graphics.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ThemeManager {
    public static final class ThemeSpec {
        public final String key;
        public final String name;
        public final int background;
        public final int card;
        public final int accent;
        public final int accentText;
        public final int text;
        public final int muted;

        ThemeSpec(String key, String name, String background, String card, String accent,
                  String accentText, String text, String muted) {
            this.key = key;
            this.name = name;
            this.background = Color.parseColor(background);
            this.card = Color.parseColor(card);
            this.accent = Color.parseColor(accent);
            this.accentText = Color.parseColor(accentText);
            this.text = Color.parseColor(text);
            this.muted = Color.parseColor(muted);
        }
    }

    private static final List<ThemeSpec> THEMES = Collections.unmodifiableList(Arrays.asList(
        new ThemeSpec("milk_tea", "奶茶米", "#F7F0E7", "#FFFDF9", "#B98961", "#FFFFFF", "#3E2D24", "#806C60"),
        new ThemeSpec("sky_blue", "晴空藍", "#EAF6FF", "#FFFFFF", "#5CA7D9", "#FFFFFF", "#233A4D", "#60798C"),
        new ThemeSpec("sun_yellow", "向日黃", "#FFF8D9", "#FFFDF3", "#E4B843", "#3B2E08", "#3C3320", "#7B704D"),
        new ThemeSpec("mint_green", "薄荷綠", "#EAF8F0", "#FFFFFF", "#62B989", "#FFFFFF", "#243D31", "#607B6D"),
        new ThemeSpec("berry_red", "莓果紅", "#FDECEF", "#FFFFFF", "#CF6E7D", "#FFFFFF", "#482A31", "#8B6870"),
        new ThemeSpec("lavender", "薰衣紫", "#F2EDFF", "#FFFFFF", "#8D78C7", "#FFFFFF", "#342C4A", "#746B88"),
        new ThemeSpec("sakura", "櫻花粉", "#FFF0F5", "#FFFFFF", "#E996B4", "#FFFFFF", "#493039", "#8A6B76"),
        new ThemeSpec("mist_silver", "灰霧銀", "#F0F2F4", "#FFFFFF", "#7D8A96", "#FFFFFF", "#2F3438", "#707980"),
        new ThemeSpec("moon_blue", "月夜深藍", "#172338", "#22324D", "#72A7E0", "#0D1A2A", "#F4F8FF", "#B8C8DB")
    ));

    private ThemeManager() {}

    public static List<ThemeSpec> all() { return THEMES; }

    public static ThemeSpec get(Context context) {
        String key = new SettingsStore(context).getThemeKey();
        return find(key);
    }

    public static ThemeSpec find(String key) {
        for (ThemeSpec spec : THEMES) {
            if (spec.key.equals(key)) return spec;
        }
        return THEMES.get(1); // 晴空藍
    }
}
