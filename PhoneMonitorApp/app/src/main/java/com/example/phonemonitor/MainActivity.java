package com.example.phonemonitor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private PreferencesHelper prefs;
    private NtfyClient ntfyClient;

    private TextView statusText;
    private EditText serverEditText;
    private EditText topicEditText;
    private EditText retryEditText;
    private Button saveButton;
    private Button testButton;
    private Button testSmsButton;
    private Button testCallButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PreferencesHelper(this);
        ntfyClient = new NtfyClient(this);

        setupViews();
        loadPreferences();
        requestPermissions();
        startMonitorService();
    }

    private void setupViews() {
        statusText = findViewById(R.id.statusText);
        serverEditText = findViewById(R.id.serverEditText);
        topicEditText = findViewById(R.id.topicEditText);
        retryEditText = findViewById(R.id.retryEditText);
        saveButton = findViewById(R.id.saveButton);
        testButton = findViewById(R.id.testButton);
        testSmsButton = findViewById(R.id.testSmsButton);
        testCallButton = findViewById(R.id.testCallButton);

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePreferences();
                startMonitorService();
            }
        });

        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTestMessage();
            }
        });

        testSmsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTestSms();
            }
        });

        testCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTestCall();
            }
        });
    }

    private void loadPreferences() {
        serverEditText.setText(prefs.getNtfyServer());
        topicEditText.setText(prefs.getNtfyTopic());
        retryEditText.setText(String.valueOf(prefs.getRetryInterval()));
    }

    private void savePreferences() {
        String server = serverEditText.getText().toString().trim();
        String topic = topicEditText.getText().toString().trim();
        String retryStr = retryEditText.getText().toString().trim();

        if (!TextUtils.isEmpty(server)) {
            prefs.setNtfyServer(server);
        }
        if (!TextUtils.isEmpty(topic)) {
            prefs.setNtfyTopic(topic);
        }
        if (!TextUtils.isEmpty(retryStr)) {
            try {
                int retry = Integer.parseInt(retryStr);
                prefs.setRetryInterval(retry);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
    }

    private void startMonitorService() {
        Intent intent = new Intent(this, MonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        statusText.setText("状态: 服务已启动");
        Toast.makeText(this, "监控服务已启动", Toast.LENGTH_SHORT).show();
    }

    private void sendTestMessage() {
        savePreferences();
        new Thread(new Runnable() {
            @Override
            public void run() {
                ntfyClient.sendSms("测试号码", "这是一条测试短信！");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "测试消息已发送", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void sendTestSms() {
        savePreferences();
        new Thread(new Runnable() {
            @Override
            public void run() {
                ntfyClient.sendSms("10086", "测试短信内容：这是一条测试短信通知！");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "测试短信通知已发送", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void sendTestCall() {
        savePreferences();
        new Thread(new Runnable() {
            @Override
            public void run() {
                ntfyClient.sendIncomingCall("13800138000");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "测试来电通知已发送", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            java.util.List<String> permissions = new java.util.ArrayList<>();
            permissions.add(Manifest.permission.RECEIVE_SMS);
            permissions.add(Manifest.permission.READ_SMS);
            permissions.add(Manifest.permission.READ_PHONE_STATE);
            permissions.add(Manifest.permission.READ_CALL_LOG);
            permissions.add(Manifest.permission.READ_PHONE_NUMBERS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            requestPermissions(permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "需要所有权限才能正常工作", Toast.LENGTH_LONG).show();
            }
        }
    }
}
