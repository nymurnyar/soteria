package com.example.soteria;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles Answer / Decline / Hang up taps from the call notification. */
public class CallActionReceiver extends BroadcastReceiver {

    static final String ACTION_ANSWER = "com.example.soteria.action.ANSWER";
    static final String ACTION_DECLINE = "com.example.soteria.action.DECLINE";
    static final String ACTION_HANG_UP = "com.example.soteria.action.HANG_UP";

    @Override
    public void onReceive(Context context, Intent intent) {
        CallManager calls = CallManager.get(context);
        String action = intent.getAction();
        if (ACTION_ANSWER.equals(action)) {
            calls.answer();
            context.startActivity(new Intent(context, InCallActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } else if (ACTION_DECLINE.equals(action)) {
            calls.decline();
        } else if (ACTION_HANG_UP.equals(action)) {
            calls.hangUp();
        }
    }
}
