package com.bearfamily.app.bearontime;

import java.util.Calendar;
import java.util.Locale;

public final class TimeTextFormatter {
    private TimeTextFormatter() {}
    public static String currentHourText() {
        Calendar now = Calendar.getInstance(Locale.TAIWAN);
        int h24 = now.get(Calendar.HOUR_OF_DAY);
        String period = h24 <= 5 ? "凌晨" : h24 <= 11 ? "上午" : h24 == 12 ? "中午" : h24 <= 17 ? "下午" : "晚上";
        int h12 = h24 == 0 ? 12 : h24 > 12 ? h24 - 12 : h24;
        return "現在時間，" + period + h12 + "點整";
    }
}
