// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.internal.explore.Agent;
import com.botmaker.sdk.internal.explore.AgentSummary;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.PressTag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What an agent card says: a thin render of its {@link AgentSummary}. */
class AgentCardsTest {

    @Test
    void aWorkingReaderSaysItsStopItsRankAndItsTime() {
        AgentSummary reader = new AgentSummary(Agent.READER, AgentSummary.State.WORKING, "unknown-1", "",
                Optional.of(new ExploreEvent.Ranked("HOME", "shop", 10, PressTag.SAFE, 1)), 1, Duration.ofSeconds(13));

        assertEquals("Reader · Working · on unknown-1 · ranked HOME › shop 10 (" + PressTag.SAFE.displayName()
                + ") · 1 session · 13 s", AgentCards.line(reader));
    }

    @Test
    void theExplorerCountsPressesAndTheCoordinatorLeases() {
        assertEquals("2 presses", AgentCards.counted(Agent.EXPLORER, 2));
        assertEquals("1 lease", AgentCards.counted(Agent.COORDINATOR, 1));
        assertEquals("0 sessions", AgentCards.counted(Agent.MECHANIC, 0));
    }

    @Test
    void anAgentThatWroteNothingSaysSo() {
        AgentSummary absent = new AgentSummary(Agent.EVALUATOR, AgentSummary.State.ABSENT, "", "", Optional.empty(), 0,
                Duration.ZERO);

        assertEquals("Evaluator · Not in this run · nothing yet · 0 sessions · 0 s", AgentCards.line(absent));
        assertEquals(absent, absent.ended(), "an agent not in the run stays so when it ends");
    }

    @Test
    void timeReadsInSecondsThenMinutesThenHours() {
        assertEquals("59 s", AgentCards.took(Duration.ofSeconds(59)));
        assertEquals("1:05", AgentCards.took(Duration.ofSeconds(65)));
        assertEquals("1:00:01", AgentCards.took(Duration.ofSeconds(3_601)));
    }

    @Test
    void aKilledRunsCardsEnd() {
        AgentSummary working = new AgentSummary(Agent.READER, AgentSummary.State.WORKING, "unknown-1", "f.png",
                Optional.empty(), 1, Duration.ofSeconds(3));

        assertEquals(new AgentSummary(Agent.READER, AgentSummary.State.ENDED, "", "f.png", Optional.empty(), 1,
                Duration.ofSeconds(3)), working.ended());
    }
}
