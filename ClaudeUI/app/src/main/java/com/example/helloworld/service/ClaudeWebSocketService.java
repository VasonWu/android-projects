package com.example.helloworld.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.helloworld.MainActivity;
import com.example.helloworld.R;
import com.example.helloworld.network.WebSocketClient;
import com.example.helloworld.util.ClientIdManager;

import java.util.LinkedList;
import java.util.List;

public class ClaudeWebSocketService extends Service {
    private static final String TAG = "ClaudeWebSocketService";
    private static final String CHANNEL_ID = "ClaudeWebSocketChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int MAX_HISTORY_LINES = 50;

    public enum Status {
        IDLE,
        CONNECTING,
        CONNECTED,
        LISTENING,
        SENDING,
        WAITING,
        RECEIVING,
        DISCONNECTED,
        ERROR
    }

    private final IBinder binder = new LocalBinder();
    private WebSocketClient webSocketClient;
    private ClientIdManager clientIdManager;
    private final MutableLiveData<Status> statusLiveData = new MutableLiveData<>(Status.IDLE);
    private final MutableLiveData<String> outputLiveData = new MutableLiveData<>();
    private final List<String> outputLines = new LinkedList<>();
    private String currentSessionId;

    public class LocalBinder extends Binder {
        public ClaudeWebSocketService getService() {
            return ClaudeWebSocketService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        clientIdManager = new ClientIdManager(this);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Claude 连接中..."));
        initWebSocket();
    }

    private void initWebSocket() {
        webSocketClient = new WebSocketClient();
        webSocketClient.setClientId(clientIdManager.getClientId());
        webSocketClient.setListener(new WebSocketClient.Listener() {
            @Override
            public void onConnected() {
                Log.d(TAG, "WebSocket connected");
                setStatus(Status.CONNECTED);
                updateNotification("Claude 已连接");
            }

            @Override
            public void onDisconnected(String reason) {
                Log.d(TAG, "WebSocket disconnected: " + reason);
                setStatus(Status.DISCONNECTED);
                updateNotification("Claude 断开连接");
            }

            @Override
            public void onOutputReceived(String data) {
                Log.d(TAG, "Output received: " + data);
                setStatus(Status.RECEIVING);
                // 在回复前添加换行和前缀符号
                appendOutput("\n🦐: " + data);
            }

            @Override
            public void onErrorReceived(String data) {
                Log.e(TAG, "Error received: " + data);
                setStatus(Status.ERROR);
            }

            @Override
            public void onExitReceived(int code) {
                Log.d(TAG, "Exit received, code: " + code);
                setStatus(Status.IDLE);
            }

            @Override
            public void onSessionCreated(String sessionId) {
                Log.d(TAG, "Session created: " + sessionId);
                currentSessionId = sessionId;
                setStatus(Status.CONNECTED);
            }

            @Override
            public void onSessionSelected(String sessionId) {
                Log.d(TAG, "Session selected: " + sessionId);
                currentSessionId = sessionId;
            }

            @Override
            public void onSessionList(String sessionsJson) {
                Log.d(TAG, "Session list received");
            }

            @Override
            public void onProcessStarted(String sessionId) {
                Log.d(TAG, "Process started: " + sessionId);
            }

            @Override
            public void onProcessStopped(String sessionId, String reason) {
                Log.d(TAG, "Process stopped: " + sessionId + ", reason: " + reason);
            }

            @Override
            public void onProcessCrashed(String sessionId) {
                Log.d(TAG, "Process crashed: " + sessionId);
            }
        });
        setStatus(Status.CONNECTING);
        webSocketClient.connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }
    }

    public LiveData<Status> getStatusLiveData() {
        return statusLiveData;
    }

    public LiveData<String> getOutputLiveData() {
        return outputLiveData;
    }

    public String getOutputBuffer() {
        StringBuilder sb = new StringBuilder();
        for (String line : outputLines) {
            sb.append(line);
        }
        return sb.toString();
    }

    public Status getCurrentStatus() {
        return statusLiveData.getValue();
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public void sendInput(String text) {
        if (webSocketClient != null && webSocketClient.isConnected()) {
            setStatus(Status.SENDING);
            appendOutput("\n> " + text + "\n");
            webSocketClient.sendInput(text, currentSessionId);
            setStatus(Status.WAITING);
        } else {
            appendOutput("\n[未连接到服务器]\n");
        }
    }

    public void sendCancel() {
        if (webSocketClient != null) {
            webSocketClient.sendCancel();
            setStatus(Status.IDLE);
        }
    }

    public void createSession() {
        if (webSocketClient != null && webSocketClient.isConnected()) {
            webSocketClient.sendSessionCreate();
        } else {
            appendOutput("\n[未连接到服务器]\n");
        }
    }

    public void selectSession(String sessionId) {
        if (webSocketClient != null && webSocketClient.isConnected()) {
            webSocketClient.sendSessionSelect(sessionId);
        }
    }

    public void listSessions() {
        if (webSocketClient != null && webSocketClient.isConnected()) {
            webSocketClient.sendSessionList();
        }
    }

    public void setListeningStatus() {
        setStatus(Status.LISTENING);
    }

    public void setIdleStatus() {
        if (statusLiveData.getValue() == Status.LISTENING) {
            setStatus(Status.CONNECTED);
        }
    }

    public void clearOutput() {
        outputLines.clear();
        outputLiveData.setValue("");
    }

    private void setStatus(Status status) {
        statusLiveData.postValue(status);
    }

    private void appendOutput(String text) {
        // 将文本按行分割并添加
        String[] lines = text.split("(?<=\\n)");
        for (String line : lines) {
            outputLines.add(line);
        }

        // 限制最多 MAX_HISTORY_LINES 行
        while (outputLines.size() > MAX_HISTORY_LINES) {
            outputLines.remove(0);
        }

        // 更新 LiveData
        StringBuilder sb = new StringBuilder();
        for (String line : outputLines) {
            sb.append(line);
        }
        outputLiveData.postValue(sb.toString());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Claude WebSocket Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持 Claude 连接的后台服务");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Claude Assistant")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(text));
        }
    }
}
