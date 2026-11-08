package com.example.soteria;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns call protection on/off. Walks the user through the permissions
 * (overlay -> phone state -> notifications), then starts CallWatcherService.
 * Launch it from anywhere: startActivity(new Intent(this, OverlaySetupActivity.class));
 */
public class OverlaySetupActivity extends AppCompatActivity {

    // After the user returns from the "Display over other apps" settings page
    private final ActivityResultLauncher<Intent> overlayLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Settings.canDrawOverlays(this)) checkAndStart();
                else toast("Soteria needs 'Display over other apps' to show the bubble");
            });

    // After the user answers the runtime permission popup
    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (missingPermissions().isEmpty()) startProtection();
                else toast("Phone permission is needed to detect calls");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Button on = new Button(this);
        on.setText("Turn on call protection");
        on.setOnClickListener(v -> checkAndStart());

        Button off = new Button(this);
        off.setText("Turn off");
        off.setOnClickListener(v -> {
            stopService(new Intent(this, CallWatcherService.class));
            toast("Call protection off");
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.addView(on);
        root.addView(off);
        setContentView(root);
    }

    /** Check each permission in order, then start the service. */
    private void checkAndStart() {
        // 1. Overlay permission lives in Settings, not a popup
        if (!Settings.canDrawOverlays(this)) {
            overlayLauncher.launch(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        // 2. Normal runtime permissions
        List<String> missing = missingPermissions();
        if (!missing.isEmpty()) {
            permLauncher.launch(missing.toArray(new String[0]));
            return;
        }
        // 3. All granted
        startProtection();
    }

    private List<String> missingPermissions() {
        List<String> wanted = new ArrayList<>();
        wanted.add(Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS);

        List<String> missing = new ArrayList<>();
        for (String p : wanted) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }
        return missing;
    }

    private void startProtection() {
        ContextCompat.startForegroundService(this, new Intent(this, CallWatcherService.class));
        toast("Soteria is on. Make or receive a call to test.");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
