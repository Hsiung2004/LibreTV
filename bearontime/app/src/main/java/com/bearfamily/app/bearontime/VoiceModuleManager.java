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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class VoiceModuleManager {
    private static final long MAX_TOTAL_BYTES = 80L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 120;
    private final Context context;
    private final File root;

    public VoiceModuleManager(Context context) {
        this.context = context.getApplicationContext();
        this.root = new File(context.getFilesDir(), "BearOnTime/voices");
        if (!root.exists()) root.mkdirs();
    }

    public File getRoot() { return root; }

    public synchronized void ensureBuiltinInstalled() {
        if (!root.exists()) root.mkdirs();
    }

    public List<VoiceModule> listModules() {
        ensureBuiltinInstalled();
        List<VoiceModule> out = new ArrayList<>();
        out.add(new VoiceModule("builtin_system_tts", "系統中文語音", Brand.STUDIO, "1.0", "", true, null));
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                if (dir.getName().startsWith(".stage_")) continue;
                VoiceModule m = readModule(dir);
                if (m != null && !m.builtin && validateModuleDirectory(dir)) out.add(m);
            }
        }
        out.sort(Comparator.comparing((VoiceModule m) -> !m.builtin).thenComparing(m -> m.name));
        return out;
    }

    public String getModuleName(String id) {
        for (VoiceModule m : listModules()) if (m.id.equals(id)) return m.name;
        return "系統中文語音";
    }

    public boolean moduleExists(String id) { return getModule(id) != null; }

    public VoiceModule getModule(String id) {
        for (VoiceModule m : listModules()) if (m.id.equals(id)) return m;
        return null;
    }

    public File voiceFile(String moduleId, int hour) {
        VoiceModule m = getModule(moduleId);
        if (m == null || m.builtin || m.directory == null) return null;
        File f = new File(m.directory, String.format(Locale.US, "%02d_00.mp3", Math.max(0, Math.min(23, hour))));
        return f.isFile() ? f : null;
    }

    public synchronized VoiceModule importZip(InputStream input, String displayFileName) throws Exception {
        String id = "custom_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);
        File stage = new File(root, ".stage_" + id);
        deleteRecursively(stage);
        stage.mkdirs();
        try {
            extractPack(input, stage, true);
            if (!validateModuleDirectory(stage)) throw new IllegalArgumentException("語音包必須包含 00_00.mp3 至 23_00.mp3 共 24 個整點音檔。 ");
            String name = readRequestedName(stage);
            if (name == null || name.trim().isEmpty()) name = stripExtension(displayFileName == null ? "自訂語音" : displayFileName);
            name = cleanName(name);
            JSONObject manifest = new JSONObject();
            manifest.put("format", 1);
            manifest.put("id", id);
            manifest.put("name", name);
            manifest.put("author", "使用者匯入");
            manifest.put("version", "1.0");
            manifest.put("preview", "08_00.mp3");
            writeUtf8(new File(stage, "manifest.json"), manifest.toString(2));
            new File(stage, "config.xml").delete();

            File dest = new File(root, id);
            copyDirectory(stage, dest);
            deleteRecursively(stage);
            return readModule(dest);
        } catch (Exception ex) {
            deleteRecursively(stage);
            throw ex;
        }
    }

    public synchronized boolean deleteModule(String id) {
        VoiceModule m = getModule(id);
        if (m == null || m.builtin || m.directory == null) return false;
        deleteRecursively(m.directory);
        return !m.directory.exists();
    }

    public boolean validateModuleDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        for (int h = 0; h < 24; h++) {
            File f = new File(dir, String.format(Locale.US, "%02d_00.mp3", h));
            if (!f.isFile() || f.length() <= 0) return false;
        }
        return true;
    }

    public void clearCustomModules() {
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return;
        for (File d : dirs) if (!d.getName().startsWith(".stage_")) deleteRecursively(d);
    }

    private VoiceModule readModule(File dir) {
        try {
            File mf = new File(dir, "manifest.json");
            JSONObject obj = mf.isFile() ? new JSONObject(readUtf8(mf)) : new JSONObject();
            String id = obj.optString("id", dir.getName());
            String name = cleanName(obj.optString("name", dir.getName()));
            String author = obj.optString("author", dir.getName().startsWith("builtin_") ? Brand.STUDIO : "使用者匯入");
            String version = obj.optString("version", "1.0");
            String preview = obj.optString("preview", "08_00.mp3");
            boolean builtin = new File(dir, ".builtin").exists() || dir.getName().startsWith("builtin_");
            return new VoiceModule(id, name, author, version, preview, builtin, dir);
        } catch (Exception ex) { return null; }
    }

    private void extractPack(InputStream input, File dest, boolean acceptLegacyConfig) throws Exception {
        long total = 0;
        int count = 0;
        String canonicalRoot = dest.getCanonicalPath() + File.separator;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zin.getNextEntry()) != null) {
                count++;
                if (count > MAX_ENTRIES) throw new IllegalArgumentException("語音包檔案數過多");
                String raw = entry.getName().replace('\\', '/');
                String name = new File(raw).getName();
                if (entry.isDirectory()) continue;
                if (!(name.matches("\\d{2}_00\\.mp3") || "manifest.json".equals(name) || (acceptLegacyConfig && "config.xml".equals(name)))) continue;
                File out = new File(dest, name);
                String canonical = out.getCanonicalPath();
                if (!canonical.startsWith(canonicalRoot)) throw new SecurityException("不安全的 ZIP 路徑");
                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out))) {
                    int n;
                    while ((n = zin.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_TOTAL_BYTES) throw new IllegalArgumentException("語音包容量超過限制");
                        bos.write(buf, 0, n);
                    }
                }
            }
        }
    }

    private String readRequestedName(File dir) {
        try {
            File mf = new File(dir, "manifest.json");
            if (mf.isFile()) return new JSONObject(readUtf8(mf)).optString("name", "");
            File legacy = new File(dir, "config.xml");
            if (legacy.isFile()) {
                Matcher m = Pattern.compile("<name>(.*?)</name>", Pattern.DOTALL).matcher(readUtf8(legacy));
                if (m.find()) return m.group(1).replaceAll("<[^>]+>", "").trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String cleanName(String name) {
        String s = name == null ? "自訂語音" : name.replaceAll("[\\r\\n\\t]", " ").trim();
        if (s.trim().isEmpty()) s = "自訂語音";
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private static String stripExtension(String name) {
        int p = name.lastIndexOf('.');
        return p > 0 ? name.substring(0, p) : name;
    }

    static String readUtf8(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    static void writeUtf8(File file, String text) throws Exception {
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
    }

    static void copyDirectory(File src, File dst) throws Exception {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) throw new IllegalStateException("無法建立資料夾");
            File[] children = src.listFiles();
            if (children != null) for (File c : children) copyDirectory(c, new File(dst, c.getName()));
        } else {
            dst.getParentFile().mkdirs();
            try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
    }

    static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File c : children) deleteRecursively(c);
        }
        file.delete();
    }
}
