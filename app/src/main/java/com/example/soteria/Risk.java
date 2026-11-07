package com.example.soteria;

/** Risk levels the LLM sends back. Colour = what the bubble turns. */
public enum Risk {
    IDLE(0xFF455A64),     // grey: no session
    SAFE(0xFF2E7D32),     // green
    CAUTION(0xFFF9A825),  // yellow
    DANGER(0xFFC62828);   // red

    public final int color;

    Risk(int color) {
        this.color = color;
    }
}
