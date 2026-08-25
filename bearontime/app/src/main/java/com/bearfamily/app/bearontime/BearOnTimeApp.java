package com.bearfamily.app.bearontime;

import android.app.Application;

public class BearOnTimeApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashLogger.install(this);
        try {
            VoiceModuleManager manager = new VoiceModuleManager(this);
            manager.ensureBuiltinInstalled();
            SettingsStore settings = new SettingsStore(this);
            if (!manager.moduleExists(settings.getSelectedVoiceModuleId())) {
                settings.setSelectedVoiceModuleId("builtin_system_tts");
            }
        } catch (Throwable t) {
            CrashLogger.recordNonFatal(this, "Application startup repair", t);
        }
    }
}
