package com.bearfamily.app.bearontime.dev;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        NativeChimeScheduler.scheduleNext(context.getApplicationContext());
    }
}
