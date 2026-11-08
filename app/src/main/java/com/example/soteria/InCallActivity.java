package com.example.soteria;

import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.text.format.DateUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;

/** Incoming / ongoing call screen with Soteria's protection status and live transcript. */
public class InCallActivity extends AppCompatActivity implements CallManager.Listener {

    private CallManager calls;
    private final Handler ticker = new Handler(Looper.getMainLooper());

    private View protectionPill;
    private ImageView protectionIcon;
    private TextView protectionText;
    private TextView avatarText;
    private TextView callerName;
    private TextView callerNumber;
    private TextView callState;
    private TextView riskBanner;
    private View transcriptCard;
    private View transcriptSpacer;
    private TextView transcriptText;
    private NestedScrollView transcriptScroll;
    private View controlsRow;
    private MaterialButton muteButton;
    private MaterialButton speakerButton;
    private MaterialButton declineButton;
    private MaterialButton answerButton;
    private View answerSpacer;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            renderCallState();
            ticker.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLockScreen();
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_in_call);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.inCallRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        calls = CallManager.get(this);
        protectionPill = findViewById(R.id.protectionPill);
        protectionIcon = findViewById(R.id.protectionIcon);
        protectionText = findViewById(R.id.protectionText);
        avatarText = findViewById(R.id.avatarText);
        callerName = findViewById(R.id.callerName);
        callerNumber = findViewById(R.id.callerNumber);
        callState = findViewById(R.id.callState);
        riskBanner = findViewById(R.id.riskBanner);
        transcriptCard = findViewById(R.id.transcriptCard);
        transcriptSpacer = findViewById(R.id.transcriptSpacer);
        transcriptText = findViewById(R.id.transcriptText);
        transcriptScroll = findViewById(R.id.transcriptScroll);
        controlsRow = findViewById(R.id.controlsRow);
        muteButton = findViewById(R.id.muteButton);
        speakerButton = findViewById(R.id.speakerButton);
        declineButton = findViewById(R.id.declineButton);
        answerButton = findViewById(R.id.answerButton);
        answerSpacer = findViewById(R.id.answerSpacer);

        answerButton.setOnClickListener(v -> calls.answer());
        declineButton.setOnClickListener(v -> {
            Call call = calls.getPrimaryCall();
            if (call != null && call.getState() == Call.STATE_RINGING) calls.decline(); else calls.hangUp();
        });
        muteButton.setOnClickListener(v -> calls.setMuted(!calls.isMuted()));
        speakerButton.setOnClickListener(v -> calls.setSpeakerOn(!calls.isSpeakerOn()));
    }

    @Override
    protected void onStart() {
        super.onStart();
        calls.addListener(this);
        ticker.post(tick);
        render();
    }

    @Override
    protected void onStop() {
        calls.removeListener(this);
        ticker.removeCallbacks(tick);
        super.onStop();
    }

    @Override
    public void onCallStateChanged() {
        render();
    }

    private void render() {
        Call call = calls.getPrimaryCall();
        if (call == null) {
            finishAndRemoveTask();
            return;
        }
        boolean ringing = call.getState() == Call.STATE_RINGING;

        String name = calls.getPrimaryDisplayName();
        callerName.setText(name);
        String number = calls.getPrimaryNumber();
        callerNumber.setText(number);
        callerNumber.setVisibility(number.isEmpty() || number.equals(name) ? View.GONE : View.VISIBLE);
        avatarText.setText(initial(name));
        renderCallState();

        // Buttons
        answerButton.setVisibility(ringing ? View.VISIBLE : View.GONE);
        answerSpacer.setVisibility(ringing ? View.VISIBLE : View.GONE);
        declineButton.setContentDescription(getString(ringing ? R.string.call_decline : R.string.call_hang_up));
        controlsRow.setVisibility(ringing ? View.INVISIBLE : View.VISIBLE);
        styleToggle(muteButton, calls.isMuted());
        styleToggle(speakerButton, calls.isSpeakerOn());

        renderProtection(ringing);
    }

    private void renderCallState() {
        Call call = calls.getPrimaryCall();
        if (call == null) return;
        String text;
        switch (call.getState()) {
            case Call.STATE_RINGING: text = getString(R.string.call_state_incoming); break;
            case Call.STATE_DIALING:
            case Call.STATE_CONNECTING: text = getString(R.string.call_state_dialing); break;
            case Call.STATE_HOLDING: text = getString(R.string.call_state_holding); break;
            case Call.STATE_DISCONNECTING:
            case Call.STATE_DISCONNECTED: text = getString(R.string.call_state_ended); break;
            case Call.STATE_ACTIVE:
                long connected = call.getDetails().getConnectTimeMillis();
                text = connected > 0
                        ? DateUtils.formatElapsedTime((System.currentTimeMillis() - connected) / 1000)
                        : getString(R.string.call_state_active);
                break;
            default: text = "";
        }
        callState.setText(text);
    }

    private void renderProtection(boolean ringing) {
        CallManager.Protection protection = calls.getProtection();
        int textColor;
        int background;
        String label;
        if (!ProtectionSettings.isActive(this) || !ProtectionSettings.isConfigured(this)) {
            label = getString(R.string.protection_off_label);
            textColor = 0xFFCBD5E1;
            background = 0x33FFFFFF;
        } else {
            switch (protection) {
                case CONNECTING:
                    label = calls.getProtectionDetail();
                    textColor = 0xFF78350F;
                    background = 0xFFFDE68A;
                    break;
                case PROTECTED:
                    label = getString(R.string.protection_active);
                    textColor = 0xFF14532D;
                    background = 0xFFBBF7D0;
                    break;
                case FAILED:
                    label = calls.getProtectionDetail();
                    textColor = 0xFF7F1D1D;
                    background = 0xFFFECACA;
                    break;
                default:
                    label = getString(ringing ? R.string.protection_ready : R.string.protection_standby);
                    textColor = 0xFFFFFFFF;
                    background = 0x33FFFFFF;
            }
        }
        protectionText.setText(label);
        protectionText.setTextColor(textColor);
        protectionIcon.setImageTintList(ColorStateList.valueOf(textColor));
        protectionPill.setBackgroundTintList(ColorStateList.valueOf(background));

        renderRisk(protection);

        boolean showTranscript = protection == CallManager.Protection.PROTECTED
                || (protection == CallManager.Protection.FAILED && !calls.getTranscriptText().isEmpty());
        transcriptCard.setVisibility(showTranscript ? View.VISIBLE : View.GONE);
        transcriptSpacer.setVisibility(showTranscript ? View.GONE : View.VISIBLE);
        if (showTranscript) {
            String text = calls.getTranscriptText();
            if (!text.contentEquals(transcriptText.getText())) {
                transcriptText.setText(text);
                transcriptScroll.post(() -> transcriptScroll.fullScroll(View.FOCUS_DOWN));
            }
        }
    }

    /** Red/amber warning with the AI's risk % and warning signs. */
    private void renderRisk(CallManager.Protection protection) {
        if (protection != CallManager.Protection.PROTECTED || !calls.isRisky()) {
            riskBanner.setVisibility(View.GONE);
            return;
        }
        int score = calls.getRiskScore();
        StringBuilder text = new StringBuilder(getString(R.string.risk_warning_title, score));
        for (String sign : calls.getRiskSigns()) text.append("\n• ").append(sign);
        String summary = calls.getRiskSummary();
        if (!summary.isEmpty()) text.append("\n").append(summary);
        riskBanner.setText(text);
        riskBanner.setBackgroundColor(score >= 70 ? 0xFFDC2626 : 0xFFB45309);
        riskBanner.setVisibility(View.VISIBLE);
    }

    private static void styleToggle(MaterialButton button, boolean on) {
        button.setBackgroundTintList(ColorStateList.valueOf(on ? 0xFFFFFFFF : 0x33FFFFFF));
        button.setIconTint(ColorStateList.valueOf(on ? 0xFF102A4C : 0xFFFFFFFF));
        button.setSelected(on);
    }

    private static String initial(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetter(c)) return String.valueOf(Character.toUpperCase(c));
        }
        return "#";
    }

    @SuppressWarnings("deprecation")
    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
