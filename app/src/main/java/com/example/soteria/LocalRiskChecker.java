package com.example.soteria;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Offline scam check used with on-phone transcription (no server, no AI).
 * Same warning signs and scoring as the backend's keyword fallback (backend/main.py).
 */
final class LocalRiskChecker {

    static final class Result {
        final int score;
        final boolean isScam;
        final List<String> signs;
        final String summary;

        Result(int score, boolean isScam, List<String> signs, String summary) {
            this.score = score;
            this.isScam = isScam;
            this.signs = signs;
            this.summary = summary;
        }
    }

    private static final Map<String, Pattern> SIGNS = new LinkedHashMap<>();

    static {
        add("Gift card or crypto payment", "gift ?cards?|itunes|google play card|bitcoin|crypto|btc|usdt");
        add("Wire / money transfer", "wire (?:the )?money|western union|moneygram|e-?transfer|send (?:the )?money");
        add("Arrest or deportation threat", "arrest|warrant|deport|police will|jail|immigration (?:status|officer)");
        add("One-time code request", "(?:verification|one[- ]time|security|6[- ]digit) code|read (?:me )?the code|otp");
        add("Password / login request", "password|pin number|login details|sign[- ]in details");
        add("Remote access request", "anydesk|teamviewer|remote access|install (?:this|an) app|screen ?share");
        add("Upfront fee for job / LMIA / work permit", "lmia|work permit|processing fee|registration fee|pay (?:a |the )?fee");
        add("Government / bank impersonation", "\\bcra\\b|revenue agency|ircc|service canada|social insurance|\\bsin\\b|your bank|fraud department");
        add("Urgency or secrecy", "right now|immediately|urgent|don'?t tell|keep this (?:between|secret)|within (?:the next )?\\d+ (?:minutes|hours)");
    }

    private static void add(String name, String regex) {
        SIGNS.put(name, Pattern.compile(regex));
    }

    private LocalRiskChecker() { }

    static Result check(String transcript) {
        String text = transcript.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, Pattern> e : SIGNS.entrySet()) {
            if (e.getValue().matcher(text).find()) found.add(e.getKey());
        }
        int score = Math.min(95, 35 * found.size());
        StringBuilder joined = new StringBuilder();
        for (String sign : found) joined.append(joined.length() > 0 ? ", " : "").append(sign);
        String summary = found.isEmpty() ? "No warning signs yet."
                : "Possible scam: " + joined.toString().toLowerCase(Locale.ROOT);
        return new Result(score, score >= 70, found, summary);
    }
}
