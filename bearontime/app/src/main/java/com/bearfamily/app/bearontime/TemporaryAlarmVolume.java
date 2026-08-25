package com.bearfamily.app.bearontime;

import android.content.Context;
import android.media.AudioManager;

public final class TemporaryAlarmVolume {
    private final Context context;
    private Integer oldVolume;

    public TemporaryAlarmVolume(Context context) { this.context = context.getApplicationContext(); }

    public void raiseIfNeeded(SettingsStore settings) {
        if (!settings.isForceAlarmChannel() || !settings.isRaiseAlarmVolume()) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int target = Math.max(1, Math.min(max, Math.round(max * settings.getAppVolume() / 100f)));
            oldVolume = am.getStreamVolume(AudioManager.STREAM_ALARM);
            if (oldVolume < target) am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);
        } catch (Exception ignored) {}
    }

    public void restore() {
        if (oldVolume == null) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            am.setStreamVolume(AudioManager.STREAM_ALARM, oldVolume, 0);
        } catch (Exception ignored) {}
        oldVolume = null;
    }
}
