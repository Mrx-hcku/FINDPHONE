package com.tracker.phonefinder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Pure persistence + location service now. Commands no longer arrive via in-app
 * Telegram long-polling (that stops working once a webhook is set on the bot) —
 * they arrive as FCM data messages relayed by the Cloud Function, handled here
 * via onStartCommand actions. This lets a fully-killed process still respond,
 * as long as it hasn't been manually Force Stopped by the user.
 */
public class LocationTrackerService extends Service {

    private static final String TAG = "TrackerService";
    private PowerManager.WakeLock wakeLock;
    private FusedLocationProviderClient fusedLocationClient;

    private String botToken;
    private String allowedChatId;

    @Override
    public void onCreate() {
        super.onCreate();
        ServiceState.isRunning = true;

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        botToken = prefs.getString(Constants.KEY_BOT_TOKEN, null);
        allowedChatId = prefs.getString(Constants.KEY_CHAT_ID, null);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        createNotificationChannel();
        startForeground(Constants.NOTIFICATION_ID, buildNotification());

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneFinder::WakeLock");
        wakeLock.acquire(10 * 60 * 60 * 1000L); // 10h safety cap, renewed on every restart

        WatchdogReceiver.scheduleNext(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (botToken == null || allowedChatId == null) {
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            botToken = prefs.getString(Constants.KEY_BOT_TOKEN, null);
            allowedChatId = prefs.getString(Constants.KEY_CHAT_ID, null);
        }
        if (botToken == null || allowedChatId == null) {
            Log.e(TAG, "Bot token / chat id not configured");
            return START_STICKY;
        }

        String action = intent != null ? intent.getAction() : null;
        if (Constants.ACTION_EXECUTE_PENDING_LOCATE.equals(action)) {
            handleLocateCommand();
            sendTelegramMessage("Location was off — it's back on now, fetching current location.");
        } else if (Constants.ACTION_LOCATE_NOW.equals(action)) {
            handleLocateCommand();
        }
        return START_STICKY; // ask system to recreate the service if it gets killed
    }

    private void handleLocateCommand() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gpsOn = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkOn = lm != null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!gpsOn && !networkOn) {
            // Location is fully off on the device. Send whatever last-known fix we have,
            // mark a pending request, and let GpsStateReceiver fire this again once it's back on.
            sendTelegramMessage("Location is currently OFF on the device. Sending last known location; " +
                    "I'll auto-send a fresh one the moment location is turned back on.");

            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(Constants.KEY_PENDING_LOCATE, true).apply();

            try {
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        sendTelegramLocation(location);
                    } else {
                        sendTelegramMessage("No last known location available on this device.");
                    }
                });
            } catch (SecurityException se) {
                sendTelegramMessage("Location permission missing on device.");
            }
            return;
        }

        try {
            CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setDurationMillis(20000)
                    .build();

            fusedLocationClient.getCurrentLocation(request, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            sendTelegramLocation(location);
                        } else {
                            sendTelegramMessage("Could not fetch location right now (GPS/network may be off).");
                        }
                    })
                    .addOnFailureListener(e -> sendTelegramMessage("Location fetch failed: " + e.getMessage()));
        } catch (SecurityException se) {
            sendTelegramMessage("Location permission missing on device.");
        }
    }

    private void sendTelegramLocation(Location location) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendLocation";
            String params = "chat_id=" + URLEncoder.encode(allowedChatId, "UTF-8")
                    + "&latitude=" + location.getLatitude()
                    + "&longitude=" + location.getLongitude();
            httpPost(url, params);
        } catch (Exception e) {
            Log.e(TAG, "sendLocation failed: " + e.getMessage());
        }
    }

    private void sendTelegramMessage(String text) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            String params = "chat_id=" + URLEncoder.encode(allowedChatId, "UTF-8")
                    + "&text=" + URLEncoder.encode(text, "UTF-8");
            httpPost(url, params);
        } catch (Exception e) {
            Log.e(TAG, "sendMessage failed: " + e.getMessage());
        }
    }

    private void httpPost(String urlStr, String params) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }
            conn.getInputStream().close();
        } catch (Exception e) {
            Log.e(TAG, "httpPost failed: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.CHANNEL_ID, "Device Protection",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("Keeps device tracking active");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private android.app.Notification buildNotification() {
        return new NotificationCompat.Builder(this, Constants.CHANNEL_ID)
                .setContentTitle("Device Protection Active")
                .setContentText("Your device is protected.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        ServiceState.isRunning = false;
        // ask watchdog to bring it back ASAP instead of waiting the full interval
        WatchdogReceiver.scheduleNext(this);
        super.onDestroy();
    }
}
