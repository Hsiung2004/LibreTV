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

        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    @Override protected void onResume() {
        super.onResume();
        try { if (schedulerBridge != null) schedulerBridge.ensureScheduled(); } catch (Throwable ignored) { }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (chimeBridge != null && chimeBridge.handleActivityResult(requestCode, resultCode, data)) return;
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
        super.onDestroy();
    }
}
