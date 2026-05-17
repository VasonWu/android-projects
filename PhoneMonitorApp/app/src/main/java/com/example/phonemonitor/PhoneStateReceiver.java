package com.example.phonemonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

public class PhoneStateReceiver extends BroadcastReceiver {
    private static final String TAG = "PhoneStateReceiver";

    private static NtfyClient ntfyClient;
    private static String lastPhoneNumber;
    private static long callStartTime;
    private static int lastState = TelephonyManager.CALL_STATE_IDLE;

    public static void setNtfyClient(NtfyClient client) {
        ntfyClient = client;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive: action=" + intent.getAction());
        if (intent != null && TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            Log.i(TAG, "onReceive: state=" + state + ", phoneNumber=" + phoneNumber);
            Log.i(TAG, "onReceive: ntfyClient=" + (ntfyClient != null ? "initialized" : "null"));

            handleStateChange(state, phoneNumber);
        }
    }

    private void handleStateChange(String state, String phoneNumber) {
        if (state == null) {
            return;
        }

        if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            if (lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                handleCallEnded();
            }
            lastState = TelephonyManager.CALL_STATE_IDLE;
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                handleCallAnswered(phoneNumber);
            }
            lastState = TelephonyManager.CALL_STATE_OFFHOOK;
        } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            handleIncomingCall(phoneNumber);
            lastState = TelephonyManager.CALL_STATE_RINGING;
        }
    }

    private void handleIncomingCall(String phoneNumber) {
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            lastPhoneNumber = phoneNumber;
            callStartTime = System.currentTimeMillis();
            Log.i(TAG, "Incoming call from: " + phoneNumber);

            if (ntfyClient != null) {
                ntfyClient.sendIncomingCall(phoneNumber);
            } else {
                Log.w(TAG, "NtfyClient not initialized");
            }
        }
    }

    private void handleCallAnswered(String phoneNumber) {
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            lastPhoneNumber = phoneNumber;
        }
        callStartTime = System.currentTimeMillis();
        Log.i(TAG, "Call answered: " + lastPhoneNumber);
    }

    private void handleCallEnded() {
        long callDuration = System.currentTimeMillis() - callStartTime;
        Log.i(TAG, "Call ended: " + lastPhoneNumber + ", duration: " + callDuration + "ms");

        if (ntfyClient != null && lastPhoneNumber != null) {
            if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                ntfyClient.sendMissedCall(lastPhoneNumber);
            } else {
                ntfyClient.sendCallEnded(lastPhoneNumber, callDuration);
            }
        } else if (ntfyClient == null) {
            Log.w(TAG, "NtfyClient not initialized");
        }
    }
}
