package com.example.soteria;

import android.app.role.RoleManager;
import android.content.Context;
import android.os.Build;
import android.telecom.TelecomManager;

/** Whether call protection is switched on, and whether the device is set up for it. */
public final class ProtectionSettings {

    private static final String PREFS_NAME = "soteria_prefs";
    private static final String KEY_ACTIVE = "protection_active";

    private ProtectionSettings() { }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ACTIVE, enabled).apply();
    }

    /** Protection only works while Soteria is the default phone app. */
    public static boolean isActive(Context context) {
        return isEnabled(context) && isDefaultDialer(context) && SettingsKeys.monitorCalls(context);
    }

    /** True when the TrustLine number and backend URL are set (Settings or local.properties). */
    public static boolean isConfigured(Context context) {
        return !listenerNumber(context).isEmpty() && !backendUrl(context).isEmpty();
    }

    /** TrustLine (Twilio) number: Settings value, else local.properties. */
    public static String listenerNumber(Context context) {
        String v = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getString(SettingsKeys.TRUSTLINE_NUMBER, "").trim();
        return v.isEmpty() ? BuildConfig.LISTENER_NUMBER : v;
    }

    /** Backend (ngrok) URL: Settings value, else local.properties. No trailing slash. */
    public static String backendUrl(Context context) {
        String v = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getString(SettingsKeys.BACKEND_URL, "").trim();
        return (v.isEmpty() ? BuildConfig.BACKEND_URL : v).replaceAll("/+$", "");
    }

    public static boolean isDefaultDialer(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roles = context.getSystemService(RoleManager.class);
            return roles != null && roles.isRoleHeld(RoleManager.ROLE_DIALER);
        }
        TelecomManager telecom = context.getSystemService(TelecomManager.class);
        return telecom != null && context.getPackageName().equals(telecom.getDefaultDialerPackage());
    }
}
