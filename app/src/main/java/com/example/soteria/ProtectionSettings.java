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
        return isEnabled(context) && isDefaultDialer(context);
    }

    /** True when the listener number and backend URL are set in local.properties. */
    public static boolean isConfigured() {
        return !BuildConfig.LISTENER_NUMBER.isEmpty() && !BuildConfig.BACKEND_URL.isEmpty();
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
