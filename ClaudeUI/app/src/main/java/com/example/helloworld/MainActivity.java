package com.example.helloworld;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import com.example.helloworld.service.ClaudeWebSocketService;
import com.example.helloworld.speech.SpeechRecognizerManager;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private static final int VOICE_MODE = 0;
    private static final int TEXT_MODE = 1;

    private ClaudeWebSocketService service;
    private boolean isServiceBound = false;
    private SpeechRecognizerManager speechRecognizerManager;

    private TextView statusText;
    private TextView outputText;
    private ScrollView outputScrollView;
    private ViewFlipper inputFlipper;
    private ImageView micButton;
    private TextView micHintText;
    private TextView tapToTypeText;
    private EditText textInput;
    private ImageButton sendButton;
    private ImageButton voiceModeButton;

    private boolean isRecording = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            ClaudeWebSocketService.LocalBinder localBinder = (ClaudeWebSocketService.LocalBinder) binder;
            service = localBinder.getService();
            isServiceBound = true;
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
        requestPermissions();
        bindToService();

        handleIntent(getIntent());
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
        inputFlipper = findViewById(R.id.inputFlipper);

        micButton = findViewById(R.id.micButton);
        micHintText = findViewById(R.id.micHintText);
        tapToTypeText = findViewById(R.id.tapToTypeText);
        textInput = findViewById(R.id.textInput);
        sendButton = findViewById(R.id.sendButton);
        voiceModeButton = findViewById(R.id.voiceModeButton);

        setupClickListeners();
    }

    private void setupClickListeners() {
        micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRecording();
            }
        });

        tapToTypeText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToTextMode();
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTextInput();
            }
        });

        voiceModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToVoiceMode();
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
                micHintText.setText(text);
            }

            @Override
            public void onResults(String text) {
                updateMicState(false);
                micHintText.setText("点击说话");
                if (!text.isEmpty() && isServiceBound) {
                    service.sendInput(text);
                } else if (isServiceBound) {
                    service.setIdleStatus();
                }
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
                updateMicState(false);
                micHintText.setText("点击说话");
                setStatusText("语音识别错误: " + errorMessage);
                if (isServiceBound) {
                    service.setIdleStatus();
                }
            }
        });
    }

    private void requestPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.RECORD_AUDIO
            };
        }

        boolean needRequest = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }

        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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

        outputText.setText(service.getOutputBuffer());
        scrollToBottom();

        service.getStatusLiveData().observe(this, new Observer<ClaudeWebSocketService.Status>() {
            @Override
            public void onChanged(ClaudeWebSocketService.Status status) {
                updateStatusText(status);
            }
        });

        service.getOutputLiveData().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String output) {
                outputText.setText(output);
                scrollToBottom();
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
            requestPermissions();
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

    private void switchToTextMode() {
        inputFlipper.setDisplayedChild(TEXT_MODE);
        textInput.requestFocus();
        showKeyboard();
    }

    private void switchToVoiceMode() {
        hideKeyboard();
        textInput.setText("");
        inputFlipper.setDisplayedChild(VOICE_MODE);
    }

    private void sendTextInput() {
        String text = textInput.getText().toString().trim();
        if (!text.isEmpty() && isServiceBound) {
            service.sendInput(text);
        }
        switchToVoiceMode();
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
