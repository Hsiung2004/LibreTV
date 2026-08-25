package com.bearfamily.app.bearontime;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DiagnosticsActivity extends Activity {
    private static final int REQ_EXPORT = 8201;
    private TextView reportView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashGuard.run(this, "系統診斷", this::buildUi);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = UiKit.pageRoot(this);
        scroll.addView(root);
        root.addView(UiKit.title(this, "🧪 系統診斷", 26f));
        root.addView(UiKit.body(this, "先確認底層健康，再加新功能。這頁可在實機問題發生後直接匯出診斷檔。"));
        root.addView(UiKit.gap(this,10));

        reportView = UiKit.body(this, Diagnostics.buildReport(this));
        reportView.setTextIsSelectable(true);
        LinearLayout card = UiKit.card(this);
        card.addView(reportView);
        root.addView(card);

        android.widget.Button refresh = UiKit.button(this, "重新執行診斷");
        refresh.setOnClickListener(v -> reportView.setText(Diagnostics.buildReport(this)));
        root.addView(refresh);
        root.addView(UiKit.gap(this,6));

        android.widget.Button export = UiKit.button(this, "匯出診斷 TXT");
        export.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/plain");
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.TAIWAN).format(new Date());
            i.putExtra(Intent.EXTRA_TITLE, "BearOnTime_Diagnostics_" + stamp + ".txt");
            startActivityForResult(i, REQ_EXPORT);
        });
        root.addView(export);
        root.addView(UiKit.gap(this,6));

        android.widget.Button clear = UiKit.button(this, "清除舊錯誤紀錄");
        clear.setOnClickListener(v -> {
            CrashLogger.clear(this);
            reportView.setText(Diagnostics.buildReport(this));
            Toast.makeText(this, "錯誤紀錄已清除", Toast.LENGTH_SHORT).show();
        });
        root.addView(clear);
        setContentView(scroll);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("無法建立檔案");
            out.write(Diagnostics.buildReport(this).getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "診斷檔已匯出", Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            CrashLogger.recordNonFatal(this, "匯出診斷", ex);
            Toast.makeText(this, "匯出失敗：" + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
