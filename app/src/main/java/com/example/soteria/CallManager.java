package com.example.soteria;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.os.HandlerCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Central call state for Soteria's phone app, plus the "protection" flow:
 *
 * <ol>
 *   <li>A normal call becomes ACTIVE and protection is on.</li>
 *   <li>We ask the backend for a one-time session code.</li>
 *   <li>We dial the Soteria Twilio listener on the same SIM (Android holds the original call).</li>
 *   <li>When the listener answers, we send the code as DTMF (the other caller can't hear it).</li>
 *   <li>Once the backend confirms the code ("listening"), we merge both calls into a conference.</li>
 *   <li>We poll the backend for the live transcript until the call ends.</li>
 * </ol>
 *
 * If anything fails we drop the listener and take the original call off hold.
 * All methods must be called on the main thread.
 */
public final class CallManager {

    public enum Protection { OFF, CONNECTING, PROTECTED, FAILED }

    public interface Listener {
        void onCallStateChanged();
    }

    private static final String TAG = "SoteriaCalls";
    private static final long PROTECTION_TIMEOUT_MS = 40_000;
    private static final long DTMF_START_DELAY_MS = 1_200;
    private static final long DTMF_TONE_MS = 220;
    private static final long DTMF_GAP_MS = 180;
    private static final long TRIAL_PROMPT_SKIP_MS = 2_500;
    private static final long LISTENING_POLL_MS = 500;
    private static final long TRANSCRIPT_POLL_MS = 1_500;
    private static final int MAX_MERGE_ATTEMPTS = 6;
    /** Token for all protection-related callbacks so they can be cancelled together. */
    private static final Object PROTECTION_TOKEN = new Object();

    private static CallManager instance;

    public static synchronized CallManager get(Context context) {
        if (instance == null) instance = new CallManager(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Call> calls = new ArrayList<>(); // everything except the listener leg
    private final List<Listener> listeners = new ArrayList<>();
    @Nullable private InCallService service;

    // Protection state for the current call.
    private Protection protection = Protection.OFF;
    private String protectionDetail = "";
    @Nullable private Call protectedCall;
    @Nullable private Call listenerCall;
    @Nullable private String sessionCode;
    private boolean codeSent;
    private final StringBuilder transcript = new StringBuilder();
    private String partialTranscript = "";
    private int transcriptNext = 0;

    // AI risk result for the current call (from the backend).
    private int riskScore = 0;
    private boolean riskIsScam = false;
    private List<String> riskSigns = new ArrayList<>();
    private String riskSummary = "";
    private boolean warnedUser = false;
    // Details of the protected call, kept so we can save it to Logs/Alerts when it ends.
    private long protectedStartMs = 0;
    private String protectedName = "";
    private String protectedNumber = "";
    private boolean protectedOutgoing = false;

    private CallManager(Context app) {
        this.app = app;
        SoteriaApi.init(app);
    }

    // ---- Wiring from SoteriaInCallService -------------------------------------------------

    void attach(InCallService service) {
        this.service = service;
    }

    void detach(InCallService service) {
        if (this.service == service) this.service = null;
    }

    void onCallAdded(Call call) {
        call.registerCallback(callback);
        if (isListenerLeg(call)) {
            Log.i(TAG, "Listener leg added");
            listenerCall = call;
            handleListenerState(call.getState());
            return;
        }
        calls.add(call);
        Log.i(TAG, "Call added, state=" + call.getState());
        if (call.getState() == Call.STATE_RINGING) {
            CallNotifications.showIncoming(app, displayName(call));
        }
        launchInCallUi();
        handleUserCallState(call, call.getState());
        notifyChanged();
    }

    void onCallRemoved(Call call) {
        call.unregisterCallback(callback);
        if (call == listenerCall) {
            listenerCall = null;
            if (protection == Protection.CONNECTING) {
                fail(app.getString(R.string.protection_failed_listener));
            } else if (protection == Protection.PROTECTED) {
                stopProtectionCallbacks();
                protection = Protection.FAILED;
                protectionDetail = app.getString(R.string.protection_listener_left);
            }
        }
        calls.remove(call);
        if (call == protectedCall) endProtection();
        if (getPrimaryCall() == null) {
            endProtection();
            CallNotifications.cancel(app);
        } else {
            updateOngoingNotification();
        }
        notifyChanged();
    }

    // ---- Public API for the UI -------------------------------------------------------------

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** The call to show: a ringing call first, then a conference, then any live call. */
    @Nullable
    public Call getPrimaryCall() {
        Call best = null;
        for (Call call : calls) {
            int state = call.getState();
            if (state == Call.STATE_DISCONNECTED) continue;
            if (state == Call.STATE_RINGING) return call;
            if (!call.getChildren().isEmpty()) return call; // merged conference
            if (best == null) best = call;
        }
        return best;
    }

    /** Name/number to display for the primary call (the real caller, never the listener). */
    public String getPrimaryDisplayName() {
        Call primary = getPrimaryCall();
        if (primary == null) return "";
        if (!primary.getChildren().isEmpty() && protectedCall != null) return displayName(protectedCall);
        return displayName(primary);
    }

    public String getPrimaryNumber() {
        Call primary = getPrimaryCall();
        if (primary == null) return "";
        Call source = !primary.getChildren().isEmpty() && protectedCall != null ? protectedCall : primary;
        String number = numberOf(source);
        return number == null ? "" : number;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public List<String> getRiskSigns() {
        return new ArrayList<>(riskSigns);
    }

    public String getRiskSummary() {
        return riskSummary;
    }

    /** True when the AI's risk is at/above the user's sensitivity threshold. */
    public boolean isRisky() {
        return riskScore >= SettingsKeys.warningThreshold(app);
    }

    public Protection getProtection() {
        return protection;
    }

    public String getProtectionDetail() {
        return protectionDetail;
    }

    /** Final lines plus the in-progress partial, ready to display. */
    public String getTranscriptText() {
        if (partialTranscript.isEmpty()) return transcript.toString();
        if (transcript.length() == 0) return partialTranscript;
        return transcript + "\n" + partialTranscript;
    }

    public void answer() {
        Call call = getPrimaryCall();
        if (call != null && call.getState() == Call.STATE_RINGING) {
            call.answer(VideoProfile.STATE_AUDIO_ONLY);
        }
    }

    public void decline() {
        Call call = getPrimaryCall();
        if (call != null && call.getState() == Call.STATE_RINGING) call.reject(false, null);
    }

    public void hangUp() {
        Call call = getPrimaryCall();
        if (call != null) call.disconnect();
        if (listenerCall != null) listenerCall.disconnect();
    }

    public boolean isMuted() {
        CallAudioState state = audioState();
        return state != null && state.isMuted();
    }

    public void setMuted(boolean muted) {
        if (service != null) service.setMuted(muted);
    }

    public boolean isSpeakerOn() {
        CallAudioState state = audioState();
        return state != null && state.getRoute() == CallAudioState.ROUTE_SPEAKER;
    }

    public void setSpeakerOn(boolean on) {
        if (service != null) {
            service.setAudioRoute(on ? CallAudioState.ROUTE_SPEAKER : CallAudioState.ROUTE_WIRED_OR_EARPIECE);
        }
    }

    @SuppressWarnings("deprecation") // getCallAudioState: fine for our min/target range
    @Nullable
    private CallAudioState audioState() {
        return service == null ? null : service.getCallAudioState();
    }

    void notifyAudioChanged() {
        notifyChanged();
    }

    // ---- Call callbacks --------------------------------------------------------------------

    private final Call.Callback callback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            if (call == listenerCall) {
                handleListenerState(state);
            } else {
                handleUserCallState(call, state);
            }
            notifyChanged();
        }

        @Override
        public void onParentChanged(Call call, Call parent) {
            if (call == protectedCall && parent != null) onMerged();
            notifyChanged();
        }

        @Override
        public void onChildrenChanged(Call call, List<Call> children) {
            if (protectedCall != null && children.contains(protectedCall)) onMerged();
            notifyChanged();
        }

        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
            notifyChanged();
        }
    };

    private void handleUserCallState(Call call, int state) {
        if (state == Call.STATE_RINGING) {
            CallNotifications.showIncoming(app, displayName(call));
            return;
        }
        updateOngoingNotification();

        if (state == Call.STATE_ACTIVE) {
            if (protectedCall != null && call.getChildren().contains(protectedCall)) {
                onMerged(); // conference became active
            } else if (protection == Protection.OFF && protectedCall == null && shouldProtect(call)) {
                startProtection(call);
            }
        } else if (state == Call.STATE_DISCONNECTED && call == protectedCall) {
            endProtection();
        }
    }

    private void handleListenerState(int state) {
        if (state == Call.STATE_ACTIVE && !codeSent && protection == Protection.CONNECTING) {
            sendSessionCode();
        } else if (state == Call.STATE_DISCONNECTED && protection == Protection.CONNECTING) {
            fail(app.getString(R.string.protection_failed_listener));
        }
    }

    // ---- Protection flow -------------------------------------------------------------------

    private boolean shouldProtect(Call call) {
        if (!ProtectionSettings.isActive(app) || !ProtectionSettings.isConfigured(app)) return false;
        if (call.getDetails().hasProperty(Call.Details.PROPERTY_CONFERENCE)) return false;
        if (isEmergency(call)) {
            Log.i(TAG, "Emergency call: never protect");
            return false;
        }
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "CALL_PHONE not granted: can't add listener");
            return false;
        }
        return true;
    }

    private void startProtection(Call call) {
        Log.i(TAG, "Starting protection");
        protectedCall = call;
        protection = Protection.CONNECTING;
        protectionDetail = app.getString(R.string.protection_connecting);
        sessionCode = null;
        codeSent = false;
        transcript.setLength(0);
        partialTranscript = "";
        transcriptNext = 0;
        riskScore = 0;
        riskIsScam = false;
        riskSigns = new ArrayList<>();
        riskSummary = "";
        warnedUser = false;
        protectedStartMs = System.currentTimeMillis();
        protectedName = displayName(call);
        String num = numberOf(call);
        protectedNumber = num == null ? "" : num;
        protectedOutgoing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && call.getDetails().getCallDirection() == Call.Details.DIRECTION_OUTGOING;
        postProtection(() -> fail(app.getString(R.string.protection_failed_timeout)), PROTECTION_TIMEOUT_MS);

        SoteriaApi.createSession((code, error) -> {
            if (protection != Protection.CONNECTING || protectedCall != call) return;
            if (error != null || code == null) {
                Log.w(TAG, "createSession failed", error);
                fail(app.getString(R.string.protection_failed_backend));
                return;
            }
            sessionCode = code;
            dialListener(call);
        });
    }

    @SuppressWarnings("MissingPermission") // CALL_PHONE checked in shouldProtect()
    private void dialListener(Call call) {
        TelecomManager telecom = app.getSystemService(TelecomManager.class);
        if (telecom == null) {
            fail(app.getString(R.string.protection_failed_dial));
            return;
        }
        Bundle extras = new Bundle();
        PhoneAccountHandle account = call.getDetails().getAccountHandle();
        if (account != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account);
        try {
            telecom.placeCall(Uri.fromParts("tel", ProtectionSettings.listenerNumber(app), null), extras);
            protectionDetail = app.getString(R.string.protection_adding);
            notifyChanged();
        } catch (SecurityException e) {
            Log.w(TAG, "placeCall denied", e);
            fail(app.getString(R.string.protection_failed_dial));
        }
    }

    /** Sends the session code as DTMF to the listener (original call is on hold). */
    private void sendSessionCode() {
        if (sessionCode == null) return;
        codeSent = true;
        protectionDetail = app.getString(R.string.protection_verifying);
        notifyChanged();
        // "*" first: dismisses Twilio's "trial account, press any key" prompt (harmless on paid
        // accounts). Then the code, then "#" to submit. The backend reads the last 6 digits.
        String digits = sessionCode + "#";
        long t = DTMF_START_DELAY_MS;
        postProtection(() -> { if (listenerCall != null) listenerCall.playDtmfTone('*'); }, t);
        postProtection(() -> { if (listenerCall != null) listenerCall.stopDtmfTone(); }, t + DTMF_TONE_MS);
        t += TRIAL_PROMPT_SKIP_MS;
        for (int i = 0; i < digits.length(); i++) {
            char digit = digits.charAt(i);
            postProtection(() -> { if (listenerCall != null) listenerCall.playDtmfTone(digit); }, t);
            postProtection(() -> { if (listenerCall != null) listenerCall.stopDtmfTone(); }, t + DTMF_TONE_MS);
            t += DTMF_TONE_MS + DTMF_GAP_MS;
        }
        postProtection(this::waitUntilListening, t + 300);
    }

    private void waitUntilListening() {
        if (protection != Protection.CONNECTING || sessionCode == null) return;
        String code = sessionCode;
        SoteriaApi.getSession(code, 0, (session, error) -> {
            if (protection != Protection.CONNECTING || !code.equals(sessionCode)) return;
            if (session != null && "listening".equals(session.status)) {
                merge(1);
            } else {
                postProtection(this::waitUntilListening, LISTENING_POLL_MS);
            }
        });
    }

    private void merge(int attempt) {
        if (protection != Protection.CONNECTING || listenerCall == null || protectedCall == null) return;
        protectionDetail = app.getString(R.string.protection_merging);
        notifyChanged();
        Call listener = listenerCall;
        Call original = protectedCall;
        if (listener.getConferenceableCalls().contains(original)) {
            listener.conference(original);
        } else if (original.getConferenceableCalls().contains(listener)) {
            original.conference(listener);
        } else if (listener.getDetails().can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
            listener.mergeConference();
        } else if (original.getDetails().can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
            original.mergeConference();
        } else if (attempt < MAX_MERGE_ATTEMPTS) {
            postProtection(() -> merge(attempt + 1), 800);
        } else {
            fail(app.getString(R.string.protection_failed_merge));
        }
    }

    private void onMerged() {
        if (protection != Protection.CONNECTING) return;
        Log.i(TAG, "Calls merged: protected");
        stopProtectionCallbacks();
        protection = Protection.PROTECTED;
        protectionDetail = app.getString(R.string.protection_active);
        updateOngoingNotification();
        pollTranscript();
    }

    private void pollTranscript() {
        if (protection != Protection.PROTECTED || sessionCode == null) return;
        String code = sessionCode;
        SoteriaApi.getSession(code, transcriptNext, (session, error) -> {
            if (protection != Protection.PROTECTED || !code.equals(sessionCode)) return;
            if (session != null) {
                for (String line : session.lines) {
                    if (transcript.length() > 0) transcript.append('\n');
                    transcript.append(line);
                }
                transcriptNext = session.next;
                partialTranscript = session.partial == null ? "" : session.partial;
                applyRisk(session);
                notifyChanged();
            }
            postProtection(this::pollTranscript, TRANSCRIPT_POLL_MS);
        });
    }

    /** Gives up on protection for this call: drop the listener and resume the original call. */
    private void fail(String reason) {
        if (protection != Protection.CONNECTING) return;
        Log.w(TAG, "Protection failed: " + reason);
        stopProtectionCallbacks();
        protection = Protection.FAILED;
        protectionDetail = reason;
        if (listenerCall != null) listenerCall.disconnect();
        if (protectedCall != null && protectedCall.getState() == Call.STATE_HOLDING) protectedCall.unhold();
        updateOngoingNotification();
        notifyChanged();
    }

    /** Stores the AI's latest answer and buzzes the phone the first time it crosses the threshold. */
    private void applyRisk(SoteriaApi.Session session) {
        riskScore = session.riskScore;
        riskIsScam = session.isScam;
        riskSigns = new ArrayList<>(session.signs);
        riskSummary = session.summary == null ? "" : session.summary;
        if (!warnedUser && isRisky()) {
            warnedUser = true;
            alertUser();
        }
    }

    @SuppressWarnings("deprecation")
    private void alertUser() {
        try {
            if (SettingsKeys.vibrateOnAlert(app)) {
                android.os.Vibrator v = (android.os.Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 400, 200, 400}, -1));
                    } else {
                        v.vibrate(new long[]{0, 400, 200, 400}, -1);
                    }
                }
            }
            if (SettingsKeys.soundOnAlert(app)) {
                new android.media.ToneGenerator(android.media.AudioManager.STREAM_VOICE_CALL, 80)
                        .startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 500);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Couldn't alert user", e);
        }
    }

    /** Saves a protected call (transcript + AI risk) so it shows in Logs and, if risky, Alerts. */
    private void saveCall(String id, long startMs, String name, String number, boolean outgoing,
                          String transcriptText, int score, boolean scam, List<String> signs, String summary) {
        long ms = Math.max(0, System.currentTimeMillis() - startMs);
        String duration = String.format(java.util.Locale.US, "%02d:%02d", ms / 60000, (ms / 1000) % 60);
        String when = new java.text.SimpleDateFormat("'Today' · h:mm a", java.util.Locale.getDefault())
                .format(new java.util.Date(startMs));
        CallLog.Risk risk = score >= 70 || scam ? CallLog.Risk.FLAGGED
                : score >= 30 ? CallLog.Risk.MONITORING : CallLog.Risk.SAFE;
        String text = transcriptText;
        if (summary != null && !summary.isEmpty()) text = text + (text.isEmpty() ? "" : "\n") + "Soteria: " + summary;
        CallHistoryStore.save(app, new CallLog(id, name, number, when, duration, text,
                score / 100.0, risk, new ArrayList<>(signs), false,
                outgoing ? CallLog.Direction.OUTGOING : CallLog.Direction.INCOMING));
    }

    private void endProtection() {
        if (protectedCall != null && (protection == Protection.PROTECTED || transcript.length() > 0)) {
            final String id = "call-" + protectedStartMs;
            final long startMs = protectedStartMs;
            final String name = protectedName;
            final String number = protectedNumber;
            final boolean outgoing = protectedOutgoing;
            saveCall(id, startMs, name, number, outgoing, getTranscriptText(),
                    riskScore, riskIsScam, riskSigns, riskSummary);
            // The backend does one last AI check when the call ends: fetch it and update the saved call.
            final String code = sessionCode;
            if (code != null) {
                main.postDelayed(() -> SoteriaApi.getSession(code, 0, (session, error) -> {
                    if (session == null) return;
                    StringBuilder all = new StringBuilder();
                    for (String line : session.lines) all.append(all.length() > 0 ? "\n" : "").append(line);
                    saveCall(id, startMs, name, number, outgoing, all.toString(),
                            session.riskScore, session.isScam, session.signs, session.summary);
                }), 4000);
            }
        }
        stopProtectionCallbacks();
        if (listenerCall != null && listenerCall.getState() != Call.STATE_DISCONNECTED) {
            listenerCall.disconnect();
        }
        protection = Protection.OFF;
        protectionDetail = "";
        protectedCall = null;
        sessionCode = null;
        codeSent = false;
    }

    private void postProtection(Runnable runnable, long delayMs) {
        HandlerCompat.postDelayed(main, runnable, PROTECTION_TOKEN, delayMs);
    }

    private void stopProtectionCallbacks() {
        main.removeCallbacksAndMessages(PROTECTION_TOKEN);
    }

    // ---- Helpers ---------------------------------------------------------------------------

    private void notifyChanged() {
        for (Listener listener : new ArrayList<>(listeners)) listener.onCallStateChanged();
    }

    private void updateOngoingNotification() {
        Call primary = getPrimaryCall();
        if (primary == null || primary.getState() == Call.STATE_RINGING) return;
        CallNotifications.showOngoing(app, getPrimaryDisplayName(),
                protection == Protection.PROTECTED
                        ? app.getString(R.string.protection_active)
                        : app.getString(R.string.notification_ongoing));
    }

    private void launchInCallUi() {
        Intent intent = new Intent(app, InCallActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            app.startActivity(intent);
        } catch (RuntimeException e) {
            // Background launch may be blocked; the call notification still opens the UI.
            Log.w(TAG, "Couldn't launch in-call UI", e);
        }
    }

    private boolean isListenerLeg(Call call) {
        String number = numberOf(call);
        String listener = ProtectionSettings.listenerNumber(app);
        if (number == null || listener.isEmpty()) return false;
        return digitsTail(number).equals(digitsTail(listener));
    }

    private static String digitsTail(String number) {
        String digits = number.replaceAll("[^0-9]", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    @SuppressWarnings("deprecation")
    private boolean isEmergency(Call call) {
        String number = numberOf(call);
        if (number == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            TelephonyManager telephony = app.getSystemService(TelephonyManager.class);
            try {
                if (telephony != null && telephony.isEmergencyNumber(number)) return true;
            } catch (RuntimeException ignored) {
                // Fall through to the legacy check.
            }
        }
        return PhoneNumberUtils.isEmergencyNumber(number);
    }

    @Nullable
    static String numberOf(Call call) {
        Uri handle = call.getDetails().getHandle();
        return handle == null ? null : handle.getSchemeSpecificPart();
    }

    String displayName(Call call) {
        String name = call.getDetails().getCallerDisplayName();
        if (name != null && !name.isEmpty()) return name;
        String number = numberOf(call);
        if (number != null && !number.isEmpty()) return number;
        return app.getString(R.string.call_unknown_caller);
    }
}
