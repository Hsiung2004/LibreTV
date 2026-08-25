package com.bearfamily.app.bearontime;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public final class HolidayRepository {
    private HolidayRepository() {}

    public static boolean isHolidayToday(Context context) {
        Calendar cal = Calendar.getInstance(Locale.TAIWAN);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN).format(cal.getTime());
        WorkdayStore store = new WorkdayStore(context);
        if (store.workingDays().contains(date)) return false;
        if (store.holidays().contains(date)) return true;
        int day = cal.get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY;
    }
}
