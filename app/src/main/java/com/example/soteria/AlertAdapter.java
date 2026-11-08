package com.example.soteria;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.AlertViewHolder> {

    public interface Listener {
        void onDetails(CallLog call);
        void onResolve(CallLog call);
        void onReopen(CallLog call);
    }

    private final List<CallLog> alerts = new ArrayList<>();
    private final AlertStore store;
    private final Listener listener;

    public AlertAdapter(AlertStore store, Listener listener) {
        this.store = store;
        this.listener = listener;
    }

    public void submitList(List<CallLog> newAlerts) {
        alerts.clear();
        alerts.addAll(newAlerts);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alert, parent, false);
        return new AlertViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertViewHolder h, int position) {
        CallLog call = alerts.get(position);
        Context ctx = h.itemView.getContext();
        AlertStore.Status status = store.getStatus(call);
        boolean open = status == AlertStore.Status.OPEN;
        Severity sev = Severity.of(call);

        h.accent.setBackgroundColor(open ? sev.accent : 0xFFCBD5E1);
        stylePill(h.severity, ctx.getString(sev.label), sev.text, sev.background);

        if (open) {
            h.status.setVisibility(View.GONE);
        } else {
            boolean falseAlarm = status == AlertStore.Status.FALSE_ALARM;
            stylePill(h.status,
                    ctx.getString(falseAlarm ? R.string.alerts_status_false_alarm : R.string.alerts_status_reviewed),
                    falseAlarm ? 0xFF475569 : 0xFF15803D,
                    falseAlarm ? 0xFFF1F5F9 : 0xFFDCFCE7);
            h.status.setVisibility(View.VISIBLE);
        }

        h.time.setText(call.getTimestamp());
        h.caller.setText(call.getDisplayName());
        String direction = ctx.getString(call.getDirection() == CallLog.Direction.INCOMING
                ? R.string.alerts_incoming : R.string.alerts_outgoing);
        // Skip the number when it's already shown as the name (unknown callers).
        String prefix = call.getPhoneNumber().equals(call.getDisplayName())
                ? "" : call.getPhoneNumber() + "  ·  ";
        h.number.setText(prefix + direction + "  ·  " + call.getDuration());

        int percent = (int) Math.round(call.getRiskScore() * 100);
        h.riskBar.setProgressCompat(percent, false);
        h.riskBar.setIndicatorColor(sev.accent);
        h.riskBar.setTrackColor(0xFFEEF2F7);
        h.riskPercent.setText(percent + "%");
        h.riskPercent.setTextColor(sev.text);

        h.chips.removeAllViews();
        for (String signal : call.getThreatCategories()) {
            h.chips.addView(makeChip(ctx, signal));
        }
        h.chips.setVisibility(call.getThreatCategories().isEmpty() ? View.GONE : View.VISIBLE);

        if (open) {
            h.resolve.setText(R.string.alerts_action_review);
            h.resolve.setOnClickListener(v -> listener.onResolve(call));
        } else {
            h.resolve.setText(R.string.alerts_action_reopen);
            h.resolve.setOnClickListener(v -> listener.onReopen(call));
        }
        h.resolve.setTextColor(0xFF102A4C);
        h.details.setOnClickListener(v -> listener.onDetails(call));
        h.itemView.setOnClickListener(v -> listener.onDetails(call));
        h.itemView.setContentDescription(ctx.getString(sev.label) + ". " + call.getDisplayName()
                + ". " + call.getTimestamp() + ". " + ctx.getString(R.string.alerts_risk_score)
                + " " + percent + "%");
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    static void stylePill(TextView pill, String text, int textColor, int background) {
        pill.setText(text);
        pill.setTextColor(textColor);
        pill.setBackgroundTintList(ColorStateList.valueOf(background));
    }

    private static Chip makeChip(Context ctx, String text) {
        float density = ctx.getResources().getDisplayMetrics().density;
        Chip chip = new Chip(ctx);
        chip.setText(text);
        chip.setClickable(false);
        chip.setFocusable(false);
        chip.setCheckable(false);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipMinHeight(28 * density);
        chip.setTextSize(12);
        chip.setTextColor(0xFF334155);
        chip.setChipBackgroundColor(ColorStateList.valueOf(0xFFF1F5F9));
        chip.setChipStrokeWidth(0);
        return chip;
    }

    /** Colours and label for each alert severity. */
    enum Severity {
        HIGH(R.string.alerts_severity_high, 0xFFB91C1C, 0xFFFEE2E2, 0xFFDC2626),
        REVIEW(R.string.alerts_severity_review, 0xFFB45309, 0xFFFFEDD5, 0xFFF59E0B);

        final int label;
        final int text;
        final int background;
        final int accent;

        Severity(int label, int text, int background, int accent) {
            this.label = label;
            this.text = text;
            this.background = background;
            this.accent = accent;
        }

        static Severity of(CallLog call) {
            return call.getRisk() == CallLog.Risk.FLAGGED ? HIGH : REVIEW;
        }
    }

    static class AlertViewHolder extends RecyclerView.ViewHolder {
        final View accent;
        final TextView severity;
        final TextView status;
        final TextView time;
        final TextView caller;
        final TextView number;
        final LinearProgressIndicator riskBar;
        final TextView riskPercent;
        final ChipGroup chips;
        final MaterialButton details;
        final MaterialButton resolve;

        AlertViewHolder(@NonNull View v) {
            super(v);
            accent = v.findViewById(R.id.accent);
            severity = v.findViewById(R.id.severityPill);
            status = v.findViewById(R.id.statusPill);
            time = v.findViewById(R.id.alertTime);
            caller = v.findViewById(R.id.alertCaller);
            number = v.findViewById(R.id.alertNumber);
            riskBar = v.findViewById(R.id.riskBar);
            riskPercent = v.findViewById(R.id.riskPercent);
            chips = v.findViewById(R.id.signalChips);
            details = v.findViewById(R.id.detailsButton);
            resolve = v.findViewById(R.id.resolveButton);
        }
    }
}
