package com.example.soteria;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.content.ContextCompat;

/** Incoming-call (full-screen) and ongoing-call notifications. */
final class CallNotifications {

    private static final String CHANNEL_INCOMING = "incoming_calls";
    private static final String CHANNEL_ONGOING = "ongoing_calls";
    private static final String CHANNEL_SCAM = "scam_alerts";
    private static final int NOTIFICATION_ID = 1001;
    private static final int SCAM_NOTIFICATION_ID = 1002;

    private CallNotifications() { }

    static void showIncoming(Context context, String caller) {
        if (!canNotify(context)) return;
        ensureChannels(context);
        Person person = new Person.Builder().setName(caller).setImportant(true).build();
        PendingIntent fullScreen = openUi(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_INCOMING)
                .setSmallIcon(R.drawable.ic_shield_check)
                .setContentTitle(caller)
                .setContentText(context.getString(R.string.call_state_incoming))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setContentIntent(fullScreen)
                .setFullScreenIntent(fullScreen, true)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(person,
                        action(context, CallActionReceiver.ACTION_DECLINE, 1),
                        action(context, CallActionReceiver.ACTION_ANSWER, 2)));
        notify(context, NOTIFICATION_ID, builder);
    }

    /**
     * Ongoing-call notification with the live transcript. Silent channel + onlyAlertOnce, so
     * frequent updates never buzz; CallManager throttles and skips identical updates.
     *
     * @param transcript latest lines (may be empty)
     * @param riskScore  0-100 when Soteria warns about this call, or -1 for no warning
     * @param connectedAtMs call start (wall clock) so the timer doesn't reset on each update
     */
    static void showOngoing(Context context, String caller, String status, String transcript,
                            int riskScore, String riskSummary, long connectedAtMs) {
        if (!canNotify(context)) return;
        ensureChannels(context);
        boolean risky = riskScore >= 0;
        String title = risky ? context.getString(R.string.notif_risk_title, riskScore, caller) : caller;
        String text = risky && !riskSummary.isEmpty() ? riskSummary
                : !transcript.isEmpty() ? lastLine(transcript) : status;
        StringBuilder big = new StringBuilder();
        if (risky && !riskSummary.isEmpty()) big.append(riskSummary).append("\n\n");
        if (!transcript.isEmpty()) big.append(transcript); else big.append(status);

        // Not CallStyle: Android only allows ongoing CallStyle from a foreground service.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ONGOING)
                .setSmallIcon(risky ? R.drawable.ic_warning : R.drawable.ic_shield_check)
                .setColor(risky ? 0xFFDC2626 : 0xFF3F8F50)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(status)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(big))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setContentIntent(openUi(context))
                .addAction(R.drawable.ic_call_end, context.getString(R.string.call_hang_up),
                        action(context, CallActionReceiver.ACTION_HANG_UP, 3));
        if (connectedAtMs > 0) builder.setWhen(connectedAtMs);
        notify(context, NOTIFICATION_ID, builder);
    }

    /**
     * Pops up (heads-up) once when a call crosses the scam threshold, so the user sees it even
     * outside Soteria or with the screen off. Vibration/sound come from CallManager per Settings.
     */
    static void showScamAlert(Context context, String caller, int riskScore, String summary,
                              java.util.List<String> signs) {
        if (!canNotify(context)) return;
        ensureChannels(context);
        StringBuilder big = new StringBuilder(summary);
        for (String sign : signs) big.append(big.length() > 0 ? "\n• " : "• ").append(sign);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SCAM)
                .setSmallIcon(R.drawable.ic_warning)
                .setColor(0xFFDC2626)
                .setContentTitle(context.getString(R.string.notif_risk_title, riskScore, caller))
                .setContentText(summary.isEmpty() ? context.getString(R.string.notif_risk_text) : summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(big))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openUi(context));
        notify(context, SCAM_NOTIFICATION_ID, builder);
    }

    static void cancel(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
        NotificationManagerCompat.from(context).cancel(SCAM_NOTIFICATION_ID);
    }

    /**
     * Last {@code lines} lines of {@code text}, at most {@code maxChars} long ("…" prefix if cut).
     * Scans back from the end without splitting, so it stays cheap on long calls.
     */
    static String tailLines(String text, int lines, int maxChars) {
        int start = text.length();
        for (int i = 0; i < lines; i++) {
            int nl = text.lastIndexOf('\n', start - 2);
            if (nl < 0) {
                start = 0;
                break;
            }
            start = nl + 1;
        }
        String tail = text.substring(start).trim();
        return tail.length() > maxChars ? "…" + tail.substring(tail.length() - maxChars) : tail;
    }

    private static String lastLine(String text) {
        int i = text.lastIndexOf('\n');
        return i >= 0 ? text.substring(i + 1) : text;
    }

    private static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("MissingPermission") // checked in canNotify()
    private static void notify(Context context, int id, NotificationCompat.Builder builder) {
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (RuntimeException e) {
            // Never let a notification problem take down call handling.
            android.util.Log.w("SoteriaCalls", "Couldn't post call notification", e);
        }
    }

    private static PendingIntent openUi(Context context) {
        Intent intent = new Intent(context, InCallActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent action(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, CallActionReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        // Each channel is checked on its own so channels added in app updates get created too.
        if (manager.getNotificationChannel(CHANNEL_INCOMING) == null) {
            NotificationChannel incoming = new NotificationChannel(CHANNEL_INCOMING,
                    context.getString(R.string.channel_incoming), NotificationManager.IMPORTANCE_HIGH);
            incoming.setSound(null, null); // Telecom already plays the ringtone
            manager.createNotificationChannel(incoming);
        }
        if (manager.getNotificationChannel(CHANNEL_ONGOING) == null) {
            NotificationChannel ongoing = new NotificationChannel(CHANNEL_ONGOING,
                    context.getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(ongoing);
        }
        if (manager.getNotificationChannel(CHANNEL_SCAM) == null) {
            NotificationChannel scam = new NotificationChannel(CHANNEL_SCAM,
                    context.getString(R.string.channel_scam), NotificationManager.IMPORTANCE_HIGH);
            scam.setSound(null, null);   // sound/vibration follow Soteria's own Settings
            scam.enableVibration(false);
            manager.createNotificationChannel(scam);
        }
    }
}
