package com.tracker.phonefinder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Registered in the manifest. Android itself is responsible for delivering FCM
 * data messages to this service even if the app's process was previously killed
 * by the system or by battery management — as long as the user hasn't manually
 * Force Stopped the app (that state blocks all wake paths, FCM included).
 */
public class MyFcmService extends FirebaseMessagingService {

    private static final String TAG = "MyFcmService";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        String cmd = data.get("cmd");
        if (cmd == null) return;

        if (cmd.equals("locate")) {
            Intent serviceIntent = new Intent(this, LocationTrackerService.class);
            serviceIntent.setAction(Constants.ACTION_LOCATE_NOW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(Constants.KEY_FCM_TOKEN, token).apply();

        String botToken = prefs.getString(Constants.KEY_BOT_TOKEN, null);
        String chatId = prefs.getString(Constants.KEY_CHAT_ID, null);
        if (botToken != null && chatId != null) {
            registerTokenWithBackend(botToken, chatId, token);
        }
    }

    /** Also called from MainActivity right after setup, so the very first token gets registered too. */
    static void registerTokenWithBackend(String botToken, String chatId, String fcmToken) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(Constants.CLOUD_FUNCTION_BASE_URL + "/registerToken");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/json");

                String json = "{\"botToken\":\"" + botToken + "\","
                        + "\"chatId\":\"" + chatId + "\","
                        + "\"fcmToken\":\"" + fcmToken + "\"}";

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                conn.getInputStream().close();
            } catch (Exception e) {
                Log.e(TAG, "Token registration failed: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "RegisterFcmTokenThread").start();
    }
}
