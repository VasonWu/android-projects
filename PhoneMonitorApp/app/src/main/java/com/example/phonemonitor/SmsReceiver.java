package com.example.phonemonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    private static NtfyClient ntfyClient;

    public static void setNtfyClient(NtfyClient client) {
        ntfyClient = client;
    }

    private static NtfyClient getNtfyClient(Context context) {
        if (ntfyClient == null) {
            ntfyClient = new NtfyClient(context.getApplicationContext());
            Log.i(TAG, "NtfyClient initialized in SmsReceiver");
        }
        return ntfyClient;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive: " + intent.getAction());
        if (intent != null && SMS_RECEIVED.equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus != null) {
                    String format = bundle.getString("format");
                    SmsMessage[] messages = new SmsMessage[pdus.length];
                    for (int i = 0; i < pdus.length; i++) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                        } else {
                            messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                        }
                    }

                    if (messages.length > 0) {
                        String sender = messages[0].getOriginatingAddress();
                        StringBuilder content = new StringBuilder();
                        for (SmsMessage message : messages) {
                            content.append(message.getMessageBody());
                        }

                        Log.i(TAG, "Received SMS from: " + sender);
                        NtfyClient client = getNtfyClient(context);
                        final String finalSender = sender != null ? sender : "未知号码";
                        final String finalContent = content.toString();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.sendSms(finalSender, finalContent);
                            }
                        }).start();
                    }
                }
            }
        }
    }
}
