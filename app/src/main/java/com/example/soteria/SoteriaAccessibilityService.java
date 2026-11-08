package com.example.soteria;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

/**
 * Does nothing by itself. Android only lets accessibility services capture the microphone while
 * a phone call is active, so having this service switched on is what makes
 * {@link OfflineTranscriber} receive real audio instead of silence during calls.
 */
public class SoteriaAccessibilityService extends AccessibilityService {

    private static volatile boolean connected;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used: Soteria doesn't read the screen.
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        connected = false;
        super.onDestroy();
    }

    /** True when the user has switched Soteria on in Settings › Accessibility. */
    public static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return connected;
        ComponentName me = new ComponentName(context, SoteriaAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        for (String entry : splitter) {
            ComponentName c = ComponentName.unflattenFromString(entry);
            if (me.equals(c)) return true;
        }
        return connected;
    }
}
