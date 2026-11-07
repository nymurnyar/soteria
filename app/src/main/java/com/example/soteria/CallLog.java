package com.example.soteria;

import java.util.List;

/** A displayable call record, ready to be populated from the backend later. */
public class CallLog {
    public enum Risk { SAFE, MONITORING, FLAGGED }
    public enum Direction { INCOMING, OUTGOING }

    private final String callId;
    private final String callerName;
    private final String phoneNumber;
    private final String timestamp;
    private final String duration;
    private final String transcript;
    private final double riskScore;
    private final Risk risk;
    private final List<String> threatCategories;
    private final boolean internalCaller;
    private final Direction direction;

    public CallLog(String callId, String callerName, String phoneNumber, String timestamp,
                   String duration, String transcript, double riskScore, Risk risk,
                   List<String> threatCategories, boolean internalCaller, Direction direction) {
        this.callId = callId;
        this.callerName = callerName;
        this.phoneNumber = phoneNumber;
        this.timestamp = timestamp;
        this.duration = duration;
        this.transcript = transcript;
        this.riskScore = riskScore;
        this.risk = risk;
        this.threatCategories = threatCategories;
        this.internalCaller = internalCaller;
        this.direction = direction;
    }

    public String getCallId() { return callId; }
    public String getCallerName() { return callerName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getTimestamp() { return timestamp; }
    public String getDuration() { return duration; }
    public String getTranscript() { return transcript; }
    public double getRiskScore() { return riskScore; }
    public Risk getRisk() { return risk; }
    public List<String> getThreatCategories() { return threatCategories; }
    public boolean isInternalCaller() { return internalCaller; }
    public Direction getDirection() { return direction; }

    public String getDisplayName() {
        return callerName == null || callerName.isEmpty() ? phoneNumber : callerName;
    }
}
