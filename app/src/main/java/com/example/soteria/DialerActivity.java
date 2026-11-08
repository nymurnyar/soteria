package com.example.soteria;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Soteria's dial pad. Required for Soteria to be the default phone app
 * (handles ACTION_DIAL / tel: links). Also where the user turns that role on.
 */
public class DialerActivity extends AppCompatActivity {

    private EditText numberInput;
    private Button setDefault;

    private final ActivityResultLauncher<Intent> roleRequest =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    r -> refreshDefaultButton());

    private final ActivityResultLauncher<String[]> permissionRequest =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    r -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialer);

        numberInput = findViewById(R.id.dial_number);
        setDefault = findViewById(R.id.btn_set_default);
        Button call = findViewById(R.id.btn_call);

        Uri data = getIntent() == null ? null : getIntent().getData();
        if (data != null && "tel".equals(data.getScheme())) {
            numberInput.setText(data.getSchemeSpecificPart());
        }

        call.setOnClickListener(v -> placeCall());
        setDefault.setOnClickListener(v -> roleRequest.launch(DefaultDialer.requestIntent(this)));

        permissionRequest.launch(new String[]{
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.POST_NOTIFICATIONS});
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDefaultButton();
    }

    private void refreshDefaultButton() {
        setDefault.setVisibility(DefaultDialer.isDefault(this) ? View.GONE : View.VISIBLE);
    }

    private void placeCall() {
        String number = numberInput.getText().toString().trim();
        if (TextUtils.isEmpty(number)) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionRequest.launch(new String[]{Manifest.permission.CALL_PHONE});
            return;
        }
        try {
            getSystemService(TelecomManager.class)
                    .placeCall(Uri.fromParts("tel", number, null), new Bundle());
        } catch (SecurityException e) {
            Toast.makeText(this, "Phone permission needed", Toast.LENGTH_SHORT).show();
        }
    }
}
