package com.example.soteria;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * Background service that:
 *  1. stays alive (foreground notification)
 *  2. listens for call state changes
 *  3. owns the overlay + session timer and wires them together
 */
public class CallWatcherService extends Service {

    private static final String CHANNEL_ID = "soteria_status";
    private static final int NOTIF_ID = 1;

    private TelephonyManager telephony;
    private OverlayManager overlay;
    private CallSessionTimer session;
    private Object callListener;   // TelephonyCallback (API 31+) or PhoneStateListener

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();

        // Overlay buttons -> session actions. Session events -> overlay UI.
        overlay = new OverlayManager(this, new OverlayManager.Actions() {
            @Override public void onStart(int minutes)  { session.start(minutes); }
            @Override public void onExtend(int minutes) { session.extend(minutes); }
            @Override public void onEnd()               { session.end(); }
        });
        session = new CallSessionTimer(overlay);

        telephony = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        try {
            registerCallListener();
        } catch (SecurityException e) {
            // READ_PHONE_STATE not granted: nothing we can do, so stop.
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        unregisterCallListener();
        if (session != null) session.end();
        if (overlay != null) overlay.hide();
        super.onDestroy();
    }

    // ---------- Call detection ----------

    private void handleCallState(int state) {
        if (!Settings.canDrawOverlays(this)) return;
        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
            case TelephonyManager.CALL_STATE_OFFHOOK:
                if (!session.isActive()) overlay.showPrompt();
                break;
            case TelephonyManager.CALL_STATE_IDLE:
                session.end();      // call is over: stop monitoring immediately
                overlay.hide();
                break;
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private class CallStateCallback extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            handleCallState(state);
        }
    }

    @SuppressWarnings("deprecation")
    private void registerCallListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            CallStateCallback cb = new CallStateCallback();
            telephony.registerTelephonyCallback(ContextCompat.getMainExecutor(this), cb);
            callListener = cb;
        } else {
            PhoneStateListener listener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String phoneNumber) {
                    handleCallState(state);
                }
            };
            telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
            callListener = listener;
        }
    }

    @SuppressWarnings("deprecation")
    private void unregisterCallListener() {
        if (callListener == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && callListener instanceof TelephonyCallback) {
            telephony.unregisterTelephonyCallback((TelephonyCallback) callListener);
        } else if (callListener instanceof PhoneStateListener) {
            telephony.listen((PhoneStateListener) callListener, PhoneStateListener.LISTEN_NONE);
        }
        callListener = null;
    }

    // ---------- Foreground notification ----------

    private void startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Protection status", NotificationManager.IMPORTANCE_LOW));
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Soteria is on")
                .setContentText("You'll be offered protection when a call starts")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }
}
