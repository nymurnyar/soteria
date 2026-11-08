package com.example.soteria;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The offline scam check used with on-phone transcription. */
public class LocalRiskCheckerTest {

    @Test
    public void normalConversation_isSafe() {
        LocalRiskChecker.Result r = LocalRiskChecker.check(
                "Hi, are we still meeting tomorrow?\nYes, see you at ten.");
        assertEquals(0, r.score);
        assertFalse(r.isScam);
        assertTrue(r.signs.isEmpty());
    }

    @Test
    public void craGiftCardThreat_isFlaggedAsScam() {
        LocalRiskChecker.Result r = LocalRiskChecker.check(
                "This is the Canada Revenue Agency\nYou must pay with gift cards right now "
                        + "or the police will arrest you");
        assertTrue(r.isScam);
        assertTrue(r.score >= 70);
        assertTrue(r.signs.contains("Gift card or crypto payment"));
        assertTrue(r.signs.contains("Arrest or deportation threat"));
        assertTrue(r.signs.contains("Government / bank impersonation"));
        assertTrue(r.signs.contains("Urgency or secrecy"));
    }

    @Test
    public void oneSign_isSuspiciousButNotScam() {
        LocalRiskChecker.Result r = LocalRiskChecker.check("Can you read me the code we just sent?");
        assertEquals(35, r.score);
        assertFalse(r.isScam);
        assertEquals(1, r.signs.size());
    }

    @Test
    public void score_isCappedAt95() {
        LocalRiskChecker.Result r = LocalRiskChecker.check(
                "gift card wire money arrest otp password anydesk lmia cra urgent");
        assertEquals(95, r.score);
    }
}
