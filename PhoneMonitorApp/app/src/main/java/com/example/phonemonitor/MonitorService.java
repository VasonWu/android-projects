package com.example.phonemonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class MonitorService extends Service {
    private static final String TAG = "MonitorService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "PhoneMonitorChannel";

    private NtfyClient ntfyClient;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service created");

        ntfyClient = new NtfyClient(this);
        SmsReceiver.setNtfyClient(ntfyClient);
        PhoneStateReceiver.setNtfyClient(ntfyClient);

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
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
        Log.i(TAG, "Service destroyed");
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
