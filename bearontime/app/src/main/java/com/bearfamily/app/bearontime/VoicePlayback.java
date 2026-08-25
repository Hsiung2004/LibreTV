package com.bearfamily.app.bearontime;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.io.File;
import java.util.Locale;
import java.util.UUID;

public final class VoicePlayback implements TextToSpeech.OnInitListener {
    private final Context context;
    private MediaPlayer player;
    private AudioFocusRequest focusRequest;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingText;
    private int pendingVolume;
    private Runnable pendingDone;

    public VoicePlayback(Context context) {
        this.context = context.getApplicationContext();
        tts = new TextToSpeech(this.context, this);
    }

    @Override public void onInit(int status) {
        ttsReady = status == TextToSpeech.SUCCESS;
        if (ttsReady) {
            tts.setLanguage(Locale.TAIWAN);
            tts.setSpeechRate(0.95f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {}
                @Override public void onDone(String utteranceId) { finishTts(); }
                @Override public void onError(String utteranceId) { finishTts(); }
            });
            if (pendingText != null) {
                String text = pendingText; int vol = pendingVolume; Runnable done = pendingDone;
                pendingText = null; pendingDone = null;
                speak(text, vol, done);
            }
        }
    }

    public synchronized void playFile(File file, int volume, boolean alarmChannel, Runnable done) {
        stopMedia();
        if (file == null || !file.isFile()) { if (done != null) done.run(); return; }
        try {
            AudioAttributes attr = attributes(alarmChannel);
            requestFocus(attr);
            player = new MediaPlayer();
            player.setAudioAttributes(attr);
            player.setDataSource(file.getAbsolutePath());
            float v = Math.max(0f, Math.min(1f, volume / 100f));
            player.setVolume(v, v);
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp -> finishMedia(done));
            player.setOnErrorListener((mp, what, extra) -> { finishMedia(done); return true; });
            player.prepareAsync();
        } catch (Exception ex) { finishMedia(done); }
    }

    public synchronized void speak(String text, int volume, Runnable done) {
        if (!ttsReady) { pendingText = text; pendingVolume = volume; pendingDone = done; return; }
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Math.max(0f, Math.min(1f, volume / 100f)));
        pendingDone = done;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "bearontime_" + UUID.randomUUID());
    }

    public void playHalfTone(String tone, int volume, Runnable done) {
        int toneType = ToneGenerator.TONE_PROP_BEEP;
        if ("cuckoo".equals(tone)) toneType = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD;
        else if ("classic".equals(tone)) toneType = ToneGenerator.TONE_DTMF_0;
        else if ("electronic".equals(tone)) toneType = ToneGenerator.TONE_CDMA_PIP;
        final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, Math.max(0, Math.min(100, volume)));
        tg.startTone(toneType, 900);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try { tg.release(); } catch (Exception ignored) {}
            if (done != null) done.run();
        }, 1100);
    }

    public synchronized void stop() {
        stopMedia();
        try { if (tts != null) tts.stop(); } catch (Exception ignored) {}
        abandonFocus();
    }

    public synchronized void shutdown() {
        stop();
        try { if (tts != null) tts.shutdown(); } catch (Exception ignored) {}
        tts = null;
    }

    private void stopMedia() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private synchronized void finishMedia(Runnable done) {
        if (player != null) { try { player.release(); } catch (Exception ignored) {} player = null; }
        abandonFocus(); if (done != null) done.run();
    }

    private synchronized void finishTts() {
        Runnable done = pendingDone; pendingDone = null;
        if (done != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(done);
    }

    private AudioAttributes attributes(boolean alarm) {
        return new AudioAttributes.Builder().setUsage(alarm ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }

    private void requestFocus(AudioAttributes attributes) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes).setOnAudioFocusChangeListener(focus -> {}).build();
            am.requestAudioFocus(focusRequest);
        } catch (Exception ignored) {}
    }

    private void abandonFocus() {
        if (focusRequest == null) return;
        try { ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocusRequest(focusRequest); }
        catch (Exception ignored) {}
        focusRequest = null;
    }
}
