package com.example.soteria;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Real protected calls (with the AI's risk result), saved on the phone.
 * Logs and Alerts read these through {@link CallLogRepository#getCalls(Context)}.
 */
public final class CallHistoryStore {

    private static final String PREFS_NAME = "soteria_call_history";
    private static final String KEY_CALLS = "calls";
    private static final int MAX_CALLS = 200;

    private CallHistoryStore() { }

    /** Adds the call, or replaces the saved call with the same id. Newest first. */
    public static synchronized void save(Context context, CallLog call) {
        List<CallLog> calls = load(context);
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).getCallId().equals(call.getCallId())) {
                calls.remove(i);
                break;
            }
        }
        calls.add(0, call);
        while (calls.size() > MAX_CALLS) calls.remove(calls.size() - 1);

        JSONArray array = new JSONArray();
        for (CallLog c : calls) array.put(toJson(c));
        prefs(context).edit().putString(KEY_CALLS, array.toString()).apply();
    }

    public static synchronized List<CallLog> load(Context context) {
        List<CallLog> calls = new ArrayList<>();
        String raw = prefs(context).getString(KEY_CALLS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) calls.add(fromJson(array.getJSONObject(i)));
        } catch (JSONException e) {
            // Corrupt data: start fresh rather than crash.
        }
        return calls;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static JSONObject toJson(CallLog c) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", c.getCallId());
            o.put("name", c.getCallerName());
            o.put("number", c.getPhoneNumber());
            o.put("timestamp", c.getTimestamp());
            o.put("duration", c.getDuration());
            o.put("transcript", c.getTranscript());
            o.put("score", c.getRiskScore());
            o.put("risk", c.getRisk().name());
            o.put("threats", new JSONArray(c.getThreatCategories()));
            o.put("internal", c.isInternalCaller());
            o.put("direction", c.getDirection().name());
        } catch (JSONException ignored) {
        }
        return o;
    }

    private static CallLog fromJson(JSONObject o) {
        List<String> threats = new ArrayList<>();
        JSONArray a = o.optJSONArray("threats");
        if (a != null) for (int i = 0; i < a.length(); i++) threats.add(a.optString(i));
        CallLog.Risk risk;
        try {
            risk = CallLog.Risk.valueOf(o.optString("risk", "SAFE"));
        } catch (IllegalArgumentException e) {
            risk = CallLog.Risk.SAFE;
        }
        CallLog.Direction direction = "OUTGOING".equals(o.optString("direction"))
                ? CallLog.Direction.OUTGOING : CallLog.Direction.INCOMING;
        return new CallLog(o.optString("id"), o.optString("name"), o.optString("number"),
                o.optString("timestamp"), o.optString("duration"), o.optString("transcript"),
                o.optDouble("score", 0), risk, threats, o.optBoolean("internal", false), direction);
    }
}
