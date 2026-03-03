package com.claudeui.app.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SpeechRecognizer"

sealed class SpeechState {
    object Idle : SpeechState()
    object Listening : SpeechState()
    data class PartialResult(val text: String) : SpeechState()
    data class FinalResult(val text: String) : SpeechState()
    data class Error(val message: String) : SpeechState()
}

class SpeechRecognizer(private val context: Context) {

    private var recognizer: AndroidSpeechRecognizer? = null

    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {
            Log.d(TAG, "Ready for speech")
            _speechState.value = SpeechState.Listening
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech")
        }

        override fun onRmsChanged(rmsdB: Float) {
        }

        override fun onBufferReceived(buffer: ByteArray?) {
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                AndroidSpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                AndroidSpeechRecognizer.ERROR_CLIENT -> "Client side error"
                AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                AndroidSpeechRecognizer.ERROR_NETWORK -> "Network error"
                AndroidSpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                AndroidSpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                AndroidSpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                AndroidSpeechRecognizer.ERROR_SERVER -> "Server error"
                AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Unknown error: $error"
            }
            Log.e(TAG, "Speech recognition error: $errorMessage")
            _speechState.value = SpeechState.Error(errorMessage)
        }

        override fun onResults(results: android.os.Bundle?) {
            val matches = results?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            Log.d(TAG, "Recognition result: $text")
            if (text.isNotEmpty()) {
                _speechState.value = SpeechState.FinalResult(text)
            }
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val matches = partialResults?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotEmpty()) {
                _speechState.value = SpeechState.PartialResult(text)
            }
        }

        override fun onEvent(eventType: Int, params: android.os.Bundle?) {
        }
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening() {
        if (!hasRecordPermission()) {
            _speechState.value = SpeechState.Error("Permission not granted")
            return
        }

        Log.d(TAG, "Starting speech recognition")

        try {
            recognizer?.destroy()
            recognizer = AndroidSpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(recognitionListener)

            val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            }

            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recognition", e)
            _speechState.value = SpeechState.Error(e.message ?: "Unknown error")
        }
    }

    fun stopListening() {
        Log.d(TAG, "Stopping speech recognition")
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recognition", e)
        }
        _speechState.value = SpeechState.Idle
    }

    fun reset() {
        _speechState.value = SpeechState.Idle
    }

    fun destroy() {
        try {
            recognizer?.destroy()
            recognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying recognizer", e)
        }
    }
}
