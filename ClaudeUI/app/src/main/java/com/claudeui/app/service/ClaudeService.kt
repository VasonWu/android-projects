package com.claudeui.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.claudeui.app.R
import com.claudeui.app.network.ConnectionState
import com.claudeui.app.network.WebSocketClient
import com.claudeui.app.network.WebSocketMessage
import com.claudeui.app.speech.SpeechRecognizer
import com.claudeui.app.speech.SpeechState
import com.claudeui.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ClaudeService"
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "claude_service_channel"

sealed class UiState {
    object Idle : UiState()
    object Listening : UiState()
    object Sending : UiState()
    object Receiving : UiState()
    data class Error(val message: String) : UiState()
}

class ClaudeService : Service() {

    private val binder = LocalBinder()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var webSocketClient: WebSocketClient
    private lateinit var speechRecognizer: SpeechRecognizer

    private val _conversationOutput = MutableStateFlow<String>("")
    val conversationOutput: StateFlow<String> = _conversationOutput.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val connectionState: StateFlow<ConnectionState>
        get() = webSocketClient.connectionState

    inner class LocalBinder : Binder() {
        fun getService(): ClaudeService = this@ClaudeService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        webSocketClient = WebSocketClient()
        speechRecognizer = SpeechRecognizer(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        observeFlows()

        webSocketClient.connect()
    }

    private fun observeFlows() {
        serviceScope.launch {
            webSocketClient.messages.collect { message ->
                when (message) {
                    is WebSocketMessage.Output -> {
                        Log.d(TAG, "Received output: ${message.data}")
                        _conversationOutput.value += message.data
                        _uiState.value = UiState.Receiving
                    }
                    is WebSocketMessage.Error -> {
                        Log.e(TAG, "Received error: ${message.data}")
                        _uiState.value = UiState.Error(message.data)
                    }
                    is WebSocketMessage.Exit -> {
                        Log.d(TAG, "Process exited with code: ${message.code}")
                        _uiState.value = UiState.Idle
                    }
                    null -> {}
                }
            }
        }

        serviceScope.launch {
            speechRecognizer.speechState.collect { state ->
                when (state) {
                    is SpeechState.Idle -> {
                        _uiState.value = UiState.Idle
                    }
                    is SpeechState.Listening -> {
                        _uiState.value = UiState.Listening
                    }
                    is SpeechState.PartialResult -> {
                    }
                    is SpeechState.FinalResult -> {
                        Log.d(TAG, "Final speech result: ${state.text}")
                        sendInput(state.text)
                    }
                    is SpeechState.Error -> {
                        _uiState.value = UiState.Error(state.message)
                    }
                }
            }
        }
    }

    fun startListening() {
        if (_uiState.value == UiState.Listening) return
        speechRecognizer.startListening()
    }

    fun stopListening() {
        speechRecognizer.stopListening()
    }

    fun sendInput(text: String) {
        if (text.isBlank()) return

        Log.d(TAG, "Sending input: $text")
        _uiState.value = UiState.Sending
        webSocketClient.sendInput(text)
    }

    fun cancel() {
        Log.d(TAG, "Cancelling current operation")
        webSocketClient.sendCancel()
        speechRecognizer.stopListening()
        _uiState.value = UiState.Idle
    }

    fun clearConversation() {
        _conversationOutput.value = ""
        webSocketClient.clearMessages()
    }

    fun hasRecordPermission(): Boolean = speechRecognizer.hasRecordPermission()

    fun reconnect() {
        webSocketClient.disconnect()
        webSocketClient.connect()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Claude Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Claude Assistant Background Service"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        speechRecognizer.destroy()
        webSocketClient.disconnect()
        super.onDestroy()
    }
}
