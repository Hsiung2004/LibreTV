package com.bearfamily.app.bearontime.dev;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity {
    private NativeChimeBridge chimeBridge;
    private NativeChimeScheduler schedulerBridge;
    private VoiceBackupBridge voiceBackupBridge;
    private WidgetBridge widgetBridge;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(1);
        ActionBar bar = getActionBar();
        if (bar != null) bar.hide();

        if (checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") != 0) {
            requestPermissions(new String[]{
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION"
            }, 1001);
        }

        WebView webView = new WebView(this);
        webView.setId(1);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setGeolocationEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebChromeClient(new GeoChrome());

        try {
            chimeBridge = new NativeChimeBridge(this, webView);
            webView.addJavascriptInterface(chimeBridge, "BearNative");
        } catch (Throwable ignored) {
            chimeBridge = null;
        }
        try {
            schedulerBridge = new NativeChimeScheduler(this);
            webView.addJavascriptInterface(schedulerBridge, "BearScheduler");
            schedulerBridge.ensureScheduled();
        } catch (Throwable ignored) {
            schedulerBridge = null;
        }
        try {
            voiceBackupBridge = new VoiceBackupBridge(this, webView);
            webView.addJavascriptInterface(voiceBackupBridge, "BearVoiceBackup");
        } catch (Throwable ignored) {
            voiceBackupBridge = null;
        }
        try {
            widgetBridge = new WidgetBridge(this);
            webView.addJavascriptInterface(widgetBridge, "BearWidget");
        } catch (Throwable ignored) {
            widgetBridge = null;
        }

        webView.loadUrl(initialUrl(getIntent()));
        setContentView(webView);
    }

    private String initialUrl(Intent intent) {
        String p = requestedPage(intent);
        return "file:///android_asset/index.html" + (p.isEmpty() ? "" : "#" + p);
    }

    private String requestedPage(Intent intent) {
        if (intent == null) return "";
        String p = intent.getStringExtra("openPage");
        if (p == null) p = "";
        p = p.trim().toLowerCase();
        if ("weather".equals(p) || "expense".equals(p) || "widgets".equals(p)
                || "calendar".equals(p) || "home".equals(p)) return p;
        return "";
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String p = requestedPage(intent);
        if (p.isEmpty()) return;
        WebView webView = (WebView) findViewById(1);
        if (webView != null) {
            final String page = p;
            webView.post(new Runnable() {
                @Override public void run() {
                    try {
                        webView.evaluateJavascript("window.go&&go('" + page + "')", null);
                    } catch (Throwable ignored) { }
                }
            });
        }
    }

    @Override protected void onResume() {
        super.onResume();
        try { if (schedulerBridge != null) schedulerBridge.ensureScheduled(); } catch (Throwable ignored) { }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (chimeBridge != null && chimeBridge.handleActivityResult(requestCode, resultCode, data)) return;
        } catch (Throwable ignored) { }
        try {
            if (voiceBackupBridge != null && voiceBackupBridge.handleActivityResult(requestCode, resultCode, data)) return;
        } catch (Throwable ignored) { }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override public void onBackPressed() {
        WebView webView = (WebView) findViewById(1);
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        try { if (chimeBridge != null) chimeBridge.stop(); } catch (Throwable ignored) { }
        chimeBridge = null;
        schedulerBridge = null;
        voiceBackupBridge = null;
        widgetBridge = null;
        super.onDestroy();
    }
}
