package com.example.soteria;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/** Lists suspicious calls (Flagged / Monitoring) and lets the user review or dismiss them. */
public class AlertsActivity extends AppCompatActivity implements AlertAdapter.Listener {

    private static final String STATE_SHOW_OPEN = "show_open";

    private static final int NAVY = 0xFF0B2C5E;
    private static final int BLUE = 0xFF3F8F50;
    private static final int RED = 0xFFDC2626;
    private static final int AMBER = 0xFFF59E0B;
    private static final int GREEN = 0xFF15803D;

    private AlertStore store;
    private AlertAdapter adapter;
    private List<CallLog> calls;
    private boolean showOpen = true;

    private View root;
    private RecyclerView list;
    private View emptyState;
    private MaterialButton tabOpen;
    private MaterialButton tabResolved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_alerts);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        root = findViewById(R.id.alertsRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        if (savedInstanceState != null) {
            showOpen = savedInstanceState.getBoolean(STATE_SHOW_OPEN, true);
        }

        store = new AlertStore(this);
        calls = CallLogRepository.getCalls(this);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        list = findViewById(R.id.alertList);
        emptyState = findViewById(R.id.emptyState);
        tabOpen = findViewById(R.id.tabOpen);
        tabResolved = findViewById(R.id.tabResolved);

        adapter = new AlertAdapter(store, this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        tabOpen.setOnClickListener(v -> selectTab(true));
        tabResolved.setOnClickListener(v -> selectTab(false));

        refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SHOW_OPEN, showOpen);
    }

    private void selectTab(boolean open) {
        if (showOpen == open) return;
        showOpen = open;
        refresh();
        list.scrollToPosition(0);
    }

    /** Recomputes counts, tabs and the visible list from the store. */
    private void refresh() {
        List<CallLog> open = store.alerts(calls, true);
        List<CallLog> resolved = store.alerts(calls, false);

        int high = 0;
        int review = 0;
        for (CallLog call : open) {
            if (call.getRisk() == CallLog.Risk.FLAGGED) high++; else review++;
        }
        bindStat(R.id.statHighRisk, high, R.string.alerts_stat_high, RED, 0xFFB91C1C);
        bindStat(R.id.statNeedsReview, review, R.string.alerts_stat_review, AMBER, 0xFFB45309);
        bindStat(R.id.statResolved, resolved.size(), R.string.alerts_stat_resolved, GREEN, GREEN);

        tabOpen.setText(getString(R.string.alerts_tab_open, open.size()));
        tabResolved.setText(getString(R.string.alerts_tab_resolved, resolved.size()));
        styleTab(tabOpen, showOpen);
        styleTab(tabResolved, !showOpen);

        List<CallLog> visible = showOpen ? open : resolved;
        adapter.submitList(visible);
        boolean empty = visible.isEmpty();
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.emptyTitle)).setText(
                showOpen ? R.string.alerts_empty_open_title : R.string.alerts_empty_resolved_title);
        ((TextView) findViewById(R.id.emptySubtitle)).setText(
                showOpen ? R.string.alerts_empty_open_body : R.string.alerts_empty_resolved_body);
    }

    private void bindStat(int includeId, int value, @StringRes int label,
                          @ColorInt int dotColor, @ColorInt int valueColor) {
        View card = findViewById(includeId);
        TextView valueView = card.findViewById(R.id.statValue);
        valueView.setText(String.valueOf(value));
        valueView.setTextColor(value == 0 ? NAVY : valueColor);
        ((TextView) card.findViewById(R.id.statLabel)).setText(label);
        card.findViewById(R.id.statDot).setBackgroundTintList(ColorStateList.valueOf(dotColor));
    }

    private static void styleTab(MaterialButton tab, boolean selected) {
        tab.setBackgroundTintList(ColorStateList.valueOf(selected ? BLUE : 0xFFFFFFFF));
        tab.setTextColor(selected ? 0xFFFFFFFF : 0xFF475569);
        tab.setStrokeColor(ColorStateList.valueOf(selected ? BLUE : 0xFFD6E6D3));
        tab.setSelected(selected);
    }

    // ---- Actions ----------------------------------------------------------------------------

    @Override
    public void onResolve(CallLog call) {
        setStatusWithUndo(call, AlertStore.Status.REVIEWED, R.string.alerts_snack_reviewed);
    }

    @Override
    public void onReopen(CallLog call) {
        setStatusWithUndo(call, AlertStore.Status.OPEN, R.string.alerts_snack_reopened);
    }

    private void setStatusWithUndo(CallLog call, AlertStore.Status status, @StringRes int message) {
        AlertStore.Status previous = store.getStatus(call);
        store.setStatus(call, status);
        refresh();
        Snackbar.make(root, message, Snackbar.LENGTH_LONG)
                .setActionTextColor(0xFFA8D5A0)
                .setAction(R.string.alerts_action_undo, v -> {
                    store.setStatus(call, previous);
                    refresh();
                })
                .show();
    }

    @Override
    public void onDetails(CallLog call) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.sheet_alert_detail, null);
        sheet.setContentView(view);
        // Open fully so the action buttons are reachable without dragging.
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);

        AlertAdapter.Severity sev = AlertAdapter.Severity.of(call);
        AlertStore.Status status = store.getStatus(call);
        boolean open = status == AlertStore.Status.OPEN;
        int percent = (int) Math.round(call.getRiskScore() * 100);

        AlertAdapter.stylePill(view.findViewById(R.id.sheetSeverity),
                getString(sev.label), sev.text, sev.background);
        ((TextView) view.findViewById(R.id.sheetTime)).setText(call.getTimestamp());
        ((TextView) view.findViewById(R.id.sheetCaller)).setText(call.getDisplayName());
        ((TextView) view.findViewById(R.id.sheetNumber)).setText(call.getPhoneNumber());

        LinearLayout facts = view.findViewById(R.id.sheetFacts);
        addFact(facts, R.string.alerts_fact_direction, getString(
                call.getDirection() == CallLog.Direction.INCOMING ? R.string.alerts_incoming : R.string.alerts_outgoing));
        addFact(facts, R.string.alerts_fact_duration, call.getDuration());
        addFact(facts, R.string.alerts_fact_identity, getString(
                call.isInternalCaller() ? R.string.alerts_internal : R.string.alerts_external));
        addFact(facts, R.string.alerts_fact_risk, percent + "%");
        addFact(facts, R.string.alerts_fact_status, getString(statusLabel(status)));

        StringBuilder why = new StringBuilder();
        for (String signal : call.getThreatCategories()) {
            if (why.length() > 0) why.append('\n');
            why.append("•  ").append(signal);
        }
        if (why.length() == 0) why.append("•  ").append(getString(R.string.alerts_why_unverified));
        ((TextView) view.findViewById(R.id.sheetWhy)).setText(why);
        ((TextView) view.findViewById(R.id.sheetTodo)).setText(
                sev == AlertAdapter.Severity.HIGH ? R.string.alerts_todo_high : R.string.alerts_todo_review);
        ((TextView) view.findViewById(R.id.sheetTranscript)).setText(call.getTranscript());

        MaterialButton secondary = view.findViewById(R.id.sheetSecondary);
        MaterialButton primary = view.findViewById(R.id.sheetPrimary);
        primary.setBackgroundTintList(ColorStateList.valueOf(BLUE));
        primary.setTextColor(0xFFFFFFFF);
        secondary.setTextColor(NAVY);
        secondary.setStrokeColor(ColorStateList.valueOf(0xFFD6E6D3));
        if (open) {
            secondary.setText(R.string.alerts_action_false_alarm);
            secondary.setOnClickListener(v -> {
                sheet.dismiss();
                setStatusWithUndo(call, AlertStore.Status.FALSE_ALARM, R.string.alerts_snack_false_alarm);
            });
            primary.setText(R.string.alerts_action_review);
            primary.setOnClickListener(v -> {
                sheet.dismiss();
                onResolve(call);
            });
        } else {
            secondary.setText(R.string.alerts_action_close);
            secondary.setOnClickListener(v -> sheet.dismiss());
            primary.setText(R.string.alerts_action_reopen);
            primary.setOnClickListener(v -> {
                sheet.dismiss();
                onReopen(call);
            });
        }
        sheet.show();
    }

    private void addFact(LinearLayout container, @StringRes int label, String value) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Math.round(8 * density);
        row.setPadding(0, pad, 0, pad);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(0xFF5E6E84);
        labelView.setTextSize(13);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(NAVY);
        valueView.setTextSize(13);
        valueView.setTypeface(valueView.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(valueView);

        container.addView(row);
    }

    @StringRes
    private static int statusLabel(AlertStore.Status status) {
        switch (status) {
            case REVIEWED: return R.string.alerts_status_reviewed_label;
            case FALSE_ALARM: return R.string.alerts_status_false_alarm_label;
            default: return R.string.alerts_status_open;
        }
    }
}
