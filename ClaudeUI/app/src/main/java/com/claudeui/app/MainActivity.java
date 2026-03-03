package com.claudeui.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;

public class MainActivity extends AppCompatActivity {

    private static final int RECORD_AUDIO_PERMISSION_CODE = 1001;
    private static final String SERVER_URL = "ws://10.0.2.2:8765";

    private TextView statusText;
    private TextView outputText;
    private EditText inputText;
    private Button sendButton;

    private WebSocketClient webSocketClient;
    private StringBuilder outputBuffer = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        outputText = findViewById(R.id.outputText);
        inputText = findViewById(R.id.inputText);
        sendButton = findViewById(R.id.sendButton);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = inputText.getText().toString().trim();
                if (!text.isEmpty()) {
                    sendMessage(text);
                    inputText.setText("");
                }
            }
        });

        checkPermissions();
        connectWebSocket();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    RECORD_AUDIO_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "录音权限已授予", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void connectWebSocket() {
        try {
            URI uri = URI.create(SERVER_URL);
            statusText.setText("连接中...");

            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("已连接");
                        }
                    });
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JSONObject json = new JSONObject(message);
                        String type = json.optString("type");

                        if ("output".equals(type)) {
                            final String data = json.optString("data", "");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    appendOutput(data);
                                    statusText.setText("等待回复...");
                                }
                            });
                        } else if ("error".equals(type)) {
                            final String data = json.optString("data", "");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    appendOutput("错误: " + data + "\n");
                                }
                            });
                        } else if ("exit".equals(type)) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    statusText.setText("已完成");
                                }
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("未连接");
                        }
                    });
                }

                @Override
                public void onError(Exception ex) {
                    final String error = ex.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("错误: " + error);
                        }
                    });
                }
            };

            webSocketClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
            statusText.setText("连接失败: " + e.getMessage());
        }
    }

    private void sendMessage(String text) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "input");
                json.put("text", text);
                webSocketClient.send(json.toString());
                statusText.setText("发送中...");
                appendOutput("> " + text + "\n");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "未连接到服务器", Toast.LENGTH_SHORT).show();
        }
    }

    private void appendOutput(String text) {
        outputBuffer.append(text);
        outputText.setText(outputBuffer.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }
}
