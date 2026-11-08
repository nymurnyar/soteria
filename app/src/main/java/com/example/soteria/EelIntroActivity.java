package com.example.soteria;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

/** 3 second eel-and-hook logo animation shown after every login. Tap anywhere to skip. */
public class EelIntroActivity extends AppCompatActivity {

    private boolean leaving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_eel_intro);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);

        EelLogoView eel = findViewById(R.id.eelLogo);
        View title = findViewById(R.id.introTitle);
        View tagline = findViewById(R.id.introTagline);

        eel.setListener(this::goHome);
        findViewById(R.id.introRoot).setOnClickListener(v -> goHome());

        // Wordmark fades up while the eel settles back into the S.
        for (View v : new View[]{title, tagline}) {
            v.setAlpha(0f);
            v.setTranslationY(24f);
        }
        title.animate().alpha(1f).translationY(0f).setStartDelay(2250).setDuration(450).start();
        tagline.animate().alpha(1f).translationY(0f).setStartDelay(2400).setDuration(450).start();

        eel.play();
    }

    private void goHome() {
        if (leaving) return;
        leaving = true;
        ((EelLogoView) findViewById(R.id.eelLogo)).cancel();
        startActivity(new Intent(this, HomeActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
