package com.example.soteria;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CallLogsActivity extends AppCompatActivity implements CallLogAdapter.OnCallClickListener {
    private final List<CallLog> allCalls = new ArrayList<>();
    private CallLogAdapter adapter;
    private String selectedFilter = "All";
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_call_logs);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.callLogsRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        RecyclerView list = findViewById(R.id.callList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CallLogAdapter(this);
        list.setAdapter(adapter);
        allCalls.addAll(CallLogRepository.getCalls(this));

        setupFilters();
        EditText search = findViewById(R.id.searchInput);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        applyFilters();
    }

    private void setupFilters() {
        int[] ids = {R.id.filterAll, R.id.filterSafe, R.id.filterMonitoring, R.id.filterFlagged};
        String[] labels = {"All", "Safe", "Monitoring", "Flagged"};
        for (int i = 0; i < ids.length; i++) {
            final String label = labels[i];
            MaterialButton button = findViewById(ids[i]);
            button.setOnClickListener(v -> {
                selectedFilter = label;
                updateFilterAppearance(ids, labels);
                applyFilters();
            });
        }
        updateFilterAppearance(ids, labels);
    }

    private void updateFilterAppearance(int[] ids, String[] labels) {
        for (int i = 0; i < ids.length; i++) {
            MaterialButton button = findViewById(ids[i]);
            boolean selected = labels[i].equals(selectedFilter);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    selected ? 0xFF4285E8 : 0xFFFFFFFF));
            button.setTextColor(selected ? 0xFFFFFFFF : 0xFF475569);
            button.setStrokeColor(android.content.res.ColorStateList.valueOf(
                    selected ? 0xFF4285E8 : 0xFFDCE5F0));
        }
    }

    private void applyFilters() {
        List<CallLog> visible = new ArrayList<>();
        for (CallLog call : allCalls) {
            boolean matchesRisk = "All".equals(selectedFilter)
                    || ("Safe".equals(selectedFilter) && call.getRisk() == CallLog.Risk.SAFE)
                    || ("Monitoring".equals(selectedFilter) && call.getRisk() == CallLog.Risk.MONITORING)
                    || ("Flagged".equals(selectedFilter) && call.getRisk() == CallLog.Risk.FLAGGED);
            String searchable = (call.getDisplayName() + " " + call.getPhoneNumber()).toLowerCase(Locale.ROOT);
            if (matchesRisk && searchable.contains(searchQuery)) visible.add(call);
        }
        adapter.submitList(visible);
        boolean hasResults = !visible.isEmpty();
        TextView empty = findViewById(R.id.emptyResults);
        empty.setVisibility(hasResults ? View.GONE : View.VISIBLE);
        findViewById(R.id.callList).setVisibility(hasResults ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onCallClick(CallLog call) {
        StringBuilder details = new StringBuilder();
        details.append("Caller\n").append(call.getDisplayName()).append(" · ").append(call.getPhoneNumber())
                .append("\n\nDate and time\n").append(call.getTimestamp())
                .append("\n\nDuration\n").append(call.getDuration())
                .append("\n\nCaller identity\n").append(call.isInternalCaller() ? "Internal" : "External")
                .append("\n\nRisk classification\n").append(riskLabel(call.getRisk()))
                .append(String.format(Locale.getDefault(), "  (score %.0f%%)", call.getRiskScore() * 100))
                .append("\n\nSample conversation\n").append(call.getTranscript());

        if (!call.getThreatCategories().isEmpty()) {
            details.append("\n\nDetected warning signals");
            for (String threat : call.getThreatCategories()) details.append("\n• ").append(threat);
        }
        String explanation = call.getRisk() == CallLog.Risk.FLAGGED
                ? "\n\nSoteria flagged this conversation because it included suspicious requests or tactics. Never share passwords, administrator credentials, or one-time codes with an unverified caller."
                : call.getRisk() == CallLog.Risk.MONITORING
                ? "\n\nSoteria could not fully verify this caller. Confirm their identity using a trusted contact method before sharing sensitive information."
                : "\n\nNo significant risk signals were detected in this sample conversation.";
        details.append(explanation);

        TextView content = new TextView(this);
        content.setText(details);
        content.setTextColor(0xFF334155);
        content.setTextSize(14);
        content.setLineSpacing(4, 1);
        content.setPadding(24, 8, 24, 8);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle(call.getDisplayName())
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static String riskLabel(CallLog.Risk risk) {
        switch (risk) {
            case FLAGGED: return "High Risk · Flagged";
            case MONITORING: return "Monitoring";
            default: return "Safe";
        }
    }
}
