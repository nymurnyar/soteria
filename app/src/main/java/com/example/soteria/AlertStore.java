package com.example.soteria;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks what the user has done with each alert (a call that isn't SAFE).
 * Stored locally for now; swap for the backend once alerts are synced.
 */
public final class AlertStore {

    public enum Status { OPEN, REVIEWED, FALSE_ALARM }

    private static final String PREFS_NAME = "soteria_alerts";

    private final SharedPreferences prefs;

    public AlertStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Status getStatus(CallLog call) {
        String value = prefs.getString(call.getCallId(), null);
        if (value == null) return Status.OPEN;
        try {
            return Status.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Status.OPEN;
        }
    }

    public void setStatus(CallLog call, Status status) {
        if (status == Status.OPEN) {
            prefs.edit().remove(call.getCallId()).apply();
        } else {
            prefs.edit().putString(call.getCallId(), status.name()).apply();
        }
    }

    public boolean isOpen(CallLog call) {
        return getStatus(call) == Status.OPEN;
    }

    /** A call becomes an alert when Soteria didn't classify it as safe. */
    public static boolean isAlert(CallLog call) {
        return call.getRisk() != CallLog.Risk.SAFE;
    }

    /** Alerts from {@code calls} (newest first) filtered by open/resolved. */
    public List<CallLog> alerts(List<CallLog> calls, boolean open) {
        List<CallLog> result = new ArrayList<>();
        for (CallLog call : calls) {
            if (isAlert(call) && isOpen(call) == open) result.add(call);
        }
        return result;
    }
}
