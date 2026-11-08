package com.example.soteria;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Launch screen. Demo account only: SoteriaDemo / Sot101. */
public class LoginActivity extends AppCompatActivity {

    // Demo credentials for the hackathon build.
    private static final String DEMO_USERNAME = "SoteriaDemo";
    private static final String DEMO_PASSWORD = "Sot101";

    private TextInputLayout usernameLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private View loginCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_login);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loginRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        usernameLayout = findViewById(R.id.usernameLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginCard = findViewById(R.id.loginCard);
        MaterialButton loginButton = findViewById(R.id.loginButton);

        TextWatcher clearErrors = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                usernameLayout.setError(null);
                passwordLayout.setError(null);
            }
        };
        usernameInput.addTextChangedListener(clearErrors);
        passwordInput.addTextChangedListener(clearErrors);

        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                attemptLogin();
                return true;
            }
            return false;
        });
        loginButton.setOnClickListener(v -> attemptLogin());

        // Gentle entrance.
        loginCard.setAlpha(0f);
        loginCard.setTranslationY(40f);
        loginCard.animate().alpha(1f).translationY(0f).setStartDelay(120).setDuration(450).start();
    }

    private void attemptLogin() {
        String username = text(usernameInput);
        String password = text(passwordInput);

        if (username.isEmpty()) {
            usernameLayout.setError(getString(R.string.error_username_required));
            return;
        }
        if (password.isEmpty()) {
            passwordLayout.setError(getString(R.string.error_password_required));
            return;
        }
        if (!username.equals(DEMO_USERNAME) || !password.equals(DEMO_PASSWORD)) {
            passwordLayout.setError(getString(R.string.error_login_invalid));
            shake(loginCard);
            return;
        }

        hideKeyboard();
        new SessionManager(this).login(username);
        startActivity(new Intent(this, EelIntroActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static void shake(View v) {
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0, 18, -18, 12, -12, 6, -6, 0)
                .setDuration(420)
                .start();
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }
}
