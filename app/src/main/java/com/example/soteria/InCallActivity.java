package com.example.soteria;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Soteria's call screen (used because Soteria is the default phone app). */
public class InCallActivity extends AppCompatActivity implements CallManager.Listener {

    private TextView number, state, trustChip, riskBanner;
    private Button answer, hangup, mute, speaker, keypadToggle, merge;
    private GridLayout keypad;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long activeSince = 0;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        setContentView(R.layout.activity_in_call);

        number = findViewById(R.id.call_number);
        state = findViewById(R.id.call_state);
        trustChip = findViewById(R.id.trust_chip);
        riskBanner = findViewById(R.id.risk_banner);
        answer = findViewById(R.id.btn_answer);
        hangup = findViewById(R.id.btn_hangup);
        mute = findViewById(R.id.btn_mute);
        speaker = findViewById(R.id.btn_speaker);
        keypadToggle = findViewById(R.id.btn_keypad);
        merge = findViewById(R.id.btn_merge);
        keypad = findViewById(R.id.keypad);

        answer.setOnClickListener(v -> {
            Call c = primary();
            if (c != null) c.answer(VideoProfile.STATE_AUDIO_ONLY);
        });
        hangup.setOnClickListener(v -> {
            if (CallManager.service != null) CallManager.service.hangUpAll();
            else finish();
        });
        mute.setOnClickListener(v -> {
            CallAudioState a = audio();
            if (CallManager.service != null && a != null) CallManager.service.setMuted(!a.isMuted());
        });
        speaker.setOnClickListener(v -> {
            CallAudioState a = audio();
            if (CallManager.service == null || a == null) return;
            boolean on = a.getRoute() == CallAudioState.ROUTE_SPEAKER;
            CallManager.service.setAudioRoute(on ? CallAudioState.ROUTE_WIRED_OR_EARPIECE
                    : CallAudioState.ROUTE_SPEAKER);
        });
        keypadToggle.setOnClickListener(v ->
                keypad.setVisibility(keypad.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        merge.setOnClickListener(v -> {
            if (CallManager.service != null) CallManager.service.manualMerge();
        });
        buildKeypad();
    }

    @Override
    protected void onStart() {
        super.onStart();
        CallManager.addListener(this);
        handler.post(ticker);
    }

    @Override
    protected void onStop() {
        CallManager.removeListener(this);
        handler.removeCallbacks(ticker);
        super.onStop();
    }

    @Override
    public void onCallsChanged() {
        runOnUiThread(this::refresh);
    }

    /** The call to show: a ringing call first, otherwise the real (non-TrustLine) call. */
    private Call primary() {
        for (Call c : CallManager.calls()) {
            if (c.getState() == Call.STATE_RINGING) return c;
        }
        if (CallManager.mainCall != null) return CallManager.mainCall;
        for (Call c : CallManager.calls()) {
            if (!TrustLine.isTrustLine(this, c)) return c;
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private CallAudioState audio() {
        return CallManager.service == null ? null : CallManager.service.getCallAudioState();
    }

    @SuppressWarnings("deprecation")
    private void refresh() {
        Call c = primary();
        if (c == null) {
            finish();
            return;
        }
        String n = TrustLine.numberOf(c);
        number.setText(n.isEmpty() ? "Unknown caller" : n);

        int s = c.getState();
        boolean ringing = s == Call.STATE_RINGING;
        answer.setVisibility(ringing ? View.VISIBLE : View.GONE);
        hangup.setText(ringing ? "Decline" : "Hang up");

        if (s == Call.STATE_ACTIVE) {
            if (activeSince == 0) activeSince = SystemClock.elapsedRealtime();
            long sec = (SystemClock.elapsedRealtime() - activeSince) / 1000;
            state.setText(String.format("%d:%02d", sec / 60, sec % 60));
        } else {
            state.setText(stateName(s));
        }

        // TrustLine chip + manual merge fallback
        String ts = CallManager.trustStatus;
        trustChip.setVisibility(ts == null || ts.isEmpty() ? View.GONE : View.VISIBLE);
        trustChip.setText("🛡 " + ts);
        merge.setVisibility(CallManager.needsManualMerge ? View.VISIBLE : View.GONE);

        // Risk banner (filled by backend polling)
        if (CallManager.risk == Risk.CAUTION || CallManager.risk == Risk.DANGER) {
            riskBanner.setVisibility(View.VISIBLE);
            riskBanner.setBackgroundColor(CallManager.risk.color);
            riskBanner.setText("⚠ " + CallManager.riskText);
        } else {
            riskBanner.setVisibility(View.GONE);
        }

        CallAudioState a = audio();
        if (a != null) {
            mute.setText(a.isMuted() ? "Unmute" : "Mute");
            speaker.setText(a.getRoute() == CallAudioState.ROUTE_SPEAKER ? "Earpiece" : "Speaker");
        }
    }

    private static String stateName(int s) {
        switch (s) {
            case Call.STATE_RINGING: return "Incoming call";
            case Call.STATE_DIALING:
            case Call.STATE_CONNECTING: return "Calling…";
            case Call.STATE_HOLDING: return "On hold";
            case Call.STATE_DISCONNECTING:
            case Call.STATE_DISCONNECTED: return "Call ended";
            default: return "";
        }
    }

    private void buildKeypad() {
        String keys = "123456789*0#";
        for (int i = 0; i < keys.length(); i++) {
            char k = keys.charAt(i);
            Button b = new Button(this);
            b.setText(String.valueOf(k));
            b.setTextSize(20);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                Call c = primary();
                if (c != null) {
                    c.playDtmfTone(k);
                    c.stopDtmfTone();
                }
            });
            keypad.addView(b);
        }
    }
}
