package com.example.soteria;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal client for the Soteria listener backend (see /backend). Callbacks run on the main thread. */
public final class SoteriaApi {

    public interface Callback<T> {
        void onResult(T result, Exception error);
    }

    /** Snapshot of a listener session. */
    public static final class Session {
        public final String status;      // waiting | listening | ended
        public final List<String> lines; // new final transcript lines since `after`
        public final int next;           // pass as `after` on the next poll
        public final String partial;     // in-progress text (may be empty)
        public final int riskScore;      // 0-100 from the AI
        public final boolean isScam;
        public final List<String> signs; // warning signs the AI found
        public final String summary;

        Session(String status, List<String> lines, int next, String partial,
                int riskScore, boolean isScam, List<String> signs, String summary) {
            this.status = status;
            this.lines = lines;
            this.next = next;
            this.partial = partial;
            this.riskScore = riskScore;
            this.isScam = isScam;
            this.signs = signs;
            this.summary = summary;
        }
    }

    private static final int TIMEOUT_MS = 6000;
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static android.content.Context appContext;

    private SoteriaApi() { }

    static void init(android.content.Context context) {
        appContext = context.getApplicationContext();
    }

    public static void createSession(Callback<String> callback) {
        IO.execute(() -> {
            try {
                JSONObject json = request("POST", "/sessions");
                deliver(callback, json.getString("code"), null);
            } catch (Exception e) {
                deliver(callback, null, e);
            }
        });
    }

    public static void getSession(String code, int after, Callback<Session> callback) {
        IO.execute(() -> {
            try {
                JSONObject json = request("GET", "/sessions/" + code + "?after=" + after);
                JSONArray array = json.optJSONArray("lines");
                List<String> lines = new ArrayList<>();
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) lines.add(array.getString(i));
                }
                JSONObject risk = json.optJSONObject("risk");
                List<String> signs = new ArrayList<>();
                JSONArray signArray = risk == null ? null : risk.optJSONArray("signs");
                if (signArray != null) {
                    for (int i = 0; i < signArray.length(); i++) signs.add(signArray.getString(i));
                }
                deliver(callback, new Session(json.optString("status"), lines,
                        json.optInt("next", after), json.optString("partial"),
                        risk == null ? 0 : risk.optInt("score", 0),
                        risk != null && risk.optBoolean("is_scam", false),
                        signs, risk == null ? "" : risk.optString("summary")), null);
            } catch (Exception e) {
                deliver(callback, null, e);
            }
        });
    }

    private static JSONObject request(String method, String path) throws IOException, org.json.JSONException {
        String base = appContext == null ? BuildConfig.BACKEND_URL.replaceAll("/+$", "")
                : ProtectionSettings.backendUrl(appContext);
        URL url = new URL(base + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("ngrok-skip-browser-warning", "1");
            if (!BuildConfig.API_KEY.isEmpty()) conn.setRequestProperty("X-Soteria-Key", BuildConfig.API_KEY);
            if ("POST".equals(method)) {
                conn.setDoOutput(true);
                conn.getOutputStream().close(); // empty body
            }
            int code = conn.getResponseCode();
            InputStream in = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = in == null ? "" : readAll(in);
            if (code >= 400) throw new IOException("HTTP " + code + " " + body);
            return new JSONObject(body);
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static <T> void deliver(Callback<T> callback, T result, Exception error) {
        MAIN.post(() -> callback.onResult(result, error));
    }
}
