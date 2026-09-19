package com.tracker.phonefinder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Build;

public class GpsStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        boolean pending = prefs.getBoolean(Constants.KEY_PENDING_LOCATE, false);
        if (!pending) return;

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean enabled = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!enabled) return; // still off, keep waiting

        prefs.edit().putBoolean(Constants.KEY_PENDING_LOCATE, false).apply();

        Intent serviceIntent = new Intent(context, LocationTrackerService.class);
        serviceIntent.setAction(Constants.ACTION_EXECUTE_PENDING_LOCATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
