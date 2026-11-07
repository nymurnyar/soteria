package com.example.soteria;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Keys and typed getters for values stored by {@link SettingsFragment}.
 * Keys must match app:key in res/xml/root_preferences.xml.
 */
public final class SettingsKeys {

    // Monitoring
    public static final String MONITOR_CALLS = "monitor_calls";
    public static final String MONITOR_SMS = "monitor_sms";
    public static final String MONITOR_EMAIL = "monitor_email";
    public static final String MONITOR_WHATSAPP = "monitor_whatsapp";
    public static final String AUTO_START_UNKNOWN = "auto_start_unknown";

    // Session
    public static final String DEFAULT_SESSION_LENGTH = "default_session_length";
    public static final String SHOW_OVERLAY = "show_overlay";

    // Alerts
    public static final String SENSITIVITY = "sensitivity";
    public static final String VIBRATE_ON_ALERT = "vibrate_on_alert";
    public static final String SOUND_ON_ALERT = "sound_on_alert";

    // Trusted contacts (each entry is stored as "Name|PhoneNumber")
    public static final String NOTIFY_CONTACTS = "notify_contacts";
    public static final String TRUSTED_CONTACTS = "trusted_contacts";

    // Privacy
    public static final String AUTO_DELETE = "auto_delete";
    public static final String AI_MODE = "ai_mode";

    private static final int DEFAULT_SESSION_MINUTES = 30;

    private SettingsKeys() {
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    // ---------- Monitoring ----------

    public static boolean monitorCalls(Context context) {
        return prefs(context).getBoolean(MONITOR_CALLS, true);
    }

    public static boolean monitorSms(Context context) {
        return prefs(context).getBoolean(MONITOR_SMS, true);
    }

    public static boolean monitorEmail(Context context) {
        return prefs(context).getBoolean(MONITOR_EMAIL, false);
    }

    public static boolean monitorWhatsapp(Context context) {
        return prefs(context).getBoolean(MONITOR_WHATSAPP, false);
    }

    public static boolean autoStartUnknown(Context context) {
        return prefs(context).getBoolean(AUTO_START_UNKNOWN, true);
    }

    // ---------- Session ----------

    /** Default session length in minutes (ListPreference stores it as a String). */
    public static int defaultSessionLengthMinutes(Context context) {
        String value = prefs(context).getString(DEFAULT_SESSION_LENGTH, null);
        if (value == null) {
            return DEFAULT_SESSION_MINUTES;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return DEFAULT_SESSION_MINUTES;
        }
    }

    public static boolean showOverlay(Context context) {
        return prefs(context).getBoolean(SHOW_OVERLAY, true);
    }

    // ---------- Alerts ----------

    /** "low", "medium" or "high". */
    public static String sensitivity(Context context) {
        return prefs(context).getString(SENSITIVITY, "medium");
    }

    public static boolean vibrateOnAlert(Context context) {
        return prefs(context).getBoolean(VIBRATE_ON_ALERT, true);
    }

    public static boolean soundOnAlert(Context context) {
        return prefs(context).getBoolean(SOUND_ON_ALERT, false);
    }

    // ---------- Trusted contacts ----------

    public static boolean notifyContacts(Context context) {
        return prefs(context).getBoolean(NOTIFY_CONTACTS, true);
    }

    /** Copy of the saved contacts, each "Name|PhoneNumber". Safe to modify. */
    public static Set<String> trustedContacts(Context context) {
        return new HashSet<>(prefs(context).getStringSet(TRUSTED_CONTACTS, new HashSet<>()));
    }

    public static void setTrustedContacts(Context context, Set<String> contacts) {
        prefs(context).edit().putStringSet(TRUSTED_CONTACTS, new HashSet<>(contacts)).apply();
    }

    // ---------- Privacy ----------

    /** Days to keep transcripts, or -1 for never delete. */
    public static int autoDeleteDays(Context context) {
        String value = prefs(context).getString(AUTO_DELETE, "1");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1; // "never"
        }
    }

    /** "on_device" or "cloud". */
    public static String aiMode(Context context) {
        return prefs(context).getString(AI_MODE, "on_device");
    }
}
