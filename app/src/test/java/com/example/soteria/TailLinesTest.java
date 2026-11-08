package com.example.soteria;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The transcript excerpt shown in the live call notification. */
public class TailLinesTest {

    @Test
    public void keepsLastThreeLines() {
        assertEquals("b\nc\nd", CallNotifications.tailLines("a\nb\nc\nd", 3, 400));
    }

    @Test
    public void shortTranscript_returnedWhole() {
        assertEquals("only line", CallNotifications.tailLines("only line", 3, 400));
        assertEquals("a\nb", CallNotifications.tailLines("a\nb", 3, 400));
        assertEquals("", CallNotifications.tailLines("", 3, 400));
    }

    @Test
    public void includesInProgressPartialAsLastLine() {
        // getTranscriptText() appends the partial on its own line.
        assertEquals("line 2\nline 3\nand now they ask for gift",
                CallNotifications.tailLines("line 1\nline 2\nline 3\nand now they ask for gift", 3, 400));
    }

    @Test
    public void longText_isCutFromTheFront() {
        String longLine = "x".repeat(1000);
        String tail = CallNotifications.tailLines("start\n" + longLine, 3, 400);
        assertTrue(tail.startsWith("…"));
        assertEquals(401, tail.length());
    }
}
