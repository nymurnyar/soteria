package com.example.soteria;

import android.os.Bundle;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;

/** Base for screens that are empty for now: shows a toolbar with a title and a back arrow. */
public abstract class PlaceholderActivity extends AppCompatActivity {

    @StringRes
    protected abstract int titleRes();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_placeholder);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.placeholderRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(titleRes());
        toolbar.setNavigationOnClickListener(v -> finish());
    }
}
