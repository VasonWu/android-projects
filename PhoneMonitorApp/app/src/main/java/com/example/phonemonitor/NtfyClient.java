package com.example.phonemonitor;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NtfyClient {
    private static final String TAG = "NtfyClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private PreferencesHelper prefs;
    private OkHttpClient client;
    private Queue<PendingMessage> pendingMessages;
    private boolean isConnected;
    private RetryThread retryThread;

    private static class PendingMessage {
        String title;
        String message;
        String tags;
        String priority;

        PendingMessage(String title, String message, String tags, String priority) {
            this.title = title;
            this.message = message;
            this.tags = tags;
            this.priority = priority;
        }
    }

    public NtfyClient(Context context) {
        this.prefs = new PreferencesHelper(context);
        this.pendingMessages = new ConcurrentLinkedQueue<>();
        this.isConnected = true;

        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void sendSms(String sender, String content) {
        send("新短信 - " + sender, content, "sms,phone", "default");
    }

    public void sendIncomingCall(String phoneNumber) {
        send("来电 - " + phoneNumber, "来电时间: " + System.currentTimeMillis(), "call,phone", "high");
    }

    public void sendMissedCall(String phoneNumber) {
        send("未接来电 - " + phoneNumber, "未接时间: " + System.currentTimeMillis(), "call,phone", "default");
    }

    public void sendCallEnded(String phoneNumber, long duration) {
        send("通话结束 - " + phoneNumber, "通话时长: " + duration + "ms", "call,phone", "default");
    }

    private void send(String title, String message, String tags, String priority) {
        if (isConnected) {
            sendImmediately(title, message, tags, priority);
        } else {
            pendingMessages.add(new PendingMessage(title, message, tags, priority));
            startRetryThread();
        }
    }

    private boolean sendImmediately(String title, String message, String tags, String priority) {
        try {
            String server = prefs.getNtfyServer();
            String topic = prefs.getNtfyTopic();
            String url = server + "/" + topic;

            JSONObject json = new JSONObject();
            json.put("title", title);
            json.put("message", message);
            if (tags != null && !tags.isEmpty()) {
                json.put("tags", tags);
            }
            if (priority != null && !priority.isEmpty()) {
                json.put("priority", priority);
            }

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    isConnected = true;
                    Log.i(TAG, "Message sent successfully");
                    sendPendingMessages();
                    return true;
                } else {
                    isConnected = false;
                    Log.e(TAG, "Failed to send message: " + response.code());
                    return false;
                }
            }
        } catch (Exception e) {
            isConnected = false;
            Log.e(TAG, "Error sending message", e);
            return false;
        }
    }

    private void sendPendingMessages() {
        PendingMessage pending;
        while ((pending = pendingMessages.poll()) != null) {
            sendImmediately(pending.title, pending.message, pending.tags, pending.priority);
        }
    }

    private void startRetryThread() {
        if (retryThread == null || !retryThread.isAlive()) {
            retryThread = new RetryThread();
            retryThread.start();
        }
    }

    private class RetryThread extends Thread {
        private volatile boolean running = true;

        @Override
        public void run() {
            while (running && !pendingMessages.isEmpty()) {
                PendingMessage pending = pendingMessages.peek();
                if (pending != null && sendImmediately(pending.title, pending.message, pending.tags, pending.priority)) {
                    pendingMessages.poll();
                } else {
                    try {
                        Thread.sleep(prefs.getRetryInterval() * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        public void stopThread() {
            running = false;
            interrupt();
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }
}
