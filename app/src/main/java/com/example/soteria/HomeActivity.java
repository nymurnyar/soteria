package com.example.soteria;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

/** Front page: Activate toggle plus entry points to Logs, Alerts and Settings. */
public class HomeActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "soteria_prefs";
    private static final String KEY_ACTIVE = "protection_active";

    private SharedPreferences prefs;
    private MaterialButton activateButton;
    private TextView protectionStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        activateButton = findViewById(R.id.activateButton);
        protectionStatus = findViewById(R.id.protectionStatus);

        // TODO: hook up real protection (default dialer + call listener) once features are defined.
        activateButton.setOnClickListener(v -> {
            boolean active = !prefs.getBoolean(KEY_ACTIVE, false);
            prefs.edit().putBoolean(KEY_ACTIVE, active).apply();
            renderActiveState(active);
        });

        findViewById(R.id.logsButton).setOnClickListener(v ->
                startActivity(new Intent(this, LogsActivity.class)));
        findViewById(R.id.alertsButton).setOnClickListener(v ->
                startActivity(new Intent(this, AlertsActivity.class)));
        findViewById(R.id.settingsButton).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        renderActiveState(prefs.getBoolean(KEY_ACTIVE, false));
    }

    private void renderActiveState(boolean active) {
        activateButton.setText(active ? R.string.action_deactivate : R.string.action_activate);
        protectionStatus.setText(active ? R.string.status_active : R.string.status_inactive);
    }
}
