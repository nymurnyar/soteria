package com.example.soteria;

import android.os.CountDownTimer;
import android.os.SystemClock;

/**
 * ScreenZen-style session logic for a call: start / extend / end + countdown.
 * No UI code here. OverlayManager listens to it and updates the bubble.
 */
public class CallSessionTimer {

    /** Anything that reacts to the session (the overlay, later the LLM pipeline). */
    public interface Listener {
        void onSessionStarted(long totalMs);
        void onTick(long remainingMs);
        void onAboutToExpire(long remainingMs);
        void onSessionEnded(boolean timedOut);
    }

    private static final long MINUTE_MS = 60_000L;
    private static final long WARN_BEFORE_MS = 2 * MINUTE_MS;

    private final Listener listener;
    private CountDownTimer timer;
    private long endAtMs;      // when the session should end (elapsedRealtime clock)
    private boolean warned;    // so "about to expire" fires only once

    public CallSessionTimer(Listener listener) {
        this.listener = listener;
    }

    public boolean isActive() {
        return timer != null;
    }

    public void start(int minutes) {
        cancelTimer();
        endAtMs = SystemClock.elapsedRealtime() + minutes * MINUTE_MS;
        warned = false;
        runTimer();
        listener.onSessionStarted(minutes * MINUTE_MS);
    }

    public void extend(int minutes) {
        if (!isActive()) {
            start(minutes);
            return;
        }
        endAtMs += minutes * MINUTE_MS;
        warned = false;
        runTimer();   // restart countdown with the new end time
    }

    public void end() {
        if (!isActive()) return;
        cancelTimer();
        listener.onSessionEnded(false);
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void runTimer() {
        cancelTimer();
        long remaining = endAtMs - SystemClock.elapsedRealtime();
        timer = new CountDownTimer(remaining, 1_000) {
            @Override
            public void onTick(long ms) {
                listener.onTick(ms);
                if (!warned && ms <= WARN_BEFORE_MS) {
                    warned = true;
                    listener.onAboutToExpire(ms);
                }
            }

            @Override
            public void onFinish() {
                timer = null;
                listener.onSessionEnded(true);
            }
        }.start();
    }
}
