package com.bearfamily.app.bearontime;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WorkdayActivity extends Activity {
    private String selected;
    private TextView status;
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selected = fmt.format(new Date());
        CrashGuard.run(this, "工作日與請假日", this::buildUi);
    }

    private void buildUi() {
        WorkdayStore store = new WorkdayStore(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = UiKit.pageRoot(this); scroll.addView(root);
        root.addView(UiKit.title(this, "🗓 工作日與請假日", 26f));
        CalendarView cal = new CalendarView(this);
        cal.setOnDateChangeListener((v,y,m,d) -> { selected = String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d); refreshStatus(); });
        root.addView(cal);
        status = UiKit.title(this, "", 18f); root.addView(status); refreshStatus();
        android.widget.Button holiday = UiKit.button(this, "標記為休假日");
        holiday.setOnClickListener(v -> { store.markHoliday(selected); refreshStatus(); }); root.addView(holiday);
        root.addView(UiKit.gap(this,6));
        android.widget.Button work = UiKit.button(this, "標記為工作日／補班日");
        work.setOnClickListener(v -> { store.markWorkingDay(selected); refreshStatus(); }); root.addView(work);
        root.addView(UiKit.gap(this,6));
        android.widget.Button clear = UiKit.button(this, "清除手動指定");
        clear.setOnClickListener(v -> { store.clear(selected); refreshStatus(); }); root.addView(clear);
        setContentView(scroll);
    }

    private void refreshStatus() {
        if (status != null) status.setText(selected + "：" + new WorkdayStore(this).status(selected));
    }
}
