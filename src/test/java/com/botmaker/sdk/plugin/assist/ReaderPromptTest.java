// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Reader's prompt for each kind of stop it takes. */
class ReaderPromptTest {

    /** A goal run's stuck: the Reader adds the frame's presses toward the goal, or ends without resuming. */
    @Test
    void aStuckStopsPromptAsksForThePressesTowardTheGoal() {
        Stop stuck = new Stop("stuck-1", StopKind.STUCK, "HOME", "HOME", "", List.of(), List.of(), Map.of(), List.of(),
                "nothing is left the run can press itself", 2);

        String prompt = ReaderPrompt.of(stuck, "open the accounts settings");

        assertTrue(prompt.contains("stuck-1"), prompt);
        assertTrue(prompt.contains("open the accounts settings"), prompt);
        assertTrue(prompt.contains("HOME"), prompt);
        assertTrue(prompt.contains("crop_pictures") && prompt.contains("read_map"), prompt);
        assertFalse(prompt.matches("(?s).*\\{(stop|kind|on|why|goal)}.*"), "every placeholder filled: " + prompt);
        assertFalse(prompt.equals(ReaderPrompt.of(new Stop("unknown_screen-1", StopKind.UNKNOWN_SCREEN, null, "", "",
                List.of(), List.of(), Map.of(), List.of(), "a screen the map lacks", 2), "open the accounts settings")));
    }
}
