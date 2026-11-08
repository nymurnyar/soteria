package com.example.soteria;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.Manifest;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.transition.TransitionManager;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/** Front page: protection toggle, overview stats, latest alert and quick access to Logs/Alerts/Settings. */
public class HomeActivity extends AppCompatActivity {

    // Palette (matches the Call Logs screen).
    private static final int NAVY = 0xFF102A4C;
    private static final int SLATE = 0xFF64748B;
    private static final int BLUE = 0xFF4285E8;
    private static final int GREEN = 0xFF15803D;
    private static final int RED = 0xFFDC2626;
    private static final int AMBER = 0xFFB45309;

    /** Step 1 of activation: become the default phone app. */
    private final ActivityResultLauncher<Intent> dialerRoleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (ProtectionSettings.isDefaultDialer(this)) {
                    requestPermissionsThenEnable();
                } else {
                    Toast.makeText(this, R.string.activation_dialer_required, Toast.LENGTH_LONG).show();
                }
            });

    /** Step 2 of activation: phone (required) + notifications (for incoming-call screen). */
    private final ActivityResultLauncher<String[]> permissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                if (hasPermission(Manifest.permission.CALL_PHONE)) {
                    setProtection(true);
                } else {
                    Toast.makeText(this, R.string.activation_permissions_required, Toast.LENGTH_LONG).show();
                }
            });

    private ViewGroup heroCard;
    private View heroIconBg;
    private ImageView heroIcon;
    private TextView heroTitle;
    private TextView heroSubtitle;
    private MaterialButton activateButton;
    private View pulseRingOuter;
    private View pulseRingInner;
    private AnimatorSet pulseAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_home);
        // Light background in both themes, so always use dark status/nav bar icons.
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        heroCard = findViewById(R.id.heroCard);
        heroIconBg = findViewById(R.id.heroIconBg);
        heroIcon = findViewById(R.id.heroIcon);
        heroTitle = findViewById(R.id.heroTitle);
        heroSubtitle = findViewById(R.id.heroSubtitle);
        activateButton = findViewById(R.id.activateButton);
        pulseRingOuter = findViewById(R.id.pulseRingOuter);
        pulseRingInner = findViewById(R.id.pulseRingInner);

        activateButton.setOnClickListener(v -> {
            if (ProtectionSettings.isActive(this)) {
                setProtection(false);
            } else {
                startActivation();
            }
        });

        renderProtection(ProtectionSettings.isActive(this));
    }

    // ---- Activation ------------------------------------------------------------------------

    private void startActivation() {
        if (!ProtectionSettings.isConfigured()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.activation_not_configured_title)
                    .setMessage(R.string.activation_not_configured_body)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        if (ProtectionSettings.isDefaultDialer(this)) {
            requestPermissionsThenEnable();
            return;
        }
        Intent request;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roles = getSystemService(RoleManager.class);
            request = roles.createRequestRoleIntent(RoleManager.ROLE_DIALER);
        } else {
            request = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
        }
        dialerRoleLauncher.launch(request);
    }

    private void requestPermissionsThenEnable() {
        List<String> needed = new ArrayList<>();
        if (!hasPermission(Manifest.permission.CALL_PHONE)) needed.add(Manifest.permission.CALL_PHONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (needed.isEmpty()) {
            setProtection(true);
        } else {
            permissionsLauncher.launch(needed.toArray(new String[0]));
        }
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void setProtection(boolean active) {
        ProtectionSettings.setEnabled(this, active);
        activateButton.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
        TransitionManager.beginDelayedTransition(heroCard);
        renderProtection(active);
        Toast.makeText(this, active ? R.string.home_protection_on_toast
                : R.string.activation_off_hint, active ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh every time we come back, e.g. after reviewing alerts or changing the default phone app.
        bindOverview(CallLogRepository.getDemoCalls());
        renderProtection(ProtectionSettings.isActive(this));
    }

    // ---- Protection hero -------------------------------------------------------------------

    private void renderProtection(boolean active) {
        heroCard.setBackgroundResource(active ? R.drawable.bg_hero_active : R.drawable.bg_hero_inactive);

        heroTitle.setText(active ? R.string.status_active : R.string.status_inactive);
        heroTitle.setTextColor(active ? 0xFFFFFFFF : NAVY);
        heroSubtitle.setText(active ? R.string.status_active_detail : R.string.status_inactive_detail);
        heroSubtitle.setTextColor(active ? 0xFFDBEAFE : SLATE);

        heroIcon.setImageResource(active ? R.drawable.ic_shield_check : R.drawable.ic_shield);
        heroIcon.setImageTintList(ColorStateList.valueOf(active ? 0xFFFFFFFF : AMBER));
        heroIconBg.setBackgroundTintList(ColorStateList.valueOf(active ? 0x33FFFFFF : 0xFFFEF3C7));

        activateButton.setText(active ? R.string.action_deactivate : R.string.action_activate);
        activateButton.setBackgroundTintList(ColorStateList.valueOf(active ? 0xFFFFFFFF : BLUE));
        activateButton.setTextColor(active ? NAVY : 0xFFFFFFFF);
        activateButton.setIconTint(ColorStateList.valueOf(active ? NAVY : 0xFFFFFFFF));

        if (active) startPulse(); else stopPulse();
    }

    /** Two staggered rings expanding out of the shield while protection is on. */
    private void startPulse() {
        if (pulseAnimator != null && pulseAnimator.isRunning()) return;
        pulseAnimator = new AnimatorSet();
        pulseAnimator.playTogether(ring(pulseRingOuter, 0), ring(pulseRingInner, 1100));
        pulseAnimator.start();
    }

    private static AnimatorSet ring(View ring, long delay) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ring, View.SCALE_X, 1f, 1.38f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ring, View.SCALE_Y, 1f, 1.38f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(ring, View.ALPHA, 0.7f, 0f);
        for (ValueAnimator a : new ValueAnimator[]{scaleX, scaleY, alpha}) {
            a.setDuration(2200);
            a.setStartDelay(delay);
            a.setRepeatCount(ValueAnimator.INFINITE);
        }
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, alpha);
        return set;
    }

    private void stopPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        for (View ring : new View[]{pulseRingOuter, pulseRingInner}) {
            ring.setAlpha(0f);
            ring.setScaleX(1f);
            ring.setScaleY(1f);
        }
    }

    // ---- Overview, latest alert, quick access -----------------------------------------------

    private void bindOverview(List<CallLog> calls) {
        int safe = 0;
        int flagged = 0;
        for (CallLog call : calls) {
            if (call.getRisk() == CallLog.Risk.SAFE) safe++;
            if (call.getRisk() == CallLog.Risk.FLAGGED) flagged++;
        }
        // Alerts the user hasn't reviewed yet (newest first); prefer the latest high-risk one.
        List<CallLog> openAlerts = new AlertStore(this).alerts(calls, true);
        CallLog latestAlert = null;
        for (CallLog call : openAlerts) {
            if (call.getRisk() == CallLog.Risk.FLAGGED) { latestAlert = call; break; }
        }
        if (latestAlert == null && !openAlerts.isEmpty()) latestAlert = openAlerts.get(0);

        bindStat(R.id.statTotal, calls.size(), R.string.home_stat_checked, BLUE);
        bindStat(R.id.statSafe, safe, R.string.stat_safe, GREEN);
        bindStat(R.id.statFlagged, flagged, R.string.stat_flagged, RED);

        View alertCard = findViewById(R.id.latestAlertCard);
        if (latestAlert == null) {
            alertCard.setVisibility(View.GONE);
        } else {
            alertCard.setVisibility(View.VISIBLE);
            String reason = latestAlert.getThreatCategories().isEmpty()
                    ? "" : " · " + latestAlert.getThreatCategories().get(0);
            ((TextView) findViewById(R.id.latestAlertTitle))
                    .setText(latestAlert.getDisplayName() + reason);
            ((TextView) findViewById(R.id.latestAlertTime)).setText(latestAlert.getTimestamp());
            alertCard.setOnClickListener(v -> open(AlertsActivity.class));
        }

        bindAction(R.id.actionLogs, R.drawable.ic_history, BLUE, 0xFFEAF2FF,
                getString(R.string.home_logs_title),
                getString(R.string.home_logs_subtitle, calls.size()), 0, CallLogsActivity.class);
        bindAction(R.id.actionAlerts, R.drawable.ic_bell, RED, 0xFFFEE2E2,
                getString(R.string.title_alerts),
                !openAlerts.isEmpty()
                        ? getString(R.string.home_alerts_subtitle_some, openAlerts.size())
                        : getString(R.string.home_alerts_subtitle_none),
                openAlerts.size(), AlertsActivity.class);
        bindAction(R.id.actionKeypad, R.drawable.ic_dialpad, GREEN, 0xFFDCFCE7,
                getString(R.string.home_keypad_title),
                getString(R.string.home_keypad_subtitle), 0, DialerActivity.class);
        bindAction(R.id.actionSettings, R.drawable.ic_settings, SLATE, 0xFFF1F5F9,
                getString(R.string.title_settings),
                getString(R.string.home_settings_subtitle), 0, SettingsActivity.class);
    }

    private void bindStat(int includeId, int value, @StringRes int label, @ColorInt int color) {
        View card = findViewById(includeId);
        TextView valueView = card.findViewById(R.id.statValue);
        valueView.setText(String.valueOf(value));
        valueView.setTextColor(color == BLUE ? NAVY : color);
        ((TextView) card.findViewById(R.id.statLabel)).setText(label);
        card.findViewById(R.id.statDot).setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private void bindAction(int includeId, @DrawableRes int icon, @ColorInt int iconColor,
                            @ColorInt int tileColor, String title, String subtitle, int badge,
                            Class<?> target) {
        View card = findViewById(includeId);
        ImageView iconView = card.findViewById(R.id.actionIcon);
        iconView.setImageResource(icon);
        iconView.setImageTintList(ColorStateList.valueOf(iconColor));
        card.findViewById(R.id.actionIconBg).setBackgroundTintList(ColorStateList.valueOf(tileColor));
        ((TextView) card.findViewById(R.id.actionTitle)).setText(title);
        ((TextView) card.findViewById(R.id.actionSubtitle)).setText(subtitle);
        TextView badgeView = card.findViewById(R.id.actionBadge);
        badgeView.setText(String.valueOf(badge));
        badgeView.setVisibility(badge > 0 ? View.VISIBLE : View.GONE);
        card.setContentDescription(title + ". " + subtitle);
        card.setOnClickListener(v -> open(target));
    }

    private void open(Class<?> target) {
        startActivity(new Intent(this, target));
    }

    @Override
    protected void onDestroy() {
        stopPulse();
        super.onDestroy();
    }
}
