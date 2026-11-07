package com.example.soteria;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Minimal session storage backed by SharedPreferences.
 * Replace with real auth (e.g. Firebase Auth / your backend) when available.
 */
public class SessionManager {

    private static final String PREFS_NAME = "soteria_session";
    private static final String KEY_USERNAME = "username";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void login(String username) {
        prefs.edit().putString(KEY_USERNAME, username).apply();
    }

    public void logout() {
        prefs.edit().remove(KEY_USERNAME).apply();
    }

    public boolean isLoggedIn() {
        return getUsername() != null;
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }
}
