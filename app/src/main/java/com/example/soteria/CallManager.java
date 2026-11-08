package com.example.soteria;

import android.telecom.Call;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared call state between {@link SoteriaInCallService} (owns the calls)
 * and {@link InCallActivity} (shows them).
 */
public final class CallManager {

    public interface Listener {
        void onCallsChanged();
    }

    private static final List<Call> calls = new ArrayList<>();
    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /** The real call (the caller), not the TrustLine leg. */
    static Call mainCall;
    /** The auto-dialed TrustLine leg. */
    static Call trustCall;
    /** Status text for the TrustLine chip on the call screen. */
    static String trustStatus = "";
    /** True when the user has to tap Merge themselves (carrier refused auto-merge). */
    static boolean needsManualMerge = false;

    /** Latest risk result for the current call (filled in by backend polling). */
    static Risk risk = Risk.IDLE;
    static String riskText = "";

    static SoteriaInCallService service;

    private CallManager() {
    }

    static synchronized void add(Call call) {
        if (!calls.contains(call)) calls.add(call);
        notifyChanged();
    }

    static synchronized void remove(Call call) {
        calls.remove(call);
        notifyChanged();
    }

    static synchronized List<Call> calls() {
        return new ArrayList<>(calls);
    }

    static void setTrustStatus(String status, boolean manualMerge) {
        trustStatus = status;
        needsManualMerge = manualMerge;
        notifyChanged();
    }

    static void setRisk(Risk level, String text) {
        risk = level;
        riskText = text;
        notifyChanged();
    }

    static void reset() {
        mainCall = null;
        trustCall = null;
        trustStatus = "";
        needsManualMerge = false;
        risk = Risk.IDLE;
        riskText = "";
    }

    static void addListener(Listener l) {
        listeners.add(l);
    }

    static void removeListener(Listener l) {
        listeners.remove(l);
    }

    static void notifyChanged() {
        for (Listener l : listeners) l.onCallsChanged();
    }
}
