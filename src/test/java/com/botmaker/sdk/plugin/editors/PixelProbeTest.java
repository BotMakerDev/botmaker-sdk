package com.botmaker.sdk.plugin.editors;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the Precision frame says about a pixel clicked on it. */
class PixelProbeTest {

    @Test
    void aPixelIsItsPositionRgbAndHex() {
        String text = MatchOverlay.probeText(12, 34, new Color(12, 200, 40), null, 10);
        assertTrue(text.startsWith("(12, 34)  RGB 12, 200, 40  #0CC828"), text);
        assertTrue(text.contains("no target colour"), text);
    }

    @Test
    void againstATargetItSaysHowFarAndWhetherItMatches() {
        Color red = new Color(200, 30, 30);
        assertTrue(MatchOverlay.probeText(0, 0, red, red, 10).endsWith("ΔE 0 from the target — matches"));
        String far = MatchOverlay.probeText(0, 0, Color.BLUE, red, 10);
        assertTrue(far.endsWith("from the target — does not match"), far);
    }

    @Test
    void hexIsTwoDigitsPerChannel() {
        assertEquals("#000A0F", MatchOverlay.hex(new Color(0, 10, 15)));
    }
}
