package com.bearfamily.app.bearontime;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CalendarView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PlannerActivity extends Activity {
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN);
    private String selectedDate;
    private LinearLayout listArea;
    private PlannerStore store;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new PlannerStore(this);
        selectedDate = fmt.format(new Date());
        CrashGuard.run(this, "日曆與待辦", this::buildUi);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = UiKit.pageRoot(this);
        scroll.addView(root);
        root.addView(UiKit.title(this, "📅 我的日曆與待辦", 26f));
        CalendarView cal = new CalendarView(this);
        cal.setDate(System.currentTimeMillis(), false, true);
        cal.setOnDateChangeListener((view, year, month, day) -> {
            selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            refreshList();
        });
        root.addView(cal);
        android.widget.Button add = UiKit.button(this, "＋ 新增待辦");
        add.setOnClickListener(v -> editDialog(null));
        root.addView(add);
        root.addView(UiKit.gap(this, 10));
        listArea = new LinearLayout(this);
        listArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(listArea);
        refreshList();
        setContentView(scroll);
    }

    private void refreshList() {
        if (listArea == null) return;
        listArea.removeAllViews();
        listArea.addView(UiKit.title(this, selectedDate + " 待辦", 20f));
        List<PlannerItem> list = store.forDate(selectedDate);
        if (list.isEmpty()) {
            listArea.addView(UiKit.body(this, "這一天沒有待辦事項。"));
            return;
        }
        for (PlannerItem p : list) {
            LinearLayout card = UiKit.card(this);
            CheckBox done = new CheckBox(this);
            done.setText(priorityIcon(p.priority) + " " + p.title);
            done.setChecked(p.done);
            done.setOnCheckedChangeListener((buttonView, isChecked) -> { p.done = isChecked; store.upsert(p); });
            card.addView(done);
            if (!p.notes.trim().isEmpty()) card.addView(UiKit.body(this, p.notes));
            android.widget.Button edit = UiKit.button(this, "編輯");
            edit.setOnClickListener(v -> editDialog(p));
            card.addView(edit);
            android.widget.Button del = UiKit.button(this, "刪除");
            del.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("刪除待辦")
                    .setMessage("確定刪除「" + p.title + "」？").setNegativeButton("取消", null)
                    .setPositiveButton("刪除", (d, w) -> { store.delete(p.id); refreshList(); }).show());
            card.addView(UiKit.gap(this, 4));
            card.addView(del);
            listArea.addView(card);
        }
    }

    private void editDialog(PlannerItem existing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 8), UiKit.dp(this, 20), 0);
        EditText title = new EditText(this);
        title.setHint("待辦內容");
        EditText notes = new EditText(this);
        notes.setHint("備註（選填）");
        Spinner priority = new Spinner(this);
        String[] levels = {"緊急", "重要", "普通", "不重要", "其他"};
        priority.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, levels));
        if (existing != null) {
            title.setText(existing.title); notes.setText(existing.notes);
            for (int i = 0; i < levels.length; i++) if (levels[i].equals(existing.priority)) priority.setSelection(i);
        }
        box.addView(title); box.addView(priority); box.addView(notes);
        new AlertDialog.Builder(this).setTitle(existing == null ? "新增待辦" : "編輯待辦").setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("儲存", (d, w) -> {
                    String t = title.getText().toString().trim();
                    if (t.trim().isEmpty()) { Toast.makeText(this, "待辦內容不可空白", Toast.LENGTH_SHORT).show(); return; }
                    PlannerItem p = existing == null ? new PlannerItem() : existing;
                    p.date = selectedDate; p.title = t; p.notes = notes.getText().toString().trim();
                    p.priority = levels[priority.getSelectedItemPosition()];
                    store.upsert(p); refreshList();
                }).show();
    }

    private String priorityIcon(String p) {
        if ("緊急".equals(p)) return "🔴";
        if ("重要".equals(p)) return "🟠";
        if ("不重要".equals(p)) return "⚪";
        if ("其他".equals(p)) return "🔵";
        return "🟢";
    }
}
