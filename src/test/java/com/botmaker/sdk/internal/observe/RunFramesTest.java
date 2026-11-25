// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.observe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The question about the game's window, as the frames the run sends its Studio half. */
class RunFramesTest {

    private static final RunEvent.WindowDoubt DOUBT = new RunEvent.WindowDoubt("lutris:rungameid/3", 30, List.of(
            new RunEvent.DoubtfulWindow("41943044", "Balatro", "wine|Balatro",
                    new RunEvent.Target("Balatro", 0, 0, 1280, 800)),
            new RunEvent.DoubtfulWindow("41943050", "Battle.net", "wine|Battle.net",
                    new RunEvent.Target("Battle.net", 40, 40, 1280, 800))));

    @Test
    void aDoubtAndItsEndReadBackWhole() throws Exception {
        assertEquals(DOUBT, RunFrames.decode(RunFrames.encode(DOUBT)));
        RunEvent.WindowSettled settled = new RunEvent.WindowSettled("lutris:rungameid/3");
        assertEquals(settled, RunFrames.decode(RunFrames.encode(settled)));
    }

    /** Neither is drawn on the screen: the overlay and the pilot skip them. */
    @Test
    void neitherIsDrawn() {
        assertFalse(DOUBT.drawn());
        assertFalse(new RunEvent.WindowSettled("x").drawn());
    }

    /** A list longer than a reader takes is cut when written, never refused whole when read. */
    @Test
    void aLongDoubtIsCutToWhatAReaderTakes() throws Exception {
        List<RunEvent.DoubtfulWindow> many = new ArrayList<>();
        for (int i = 0; i < RunFrames.MAX_WINDOWS + 5; i++) {
            many.add(new RunEvent.DoubtfulWindow(String.valueOf(i), "w" + i, "wine|w" + i, RunEvent.NO_SURFACE));
        }
        RunEvent.WindowDoubt read = (RunEvent.WindowDoubt) RunFrames.decode(RunFrames.encode(
                new RunEvent.WindowDoubt("x", 30, many)));
        assertEquals(RunFrames.MAX_WINDOWS, read.windows().size());
    }

    @Test
    void aDoubtCutShortIsUnreadable() {
        byte[] frame = RunFrames.encode(DOUBT);
        assertThrows(RunFrames.Unreadable.class, () -> RunFrames.decode(Arrays.copyOf(frame, frame.length - 6)));
    }
}
