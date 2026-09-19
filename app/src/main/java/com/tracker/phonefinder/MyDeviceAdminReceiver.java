package com.tracker.phonefinder;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class MyDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // Shown to whoever tries to turn off Device Admin (e.g. a thief)
        return "Disabling this will stop your phone from being trackable if lost.";
    }
}
