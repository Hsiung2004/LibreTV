package com.bearfamily.app.bearontime;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.io.File;
import java.util.Calendar;

public class VoiceAnnounceService extends Service {
    public static final String ACTION_EVENT = "com.bearfamily.app.bearontime.ACTION_EVENT";
    public static final String ACTION_TEST_HOUR = "com.bearfamily.app.bearontime.ACTION_TEST_HOUR";
    public static final String ACTION_TEST_HALF = "com.bearfamily.app.bearontime.ACTION_TEST_HALF";
    public static final String EXTRA_EVENT_TYPE = "event_type";
    private static final String CHANNEL_ID = "bearontime_voice_service";
    private VoicePlayback playback;
    private Handler handler;

    @Override public void onCreate() {
        super.onCreate();
        playback = new VoicePlayback(this);
        handler = new Handler(Looper.getMainLooper());
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(2001, notification());
        String action = intent == null ? ACTION_EVENT : intent.getAction();
        String event = intent == null ? TimeAlarmScheduler.EVENT_HOUR : intent.getStringExtra(EXTRA_EVENT_TYPE);
        if (ACTION_TEST_HALF.equals(action)) playHalf(startId, true);
        else if (ACTION_TEST_HOUR.equals(action)) playHour(startId, true);
        else if (TimeAlarmScheduler.EVENT_HALF.equals(event)) playHalf(startId, false);
        else playHour(startId, false);
        handler.postDelayed(() -> stopSelf(startId), 45_000);
        return START_NOT_STICKY;
    }

    private void playHour(int startId, boolean test) {
        SettingsStore s = new SettingsStore(this);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (!test && (!s.isEnabled() || !s.getEnabledHours().contains(hour))) { stopSelf(startId); return; }
        VoiceModuleManager manager = new VoiceModuleManager(this);
        manager.ensureBuiltinInstalled();
        if (!manager.moduleExists(s.getSelectedVoiceModuleId())) s.setSelectedVoiceModuleId("builtin_system_tts");
        File file = manager.voiceFile(s.getSelectedVoiceModuleId(), hour);
        TemporaryAlarmVolume guard = new TemporaryAlarmVolume(this);
        guard.raiseIfNeeded(s);
        Runnable done = () -> { guard.restore(); stopSelf(startId); };
        if (file != null) playback.playFile(file, s.getAppVolume(), s.isForceAlarmChannel(), done);
        else playback.speak(TimeTextFormatter.currentHourText(), s.getAppVolume(), done);
    }

    private void playHalf(int startId, boolean test) {
        SettingsStore s = new SettingsStore(this);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (!s.isHalfHourEnabled() && !test) { stopSelf(startId); return; }
        if (!s.getEnabledHours().contains(hour) && !test) { stopSelf(startId); return; }
        TemporaryAlarmVolume guard = new TemporaryAlarmVolume(this);
        guard.raiseIfNeeded(s);
        playback.playHalfTone(s.getHalfHourTone(), s.getAppVolume(), () -> {
            guard.restore();
            stopSelf(startId);
        });
    }

    private Notification notification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setContentTitle(Brand.APP_NAME)
                .setContentText("報時服務執行中")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setOngoing(false).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "熊正點報時服務", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(channel);
    }

    @Override public void onDestroy() {
        if (playback != null) playback.shutdown();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
