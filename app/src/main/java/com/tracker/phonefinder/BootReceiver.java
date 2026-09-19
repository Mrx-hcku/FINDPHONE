package com.tracker.phonefinder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)
                || action.equals("android.intent.action.QUICKBOOT_POWERON")
                || action.equals(Intent.ACTION_MY_PACKAGE_REPLACED)) {

            SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            boolean setupDone = prefs.getBoolean(Constants.KEY_SETUP_DONE, false);
            if (!setupDone) return; // no bot token/chat id configured yet

            Intent serviceIntent = new Intent(context, LocationTrackerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            WatchdogReceiver.scheduleNext(context);
        }
    }
}
