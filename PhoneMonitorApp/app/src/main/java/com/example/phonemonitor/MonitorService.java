package com.example.phonemonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class MonitorService extends Service {
    private static final String TAG = "MonitorService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "PhoneMonitorChannel";

    private NtfyClient ntfyClient;
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private String lastPhoneNumber;
    private boolean isRinging = false;
    private boolean isOffhook = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service created");

        ntfyClient = new NtfyClient(this);
        SmsReceiver.setNtfyClient(ntfyClient);
        PhoneStateReceiver.setNtfyClient(ntfyClient);
        Log.i(TAG, "NtfyClient set for receivers");

        // Register PhoneStateListener for call state monitoring (deprecated but still works without carrier privileges)
        telephonyManager = getSystemService(TelephonyManager.class);
        if (telephonyManager != null) {
            phoneStateListener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String phoneNumber) {
                    Log.i(TAG, "Phone state changed: " + state + ", phoneNumber: " + phoneNumber);
                    handleStateChange(state, phoneNumber);
                }
            };
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            Log.i(TAG, "PhoneStateListener registered");
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            Log.i(TAG, "PhoneStateListener unregistered");
        }
        Log.i(TAG, "Service destroyed");
    }

    private void handleStateChange(int state, String phoneNumber) {
        Log.i(TAG, "Handling state change: " + state + ", phoneNumber: " + phoneNumber + ", wasRinging=" + isRinging + ", wasOffhook=" + isOffhook);

        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            lastPhoneNumber = phoneNumber;
        }

        final NtfyClient client = ntfyClient;
        final String finalPhoneNumber = phoneNumber;
        final String finalLastPhoneNumber = lastPhoneNumber;

        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                Log.i(TAG, "Incoming call detected from: " + phoneNumber);
                isRinging = true;
                isOffhook = false;
                if (client != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.sendIncomingCall(finalPhoneNumber != null ? finalPhoneNumber : "来电");
                        }
                    }).start();
                }
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK:
                if (isRinging) {
                    Log.i(TAG, "Call answered: " + lastPhoneNumber);
                }
                isOffhook = true;
                break;

            case TelephonyManager.CALL_STATE_IDLE:
                if (isRinging) {
                    Log.i(TAG, "Missed call detected from: " + lastPhoneNumber);
                    if (client != null) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.sendMissedCall(finalLastPhoneNumber != null ? finalLastPhoneNumber : "未接来电");
                            }
                        }).start();
                    }
                } else if (isOffhook) {
                    Log.i(TAG, "Call ended: " + lastPhoneNumber);
                    if (client != null) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.sendCallEnded(finalLastPhoneNumber != null ? finalLastPhoneNumber : "通话结束", 0);
                            }
                        }).start();
                    }
                }
                isRinging = false;
                isOffhook = false;
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
