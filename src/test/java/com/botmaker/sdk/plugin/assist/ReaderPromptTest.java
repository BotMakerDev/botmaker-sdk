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

    /**
     * A screen the map lacks is marked by an element of the stop's tree whenever it has one, by a picture only when
     * it has none: an app's density change loses a picture (2026-11-27). The bad map and stuck prompts say so too.
     */
    @Test
    void theReaderMarksByAnElementWhenTheStopHasElements() {
        String prompt = ReaderPrompt.of(new Stop("unknown_screen-1", StopKind.UNKNOWN_SCREEN, null, "", "", List.of(),
                List.of("\"Network & internet\" tappable (0,80 540x60)"), Map.of(), List.of(), "a screen the map lacks",
                2), "");

        assertTrue(prompt.contains("`save_element`") && prompt.contains("\"as\":\"marker\""), prompt);
        assertTrue(prompt.indexOf("save_element") < prompt.indexOf("crop_pictures"), "the element first: " + prompt);
        assertTrue(prompt.contains("by a picture only when the stop has no elements"), prompt);
        for (StopKind kind : List.of(StopKind.BAD_MAP, StopKind.STUCK)) {
            String other = ReaderPrompt.of(new Stop(kind.name().toLowerCase() + "-1", kind, "HOME", "HOME", "",
                    List.of(), List.of(), Map.of(), List.of(), "why", 2), "");
            assertTrue(other.contains("save_element"), kind + ": " + other);
        }
    }

    /** Inputs with no verdict are judged against the goal, all of them, in one kind of edit. */
    @Test
    void aNoVerdictStopsPromptAsksToJudgeAgainstTheGoal() {
        String prompt = ReaderPrompt.of(new Stop("no_verdict-1", StopKind.NO_VERDICT, null, "map", "", List.of(),
                List.of(), Map.of(), List.of(), "1 inputs have no verdict: HOME.airplaneMode", 2),
                "map the settings, changing no setting");

        assertTrue(prompt.contains("set_verdict") && prompt.contains("HOME.airplaneMode"), prompt);
        assertTrue(prompt.contains("changing no setting") && prompt.contains("rank_presses"), prompt);
        assertFalse(prompt.matches("(?s).*\\{(stop|kind|on|why|goal)}.*"), "every placeholder filled: " + prompt);
    }
}
