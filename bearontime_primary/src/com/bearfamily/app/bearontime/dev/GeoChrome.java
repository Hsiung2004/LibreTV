package com.bearfamily.app.bearontime.dev;

import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;

public final class GeoChrome extends WebChromeClient {
    @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        callback.invoke(origin, true, false);
    }
}
