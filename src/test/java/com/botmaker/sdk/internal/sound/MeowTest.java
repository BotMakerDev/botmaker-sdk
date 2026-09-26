package com.botmaker.sdk.internal.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The meow's shape, measured; whether it sounds like a cat is for an ear. */
class MeowTest {

    private static final int RATE = 44_100;

    @Test
    void itLastsAboutSixHundredMilliseconds() {
        assertEquals(Math.round(Meow.SECONDS * RATE), Meow.samples(RATE).length);
    }

    @Test
    void itNeverClipsAndReachesItsPeak() {
        short[] wave = Meow.samples(RATE);
        int max = 0;
        for (short s : wave) max = Math.max(max, Math.abs(s));
        assertTrue(max < Short.MAX_VALUE, "clipped");
        assertEquals(Math.round(Meow.PEAK * Short.MAX_VALUE), max, 1);
    }

    @Test
    void itStartsAndEndsInSilence() {
        short[] wave = Meow.samples(RATE);
        assertTrue(Math.abs(wave[0]) < 200, "starts with a click: " + wave[0]);
        assertTrue(Math.abs(wave[wave.length - 1]) < 200, "ends with a click: " + wave[wave.length - 1]);
    }

    @Test
    void itsPitchRisesThenFalls() {
        double start = Meow.pitch(0.05);
        double peak = Meow.pitch(0.33);
        double end = Meow.pitch(0.95);
        assertTrue(peak > start && peak > end, start + " / " + peak + " / " + end);
        assertTrue(end < start, "a meow ends lower than it starts");
    }
}
