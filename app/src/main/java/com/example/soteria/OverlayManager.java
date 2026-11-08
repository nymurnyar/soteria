package com.example.soteria;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

/**
 * Draws the floating bubble + control panel over other apps.
 * Implements CallSessionTimer.Listener so the timer updates it directly.
 */
public class OverlayManager implements CallSessionTimer.Listener {

    /** What the panel buttons do. The service wires these to CallSessionTimer. */
    public interface Actions {
        void onStart(int minutes);
        void onExtend(int minutes);
        void onEnd();
    }

    private enum Mode { PROMPT, ACTIVE }

    private final Context context;
    private final Actions actions;
    private final WindowManager wm;
    private final LayoutInflater inflater;
    private final WindowManager.LayoutParams bubbleParams;
    private final WindowManager.LayoutParams panelParams;

    private View bubble;
    private View panel;
    private boolean sessionActive = false;

    // Drag state
    private int dragStartX, dragStartY;
    private float touchStartX, touchStartY;
    private boolean moved;

    public OverlayManager(Context context, Actions actions) {
        this.context = context;
        this.actions = actions;
        this.wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.inflater = LayoutInflater.from(context);

        bubbleParams = overlayParams();
        bubbleParams.x = dp(16);
        bubbleParams.y = dp(200);
        panelParams = overlayParams();
    }

    // ===== Public API (service / later the LLM pipeline) =====

    /** Call started: show bubble + ask how long to monitor. */
    public void showPrompt() {
        ensureBubble();
        showPanel(Mode.PROMPT, "Protect this call?", "Choose how long Soteria should watch.");
    }

    /** LLM result: recolour the bubble and pop the panel with the reason. */
    public void showWarning(Risk risk, String reason) {
        ensureBubble();
        setBubbleColor(risk);
        if (risk == Risk.DANGER || risk == Risk.CAUTION) {
            String title = (risk == Risk.DANGER) ? "⚠ Likely scam" : "Be careful";
            showPanel(Mode.ACTIVE, title, reason);
        }
    }

    /** Remove everything from the screen. */
    public void hide() {
        hidePanel();
        if (bubble != null) {
            safeRemove(bubble);
            bubble = null;
        }
    }

    // ===== CallSessionTimer.Listener =====

    @Override
    public void onSessionStarted(long totalMs) {
        sessionActive = true;
        hidePanel();
        setBubbleColor(Risk.SAFE);
        setBubbleText(formatTime(totalMs));
    }

    @Override
    public void onTick(long remainingMs) {
        setBubbleText(formatTime(remainingMs));
    }

    @Override
    public void onAboutToExpire(long remainingMs) {
        showPanel(Mode.ACTIVE, "Still on the call?",
                "Monitoring ends in " + formatTime(remainingMs) + ". Extend?");
    }

    @Override
    public void onSessionEnded(boolean timedOut) {
        sessionActive = false;
        setBubbleColor(Risk.IDLE);
        setBubbleText("🛡");
        if (timedOut) {
            showPanel(Mode.PROMPT, "Session ended", "Still talking? Start another session.");
        } else {
            hidePanel();
        }
    }

    // ===== Bubble =====

    private void ensureBubble() {
        if (bubble != null) return;
        View view = inflater.inflate(R.layout.overlay_bubble, null);
        makeDraggable(view);
        try {
            wm.addView(view, bubbleParams);
        } catch (RuntimeException e) {
            // "Display over other apps" was revoked mid-call: skip the bubble instead of crashing.
            return;
        }
        bubble = view;
        setBubbleColor(Risk.IDLE);
    }

    /** Drag to move; a tap (no real movement) toggles the panel. */
    @SuppressLint("ClickableViewAccessibility")
    private void makeDraggable(View view) {
        view.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartX = bubbleParams.x;
                    dragStartY = bubbleParams.y;
                    touchStartX = e.getRawX();
                    touchStartY = e.getRawY();
                    moved = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (e.getRawX() - touchStartX);
                    int dy = (int) (e.getRawY() - touchStartY);
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                    bubbleParams.x = dragStartX + dx;
                    bubbleParams.y = dragStartY + dy;
                    wm.updateViewLayout(v, bubbleParams);
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!moved) {
                        v.performClick();
                        togglePanel();
                    }
                    return true;

                default:
                    return false;
            }
        });
    }

    private void togglePanel() {
        if (panel != null) {
            hidePanel();
        } else if (sessionActive) {
            showPanel(Mode.ACTIVE, "Soteria is watching", "Extend or end this session.");
        } else {
            showPanel(Mode.PROMPT, "Protect this call?", "Choose how long Soteria should watch.");
        }
    }

    private void setBubbleText(String text) {
        if (bubble == null) return;
        ((TextView) bubble.findViewById(R.id.bubbleText)).setText(text);
    }

    private void setBubbleColor(Risk risk) {
        if (bubble == null) return;
        Drawable bg = bubble.getBackground().mutate();
        if (bg instanceof GradientDrawable) {
            ((GradientDrawable) bg).setColor(risk.color);
        }
    }

    // ===== Panel =====

    private void showPanel(Mode mode, String title, String message) {
        if (panel == null) {
            View view = inflater.inflate(R.layout.overlay_panel, null);
            wireButtons(view);
            // Open the panel just below the bubble
            panelParams.x = bubbleParams.x;
            panelParams.y = bubbleParams.y + dp(72);
            try {
                wm.addView(view, panelParams);
            } catch (RuntimeException e) {
                return; // overlay permission revoked
            }
            panel = view;
        }
        ((TextView) panel.findViewById(R.id.panelTitle)).setText(title);
        ((TextView) panel.findViewById(R.id.panelMessage)).setText(message);
        panel.findViewById(R.id.promptRow).setVisibility(mode == Mode.PROMPT ? View.VISIBLE : View.GONE);
        panel.findViewById(R.id.activeRow).setVisibility(mode == Mode.ACTIVE ? View.VISIBLE : View.GONE);
    }

    private void wireButtons(View v) {
        ((Button) v.findViewById(R.id.btn15)).setOnClickListener(b -> actions.onStart(15));
        ((Button) v.findViewById(R.id.btn30)).setOnClickListener(b -> actions.onStart(30));
        ((Button) v.findViewById(R.id.btn60)).setOnClickListener(b -> actions.onStart(60));
        ((Button) v.findViewById(R.id.btnSkip)).setOnClickListener(b -> hide());
        ((Button) v.findViewById(R.id.btnExtend)).setOnClickListener(b -> {
            actions.onExtend(15);
            hidePanel();
        });
        ((Button) v.findViewById(R.id.btnEnd)).setOnClickListener(b -> actions.onEnd());
    }

    private void hidePanel() {
        if (panel != null) {
            safeRemove(panel);
            panel = null;
        }
    }

    private void safeRemove(View view) {
        try {
            wm.removeView(view);
        } catch (RuntimeException ignored) {
            // Already detached.
        }
    }

    // ===== Helpers =====

    /** Window settings shared by bubble + panel. */
    @SuppressWarnings("deprecation")
    private WindowManager.LayoutParams overlayParams() {
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY   // draws over other apps
                : WindowManager.LayoutParams.TYPE_PHONE;                // Android 7 fallback
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,       // taps outside reach the call screen
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        return p;
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    private String formatTime(long ms) {
        long totalSec = (ms + 999) / 1000;
        return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60);
    }
}
