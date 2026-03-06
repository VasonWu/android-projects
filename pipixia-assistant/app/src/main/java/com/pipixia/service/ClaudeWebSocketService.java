package com.pipixia.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.pipixia.MainActivity;
import com.pipixia.R;
import com.pipixia.audio.AudioPlayerManager;
import com.pipixia.network.WebSocketClient;
import com.pipixia.util.ClientIdManager;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ClaudeWebSocketService extends Service {
    private static final String TAG = "ClaudeWebSocketService";
    private static final String CHANNEL_ID = "ClaudeWebSocketChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String MESSAGE_CHANNEL_ID = "PipixiaMessageChannel";
    private static final int MESSAGE_NOTIFICATION_ID = 2;
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
    private final MutableLiveData<String> statusLineLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> statusLineLiveData2 = new MutableLiveData<>();
    private final MutableLiveData<Boolean> statusLineVisibleLiveData = new MutableLiveData<>(false);
    private final List<String> outputLines = new LinkedList<>();
    private String currentSessionId;

    // Status line dot animation
    private String lastStatusText = "";
    private int dotCount = 0;

    // Flag to track if we're in a streaming response
    private boolean isStreaming = false;
    private StringBuilder currentResponseBuilder = new StringBuilder();
    private boolean hasOutputPrefix = false;
    private int lastOutputStartIndex = -1;

    // AudioPlayerManager binding
    private AudioPlayerManager audioPlayerManager;
    private boolean isAudioPlayerBound = false;
    private boolean audioPlayerNeedsUpdate = false;

    // MainActivity visibility tracking
    private boolean isMainActivityVisible = false;
    private String lastReceivedMessage = "";

    private final ServiceConnection audioPlayerConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioPlayerManager.LocalBinder binder = (AudioPlayerManager.LocalBinder) service;
            audioPlayerManager = binder.getService();
            isAudioPlayerBound = true;
            Log.d(TAG, "AudioPlayerManager bound");
            if (audioPlayerNeedsUpdate) {
                updateNotification();
                audioPlayerNeedsUpdate = false;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            audioPlayerManager = null;
            isAudioPlayerBound = false;
            Log.d(TAG, "AudioPlayerManager unbound");
        }
    };

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
        createMessageNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        initWebSocket();
        bindToAudioPlayer();
    }

    private void bindToAudioPlayer() {
        Intent intent = new Intent(this, AudioPlayerManager.class);
        bindService(intent, audioPlayerConnection, Context.BIND_AUTO_CREATE);
    }

    public void notifyAudioPlayerStateChanged() {
        Log.d(TAG, "Audio player state changed, updating notification");
        updateNotification();
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
                isStreaming = true;
                currentResponseBuilder.append(data);

                // 处理流式输出：首次添加前缀，后续只更新最后一行
                if (!hasOutputPrefix) {
                    // 首次输出，添加前缀
                    appendOutput("\n🦐: " + currentResponseBuilder.toString());
                    hasOutputPrefix = true;
                    // 记录最后一个输出块的起始位置
                    lastOutputStartIndex = outputLines.size() - 1;
                } else {
                    // 更新最后一个输出块
                    if (lastOutputStartIndex >= 0 && lastOutputStartIndex < outputLines.size()) {
                        // 移除旧的输出行
                        while (outputLines.size() > lastOutputStartIndex) {
                            outputLines.remove(outputLines.size() - 1);
                        }
                        // 添加新的完整输出
                        String fullOutput = "🦐: " + currentResponseBuilder.toString();
                        String[] lines = fullOutput.split("(?<=\\n)");
                        for (String line : lines) {
                            outputLines.add(line);
                        }
                        // 更新LiveData
                        updateOutputLiveData();
                    }
                }

                lastReceivedMessage = data;
                // 如果MainActivity不可见，显示通知
                if (!isMainActivityVisible) {
                    showMessageNotification(currentResponseBuilder.toString());
                } else {
                    cancelMessageNotification();
                }
            }

            @Override
            public void onErrorReceived(String data) {
                Log.e(TAG, "Error received: " + data);
                setStatus(Status.ERROR);
                appendOutput("\n[错误] " + data);
            }

            @Override
            public void onExitReceived(int code) {
                Log.d(TAG, "Exit received, code: " + code);
                setStatus(Status.IDLE);
                // Hide status line when done
                setStatusLineVisible(false);
                // Finalize the response
                if (isStreaming && currentResponseBuilder.length() > 0) {
                    // Keep the final output, don't clear
                }
                // Reset streaming state
                isStreaming = false;
                hasOutputPrefix = false;
                lastOutputStartIndex = -1;
                currentResponseBuilder = new StringBuilder();
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

            @Override
            public void onStatusReceived(String data) {
                Log.d(TAG, "Status received: " + data);
                // Show status line and update it
                setStatusLineVisible(true);
                setStatusLine(data);
            }

            @Override
            public void onToolUseReceived(String name, String input, String display) {
                Log.d(TAG, "Tool use received: " + name);
                // Show tool use in status line
                setStatusLineVisible(true);
                setStatusLine("\uD83D\uDD27 调用工具: " + name);
                // Also append tool info to output (optional, for history)
                if (display != null && !display.isEmpty()) {
                    appendOutput("\n" + display);
                }
            }
        });
        setStatus(Status.CONNECTING);
        webSocketClient.connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + (intent != null ? intent.getAction() : null));
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
        if (isAudioPlayerBound) {
            unbindService(audioPlayerConnection);
            isAudioPlayerBound = false;
        }
    }

    public LiveData<Status> getStatusLiveData() {
        return statusLiveData;
    }

    public LiveData<String> getOutputLiveData() {
        return outputLiveData;
    }

    public LiveData<String> getStatusLineLiveData() {
        return statusLineLiveData;
    }

    public LiveData<String> getStatusLineLiveData2() {
        return statusLineLiveData2;
    }

    public LiveData<Boolean> getStatusLineVisibleLiveData() {
        return statusLineVisibleLiveData;
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
            // Clear streaming state for new input
            isStreaming = false;
            hasOutputPrefix = false;
            lastOutputStartIndex = -1;
            currentResponseBuilder = new StringBuilder();
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
            setStatusLineVisible(false);
            isStreaming = false;
            hasOutputPrefix = false;
            lastOutputStartIndex = -1;
            currentResponseBuilder = new StringBuilder();
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

    private void setStatusLine(String text) {
        // Compare with last status text
        if (text.equals(lastStatusText)) {
            // Same text, increment dot count (0-5)
            dotCount = (dotCount + 1) % 6;
        } else {
            // Different text, reset dot count
            dotCount = 0;
            lastStatusText = text;
        }

        // Split into two lines
        String line1 = text;
        String line2 = "";

        // If there's a newline, split
        int newlineIndex = text.indexOf('\n');
        if (newlineIndex >= 0) {
            line1 = text.substring(0, newlineIndex);
            line2 = text.substring(newlineIndex + 1);
        }

        // Add dots to first line
        if (dotCount > 0) {
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < dotCount; i++) {
                dots.append(".");
            }
            line1 = line1 + dots;
        }

        statusLineLiveData.postValue(line1);
        statusLineLiveData2.postValue(line2);
    }

    private void setStatusLineVisible(boolean visible) {
        statusLineVisibleLiveData.postValue(visible);
    }

    private void updateOutputLiveData() {
        while (outputLines.size() > MAX_HISTORY_LINES) {
            outputLines.remove(0);
            if (lastOutputStartIndex > 0) {
                lastOutputStartIndex--;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String line : outputLines) {
            sb.append(line);
        }
        outputLiveData.postValue(sb.toString());
    }

    private void appendOutput(String text) {
        String[] lines = text.split("(?<=\\n)");
        for (String line : lines) {
            outputLines.add(line);
        }

        updateOutputLiveData();
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

    private int getStatusIcon(Status status) {
        switch (status) {
            case CONNECTED:
            case IDLE:
                return android.R.drawable.ic_dialog_info;
            case CONNECTING:
            case WAITING:
            case SENDING:
                return android.R.drawable.ic_popup_sync;
            case LISTENING:
            case RECEIVING:
                return android.R.drawable.ic_btn_speak_now;
            case DISCONNECTED:
                return android.R.drawable.ic_dialog_alert;
            case ERROR:
                return android.R.drawable.ic_delete;
            default:
                return android.R.drawable.ic_dialog_info;
        }
    }

    private String getAudioPlayerNotificationText() {
        if (!isAudioPlayerBound || audioPlayerManager == null) {
            return "";
        }
        List<String> playlist = audioPlayerManager.getPlaylist();
        if (playlist.isEmpty()) {
            return "";
        }
        int currentIndex = audioPlayerManager.getCurrentIndex();
        String currentFile = "";
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            String path = playlist.get(currentIndex);
            File file = new File(path);
            currentFile = file.getName();
        }
        int remaining = playlist.size() - (currentIndex >= 0 ? 1 : 0);
        if (remaining > 0) {
            return String.format("正在播放: %s (剩余 %d 首)", currentFile, remaining);
        } else {
            return String.format("正在播放: %s", currentFile);
        }
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
        int iconRes = getStatusIcon(status);
        String contentText = getAudioPlayerNotificationText();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("皮皮虾")
                .setSmallIcon(iconRes)
                .setColor(color)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (!contentText.isEmpty()) {
            builder.setContentText(contentText);
        }

        if (isAudioPlayerBound && audioPlayerManager != null) {
            Intent playIntent = new Intent(this, AudioPlayerManager.class);
            playIntent.setAction(AudioPlayerManager.ACTION_PLAY);
            PendingIntent playPendingIntent = PendingIntent.getService(
                    this, 0, playIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Intent pauseIntent = new Intent(this, AudioPlayerManager.class);
            pauseIntent.setAction(AudioPlayerManager.ACTION_PAUSE);
            PendingIntent pausePendingIntent = PendingIntent.getService(
                    this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Intent nextIntent = new Intent(this, AudioPlayerManager.class);
            nextIntent.setAction(AudioPlayerManager.ACTION_NEXT);
            PendingIntent nextPendingIntent = PendingIntent.getService(
                    this, 2, nextIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Intent clearIntent = new Intent(this, AudioPlayerManager.class);
            clearIntent.setAction(AudioPlayerManager.ACTION_CLEAR);
            PendingIntent clearPendingIntent = PendingIntent.getService(
                    this, 3, clearIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            boolean isPlaying = audioPlayerManager.isPlaying();
            List<String> playlist = audioPlayerManager.getPlaylist();

            if (!playlist.isEmpty()) {
                if (isPlaying) {
                    builder.addAction(android.R.drawable.ic_media_pause, "暂停", pausePendingIntent);
                } else {
                    builder.addAction(android.R.drawable.ic_media_play, "播放", playPendingIntent);
                }
                builder.addAction(android.R.drawable.ic_media_next, "下一首", nextPendingIntent);
                builder.addAction(android.R.drawable.ic_menu_delete, "清空", clearPendingIntent);
            }
        } else {
            audioPlayerNeedsUpdate = true;
        }

        return builder.build();
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }

    public void setMainActivityVisible(boolean visible) {
        Log.d(TAG, "MainActivity visible: " + visible);
        isMainActivityVisible = visible;
        if (visible) {
            cancelMessageNotification();
        }
    }

    private void createMessageNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    MESSAGE_CHANNEL_ID,
                    "皮皮虾消息",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("收到新消息时的通知");
            channel.enableVibration(true);
            channel.enableLights(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showMessageNotification(String message) {
        Log.d(TAG, "Showing message notification");
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // 截断过长的消息
        String displayMessage = message;
        if (displayMessage.length() > 100) {
            displayMessage = displayMessage.substring(0, 100) + "...";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
                .setContentTitle("皮皮虾助手")
                .setContentText(displayMessage)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(MESSAGE_NOTIFICATION_ID, builder.build());
        }
    }

    public void cancelMessageNotification() {
        Log.d(TAG, "Canceling message notification");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(MESSAGE_NOTIFICATION_ID);
        }
    }
}
