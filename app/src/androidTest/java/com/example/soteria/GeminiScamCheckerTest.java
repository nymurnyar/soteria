package com.example.soteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Calls the real Gemini API from the device with the key built in from .env.
 * Needs internet on the device/emulator. Run: ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4.class)
public class GeminiScamCheckerTest {

    @Test
    public void keyIsBuiltIn() {
        assertTrue("GEMINI_API_KEY missing: add it to .env and rebuild", GeminiScamChecker.isConfigured());
    }

    @Test
    public void scamCall_isFlagged() throws Exception {
        LocalRiskChecker.Result r = GeminiScamChecker.checkBlocking(
                "This is the Canada Revenue Agency.\n"
                        + "You must pay with gift cards right now or the police will arrest you.");
        Log.i("GeminiTest", "scam -> " + r.score + " " + r.signs + " " + r.summary);
        assertTrue(r.isScam);
        assertTrue(r.score >= 70);
        assertFalse(r.signs.isEmpty());
    }

    @Test
    public void normalCall_isSafe() throws Exception {
        LocalRiskChecker.Result r = GeminiScamChecker.checkBlocking(
                "Hi Sam, are we still on for lunch tomorrow?\nYes, see you at noon at the usual place.");
        Log.i("GeminiTest", "normal -> " + r.score + " " + r.summary);
        assertFalse(r.isScam);
        assertTrue(r.score < 30);
    }

    /** Speech-to-text style input (no punctuation, small mistakes) still gets flagged. */
    @Test
    public void messyTranscript_isFlagged() throws Exception {
        LocalRiskChecker.Result r = GeminiScamChecker.checkBlocking(
                "hello this is microsoft tech support your computer has a virus\n"
                        + "please install any desk so i can fix it and read me the code we sent you");
        Log.i("GeminiTest", "messy -> " + r.score + " " + r.signs);
        assertTrue(r.score >= 70);
    }
}
