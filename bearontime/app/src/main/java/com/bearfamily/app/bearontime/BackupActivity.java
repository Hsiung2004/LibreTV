package com.bearfamily.app.bearontime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackupActivity extends Activity {
    private static final int REQ_EXPORT = 5101, REQ_IMPORT = 5102;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashGuard.run(this, "備份與還原", this::buildUi);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); LinearLayout root = UiKit.pageRoot(this); scroll.addView(root);
        root.addView(UiKit.title(this, "💾 備份與還原", 26f));
        root.addView(UiKit.body(this, "備份包含：報時設定、主題、待辦、工作日／休假日、自訂語音模組。"));
        root.addView(UiKit.gap(this,12));
        android.widget.Button exp = UiKit.button(this, "匯出完整備份 ZIP");
        exp.setOnClickListener(v -> exportBackup()); root.addView(exp);
        root.addView(UiKit.gap(this,8));
        android.widget.Button imp = UiKit.button(this, "匯入備份 ZIP");
        imp.setOnClickListener(v -> importBackup()); root.addView(imp);
        setContentView(scroll);
    }

    private void exportBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/zip");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.TAIWAN).format(new Date());
        i.putExtra(Intent.EXTRA_TITLE, "BearOnTime_Backup_" + stamp + ".zip");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/zip");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalArgumentException("無法建立備份檔");
                    new BackupManager(this).exportTo(out);
                }
                Toast.makeText(this, "備份完成", Toast.LENGTH_LONG).show();
            } else if (requestCode == REQ_IMPORT) {
                new AlertDialog.Builder(this).setTitle("匯入備份")
                        .setMessage("匯入會以備份內容還原目前設定與自訂語音，是否繼續？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("開始還原", (d,w) -> restore(uri)).show();
            }
        } catch (Exception ex) { showError(ex); }
    }

    private void restore(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalArgumentException("無法讀取備份檔");
            new BackupManager(this).restoreFrom(in);
            Toast.makeText(this, "還原完成", Toast.LENGTH_LONG).show();
        } catch (Exception ex) { showError(ex); }
    }

    private void showError(Exception ex) {
        new AlertDialog.Builder(this).setTitle("作業失敗").setMessage(ex.getMessage()).setPositiveButton("知道了", null).show();
    }
}
