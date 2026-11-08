package com.example.soteria;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telecom.TelecomManager;

/** Becoming the default phone app is what lets Soteria add + merge TrustLine automatically. */
public final class DefaultDialer {

    private DefaultDialer() {
    }

    static boolean isDefault(Context context) {
        TelecomManager tm = context.getSystemService(TelecomManager.class);
        return tm != null && context.getPackageName().equals(tm.getDefaultDialerPackage());
    }

    /** Intent that shows Android's "Set Soteria as your default phone app?" dialog. */
    static Intent requestIntent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = context.getSystemService(RoleManager.class);
            return rm.createRequestRoleIntent(RoleManager.ROLE_DIALER);
        }
        return new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                        context.getPackageName());
    }
}
