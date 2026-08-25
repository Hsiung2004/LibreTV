package com.bearfamily.app.bearontime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Toast;
import java.io.InputStream;
import java.util.Calendar;
import java.util.List;

public class VoiceModulesActivity extends Activity {
    private static final int REQ_IMPORT = 4101;
    private VoicePlayback preview;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preview = new VoicePlayback(this);
        new VoiceModuleManager(this).ensureBuiltinInstalled();
        CrashGuard.run(this, "報時聲音組", this::buildUi);
    }

    @Override protected void onResume() { super.onResume(); CrashGuard.run(this, "報時聲音組重新整理", this::buildUi); }
    @Override protected void onDestroy() { if (preview != null) preview.stop(); super.onDestroy(); }

    private void buildUi() {
        SettingsStore settings = new SettingsStore(this);
        VoiceModuleManager manager = new VoiceModuleManager(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = UiKit.pageRoot(this);
        scroll.addView(root);
        root.addView(UiKit.title(this, "🔊 報時聲音組", 26f));
        root.addView(UiKit.body(this, "勾選目前使用語音；每組可直接試聽。自訂 ZIP 需包含 00_00.mp3 至 23_00.mp3。"));
        root.addView(UiKit.gap(this, 12));

        List<VoiceModule> modules = manager.listModules();
        for (VoiceModule m : modules) {
            LinearLayout card = UiKit.card(this);
            RadioButton radio = new RadioButton(this);
            radio.setText(m.name + (m.builtin ? "（內建）" : ""));
            radio.setChecked(m.id.equals(settings.getSelectedVoiceModuleId()));
            radio.setOnClickListener(v -> {
                settings.setSelectedVoiceModuleId(m.id);
                Toast.makeText(this, "已選擇：" + m.name, Toast.LENGTH_SHORT).show();
                buildUi();
            });
            card.addView(radio);
            card.addView(UiKit.body(this, "版本 " + m.version + "　" + m.author));
            android.widget.Button test = UiKit.button(this, "▶ 試聽");
            test.setOnClickListener(v -> {
                int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                java.io.File f = manager.voiceFile(m.id, hour);
                if (f != null) preview.playFile(f, settings.getAppVolume(), settings.isForceAlarmChannel(), null);
                else preview.speak(TimeTextFormatter.currentHourText(), settings.getAppVolume(), null);
            });
            card.addView(UiKit.gap(this, 6));
            card.addView(test);
            if (!m.builtin) {
                android.widget.Button del = UiKit.button(this, "刪除此自訂語音");
                del.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle("刪除語音")
                        .setMessage("確定刪除「" + m.name + "」？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("刪除", (d, w) -> {
                            if (m.id.equals(settings.getSelectedVoiceModuleId())) settings.setSelectedVoiceModuleId("builtin_system_tts");
                            manager.deleteModule(m.id);
                            buildUi();
                        }).show());
                card.addView(UiKit.gap(this, 6));
                card.addView(del);
            }
            root.addView(card);
        }

        android.widget.Button add = UiKit.button(this, "＋ 新增語音模組 ZIP");
        add.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");
            startActivityForResult(i, REQ_IMPORT);
        });
        root.addView(add);
        setContentView(scroll);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = fileName(uri);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalArgumentException("無法讀取 ZIP");
            VoiceModule module = new VoiceModuleManager(this).importZip(in, name);
            Toast.makeText(this, "已新增語音：" + module.name, Toast.LENGTH_LONG).show();
            buildUi();
        } catch (Exception ex) {
            new AlertDialog.Builder(this).setTitle("語音模組匯入失敗").setMessage(ex.getMessage()).setPositiveButton("知道了", null).show();
        }
    }

    private String fileName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return "自訂語音.zip";
    }
}
