package com.example.helloworld.audio;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.example.helloworld.service.ClaudeWebSocketService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioPlayerManager extends Service {
    private static final String TAG = "AudioPlayerManager";

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

    // ClaudeWebSocketService binding
    private ClaudeWebSocketService webSocketService;
    private boolean isWebSocketServiceBound = false;

    private final ServiceConnection webSocketConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ClaudeWebSocketService.LocalBinder binder = (ClaudeWebSocketService.LocalBinder) service;
            webSocketService = binder.getService();
            isWebSocketServiceBound = true;
            Log.d(TAG, "ClaudeWebSocketService bound");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            webSocketService = null;
            isWebSocketServiceBound = false;
            Log.d(TAG, "ClaudeWebSocketService unbound");
        }
    };

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

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_CLEAR);
        filter.addAction(ACTION_ADD_TO_PLAYLIST);
        filter.addAction(ACTION_SET_PLAYLIST);
        registerReceiver(commandReceiver, filter);

        initMediaPlayer();
        bindToWebSocketService();
    }

    private void bindToWebSocketService() {
        Intent intent = new Intent(this, ClaudeWebSocketService.class);
        bindService(intent, webSocketConnection, Context.BIND_AUTO_CREATE);
    }

    private void notifyNotificationUpdate() {
        if (isWebSocketServiceBound && webSocketService != null) {
            webSocketService.notifyAudioPlayerStateChanged();
        }
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
                notifyNotificationUpdate();
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + (intent != null ? intent.getAction() : null));

        if (intent != null && intent.getAction() != null) {
            handleIntentAction(intent, intent.getAction());
        }

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
            notifyNotificationUpdate();
        } else if (playlist.size() > 0 && currentIndex < 0) {
            play(0);
        }
    }

    private void handlePause() {
        Log.d(TAG, "handlePause");
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            notifyNotificationUpdate();
        }
    }

    private void handleNext() {
        Log.d(TAG, "handleNext, currentIndex=" + currentIndex + ", size=" + playlist.size());
        if (playlist.size() > 0 && currentIndex >= 0) {
            if (currentIndex < playlist.size()) {
                playlist.remove(currentIndex);
            }
            if (playlist.size() > 0) {
                if (currentIndex >= playlist.size()) {
                    currentIndex = 0;
                }
                play(currentIndex);
            } else {
                currentIndex = -1;
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
        notifyNotificationUpdate();
        stopSelf();
    }

    public void addToPlaylist(String filePath) {
        Log.d(TAG, "addToPlaylist: " + filePath);
        File file = new File(filePath);
        if (file.exists()) {
            playlist.add(filePath);
            notifyNotificationUpdate();
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
        notifyNotificationUpdate();
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
            notifyNotificationUpdate();
        } catch (IOException e) {
            Log.e(TAG, "Failed to play: " + filePath, e);
            handleNext();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        unregisterReceiver(commandReceiver);

        if (isWebSocketServiceBound) {
            unbindService(webSocketConnection);
            isWebSocketServiceBound = false;
        }

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

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
