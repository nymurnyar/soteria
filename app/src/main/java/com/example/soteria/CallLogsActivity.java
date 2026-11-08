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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload every time: a call may have just been saved (or its final AI result updated).
        allCalls.clear();
        allCalls.addAll(CallLogRepository.getCalls(this));
        bindStats();
        applyFilters();
    }

    /** Summary cards: real counts from the saved calls (plus the samples). */
    private void bindStats() {
        int safe = 0, flagged = 0, monitoring = 0;
        for (CallLog call : allCalls) {
            switch (call.getRisk()) {
                case FLAGGED: flagged++; break;
                case MONITORING: monitoring++; break;
                default: safe++;
            }
        }
        ((TextView) findViewById(R.id.statTotal)).setText(String.valueOf(allCalls.size()));
        ((TextView) findViewById(R.id.statSafe)).setText(String.valueOf(safe));
        ((TextView) findViewById(R.id.statFlagged)).setText(String.valueOf(flagged));
        ((TextView) findViewById(R.id.statMonitoring)).setText(String.valueOf(monitoring));
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
                    selected ? 0xFF3F8F50 : 0xFFFFFFFF));
            button.setTextColor(selected ? 0xFFFFFFFF : 0xFF475569);
            button.setStrokeColor(android.content.res.ColorStateList.valueOf(
                    selected ? 0xFF3F8F50 : 0xFFD6E6D3));
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
        boolean sample = CallLogRepository.isSample(call);
        StringBuilder details = new StringBuilder();
        if (sample) details.append("Sample call (built-in example, not a real call)\n\n");
        String number = call.getPhoneNumber();
        details.append("Caller\n").append(call.getDisplayName())
                .append(number.isEmpty() || number.equals(call.getDisplayName()) ? "" : " · " + number)
                .append("\n\nDate and time\n").append(call.getTimestamp())
                .append("\n\nDuration\n").append(call.getDuration())
                .append("\n\nCaller identity\n").append(sample
                        ? (call.isInternalCaller() ? "Internal" : "External")
                        : "Not verified (caller verification is pending)")
                .append("\n\nRisk classification\n").append(riskLabel(call.getRisk()))
                .append(String.format(Locale.getDefault(), "  (score %.0f%%)", call.getRiskScore() * 100))
                .append(sample ? "\n\nSample conversation\n" : "\n\nTranscript\n")
                .append(call.getTranscript().isEmpty() ? "(nothing transcribed)" : call.getTranscript());

        if (!call.getThreatCategories().isEmpty()) {
            details.append("\n\nDetected warning signals");
            for (String threat : call.getThreatCategories()) details.append("\n• ").append(threat);
        }
        String explanation = call.getRisk() == CallLog.Risk.FLAGGED
                ? "\n\nSoteria flagged this conversation because it included suspicious requests or tactics. Never share passwords, administrator credentials, or one-time codes with an unverified caller."
                : call.getRisk() == CallLog.Risk.MONITORING
                ? "\n\nSoteria could not fully verify this caller. Confirm their identity using a trusted contact method before sharing sensitive information."
                : "\n\nNo significant risk signals were detected in this conversation.";
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
