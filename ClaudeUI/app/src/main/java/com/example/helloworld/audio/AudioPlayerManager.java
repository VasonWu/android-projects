package com.example.helloworld.audio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.helloworld.MainActivity;
import com.example.helloworld.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioPlayerManager extends Service {
    private static final String TAG = "AudioPlayerManager";
    private static final String CHANNEL_ID = "AudioPlayerChannel";
    private static final int NOTIFICATION_ID = 1001;

    // Intent Actions
    public static final String ACTION_PLAY = "com.example.helloworld.PLAY";
    public static final String ACTION_PAUSE = "com.example.helloworld.PAUSE";
    public static final String ACTION_NEXT = "com.example.helloworld.NEXT";
    public static final String ACTION_CLEAR = "com.example.helloworld.CLEAR";
    public static final String ACTION_ADD_TO_PLAYLIST = "com.example.helloworld.ADD_TO_PLAYLIST";
    public static final String ACTION_SET_PLAYLIST = "com.example.helloworld.SET_PLAYLIST";

    // Extras
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_FILE_PATH_LIST = "file_path_list";

    private MediaPlayer mediaPlayer;
    private List<String> playlist = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isPrepared = false;

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public AudioPlayerManager getService() {
            return AudioPlayerManager.this;
        }
    }

    private BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            handleIntentAction(intent, action);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AudioPlayerService created");

        // Register receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_CLEAR);
        filter.addAction(ACTION_ADD_TO_PLAYLIST);
        filter.addAction(ACTION_SET_PLAYLIST);
        registerReceiver(commandReceiver, filter);

        createNotificationChannel();
        initMediaPlayer();
    }

    private void initMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d(TAG, "Playback completed");
                handleNext();
            }
        });
        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
                handleNext();
                return true;
            }
        });
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                isPrepared = true;
                mp.start();
                updateNotification();
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "皮皮虾音频播放器",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("音频播放控制");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + (intent != null ? intent.getAction() : null));

        // Handle intent actions immediately
        if (intent != null && intent.getAction() != null) {
            handleIntentAction(intent, intent.getAction());
        }

        // Show notification and make it foreground
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    private void handleIntentAction(Intent intent, String action) {
        Log.d(TAG, "handleIntentAction: " + action);
        switch (action) {
            case ACTION_ADD_TO_PLAYLIST:
                String path = intent.getStringExtra(EXTRA_FILE_PATH);
                if (path != null) {
                    addToPlaylist(path);
                } else {
                    // Try to get from getData()
                    Uri data = intent.getData();
                    if (data != null) {
                        addToPlaylist(data.getPath());
                    }
                }
                break;
            case ACTION_SET_PLAYLIST:
                ArrayList<String> paths = intent.getStringArrayListExtra(EXTRA_FILE_PATH_LIST);
                if (paths != null) {
                    setPlaylist(paths);
                }
                break;
            case ACTION_PLAY:
                handlePlay();
                break;
            case ACTION_PAUSE:
                handlePause();
                break;
            case ACTION_NEXT:
                handleNext();
                break;
            case ACTION_CLEAR:
                handleClear();
                break;
        }
    }

    private void handlePlay() {
        Log.d(TAG, "handlePlay");
        if (mediaPlayer != null && isPrepared && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            updateNotification();
        } else if (playlist.size() > 0 && currentIndex < 0) {
            play(0);
        }
    }

    private void handlePause() {
        Log.d(TAG, "handlePause");
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            updateNotification();
        }
    }

    private void handleNext() {
        Log.d(TAG, "handleNext, currentIndex=" + currentIndex + ", size=" + playlist.size());
        if (playlist.size() > 0 && currentIndex >= 0) {
            // Remove the current (completed) track
            if (currentIndex < playlist.size()) {
                playlist.remove(currentIndex);
            }
            // Play next if available
            if (playlist.size() > 0) {
                if (currentIndex >= playlist.size()) {
                    currentIndex = 0;
                }
                play(currentIndex);
            } else {
                currentIndex = -1;
                stopForeground(true);
                stopSelf();
            }
        }
    }

    private void handleClear() {
        Log.d(TAG, "handleClear");
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.reset();
            isPrepared = false;
        }
        playlist.clear();
        currentIndex = -1;
        updateNotification();
        stopForeground(true);
        stopSelf();
    }

    public void addToPlaylist(String filePath) {
        Log.d(TAG, "addToPlaylist: " + filePath);
        File file = new File(filePath);
        if (file.exists()) {
            playlist.add(filePath);
            updateNotification();
            // If nothing is playing, start playing
            if (currentIndex < 0) {
                play(playlist.size() - 1);
            }
        } else {
            Log.e(TAG, "File not found: " + filePath);
        }
    }

    public void setPlaylist(List<String> filePaths) {
        Log.d(TAG, "setPlaylist: " + filePaths.size() + " files");
        playlist.clear();
        for (String path : filePaths) {
            File file = new File(path);
            if (file.exists()) {
                playlist.add(path);
            }
        }
        currentIndex = -1;
        updateNotification();
        if (playlist.size() > 0) {
            play(0);
        }
    }

    private void play(int index) {
        Log.d(TAG, "play: index=" + index);
        if (index < 0 || index >= playlist.size()) {
            return;
        }

        String filePath = playlist.get(index);
        currentIndex = index;

        try {
            if (mediaPlayer == null) {
                initMediaPlayer();
            } else {
                mediaPlayer.reset();
                isPrepared = false;
            }

            mediaPlayer.setDataSource(this, Uri.fromFile(new File(filePath)));
            mediaPlayer.prepareAsync();
            updateNotification();
        } catch (IOException e) {
            Log.e(TAG, "Failed to play: " + filePath, e);
            handleNext();
        }
    }

    private Notification buildNotification() {
        // Create intents for actions
        Intent playIntent = new Intent(this, AudioPlayerManager.class);
        playIntent.setAction(ACTION_PLAY);
        PendingIntent playPendingIntent = PendingIntent.getService(
                this, 0, playIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent pauseIntent = new Intent(this, AudioPlayerManager.class);
        pauseIntent.setAction(ACTION_PAUSE);
        PendingIntent pausePendingIntent = PendingIntent.getService(
                this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent nextIntent = new Intent(this, AudioPlayerManager.class);
        nextIntent.setAction(ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getService(
                this, 2, nextIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent clearIntent = new Intent(this, AudioPlayerManager.class);
        clearIntent.setAction(ACTION_CLEAR);
        PendingIntent clearPendingIntent = PendingIntent.getService(
                this, 3, clearIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Main content intent
        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE);

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("皮皮虾播放器")
                .setContentText(getNotificationText())
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        // Add action buttons
        boolean isPlaying = mediaPlayer != null && mediaPlayer.isPlaying();

        if (isPlaying) {
            builder.addAction(android.R.drawable.ic_media_pause, "暂停", pausePendingIntent);
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "播放", playPendingIntent);
        }

        builder.addAction(android.R.drawable.ic_media_next, "下一首", nextPendingIntent);
        builder.addAction(android.R.drawable.ic_menu_delete, "清空", clearPendingIntent);

        return builder.build();
    }

    private String getNotificationText() {
        if (playlist.isEmpty()) {
            return "播放列表为空";
        }
        String currentFile = "";
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            String path = playlist.get(currentIndex);
            File file = new File(path);
            currentFile = file.getName();
        }
        return String.format("正在播放: %s (剩余 %d 首)",
                currentFile, playlist.size() - (currentIndex >= 0 ? 1 : 0));
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        unregisterReceiver(commandReceiver);

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // Public API for bound clients
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public List<String> getPlaylist() {
        return new ArrayList<>(playlist);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }
}
