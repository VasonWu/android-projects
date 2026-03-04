package com.example.helloworld.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.helloworld.network.protocol.CancelMessage;
import com.example.helloworld.network.protocol.ErrorMessage;
import com.example.helloworld.network.protocol.ExitMessage;
import com.example.helloworld.network.protocol.InputMessage;
import com.example.helloworld.network.protocol.MessageType;
import com.example.helloworld.network.protocol.OutputMessage;
import com.example.helloworld.network.protocol.SessionCreateMessage;
import com.example.helloworld.network.protocol.SessionListMessage;
import com.example.helloworld.network.protocol.SessionSelectMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class WebSocketClient {
    private static final String TAG = "WebSocketClient";
    private static final String DEFAULT_SERVER_URI = "ws://127.0.0.1:8765";
    private static final long RECONNECT_DELAY_MS = 3000;

    public interface Listener {
        void onConnected();
        void onDisconnected(String reason);
        void onOutputReceived(String data);
        void onErrorReceived(String data);
        void onExitReceived(int code);
        void onSessionCreated(String sessionId);
        void onSessionSelected(String sessionId);
        void onSessionList(String sessionsJson);
        void onProcessStarted(String sessionId);
        void onProcessStopped(String sessionId, String reason);
        void onProcessCrashed(String sessionId);
    }

    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String serverUri;
    private String clientId;
    private Listener listener;
    private WebSocketClientImpl client;
    private boolean isConnecting = false;

    public WebSocketClient() {
        this(DEFAULT_SERVER_URI);
    }

    public WebSocketClient(String serverUri) {
        this.serverUri = serverUri;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized void connect() {
        if (isConnecting || (client != null && client.isOpen())) {
            Log.d(TAG, "Already connected or connecting");
            return;
        }

        isConnecting = true;
        try {
            URI uri = URI.create(serverUri);
            client = new WebSocketClientImpl(uri);
            client.connect();
            Log.d(TAG, "Connecting to " + serverUri);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create client", e);
            isConnecting = false;
            scheduleReconnect();
        }
    }

    public synchronized void disconnect() {
        isConnecting = false;
        if (client != null) {
            client.close();
            client = null;
        }
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    public void sendInput(String text, String sessionId) {
        if (clientId == null) {
            Log.e(TAG, "clientId not set");
            return;
        }
        InputMessage message = new InputMessage(text, clientId, sessionId);
        send(gson.toJson(message));
    }

    public void sendCancel() {
        if (clientId == null) {
            Log.e(TAG, "clientId not set");
            return;
        }
        CancelMessage message = new CancelMessage(clientId);
        send(gson.toJson(message));
    }

    public void sendSessionCreate() {
        if (clientId == null) {
            Log.e(TAG, "clientId not set");
            return;
        }
        SessionCreateMessage message = new SessionCreateMessage(clientId);
        send(gson.toJson(message));
    }

    public void sendSessionSelect(String sessionId) {
        if (clientId == null) {
            Log.e(TAG, "clientId not set");
            return;
        }
        SessionSelectMessage message = new SessionSelectMessage(clientId, sessionId);
        send(gson.toJson(message));
    }

    public void sendSessionList() {
        if (clientId == null) {
            Log.e(TAG, "clientId not set");
            return;
        }
        SessionListMessage message = new SessionListMessage(clientId);
        send(gson.toJson(message));
    }

    private void send(String json) {
        if (client != null && client.isOpen()) {
            client.send(json);
            Log.d(TAG, "Sent: " + json);
        } else {
            Log.w(TAG, "Not connected, cannot send message");
        }
    }

    private void scheduleReconnect() {
        if (!isConnecting) {
            Log.d(TAG, "Scheduling reconnect in " + RECONNECT_DELAY_MS + "ms");
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    connect();
                }
            }, RECONNECT_DELAY_MS);
        }
    }

    private void notifyOnConnected() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onConnected();
                }
            }
        });
    }

    private void notifyOnDisconnected(final String reason) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onDisconnected(reason);
                }
            }
        });
    }

    private void notifyOnOutputReceived(final String data) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onOutputReceived(data);
                }
            }
        });
    }

    private void notifyOnErrorReceived(final String data) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onErrorReceived(data);
                }
            }
        });
    }

    private void notifyOnExitReceived(final int code) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onExitReceived(code);
                }
            }
        });
    }

    private void notifyOnSessionCreated(final String sessionId) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onSessionCreated(sessionId);
                }
            }
        });
    }

    private void notifyOnSessionSelected(final String sessionId) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onSessionSelected(sessionId);
                }
            }
        });
    }

    private void notifyOnSessionList(final String sessionsJson) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onSessionList(sessionsJson);
                }
            }
        });
    }

    private void notifyOnProcessStarted(final String sessionId) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onProcessStarted(sessionId);
                }
            }
        });
    }

    private void notifyOnProcessStopped(final String sessionId, final String reason) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onProcessStopped(sessionId, reason);
                }
            }
        });
    }

    private void notifyOnProcessCrashed(final String sessionId) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onProcessCrashed(sessionId);
                }
            }
        });
    }

    private class WebSocketClientImpl extends org.java_websocket.client.WebSocketClient {
        public WebSocketClientImpl(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            Log.d(TAG, "WebSocket connected");
            isConnecting = false;
            notifyOnConnected();
        }

        @Override
        public void onMessage(String message) {
            Log.d(TAG, "Received raw message: " + message);
            handleMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            Log.d(TAG, "WebSocket closed: code=" + code + ", reason=" + reason + ", remote=" + remote);
            isConnecting = false;
            notifyOnDisconnected(reason);
            scheduleReconnect();
        }

        @Override
        public void onError(Exception ex) {
            Log.e(TAG, "WebSocket error", ex);
            isConnecting = false;
            notifyOnErrorReceived(ex.getMessage() != null ? ex.getMessage() : "Unknown error");
        }

        private void handleMessage(String json) {
            try {
                Log.d(TAG, "Parsing message: " + json);
                JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();

                if (!jsonObj.has("type")) {
                    Log.w(TAG, "Message missing 'type' field");
                    return;
                }

                String type = jsonObj.get("type").getAsString();
                Log.d(TAG, "Message type: " + type);

                if (MessageType.TYPE_OUTPUT.equals(type)) {
                    if (jsonObj.has("data")) {
                        String data = jsonObj.get("data").getAsString();
                        Log.d(TAG, "Output data: " + data);
                        notifyOnOutputReceived(data);
                    } else {
                        Log.w(TAG, "Output message missing 'data' field");
                    }
                } else if (MessageType.TYPE_ERROR.equals(type)) {
                    if (jsonObj.has("data")) {
                        String data = jsonObj.get("data").getAsString();
                        notifyOnErrorReceived(data);
                    }
                } else if (MessageType.TYPE_EXIT.equals(type)) {
                    int code = jsonObj.has("code") ? jsonObj.get("code").getAsInt() : 0;
                    notifyOnExitReceived(code);
                } else if (MessageType.TYPE_SESSION_CREATED.equals(type)) {
                    if (jsonObj.has("session_id")) {
                        String sessionId = jsonObj.get("session_id").getAsString();
                        notifyOnSessionCreated(sessionId);
                    }
                } else if (MessageType.TYPE_SESSION_SELECTED.equals(type)) {
                    if (jsonObj.has("session_id")) {
                        String sessionId = jsonObj.get("session_id").getAsString();
                        notifyOnSessionSelected(sessionId);
                    }
                } else if (MessageType.TYPE_SESSION_LIST.equals(type)) {
                    notifyOnSessionList(json);
                } else if (MessageType.TYPE_PROCESS_STARTED.equals(type)) {
                    String sessionId = jsonObj.has("session_id") ? jsonObj.get("session_id").getAsString() : null;
                    notifyOnProcessStarted(sessionId);
                } else if (MessageType.TYPE_PROCESS_STOPPED.equals(type)) {
                    String sessionId = jsonObj.has("session_id") ? jsonObj.get("session_id").getAsString() : null;
                    String reason = jsonObj.has("reason") ? jsonObj.get("reason").getAsString() : null;
                    notifyOnProcessStopped(sessionId, reason);
                } else if (MessageType.TYPE_PROCESS_CRASHED.equals(type)) {
                    String sessionId = jsonObj.has("session_id") ? jsonObj.get("session_id").getAsString() : null;
                    notifyOnProcessCrashed(sessionId);
                } else {
                    Log.w(TAG, "Unknown message type: " + type);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse message: " + json, e);
            }
        }
    }
}
