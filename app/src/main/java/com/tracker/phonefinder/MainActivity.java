package com.tracker.phonefinder;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_LOCATION = 1001;
    private static final int REQ_BACKGROUND_LOCATION = 1002;
    private static final int REQ_NOTIFICATIONS = 1003;
    private static final int REQ_DEVICE_ADMIN = 1004;

    private EditText etBotToken, etChatId;
    private TextView tvStatus;
    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etBotToken = findViewById(R.id.etBotToken);
        etChatId = findViewById(R.id.etChatId);
        tvStatus = findViewById(R.id.tvStatus);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnHideIcon = findViewById(R.id.btnHideIcon);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        etBotToken.setText(prefs.getString(Constants.KEY_BOT_TOKEN, ""));
        etChatId.setText(prefs.getString(Constants.KEY_CHAT_ID, ""));

        btnSave.setOnClickListener(v -> saveAndStart());
        btnHideIcon.setOnClickListener(v -> hideAppIcon());

        updateStatus();
    }

    private void saveAndStart() {
        String token = etBotToken.getText().toString().trim();
        String chatId = etChatId.getText().toString().trim();

        if (token.isEmpty() || chatId.isEmpty()) {
            Toast.makeText(this, "Enter both bot token and chat ID", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(Constants.KEY_BOT_TOKEN, token)
                .putString(Constants.KEY_CHAT_ID, chatId)
                .putBoolean(Constants.KEY_SETUP_DONE, true)
                .apply();

        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(fcmToken -> {
                    prefs.edit().putString(Constants.KEY_FCM_TOKEN, fcmToken).apply();
                    MyFcmService.registerTokenWithBackend(token, chatId, fcmToken);
                });

        requestRuntimePermissions();
    }

    private void requestRuntimePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_LOCATION);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQ_BACKGROUND_LOCATION);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATIONS);
            return;
        }

        requestBatteryOptimizationExemption();
    }

    private void requestBatteryOptimizationExemption() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        String packageName = getPackageName();
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        }
        requestDeviceAdmin();
    }

    private void requestDeviceAdmin() {
        if (!dpm.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Needed to protect the app from being disabled or removed if the device is lost.");
            startActivityForResult(intent, REQ_DEVICE_ADMIN);
            return;
        }
        startTrackerService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Re-run the chain regardless of individual grant/deny so setup always reaches the end
        requestRuntimePermissions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DEVICE_ADMIN) {
            startTrackerService();
        }
    }

    private void startTrackerService() {
        Intent serviceIntent = new Intent(this, LocationTrackerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        updateStatus();
        Toast.makeText(this, "Protection enabled", Toast.LENGTH_SHORT).show();
    }

    private void hideAppIcon() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(Constants.KEY_SETUP_DONE, false)) {
            Toast.makeText(this, "Set up protection first, then hide the icon", Toast.LENGTH_SHORT).show();
            return;
        }
        ComponentName alias = new ComponentName(this, "com.tracker.phonefinder.LauncherAlias");
        getPackageManager().setComponentEnabledSetting(
                alias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        prefs.edit().putBoolean(Constants.KEY_ICON_HIDDEN, true).apply();
        Toast.makeText(this, "Icon hidden. Find the app again via Settings > Apps > System Services.", Toast.LENGTH_LONG).show();
    }

    private void updateStatus() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        boolean done = prefs.getBoolean(Constants.KEY_SETUP_DONE, false);
        tvStatus.setText(done ? "Status: Active" : "Status: Not configured");
    }
}
