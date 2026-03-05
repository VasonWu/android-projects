package com.pipixia.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public class ClientIdManager {
    private static final String PREFS_NAME = "PipixiaPrefs";
    private static final String KEY_CLIENT_ID = "client_id";

    private final SharedPreferences prefs;
    private String clientId;

    public ClientIdManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        clientId = prefs.getString(KEY_CLIENT_ID, null);
        if (clientId == null) {
            clientId = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_CLIENT_ID, clientId).apply();
        }
    }

    public String getClientId() {
        return clientId;
    }
}
