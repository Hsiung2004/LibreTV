package com.bearfamily.app.bearontime;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class Diagnostics {
    private Diagnostics() {}

    public static String buildReport(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("熊正點報時 系統診斷\n");
        sb.append("產生時間：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.TAIWAN).format(new Date())).append("\n\n");
        sb.append("[版本]\n");
        sb.append("App：").append(Brand.APP_NAME).append(" ").append(Brand.VERSION).append("\n");
        sb.append("Package：").append(c.getPackageName()).append("\n");
        sb.append("Android：").append(Build.VERSION.RELEASE).append(" / SDK ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("裝置：").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n\n");

        sb.append("[權限]\n");
        boolean notif = Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        NotificationManager nm = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean dnd = Build.VERSION.SDK_INT < 23 || nm.isNotificationPolicyAccessGranted();
        sb.append("通知：").append(pass(notif)).append("\n");
        sb.append("精準鬧鐘：").append(pass(exact)).append("\n");
        sb.append("勿擾政策：").append(pass(dnd)).append("\n\n");

        sb.append("[儲存空間]\n");
        File files = c.getFilesDir();
        sb.append("FilesDir：").append(files.getAbsolutePath()).append("\n");
        sb.append("可寫入：").append(pass(files.canWrite())).append("\n");
        sb.append("可用空間：").append(files.getUsableSpace() / (1024L*1024L)).append(" MB\n\n");

        sb.append("[語音模組]\n");
        VoiceModuleManager vm = new VoiceModuleManager(c);
        vm.ensureBuiltinInstalled();
        List<VoiceModule> modules = vm.listModules();
        SettingsStore settings = new SettingsStore(c);
        sb.append("目前選擇：").append(settings.getSelectedVoiceModuleId()).append("\n");
        sb.append("模組數：").append(modules.size()).append("\n");
        for (VoiceModule m : modules) {
            sb.append("- ").append(m.name).append(" [").append(m.id).append("] ")
              .append(m.builtin ? "TTS PASS" : (vm.validateModuleDirectory(m.directory) ? "24/24 PASS" : "FAIL"))
              .append(m.builtin ? " / built-in" : " / custom").append("\n");
        }
        sb.append("\n[排程設定]\n");
        sb.append("自動報時：").append(settings.isEnabled() ? "ON" : "OFF").append("\n");
        sb.append("半點提示：").append(settings.isHalfHourEnabled() ? "ON" : "OFF").append("\n");
        sb.append("啟用整點數：").append(settings.getEnabledHours().size()).append(" / 24\n");
        sb.append("下一次預估：").append(TimeAlarmScheduler.nextTriggerSummary(settings.isHalfHourEnabled())).append("\n");

        String last = CrashLogger.readLastCrash(c);
        sb.append("\n[最近崩潰]\n");
        sb.append(last.isEmpty() ? "無已記錄的未捕捉崩潰。\n" : last);
        String nf = CrashLogger.readNonFatal(c);
        if (!nf.isEmpty()) {
            sb.append("\n[安全模式攔截紀錄]\n").append(nf);
        }
        return sb.toString();
    }

    private static String pass(boolean ok) { return ok ? "PASS" : "NEEDS ATTENTION"; }
}
