package com.example.soteria;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/** The live ongoing-call notification and the scam pop-up, posted for real on the device. */
@RunWith(AndroidJUnit4.class)
public class CallNotificationsTest {

    private Context context;
    private NotificationManager manager;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        manager = context.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @After
    public void tearDown() {
        CallNotifications.cancel(context);
    }

    @Test
    public void ongoing_showsLiveTranscript_andKeepsCallStartTime() throws Exception {
        long start = System.currentTimeMillis() - 65_000;
        CallNotifications.showOngoing(context, "Alex", "Protected · Offline transcription",
                "Hi Alex\nAre we still on for lunch?", -1, "", start);
        Notification n = waitFor(1001);
        assertNotNull(n);
        assertEquals("Alex", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals("Are we still on for lunch?", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("Hi Alex"));
        assertEquals(start, n.when); // timer counts from the call start, not from each update
        assertTrue((n.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertTrue((n.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);

        // A live update replaces the same notification (no new one, no buzz).
        CallNotifications.showOngoing(context, "Alex", "Protected · Offline transcription",
                "Are we still on for lunch?\nYes, at noon", -1, "", start);
        Thread.sleep(300);
        assertEquals("Yes, at noon", waitFor(1001).extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertEquals(1, Arrays.stream(manager.getActiveNotifications()).filter(s -> s.getId() == 1001).count());
    }

    @Test
    public void ongoing_turnsIntoWarning_whenRisky() throws Exception {
        CallNotifications.showOngoing(context, "Unknown", "Protected · Offline transcription",
                "This is the CRA\nPay with gift cards now", 92, "Likely CRA gift card scam.",
                System.currentTimeMillis());
        Notification n = waitFor(1001);
        assertNotNull(n);
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("92%"));
        assertEquals("Likely CRA gift card scam.", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertEquals(0xFFDC2626, n.color);
    }

    @Test
    public void scamAlert_popsUpOnHighImportanceChannel() throws Exception {
        CallNotifications.showScamAlert(context, "Unknown", 95, "Caller demands gift cards.",
                Arrays.asList("Gift card payment", "Arrest threat"));
        Notification n = waitFor(1002);
        assertNotNull(n);
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("Arrest threat"));
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = manager.getNotificationChannel(n.getChannelId());
            assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.getImportance()); // heads-up
        }
        CallNotifications.cancel(context);
        Thread.sleep(300);
        assertNull(find(1002));
        assertNull(find(1001));
    }

    @Nullable
    private Notification waitFor(int id) throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            Notification n = find(id);
            if (n != null) return n;
            Thread.sleep(100);
        }
        return null;
    }

    @Nullable
    private Notification find(int id) {
        for (StatusBarNotification sbn : manager.getActiveNotifications()) {
            if (sbn.getId() == id) return sbn.getNotification();
        }
        return null;
    }
}
