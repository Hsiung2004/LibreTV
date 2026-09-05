package com.bearfamily.app.bearontime.dev;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private NativeChimeBridge chimeBridge;
    private NativeChimeScheduler schedulerBridge;
    private VoiceBackupBridge voiceBackupBridge;
    private WidgetBridge widgetBridge;
    private WebView webView;
    private String pendingOpenPage = "";

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

        webView = new WebView(this);
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
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                applyPendingOpenPage();
            }
        });

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

        pendingOpenPage = requestedPage(getIntent());
        webView.loadUrl(initialUrl(pendingOpenPage));
        setContentView(webView);
    }

    private String initialUrl(String p) {
        if ("quickadd".equals(p)) return "file:///android_asset/index.html#home";
        return "file:///android_asset/index.html" + (p.isEmpty() ? "" : "#" + p);
    }

    private String requestedPage(Intent intent) {
        if (intent == null) return "";
        String p = intent.getStringExtra("openPage");
        if (p == null) p = "";
        p = p.trim().toLowerCase();
        if ("weather".equals(p) || "expense".equals(p) || "widgets".equals(p)
                || "calendar".equals(p) || "home".equals(p) || "quickadd".equals(p)) return p;
        return "";
    }

    private void applyPendingOpenPage() {
        if (webView == null || pendingOpenPage == null || pendingOpenPage.isEmpty()) return;
        final String p = pendingOpenPage;
        pendingOpenPage = "";
        webView.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    if ("quickadd".equals(p)) {
                        webView.evaluateJavascript("window.go&&go('home');setTimeout(function(){window.quickAddForHome&&quickAddForHome()},120)", null);
                    } else {
                        webView.evaluateJavascript("window.go&&go('" + p + "')", null);
                    }
                } catch (Throwable ignored) { }
            }
        }, 120L);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String p = requestedPage(intent);
        if (p.isEmpty()) return;
        pendingOpenPage = p;
        if (webView == null) return;
        webView.post(new Runnable() {
            @Override public void run() { applyPendingOpenPage(); }
        });
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
        webView = null;
        super.onDestroy();
    }
}
