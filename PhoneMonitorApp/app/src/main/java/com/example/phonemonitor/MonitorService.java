package com.example.phonemonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.Executor;

public class MonitorService extends Service {
    private static final String TAG = "MonitorService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "PhoneMonitorChannel";

    private NtfyClient ntfyClient;
    private TelephonyManager telephonyManager;
    private PhoneStateCallback phoneStateCallback;
    private String lastPhoneNumber;
    private boolean isRinging = false;
    private boolean isOffhook = false;

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service created");

        ntfyClient = new NtfyClient(this);
        SmsReceiver.setNtfyClient(ntfyClient);
        Log.i(TAG, "NtfyClient set for SmsReceiver");

        // Register TelephonyCallback for call state monitoring
        telephonyManager = getSystemService(TelephonyManager.class);
        if (telephonyManager != null) {
            phoneStateCallback = new PhoneStateCallback();
            Executor executor = getMainExecutor();
            telephonyManager.registerTelephonyCallback(executor, phoneStateCallback);
            Log.i(TAG, "TelephonyCallback registered");
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        Log.i(TAG, "Foreground service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (telephonyManager != null && phoneStateCallback != null) {
            telephonyManager.unregisterTelephonyCallback(phoneStateCallback);
            Log.i(TAG, "TelephonyCallback unregistered");
        }
        Log.i(TAG, "Service destroyed");
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private class PhoneStateCallback extends TelephonyCallback implements TelephonyCallback.CallStateListener, TelephonyCallback.CallDisconnectCauseListener {
        @Override
        public void onCallStateChanged(int state) {
            Log.i(TAG, "Phone state changed: " + state);
            handleStateChange(state);
        }

        @Override
        public void onCallDisconnectCauseChanged(int disconnectCause, int preciseDisconnectCause) {
            Log.i(TAG, "Call disconnect cause: " + disconnectCause + ", precise: " + preciseDisconnectCause);
        }
    }

    private void handleStateChange(int state) {
        Log.i(TAG, "Handling state change: " + state + ", wasRinging=" + isRinging + ", wasOffhook=" + isOffhook);

        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                // Incoming call - we don't get phone number from TelephonyCallback in API 31+
                Log.i(TAG, "Incoming call detected");
                isRinging = true;
                isOffhook = false;
                if (ntfyClient != null) {
                    ntfyClient.sendIncomingCall("来电");
                }
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK:
                if (isRinging) {
                    // Call answered
                    Log.i(TAG, "Call answered");
                }
                isOffhook = true;
                break;

            case TelephonyManager.CALL_STATE_IDLE:
                if (isRinging) {
                    // Missed call
                    Log.i(TAG, "Missed call detected");
                    if (ntfyClient != null) {
                        ntfyClient.sendMissedCall("未接来电");
                    }
                } else if (isOffhook) {
                    // Call ended
                    Log.i(TAG, "Call ended");
                    if (ntfyClient != null) {
                        ntfyClient.sendCallEnded("通话结束", 0);
                    }
                }
                break;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Phone Monitor Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Foreground service for monitoring SMS and calls");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        String status = ntfyClient.isConnected() ? "已连接" : "等待连接";
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("手机监控")
                .setContentText("正在监控短信和来电 - " + status)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
}
