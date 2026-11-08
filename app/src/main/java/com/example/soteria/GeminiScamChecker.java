package com.example.soteria;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scam analysis with Gemini, called straight from the phone (key from .env via BuildConfig).
 * Used with offline transcription when the phone has internet; the keyword check is the fallback.
 */
final class GeminiScamChecker {

    interface Callback {
        /** Main thread. {@code result} is null if Gemini couldn't be reached or answered badly. */
        void onResult(@Nullable LocalRiskChecker.Result result);
    }

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final int TIMEOUT_MS = 12_000;
    private static final int MAX_TRANSCRIPT_CHARS = 12_000; // keep requests small on long calls

    static final String PROMPT = "You check live phone-call transcripts for scams and social engineering. "
            + "Both voices are mixed into one transcript and lines are not labelled by speaker. "
            + "Speech-to-text may contain small mistakes. "
            + "Warning signs include: gift card or crypto payment, wire transfers, deportation or arrest threats, "
            + "one-time code or password requests, remote access apps, upfront fees for jobs, LMIAs and work "
            + "permits, impersonating government/bank/police/IT/CRA/IRCC, urgency, secrecy, threats. "
            + "risk is an integer 0-100: under 30 for normal conversation, 30-69 for suspicious, 70+ for likely scam. "
            + "signs: short warning signs found (max 6, empty if none). "
            + "summary: one short sentence for the person on the call.";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private GeminiScamChecker() { }

    /** True when a Gemini key was provided at build time (.env GEMINI_API_KEY). */
    static boolean isConfigured() {
        return !BuildConfig.GEMINI_API_KEY.isEmpty();
    }

    static void check(String transcript, Callback callback) {
        IO.execute(() -> {
            LocalRiskChecker.Result result;
            try {
                result = checkBlocking(transcript);
            } catch (IOException | JSONException | RuntimeException e) {
                android.util.Log.w("SoteriaGemini", "Gemini check failed: " + e.getMessage());
                result = null;
            }
            LocalRiskChecker.Result r = result;
            MAIN.post(() -> callback.onResult(r));
        });
    }

    /** Calls Gemini on the current thread. Throws if it can't be reached or the answer is unusable. */
    @WorkerThread
    static LocalRiskChecker.Result checkBlocking(String transcript) throws IOException, JSONException {
        if (!isConfigured()) throw new IOException("GEMINI_API_KEY not set in .env");
        String text = transcript.length() > MAX_TRANSCRIPT_CHARS
                ? transcript.substring(transcript.length() - MAX_TRANSCRIPT_CHARS) : transcript;

        JSONObject body = new JSONObject()
                .put("systemInstruction", new JSONObject().put("parts",
                        new JSONArray().put(new JSONObject().put("text", PROMPT))))
                .put("contents", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject()
                                .put("text", "Transcript so far:\n" + text)))))
                .put("generationConfig", new JSONObject()
                        .put("temperature", 0)
                        .put("responseMimeType", "application/json")
                        .put("responseSchema", schema()));

        URL url = new URL(String.format(ENDPOINT, BuildConfig.GEMINI_MODEL));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            InputStream in = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String response = in == null ? "" : readAll(in);
            if (code >= 400) throw new IOException("Gemini HTTP " + code + ": " + response);
            return parse(response);
        } finally {
            conn.disconnect();
        }
    }

    static LocalRiskChecker.Result parse(String response) throws JSONException {
        JSONObject reply = new JSONObject(response);
        String json = reply.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
        JSONObject data = new JSONObject(json.substring(json.indexOf('{'), json.lastIndexOf('}') + 1));
        int score = Math.max(0, Math.min(100, data.optInt("risk", 0)));
        List<String> signs = new ArrayList<>();
        JSONArray arr = data.optJSONArray("signs");
        if (arr != null) for (int i = 0; i < arr.length() && signs.size() < 6; i++) signs.add(arr.optString(i));
        return new LocalRiskChecker.Result(score, data.optBoolean("is_scam", score >= 70), signs,
                data.optString("summary", ""));
    }

    private static JSONObject schema() throws JSONException {
        return new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject()
                        .put("is_scam", new JSONObject().put("type", "BOOLEAN"))
                        .put("risk", new JSONObject().put("type", "INTEGER"))
                        .put("signs", new JSONObject().put("type", "ARRAY")
                                .put("items", new JSONObject().put("type", "STRING")))
                        .put("summary", new JSONObject().put("type", "STRING")))
                .put("required", new JSONArray().put("is_scam").put("risk").put("signs").put("summary"));
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
