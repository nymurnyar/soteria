package com.example.soteria;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Demo data source. Replace this provider with the FastAPI repository when available. */
public final class CallLogRepository {
    private CallLogRepository() { }

    /** Real protected calls saved on this phone (newest first), then the demo calls. */
    public static List<CallLog> getCalls(android.content.Context context) {
        List<CallLog> calls = new ArrayList<>(CallHistoryStore.load(context));
        calls.addAll(getDemoCalls());
        return calls;
    }

    public static List<CallLog> getDemoCalls() {
        List<CallLog> calls = new ArrayList<>();
        calls.add(call("demo-001", "Alex Morgan", "+1 780 555 0142", 0, "10:42 AM",
                "04:32", CallLog.Risk.SAFE, 0.08, true, CallLog.Direction.INCOMING,
                "Caller: Hi Alex, confirming our project check-in.\nYou: Thanks, that works for me.",
                new String[0]));
        calls.add(call("demo-002", "Unknown Caller", "Private number", 1, "4:18 PM",
                "08:15", CallLog.Risk.FLAGGED, 0.96, false, CallLog.Direction.INCOMING,
                "Caller: I'm calling from your IT department.\nCaller: We need your administrator login to complete an urgent security audit.",
                new String[]{"Possible impersonation", "Artificial urgency", "Credential request"}));
        calls.add(call("demo-003", "Priya Sharma", "+1 780 555 0188", 2, "2:06 PM",
                "02:48", CallLog.Risk.SAFE, 0.05, true, CallLog.Direction.OUTGOING,
                "You: Hi Priya, are we still meeting tomorrow?\nCaller: Yes, see you then.",
                new String[0]));
        calls.add(call("demo-004", "+1 780 555 0123", "+1 780 555 0123", 3, "11:27 AM",
                "06:21", CallLog.Risk.MONITORING, 0.57, false, CallLog.Direction.INCOMING,
                "Caller: I have a quick question about the account.\nSoteria: The caller identity could not be confirmed.",
                new String[]{"Unverified caller identity"}));
        calls.add(call("demo-005", "IT Support", "+1 800 555 0199", 4, "9:03 AM",
                "03:54", CallLog.Risk.FLAGGED, 0.89, false, CallLog.Direction.INCOMING,
                "Caller: Your company account will be disabled today.\nCaller: Read me the one-time code so I can secure it.",
                new String[]{"Possible impersonation", "Artificial urgency", "One-time code request"}));
        calls.add(call("demo-006", "Daniel Wilson", "+1 780 555 0167", 5, "5:51 PM",
                "05:12", CallLog.Risk.SAFE, 0.11, true, CallLog.Direction.INCOMING,
                "Caller: I sent over the notes from this morning.\nYou: Got them, thank you.",
                new String[0]));
        calls.add(call("demo-007", "Unknown Number", "Unknown number", 6, "1:32 PM",
                "01:43", CallLog.Risk.MONITORING, 0.62, false, CallLog.Direction.INCOMING,
                "Caller: Hello, can you hear me?\nSoteria: No verified identity information was available.",
                new String[]{"Unverified caller identity"}));
        calls.add(call("demo-008", "Sarah Thompson", "+1 780 555 0106", 7, "Yesterday, 3:16 PM",
                "07:30", CallLog.Risk.SAFE, 0.04, true, CallLog.Direction.OUTGOING,
                "You: I have a quick update for you.\nCaller: Great, go ahead.",
                new String[0]));
        return calls;
    }

    private static CallLog call(String id, String name, String number, int daysAgo, String time,
                                String duration, CallLog.Risk risk, double score, boolean internal,
                                CallLog.Direction direction, String transcript, String[] threats) {
        Calendar date = Calendar.getInstance();
        date.add(Calendar.DAY_OF_YEAR, -daysAgo);
        String dateText;
        if (daysAgo == 0) {
            dateText = "Today";
        } else if (daysAgo == 1) {
            dateText = "Yesterday";
        } else {
            dateText = new SimpleDateFormat("MMM d", Locale.getDefault()).format(date.getTime());
        }
        return new CallLog(id, name, number, dateText + " · " + time, duration, transcript, score,
                risk, Arrays.asList(threats), internal, direction);
    }
}
