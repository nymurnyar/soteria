package com.example.soteria;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;

/** Keypad. Required for Soteria to be the default phone app (handles ACTION_DIAL). */
public class DialerActivity extends AppCompatActivity {

    private static final String[][] KEYS = {
            {"1", ""}, {"2", "ABC"}, {"3", "DEF"},
            {"4", "GHI"}, {"5", "JKL"}, {"6", "MNO"},
            {"7", "PQRS"}, {"8", "TUV"}, {"9", "WXYZ"},
            {"*", ""}, {"0", "+"}, {"#", ""},
    };

    private final StringBuilder number = new StringBuilder();
    private TextView display;

    private final ActivityResultLauncher<String> callPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) placeCall();
                else Toast.makeText(this, R.string.dialer_permission_needed, Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_dialer);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dialerRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        display = findViewById(R.id.numberDisplay);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.callButton).setOnClickListener(v -> onCallPressed());
        findViewById(R.id.deleteButton).setOnClickListener(v -> {
            if (number.length() > 0) number.deleteCharAt(number.length() - 1);
            render();
        });
        findViewById(R.id.deleteButton).setOnLongClickListener(v -> {
            number.setLength(0);
            render();
            return true;
        });
        buildKeypad(findViewById(R.id.keypad));
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView badge = findViewById(R.id.protectionBadge);
        boolean active = ProtectionSettings.isActive(this) && ProtectionSettings.isConfigured(this);
        badge.setText(active ? R.string.dialer_protected : R.string.dialer_unprotected);
        badge.setTextColor(active ? 0xFF15803D : 0xFF5E6E84);
        badge.setBackgroundTintList(ColorStateList.valueOf(active ? 0xFFDCFCE7 : 0xFFEEF4ED));
    }

    /** Pre-fills the number from tel: links (ACTION_DIAL / ACTION_VIEW). */
    private void handleIntent(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data != null && "tel".equals(data.getScheme())) {
            number.setLength(0);
            number.append(data.getSchemeSpecificPart());
        }
        render();
    }

    private void buildKeypad(LinearLayout keypad) {
        float density = getResources().getDisplayMetrics().density;
        for (int row = 0; row < 4; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            keypad.addView(rowLayout, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.round(76 * density)));
            for (int col = 0; col < 3; col++) {
                String[] key = KEYS[row * 3 + col];
                MaterialButton button = new MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                button.setText(key[1].isEmpty() ? key[0] : key[0] + "\n" + key[1]);
                button.setTextSize(key[1].isEmpty() ? 26 : 22);
                button.setTextColor(0xFF0B2C5E);
                button.setAllCaps(false);
                button.setStrokeWidth(0);
                button.setGravity(Gravity.CENTER);
                button.setPadding(0, 0, 0, 0);
                button.setInsetTop(0);
                button.setInsetBottom(0);
                button.setCornerRadius(Math.round(38 * density));
                button.setContentDescription(key[0]);
                button.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    number.append(key[0]);
                    render();
                });
                if ("0".equals(key[0])) {
                    button.setOnLongClickListener(v -> {
                        number.append('+');
                        render();
                        return true;
                    });
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                int margin = Math.round(6 * density);
                lp.setMargins(margin, margin / 2, margin, margin / 2);
                rowLayout.addView(button, lp);
            }
        }
    }

    private void render() {
        String raw = number.toString();
        String formatted = PhoneNumberUtils.formatNumber(raw, Locale.getDefault().getCountry());
        display.setText(formatted != null ? formatted : raw);
    }

    private void onCallPressed() {
        if (number.length() == 0) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
            placeCall();
        } else {
            callPermission.launch(Manifest.permission.CALL_PHONE);
        }
    }

    @SuppressWarnings("MissingPermission") // checked in onCallPressed()
    private void placeCall() {
        TelecomManager telecom = getSystemService(TelecomManager.class);
        if (telecom == null) return;
        try {
            telecom.placeCall(Uri.fromParts("tel", number.toString(), null), new Bundle());
            number.setLength(0);
            render();
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.dialer_permission_needed, Toast.LENGTH_LONG).show();
        }
    }
}
