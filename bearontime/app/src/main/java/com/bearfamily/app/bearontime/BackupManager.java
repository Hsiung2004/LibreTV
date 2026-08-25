package com.bearfamily.app.bearontime;

import android.content.Context;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class BackupManager {
    private static final long MAX_RESTORE_BYTES = 150L * 1024L * 1024L;
    private final Context context;

    public BackupManager(Context context) { this.context = context.getApplicationContext(); }

    public void exportTo(OutputStream output) throws Exception {
        try (ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(output))) {
            JSONObject info = new JSONObject();
            info.put("app", Brand.APP_ID);
            info.put("display_name", Brand.APP_NAME);
            info.put("backup_version", 1);
            info.put("app_version", Brand.VERSION);
            putText(zout, "backup_info.json", info.toString(2));
            putText(zout, "settings.json", new SettingsStore(context).toJson());
            putText(zout, "planner.json", new PlannerStore(context).exportJson());
            putText(zout, "workdays.json", new WorkdayStore(context).exportJson());

            VoiceModuleManager vm = new VoiceModuleManager(context);
            vm.ensureBuiltinInstalled();
            File[] dirs = vm.getRoot().listFiles(File::isDirectory);
            if (dirs != null) for (File dir : dirs) {
                if (new File(dir, ".builtin").exists() || dir.getName().startsWith("builtin_") || dir.getName().startsWith(".stage_")) continue;
                addDirectory(zout, dir, "voices/" + dir.getName() + "/");
            }
        }
    }

    public void restoreFrom(InputStream input) throws Exception {
        File stage = new File(context.getCacheDir(), "bearontime_restore_stage");
        VoiceModuleManager.deleteRecursively(stage); stage.mkdirs();
        long total = 0; int entries = 0;
        String rootPath = stage.getCanonicalPath() + File.separator;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry e; byte[] buf = new byte[8192];
            while ((e = zin.getNextEntry()) != null) {
                if (++entries > 500) throw new IllegalArgumentException("備份檔內容過多");
                if (e.isDirectory()) continue;
                File out = new File(stage, e.getName());
                if (!out.getCanonicalPath().startsWith(rootPath)) throw new SecurityException("備份檔路徑不安全");
                out.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int n; while ((n = zin.read(buf)) > 0) {
                        total += n; if (total > MAX_RESTORE_BYTES) throw new IllegalArgumentException("備份檔過大");
                        fos.write(buf,0,n);
                    }
                }
            }
        }

        File info = new File(stage, "backup_info.json");
        if (!info.isFile()) throw new IllegalArgumentException("不是有效的熊正點報時備份檔");
        JSONObject metadata = new JSONObject(VoiceModuleManager.readUtf8(info));
        if (!Brand.APP_ID.equals(metadata.optString("app", ""))) throw new IllegalArgumentException("備份檔類型不相容");

        VoiceModuleManager vm = new VoiceModuleManager(context);
        File stagedVoices = new File(stage, "voices");
        if (stagedVoices.isDirectory()) {
            File[] checkDirs = stagedVoices.listFiles(File::isDirectory);
            if (checkDirs != null) for (File dir : checkDirs) {
                if (!vm.validateModuleDirectory(dir)) throw new IllegalArgumentException("備份內語音模組不完整：" + dir.getName());
            }
        }

        File settings = new File(stage, "settings.json");
        File planner = new File(stage, "planner.json");
        File workdays = new File(stage, "workdays.json");
        if (settings.isFile()) new SettingsStore(context).importJson(VoiceModuleManager.readUtf8(settings));
        if (planner.isFile()) new PlannerStore(context).importJson(VoiceModuleManager.readUtf8(planner));
        if (workdays.isFile()) new WorkdayStore(context).importJson(VoiceModuleManager.readUtf8(workdays));

        vm.ensureBuiltinInstalled();
        if (stagedVoices.isDirectory()) {
            vm.clearCustomModules();
            File[] dirs = stagedVoices.listFiles(File::isDirectory);
            if (dirs != null) for (File dir : dirs) {
                if (!vm.validateModuleDirectory(dir)) continue;
                File dest = new File(vm.getRoot(), dir.getName());
                VoiceModuleManager.copyDirectory(dir, dest);
            }
        }
        SettingsStore ss = new SettingsStore(context);
        if (!vm.moduleExists(ss.getSelectedVoiceModuleId())) ss.setSelectedVoiceModuleId("builtin_system_tts");
        if (ss.isEnabled()) TimeAlarmScheduler.scheduleNext(context); else TimeAlarmScheduler.cancel(context);
        VoiceModuleManager.deleteRecursively(stage);
    }

    private void putText(ZipOutputStream zout, String name, String text) throws Exception {
        zout.putNextEntry(new ZipEntry(name));
        zout.write(text.getBytes(StandardCharsets.UTF_8));
        zout.closeEntry();
    }

    private void addDirectory(ZipOutputStream zout, File dir, String prefix) throws Exception {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.getName().equals(".builtin")) continue;
            if (f.isDirectory()) addDirectory(zout, f, prefix + f.getName() + "/");
            else {
                zout.putNextEntry(new ZipEntry(prefix + f.getName()));
                try (FileInputStream in = new FileInputStream(f)) {
                    byte[] buf = new byte[8192]; int n; while ((n=in.read(buf))>0) zout.write(buf,0,n);
                }
                zout.closeEntry();
            }
        }
    }
}
