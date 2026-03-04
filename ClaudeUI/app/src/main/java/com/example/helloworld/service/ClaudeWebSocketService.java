package com.example.helloworld.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
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
import com.example.helloworld.audio.AudioPlayerManager;
import com.example.helloworld.network.WebSocketClient;
import com.example.helloworld.util.ClientIdManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ClaudeWebSocketService extends Service {
    private static final String TAG = "ClaudeWebSocketService";
    private static final String CHANNEL_ID = "ClaudeWebSocketChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int MAX_HISTORY_LINES = 50;

    // Status colors (ARGB)
    private static final int COLOR_BLUE = 0xFF2196F3;    // Connected/Idle - Blue
    private static final int COLOR_GRAY = 0xFF9E9E9E;    // Disconnected - Gray
    private static final int COLOR_RED = 0xFFF44336;     // Error - Red
    private static final int COLOR_YELLOW = 0xFFFF9800;  // Connecting/Waiting - Yellow
    private static final int COLOR_GREEN = 0xFF4CAF50;    // Listening/Receiving - Green

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
        startForeground(NOTIFICATION_ID, createNotification());
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
                updateNotification();
            }

            @Override
            public void onDisconnected(String reason) {
                Log.d(TAG, "WebSocket disconnected: " + reason);
                setStatus(Status.DISCONNECTED);
                updateNotification();
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
        Log.d(TAG, "onStartCommand: " + (intent != null ? intent.getAction() : null));

        // Handle audio playback intents
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();

            // Forward to AudioPlayerManager
            Intent audioIntent = new Intent(this, AudioPlayerManager.class);
            audioIntent.setAction(action);
            audioIntent.putExtras(intent);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(audioIntent);
            } else {
                startService(audioIntent);
            }
        }

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
            appendOutput("\n🐢: " + text + "\n");
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
        updateNotification();
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

    private int getStatusColor(Status status) {
        switch (status) {
            case CONNECTED:
            case IDLE:
                return COLOR_BLUE;
            case CONNECTING:
            case WAITING:
            case SENDING:
                return COLOR_YELLOW;
            case LISTENING:
            case RECEIVING:
                return COLOR_GREEN;
            case DISCONNECTED:
                return COLOR_GRAY;
            case ERROR:
                return COLOR_RED;
            default:
                return COLOR_GRAY;
        }
    }

    private String getStatusText(Status status) {
        switch (status) {
            case IDLE:
                return "准备就绪";
            case CONNECTING:
                return "正在连接...";
            case CONNECTED:
                return "已连接";
            case LISTENING:
                return "正在聆听...";
            case SENDING:
                return "正在发送...";
            case WAITING:
                return "正在等待回复...";
            case RECEIVING:
                return "正在接收...";
            case DISCONNECTED:
                return "已断开连接";
            case ERROR:
                return "出错了";
            default:
                return "未知状态";
        }
    }

    private Bitmap createColoredDot(int color) {
        int size = 48;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, paint);
        return bitmap;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "皮皮虾",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("皮皮虾助手服务");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        Status status = statusLiveData.getValue();
        if (status == null) status = Status.IDLE;

        int color = getStatusColor(status);
        String text = getStatusText(status);
        Bitmap coloredDot = createColoredDot(color);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("皮皮虾")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setLargeIcon(coloredDot)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }
}
