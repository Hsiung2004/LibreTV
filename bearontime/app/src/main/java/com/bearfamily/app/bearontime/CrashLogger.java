package com.bearfamily.app.bearontime;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CrashLogger {
    private static final String DIR = "BearOnTime/diagnostics";
    private static final String LAST_CRASH = "last_crash.txt";
    private static final String NON_FATAL = "non_fatal.log";

    private CrashLogger() {}

    public static void install(Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try { writeCrash(app, "UNCAUGHT", thread == null ? "unknown" : thread.getName(), throwable, LAST_CRASH, false); }
            catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }

    public static void recordNonFatal(Context context, String area, Throwable throwable) {
        try { writeCrash(context.getApplicationContext(), "NON_FATAL", area, throwable, NON_FATAL, true); }
        catch (Throwable ignored) {}
    }

    private static void writeCrash(Context c, String type, String area, Throwable t, String fileName, boolean append) throws Exception {
        File dir = new File(c.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, fileName);
        StringWriter sw = new StringWriter();
        if (t != null) t.printStackTrace(new PrintWriter(sw));
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.TAIWAN).format(new Date());
        String text = "\n============================================================\n" +
                "Time: " + time + "\n" +
                "Type: " + type + "\n" +
                "Area: " + area + "\n" +
                "App: " + Brand.APP_NAME + " " + Brand.VERSION + "\n" +
                "Package: " + c.getPackageName() + "\n" +
                "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n" +
                "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                sw + "\n";
        java.io.FileOutputStream fos = new java.io.FileOutputStream(out, append);
        try { fos.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        finally { fos.close(); }
    }

    public static String readLastCrash(Context c) {
        try {
            File f = new File(c.getFilesDir(), DIR + "/" + LAST_CRASH);
            return f.isFile() ? VoiceModuleManager.readUtf8(f) : "";
        } catch (Exception e) { return ""; }
    }

    public static String readNonFatal(Context c) {
        try {
            File f = new File(c.getFilesDir(), DIR + "/" + NON_FATAL);
            return f.isFile() ? VoiceModuleManager.readUtf8(f) : "";
        } catch (Exception e) { return ""; }
    }

    public static void clear(Context c) {
        File dir = new File(c.getFilesDir(), DIR);
        new File(dir, LAST_CRASH).delete();
        new File(dir, NON_FATAL).delete();
    }
}
