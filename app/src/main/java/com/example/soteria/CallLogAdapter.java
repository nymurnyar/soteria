package com.example.soteria;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CallLogAdapter extends RecyclerView.Adapter<CallLogAdapter.CallViewHolder> {
    public interface OnCallClickListener {
        void onCallClick(CallLog call);
    }

    private final List<CallLog> calls = new ArrayList<>();
    private final OnCallClickListener listener;

    public CallLogAdapter(OnCallClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<CallLog> newCalls) {
        calls.clear();
        calls.addAll(newCalls);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_call_log, parent, false);
        return new CallViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        CallLog call = calls.get(position);
        holder.name.setText(call.getDisplayName());
        holder.meta.setText(String.format(Locale.getDefault(), "%s  ·  %s  ·  %s",
                call.getTimestamp(), call.getDuration(),
                call.getDirection() == CallLog.Direction.INCOMING ? "Incoming" : "Outgoing"));
        holder.identity.setText(call.isInternalCaller() ? "INTERNAL" : "EXTERNAL");
        holder.risk.setText(riskLabel(call.getRisk()));
        holder.risk.setTextColor(riskColor(call.getRisk()));
        holder.risk.setBackgroundTintList(android.content.res.ColorStateList.valueOf(riskBackground(call.getRisk())));
        holder.direction.setText(call.getDirection() == CallLog.Direction.INCOMING ? "↓" : "↑");
        holder.direction.setTextColor(call.getDirection() == CallLog.Direction.INCOMING
                ? Color.rgb(66, 133, 232) : Color.rgb(16, 42, 76));
        holder.itemView.setOnClickListener(v -> listener.onCallClick(call));
    }

    @Override
    public int getItemCount() { return calls.size(); }

    private static String riskLabel(CallLog.Risk risk) {
        switch (risk) {
            case FLAGGED: return "Flagged";
            case MONITORING: return "Monitoring";
            default: return "Safe";
        }
    }

    private static int riskColor(CallLog.Risk risk) {
        switch (risk) {
            case FLAGGED: return Color.rgb(185, 28, 28);
            case MONITORING: return Color.rgb(180, 83, 9);
            default: return Color.rgb(21, 128, 61);
        }
    }

    private static int riskBackground(CallLog.Risk risk) {
        switch (risk) {
            case FLAGGED: return Color.rgb(254, 226, 226);
            case MONITORING: return Color.rgb(255, 237, 213);
            default: return Color.rgb(220, 252, 231);
        }
    }

    static class CallViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;
        final TextView identity;
        final TextView risk;
        final TextView direction;

        CallViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.callerName);
            meta = itemView.findViewById(R.id.callMeta);
            identity = itemView.findViewById(R.id.callerIdentity);
            risk = itemView.findViewById(R.id.riskBadge);
            direction = itemView.findViewById(R.id.directionIcon);
        }
    }
}
