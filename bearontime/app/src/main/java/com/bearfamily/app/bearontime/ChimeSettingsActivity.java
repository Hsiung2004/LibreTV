package com.bearfamily.app.bearontime;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChimeSettingsActivity extends Activity {
    private final List<CheckBox> hourChecks = new ArrayList<>();
    private Switch enabledSwitch;
    private Switch halfSwitch;
    private Switch forceAlarmSwitch;
    private Switch raiseVolumeSwitch;
    private Switch skipHolidaySwitch;
    private SeekBar volume;
    private TextView volumeLabel;
    private Spinner halfTone;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashGuard.run(this, "整點與半點報時", this::buildUi);
    }

    private void buildUi() {
        SettingsStore s = new SettingsStore(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = UiKit.pageRoot(this);
        scroll.addView(root);
        root.addView(UiKit.title(this, "🕐 整點與半點報時", 26f));
        root.addView(UiKit.body(this, "所有控制元件均直接建立，不依賴舊版 View ID。"));
        root.addView(UiKit.gap(this, 12));

        LinearLayout card = UiKit.card(this);
        enabledSwitch = new Switch(this);
        enabledSwitch.setText("啟用自動報時");
        enabledSwitch.setChecked(s.isEnabled());
        card.addView(enabledSwitch);

        volumeLabel = UiKit.body(this, "App 音量：" + s.getAppVolume() + "%");
        card.addView(volumeLabel);
        volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(s.getAppVolume());
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { volumeLabel.setText("App 音量：" + progress + "%"); }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(volume);

        forceAlarmSwitch = new Switch(this);
        forceAlarmSwitch.setText("使用鬧鐘音訊通道");
        forceAlarmSwitch.setChecked(s.isForceAlarmChannel());
        card.addView(forceAlarmSwitch);

        raiseVolumeSwitch = new Switch(this);
        raiseVolumeSwitch.setText("報時時暫時提高鬧鐘音量");
        raiseVolumeSwitch.setChecked(s.isRaiseAlarmVolume());
        card.addView(raiseVolumeSwitch);

        skipHolidaySwitch = new Switch(this);
        skipHolidaySwitch.setText("休假日略過自動報時");
        skipHolidaySwitch.setChecked(s.isSkipHolidays());
        card.addView(skipHolidaySwitch);
        root.addView(card);

        LinearLayout hoursCard = UiKit.card(this);
        hoursCard.addView(UiKit.title(this, "整點啟用時段", 20f));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        Set<Integer> selected = s.getEnabledHours();
        hourChecks.clear();
        for (int h = 0; h < 24; h++) {
            CheckBox cb = new CheckBox(this);
            cb.setText(String.format("%02d:00", h));
            cb.setChecked(selected.contains(h));
            cb.setTag(h);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            cb.setLayoutParams(lp);
            grid.addView(cb);
            hourChecks.add(cb);
        }
        hoursCard.addView(grid);
        root.addView(hoursCard);

        LinearLayout halfCard = UiKit.card(this);
        halfSwitch = new Switch(this);
        halfSwitch.setText("啟用半點提示音（xx:30）");
        halfSwitch.setChecked(s.isHalfHourEnabled());
        halfCard.addView(halfSwitch);
        halfCard.addView(UiKit.body(this, "半點音效"));
        halfTone = new Spinner(this);
        String[] toneNames = {"清脆鐘聲", "咕咕鐘", "經典鐘聲", "電子提示"};
        halfTone.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, toneNames));
        String tone = s.getHalfHourTone();
        int idx = "cuckoo".equals(tone) ? 1 : "classic".equals(tone) ? 2 : "electronic".equals(tone) ? 3 : 0;
        halfTone.setSelection(idx);
        halfCard.addView(halfTone);
        root.addView(halfCard);

        android.widget.Button save = UiKit.button(this, "儲存並重新排程");
        save.setOnClickListener(v -> save());
        root.addView(save);
        root.addView(UiKit.gap(this, 8));
        android.widget.Button testHour = UiKit.button(this, "▶ 立即試播目前整點語音");
        testHour.setOnClickListener(v -> startVoiceService(VoiceAnnounceService.ACTION_TEST_HOUR, null));
        root.addView(testHour);
        root.addView(UiKit.gap(this, 8));
        android.widget.Button testHalf = UiKit.button(this, "▶ 試播半點提示音");
        testHalf.setOnClickListener(v -> {
            saveOnly();
            startVoiceService(VoiceAnnounceService.ACTION_TEST_HALF, null);
        });
        root.addView(testHalf);
        setContentView(scroll);
    }

    private void saveOnly() {
        SettingsStore s = new SettingsStore(this);
        s.setEnabled(enabledSwitch.isChecked());
        s.setAppVolume(volume.getProgress());
        s.setForceAlarmChannel(forceAlarmSwitch.isChecked());
        s.setRaiseAlarmVolume(raiseVolumeSwitch.isChecked());
        s.setSkipHolidays(skipHolidaySwitch.isChecked());
        s.setHalfHourEnabled(halfSwitch.isChecked());
        String[] keys = {"chime", "cuckoo", "classic", "electronic"};
        s.setHalfHourTone(keys[Math.max(0, Math.min(3, halfTone.getSelectedItemPosition()))]);
        Set<Integer> hours = new HashSet<>();
        for (CheckBox cb : hourChecks) if (cb.isChecked()) hours.add((Integer) cb.getTag());
        s.setEnabledHours(hours);
    }

    private void save() {
        saveOnly();
        SettingsStore s = new SettingsStore(this);
        if (s.isEnabled()) TimeAlarmScheduler.scheduleNext(this); else TimeAlarmScheduler.cancel(this);
        Toast.makeText(this, "設定已儲存", Toast.LENGTH_SHORT).show();
    }

    private void startVoiceService(String action, String eventType) {
        Intent i = new Intent(this, VoiceAnnounceService.class).setAction(action);
        if (eventType != null) i.putExtra(VoiceAnnounceService.EXTRA_EVENT_TYPE, eventType);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }
}
