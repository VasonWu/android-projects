package com.pipixia.speech;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;

public class SpeechRecognizerManager {
    private static final String TAG = "SpeechRecognizerManager";

    public interface Listener {
        void onReadyForSpeech();
        void onPartialResults(String text);
        void onResults(String text);
        void onError(int errorCode, String errorMessage);
    }

    private final Context context;
    private final SpeechRecognizer speechRecognizer;
    private Listener listener;
    private boolean isListening = false;

    public SpeechRecognizerManager(Context context) {
        this.context = context.getApplicationContext();
        this.speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this.context);
        this.speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "onReadyForSpeech");
                isListening = true;
                if (listener != null) {
                    listener.onReadyForSpeech();
                }
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech");
                isListening = false;
            }

            @Override
            public void onError(int error) {
                Log.e(TAG, "onError: " + error);
                isListening = false;
                if (listener != null) {
                    listener.onError(error, getErrorMessage(error));
                }
            }

            @Override
            public void onResults(Bundle results) {
                Log.d(TAG, "onResults");
                isListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty() && listener != null) {
                    listener.onResults(matches.get(0));
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty() && listener != null) {
                    listener.onPartialResults(matches.get(0));
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void startListening() {
        if (isListening) {
            Log.d(TAG, "Already listening");
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);

        speechRecognizer.startListening(intent);
    }

    public void stopListening() {
        if (isListening) {
            speechRecognizer.stopListening();
        }
    }

    public void cancel() {
        speechRecognizer.cancel();
        isListening = false;
    }

    public boolean isListening() {
        return isListening;
    }

    public void destroy() {
        speechRecognizer.destroy();
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "音频录制错误";
            case SpeechRecognizer.ERROR_CLIENT:
                return "客户端错误";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "权限不足";
            case SpeechRecognizer.ERROR_NETWORK:
                return "网络错误";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "网络超时";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "没有匹配结果";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "识别器忙";
            case SpeechRecognizer.ERROR_SERVER:
                return "服务器错误";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "语音超时";
            default:
                return "未知错误";
        }
    }
}
