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
    private static final int NOTIFICATION_ID = 1001;

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
        notify(context, builder);
    }

    static void showOngoing(Context context, String caller, String status) {
        if (!canNotify(context)) return;
        ensureChannels(context);
        // Not CallStyle: Android only allows ongoing CallStyle from a foreground service.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ONGOING)
                .setSmallIcon(R.drawable.ic_shield_check)
                .setContentTitle(caller)
                .setContentText(status)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setUsesChronometer(true)
                .setContentIntent(openUi(context))
                .addAction(R.drawable.ic_call_end, context.getString(R.string.call_hang_up),
                        action(context, CallActionReceiver.ACTION_HANG_UP, 3));
        notify(context, builder);
    }

    static void cancel(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
    }

    private static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("MissingPermission") // checked in canNotify()
    private static void notify(Context context, NotificationCompat.Builder builder) {
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
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
        if (manager == null || manager.getNotificationChannel(CHANNEL_INCOMING) != null) return;
        NotificationChannel incoming = new NotificationChannel(CHANNEL_INCOMING,
                context.getString(R.string.channel_incoming), NotificationManager.IMPORTANCE_HIGH);
        incoming.setSound(null, null); // Telecom already plays the ringtone
        manager.createNotificationChannel(incoming);
        NotificationChannel ongoing = new NotificationChannel(CHANNEL_ONGOING,
                context.getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(ongoing);
    }
}
