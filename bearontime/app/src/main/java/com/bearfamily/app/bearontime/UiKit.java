package com.bearfamily.app.bearontime;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

public final class UiKit {
    private UiKit() {}

    public static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static LinearLayout pageRoot(Activity a) {
        ThemeManager.ThemeSpec t = ThemeManager.get(a);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a, 18), dp(a, 18), dp(a, 18), dp(a, 28));
        root.setBackgroundColor(t.background);
        return root;
    }

    public static LinearLayout card(Context c) {
        ThemeManager.ThemeSpec t = ThemeManager.get(c);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(t.card);
        bg.setCornerRadius(dp(c, 18));
        bg.setStroke(dp(c, 1), withAlpha(t.muted, 45));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 12));
        card.setLayoutParams(lp);
        return card;
    }

    public static TextView title(Context c, String text, float sp) {
        ThemeManager.ThemeSpec t = ThemeManager.get(c);
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(t.text);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    public static TextView body(Context c, String text) {
        ThemeManager.ThemeSpec t = ThemeManager.get(c);
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(15f);
        v.setTextColor(t.muted);
        v.setLineSpacing(0f, 1.15f);
        return v;
    }

    public static Button button(Context c, String text) {
        ThemeManager.ThemeSpec t = ThemeManager.get(c);
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(16f);
        b.setAllCaps(false);
        b.setTextColor(t.accentText);
        b.setBackgroundTintList(ColorStateList.valueOf(t.accent));
        b.setMinHeight(dp(c, 48));
        return b;
    }

    public static Space gap(Context c, int dp) {
        Space s = new Space(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, UiKit.dp(c, dp)));
        return s;
    }

    public static TextView centerFooter(Context c) {
        TextView v = body(c, Brand.STUDIO + "\n設計者：" + Brand.DESIGNER);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, dp(c, 22), 0, dp(c, 12));
        return v;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
