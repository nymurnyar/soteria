package com.example.soteria;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
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
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.List;

/**
 * Android binds this service when Soteria is the default phone app.
 *
 * Auto-TrustLine flow:
 *  1. A real call becomes ACTIVE (answered / picked up).
 *  2. If "Auto-add TrustLine" is on, we dial the TrustLine (Twilio) number.
 *     Android puts the real call on hold automatically.
 *  3. When TrustLine answers, we merge both calls into a conference.
 *     TrustLine now hears the call and transcribes it.
 *  4. When the real call ends, we hang up the TrustLine leg too.
 */
public class SoteriaInCallService extends InCallService {

    private static final String TAG = "SoteriaInCall";
    private static final String CHANNEL_ID = "soteria_calls";
    private static final int NOTIF_ID = 42;
    private static final int MAX_MERGE_TRIES = 8;
    private static final long MERGE_RETRY_MS = 750;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean trustDialed = false;
    private boolean merged = false;
    private int mergeTries = 0;

    private final Call.Callback callback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            evaluate();
            updateNotification();
            CallManager.notifyChanged();
        }

        @Override
        public void onConferenceableCallsChanged(Call call, List<Call> conferenceableCalls) {
            evaluate();
        }

        @Override
        public void onParentChanged(Call call, Call parent) {
            checkMerged();
        }

        @Override
        public void onChildrenChanged(Call call, List<Call> children) {
            checkMerged();
        }

        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
            evaluate();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        CallManager.service = this;
    }

    @Override
    public void onDestroy() {
        if (CallManager.service == this) CallManager.service = null;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------- Calls added / removed ----------

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        call.registerCallback(callback);

        boolean isConference = call.getDetails() != null
                && call.getDetails().hasProperty(Call.Details.PROPERTY_CONFERENCE);

        if (TrustLine.isTrustLine(this, call)) {
            CallManager.trustCall = call;
            CallManager.setTrustStatus("Connecting TrustLine…", false);
        } else if (!isConference && CallManager.mainCall == null) {
            // A brand-new real call: reset per-call state.
            CallManager.reset();
            CallManager.mainCall = call;
            trustDialed = false;
            merged = false;
            mergeTries = 0;
        }

        CallManager.add(call);
        showCallScreen();
        updateNotification();
        evaluate();
        checkMerged();
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        call.unregisterCallback(callback);
        CallManager.remove(call);

        if (call == CallManager.mainCall) {
            // Caller hung up: drop TrustLine too.
            hangUpTrustLine();
            CallManager.mainCall = null;
        } else if (call == CallManager.trustCall) {
            CallManager.trustCall = null;
            if (!merged) {
                CallManager.setTrustStatus("TrustLine couldn't join", false);
                unholdMain();
            }
        }

        if (CallManager.calls().isEmpty()) {
            handler.removeCallbacksAndMessages(null);
            cancelNotification();
            CallManager.reset();
            CallManager.notifyChanged();
        } else {
            updateNotification();
        }
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        CallManager.notifyChanged();
    }

    // ---------- Auto TrustLine logic ----------

    @SuppressWarnings("deprecation")
    private void evaluate() {
        Call main = CallManager.mainCall;
        Call trust = CallManager.trustCall;

        // Real call ended while TrustLine is still up -> hang up TrustLine.
        if (main != null && main.getState() == Call.STATE_DISCONNECTED) {
            hangUpTrustLine();
            return;
        }

        // Step 2: real call answered -> dial TrustLine.
        if (main != null && !trustDialed && main.getState() == Call.STATE_ACTIVE) {
            String number = TrustLine.numberOf(main);
            if (!SettingsKeys.autoTrustLine(this)) {
                trustDialed = true; // user turned it off; don't try again this call
                return;
            }
            if (TrustLine.isEmergency(this, number)) {
                trustDialed = true;
                CallManager.setTrustStatus("TrustLine off for emergency calls", false);
                return;
            }
            trustDialed = true;
            dialTrustLine(main);
        }

        // Step 3: TrustLine answered -> merge.
        if (trust != null && !merged && trust.getState() == Call.STATE_ACTIVE) {
            tryMerge();
        }
    }

    private void dialTrustLine(Call main) {
        TelecomManager tm = getSystemService(TelecomManager.class);
        Uri uri = Uri.fromParts("tel", TrustLine.number(this), null);
        Bundle extras = new Bundle();
        PhoneAccountHandle account = main.getDetails() == null ? null : main.getDetails().getAccountHandle();
        if (account != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account);
        try {
            tm.placeCall(uri, extras);
            CallManager.setTrustStatus("Adding TrustLine…", false);
        } catch (SecurityException e) {
            Log.w(TAG, "No CALL_PHONE permission", e);
            CallManager.setTrustStatus("Allow Phone permission to add TrustLine", false);
        }
    }

    /** Merge the TrustLine leg with the real call. Retries because carriers take a moment. */
    private void tryMerge() {
        if (merged) return;
        Call main = CallManager.mainCall;
        Call trust = CallManager.trustCall;
        if (main == null || trust == null) return;

        try {
            if (trust.getConferenceableCalls().contains(main)) {
                trust.conference(main);
            } else if (main.getConferenceableCalls().contains(trust)) {
                main.conference(trust);
            } else if (trust.getDetails().can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                trust.mergeConference();
            } else if (main.getDetails().can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                main.mergeConference();
            }
        } catch (Exception e) {
            Log.w(TAG, "Merge attempt failed", e);
        }

        mergeTries++;
        handler.postDelayed(() -> {
            checkMerged();
            if (merged) return;
            if (mergeTries < MAX_MERGE_TRIES) {
                tryMerge();
            } else {
                // Carrier won't let us merge automatically: show a Merge button.
                CallManager.setTrustStatus("Tap Merge to add TrustLine", true);
            }
        }, MERGE_RETRY_MS);
    }

    /** Merge triggered by the user (fallback button). */
    void manualMerge() {
        mergeTries = 0;
        tryMerge();
    }

    private void checkMerged() {
        Call main = CallManager.mainCall;
        Call trust = CallManager.trustCall;
        if (merged || main == null || trust == null) return;
        Call mp = main.getParent();
        Call tp = trust.getParent();
        if (mp != null && mp == tp) {
            merged = true;
            handler.removeCallbacksAndMessages(null);
            CallManager.setTrustStatus("TrustLine is listening", false);
        }
    }

    private void hangUpTrustLine() {
        Call trust = CallManager.trustCall;
        if (trust != null) {
            try {
                trust.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void unholdMain() {
        Call main = CallManager.mainCall;
        if (main != null && main.getState() == Call.STATE_HOLDING) main.unhold();
    }

    /** Hang up everything for the current call (real call + TrustLine). */
    void hangUpAll() {
        for (Call c : CallManager.calls()) {
            try {
                if (c.getState() == Call.STATE_RINGING) c.reject(false, null);
                else c.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    // ---------- Call screen + notification ----------

    private void showCallScreen() {
        Intent i = new Intent(this, InCallActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "Couldn't open call screen directly; notification will", e);
        }
    }

    @SuppressWarnings("deprecation")
    private void updateNotification() {
        Call main = CallManager.mainCall;
        if (main == null) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Calls",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null); // the system plays the ringtone
            nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, InCallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        boolean ringing = main.getState() == Call.STATE_RINGING;
        String who = TrustLine.numberOf(main);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(ringing ? "Incoming call" : "Ongoing call")
                .setContentText(who.isEmpty() ? "Unknown" : who)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, ringing)
                .build();
        try {
            nm.notify(NOTIF_ID, n);
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted
        }
    }

    private void cancelNotification() {
        getSystemService(NotificationManager.class).cancel(NOTIF_ID);
    }
}
