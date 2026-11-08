package com.example.soteria;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;

/** Helpers for the TrustLine (Twilio) number. */
public final class TrustLine {

    private TrustLine() {
    }

    /** Number from Settings, e.g. "+16043736537". */
    static String number(Context context) {
        return SettingsKeys.trustLineNumber(context);
    }

    static String numberOf(Call call) {
        if (call == null || call.getDetails() == null) return "";
        Uri handle = call.getDetails().getHandle();
        return handle == null ? "" : handle.getSchemeSpecificPart();
    }

    /** True if this call is to/from the TrustLine number (compares the last 10 digits). */
    static boolean isTrustLine(Context context, Call call) {
        return sameNumber(numberOf(call), number(context));
    }

    static boolean sameNumber(String a, String b) {
        String da = digits(a), db = digits(b);
        if (da.isEmpty() || db.isEmpty()) return false;
        int n = Math.min(10, Math.min(da.length(), db.length()));
        return da.substring(da.length() - n).equals(db.substring(db.length() - n));
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    /** Never add TrustLine to an emergency call. */
    @SuppressWarnings("deprecation")
    static boolean isEmergency(Context context, String number) {
        String d = digits(number);
        if (d.equals("911") || d.equals("112") || d.equals("999")) return true;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                TelephonyManager tm = context.getSystemService(TelephonyManager.class);
                return tm != null && tm.isEmergencyNumber(number);
            }
            return PhoneNumberUtils.isEmergencyNumber(number);
        } catch (Exception e) {
            return false;
        }
    }
}
