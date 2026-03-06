package com.pipixia;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import com.pipixia.service.ClaudeWebSocketService;
import com.pipixia.speech.SpeechRecognizerManager;

import io.noties.markwon.Markwon;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 101;

    private ClaudeWebSocketService service;
    private boolean isServiceBound = false;
    private SpeechRecognizerManager speechRecognizerManager;
    private Markwon markwon;

    private TextView statusText;
    private TextView outputText;
    private ScrollView outputScrollView;
    private EditText textInput;
    private ImageButton sendButton;
    private ImageButton micButton;
    private ImageButton newSessionButton;
    private LinearLayout statusBarLayout;

    private boolean isRecording = false;
    private boolean isActivityVisible = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            ClaudeWebSocketService.LocalBinder localBinder = (ClaudeWebSocketService.LocalBinder) binder;
            service = localBinder.getService();
            isServiceBound = true;
            service.setMainActivityVisible(isActivityVisible);
            observeService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bottom_sheet);

        initViews();
        initSpeechRecognizer();
        requestAllPermissions();
        bindToService();

        handleIntent(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        isActivityVisible = true;
        if (isServiceBound && service != null) {
            service.setMainActivityVisible(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        isActivityVisible = false;
        if (isServiceBound && service != null) {
            service.setMainActivityVisible(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check permissions again when app resumes
        checkAndRequestManageStorage();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();

        if (Intent.ACTION_ASSIST.equals(action) ||
            "android.intent.action.VOICE_ASSIST".equals(action) ||
            Intent.ACTION_SEARCH.equals(action) ||
            "android.speech.action.WEB_SEARCH".equals(action)) {

            if (isRecording) {
                speechRecognizerManager.stopListening();
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                speechRecognizerManager.startListening();
            }
        }
    }

    private void initViews() {
        statusText = findViewById(R.id.statusText);
        outputText = findViewById(R.id.outputText);
        outputScrollView = findViewById(R.id.outputScrollView);
        statusBarLayout = findViewById(R.id.statusBarLayout);

        textInput = findViewById(R.id.textInput);
        sendButton = findViewById(R.id.sendButton);
        micButton = findViewById(R.id.micButton);
        newSessionButton = findViewById(R.id.newSessionButton);

        // 初始化 Markwon
        markwon = Markwon.create(this);

        setupClickListeners();
    }

    private void setupClickListeners() {
        micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRecording();
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTextInput();
            }
        });

        newSessionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isServiceBound) {
                    service.createSession();
                    service.clearOutput();
                }
            }
        });
    }

    private void initSpeechRecognizer() {
        speechRecognizerManager = new SpeechRecognizerManager(this);
        speechRecognizerManager.setListener(new SpeechRecognizerManager.Listener() {
            @Override
            public void onReadyForSpeech() {
                updateMicState(true);
                setStatusText("正在聆听...");
                if (isServiceBound) {
                    service.setListeningStatus();
                }
            }

            @Override
            public void onPartialResults(String text) {
                // 可以在输入框显示临时结果
            }

            @Override
            public void onResults(String text) {
                updateMicState(false);
                if (!text.isEmpty() && isServiceBound) {
                    service.sendInput(text);
                } else if (isServiceBound) {
                    service.setIdleStatus();
                }
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
                updateMicState(false);
                setStatusText("语音识别错误: " + errorMessage);
                if (isServiceBound) {
                    service.setIdleStatus();
                }
            }
        });
    }

    private void requestAllPermissions() {
        Log.d(TAG, "Requesting all permissions...");

        // First check MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE not granted, requesting...");
                checkAndRequestManageStorage();
            } else {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE already granted");
            }
        }

        // Request other permissions
        requestDangerousPermissions();
    }

    private void checkAndRequestManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.d(TAG, "Opening MANAGE_EXTERNAL_STORAGE settings...");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open manage storage settings", e);
                    // Fallback to general settings
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                    } catch (Exception e2) {
                        Log.e(TAG, "Failed to open general settings too", e2);
                    }
                }
            }
        }
    }

    private void requestDangerousPermissions() {
        List<String> permissions = new ArrayList<>();

        // Always request RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        // POST_NOTIFICATIONS for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // READ_EXTERNAL_STORAGE for Android < 13
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        // READ_MEDIA_AUDIO for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        }

        if (!permissions.isEmpty()) {
            Log.d(TAG, "Requesting dangerous permissions: " + permissions);
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "All dangerous permissions already granted");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Log.d(TAG, "MANAGE_EXTERNAL_STORAGE granted!");
                } else {
                    Log.d(TAG, "MANAGE_EXTERNAL_STORAGE not granted");
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                Log.d(TAG, "Permission: " + permissions[i] + " = " +
                        (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
            }
        }
    }

    private void bindToService() {
        Intent intent = new Intent(this, ClaudeWebSocketService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void observeService() {
        if (service == null) return;

        // 使用 Markwon 渲染初始内容
        markwon.setMarkdown(outputText, service.getOutputBuffer());
        scrollToBottom();

        service.getStatusLiveData().observe(this, new Observer<ClaudeWebSocketService.Status>() {
            @Override
            public void onChanged(ClaudeWebSocketService.Status status) {
                // 只有当没有过程信息时才更新状态文本
                Boolean statusLineVisible = service.getStatusLineVisibleLiveData().getValue();
                if (statusLineVisible == null || !statusLineVisible) {
                    updateStatusText(status);
                }
            }
        });

        service.getOutputLiveData().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String output) {
                // 使用 Markwon 渲染 Markdown 内容
                markwon.setMarkdown(outputText, output);
                scrollToBottom();
                // 如果Activity可见，取消任何未读通知
                if (isActivityVisible && isServiceBound && service != null) {
                    service.cancelMessageNotification();
                }
            }
        });

        service.getStatusLineLiveData().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String statusLine) {
                statusText.setText(statusLine);
            }
        });

        service.getStatusLineVisibleLiveData().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean visible) {
                if (!visible) {
                    // 没有过程信息时：恢复显示当前状态
                    ClaudeWebSocketService.Status status = service.getCurrentStatus();
                    if (status != null) {
                        updateStatusText(status);
                    }
                }
            }
        });
    }

    private void updateStatusText(ClaudeWebSocketService.Status status) {
        switch (status) {
            case IDLE:
                setStatusText("准备就绪");
                break;
            case CONNECTING:
                setStatusText("正在连接...");
                break;
            case CONNECTED:
                setStatusText("已连接");
                break;
            case LISTENING:
                setStatusText("正在聆听...");
                break;
            case SENDING:
                setStatusText("正在发送...");
                break;
            case WAITING:
                setStatusText("正在等待回复...");
                break;
            case RECEIVING:
                setStatusText("正在接收...");
                break;
            case DISCONNECTED:
                setStatusText("已断开连接");
                break;
            case ERROR:
                setStatusText("出错了");
                break;
        }
    }

    private void setStatusText(String text) {
        statusText.setText(text);
    }

    private void scrollToBottom() {
        outputScrollView.post(new Runnable() {
            @Override
            public void run() {
                outputScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void toggleRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestAllPermissions();
            return;
        }

        if (isRecording) {
            speechRecognizerManager.stopListening();
        } else {
            speechRecognizerManager.startListening();
        }
    }

    private void updateMicState(boolean recording) {
        isRecording = recording;
        if (recording) {
            micButton.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light));
        } else {
            micButton.setColorFilter(null);
        }
    }

    private void sendTextInput() {
        String text = textInput.getText().toString().trim();
        if (!text.isEmpty() && isServiceBound) {
            service.sendInput(text);
        }
        textInput.setText("");
        hideKeyboard();
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(textInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        if (speechRecognizerManager != null) {
            speechRecognizerManager.destroy();
        }
    }
}
