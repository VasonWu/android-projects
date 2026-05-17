package com.example.phonemonitor;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesHelper {
    private static final String PREFS_NAME = "PhoneMonitorPrefs";
    private static final String KEY_NTFY_SERVER = "ntfyServer";
    private static final String KEY_NTFY_TOPIC = "ntfyTopic";
    private static final String KEY_RETRY_INTERVAL = "retryInterval";

    private static final String DEFAULT_NTFY_SERVER = "http://192.168.1.13:18081";
    private static final String DEFAULT_NTFY_TOPIC = "phone-monitor";
    private static final int DEFAULT_RETRY_INTERVAL = 60; // seconds

    private SharedPreferences prefs;

    public PreferencesHelper(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getNtfyServer() {
        return prefs.getString(KEY_NTFY_SERVER, DEFAULT_NTFY_SERVER);
    }

    public void setNtfyServer(String server) {
        prefs.edit().putString(KEY_NTFY_SERVER, server).apply();
    }

    public String getNtfyTopic() {
        return prefs.getString(KEY_NTFY_TOPIC, DEFAULT_NTFY_TOPIC);
    }

    public void setNtfyTopic(String topic) {
        prefs.edit().putString(KEY_NTFY_TOPIC, topic).apply();
    }

    public int getRetryInterval() {
        return prefs.getInt(KEY_RETRY_INTERVAL, DEFAULT_RETRY_INTERVAL);
    }

    public void setRetryInterval(int interval) {
        prefs.edit().putInt(KEY_RETRY_INTERVAL, interval).apply();
    }
}
