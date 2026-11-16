// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.Operation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LookTest {

    @Test
    void anInitialIsTheFirstLetterOrDigitUpperCased() {
        assertEquals("F", Look.initial("farm@example.com"));
        assertEquals("A", Look.initial("  _a player"));
        assertEquals("7", Look.initial("7th@example.com"));
        assertEquals("É", Look.initial("élise@example.com"));
        assertEquals("?", Look.initial("--"));
        assertEquals("?", Look.initial(null));
    }

    @Test
    void aHueIsStableIgnoresCaseAndSpreadsNearKeys() {
        assertEquals(Look.hue("google:farm@example.com", Look.HUES), Look.hue("GOOGLE:Farm@Example.com", Look.HUES));
        Set<Integer> hues = new HashSet<>();
        IntStream.range(0, 16).forEach(i -> {
            int hue = Look.hue("google:account-" + i + "@example.com", Look.HUES);
            assertTrue(hue >= 0 && hue < Look.HUES);
            hues.add(hue);
        });
        assertTrue(hues.size() >= 5, "sixteen near keys land on " + hues.size() + " hues");
        assertEquals(0, Look.hue(null, 1));
    }

    @Test
    void everyPlatformHasTwoLettersAndTheUnknownOneAMark() {
        assertEquals("Mu", Look.letters(PlatformId.MUMU));
        assertEquals("LD", Look.letters(PlatformId.LDPLAYER));
        assertEquals("ME", Look.letters(PlatformId.MEMU));
        assertEquals("?", Look.letters(PlatformId.UNKNOWN));
        assertEquals("?", Look.letters(null));
        for (PlatformId platform : PlatformId.values()) assertNotNull(Look.letters(platform));
    }

    @Test
    void aPillSaysTheStateInAWordAndATone() {
        assertEquals(new Look.Pill(Look.Tone.OK, "Running"), Look.pill(true, true));
        assertEquals(new Look.Pill(Look.Tone.MUTED, "Stopped"), Look.pill(false, true));
        assertEquals(new Look.Pill(Look.Tone.OUTLINED, "Not read"), Look.pill(false, false));
        assertEquals(new Look.Pill(Look.Tone.WARNING, "Needs you"), Look.pill(Operation.State.NEEDS_YOU));
        assertEquals(new Look.Pill(Look.Tone.DANGER, "Failed"), Look.pill(Operation.State.FAILED));
        assertEquals(Look.Tone.OUTLINED, Look.pill((Operation.State) null).tone());
    }

    @Test
    void theSheetIsOnTheClasspath() {
        assertTrue(Look.SHEET.endsWith("emulators.css"));
    }
}
