package com.claudeui.app.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.java_websocket.client.WebSocketClient as JavaWebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer

private const val TAG = "WebSocketClient"
private const val RECONNECT_DELAY_MS = 3000L

sealed class WebSocketMessage {
    data class Output(val data: String) : WebSocketMessage()
    data class Error(val data: String) : WebSocketMessage()
    data class Exit(val code: Int) : WebSocketMessage()
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
}

class WebSocketClient(
    private val serverUrl: String = "ws://10.0.2.2:8765"
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var client: JavaWebSocketClient? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableStateFlow<WebSocketMessage?>(null)
    val messages: StateFlow<WebSocketMessage?> = _messages.asStateFlow()

    private var autoReconnect = true

    fun connect() {
        if (_connectionState.value == ConnectionState.Connecting ||
            _connectionState.value == ConnectionState.Connected) {
            Log.d(TAG, "Already connecting or connected")
            return
        }

        autoReconnect = true
        scope.launch {
            connectInternal()
        }
    }

    fun disconnect() {
        autoReconnect = false
        client?.close()
        client = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private suspend fun connectInternal() {
        try {
            _connectionState.value = ConnectionState.Connecting
            Log.d(TAG, "Connecting to $serverUrl")

            val uri = URI.create(serverUrl)
            client = object : JavaWebSocketClient(uri) {
                override fun onOpen(handshake: ServerHandshake?) {
                    Log.d(TAG, "WebSocket connected")
                    _connectionState.value = ConnectionState.Connected
                }

                override fun onMessage(message: String?) {
                    message?.let {
                        Log.d(TAG, "Received message: $it")
                        handleMessage(it)
                    }
                }

                override fun onMessage(bytes: ByteBuffer?) {
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.d(TAG, "WebSocket closed: code=$code, reason=$reason, remote=$remote")
                    _connectionState.value = ConnectionState.Disconnected

                    if (autoReconnect) {
                        scope.launch {
                            delay(RECONNECT_DELAY_MS)
                            if (autoReconnect) {
                                connectInternal()
                            }
                        }
                    }
                }

                override fun onError(ex: Exception?) {
                    Log.e(TAG, "WebSocket error", ex)
                    _messages.value = WebSocketMessage.Error(ex?.message ?: "Unknown error")
                }
            }

            client?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect", e)
            _connectionState.value = ConnectionState.Disconnected
            if (autoReconnect) {
                delay(RECONNECT_DELAY_MS)
                connectInternal()
            }
        }
    }

    private fun handleMessage(jsonString: String) {
        try {
            val json = org.json.JSONObject(jsonString)
            when (json.optString("type")) {
                "output" -> {
                    val data = json.optString("data", "")
                    _messages.value = WebSocketMessage.Output(data)
                }
                "error" -> {
                    val data = json.optString("data", "")
                    _messages.value = WebSocketMessage.Error(data)
                }
                "exit" -> {
                    val code = json.optInt("code", 0)
                    _messages.value = WebSocketMessage.Exit(code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message", e)
        }
    }

    fun sendInput(text: String) {
        if (_connectionState.value != ConnectionState.Connected) {
            Log.w(TAG, "Not connected, cannot send message")
            return
        }

        try {
            val json = org.json.JSONObject()
            json.put("type", "input")
            json.put("text", text)
            client?.send(json.toString())
            Log.d(TAG, "Sent input: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
        }
    }

    fun sendCancel() {
        if (_connectionState.value != ConnectionState.Connected) {
            Log.w(TAG, "Not connected, cannot send cancel")
            return
        }

        try {
            val json = org.json.JSONObject()
            json.put("type", "cancel")
            client?.send(json.toString())
            Log.d(TAG, "Sent cancel")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send cancel", e)
        }
    }

    fun clearMessages() {
        _messages.value = null
    }
}
