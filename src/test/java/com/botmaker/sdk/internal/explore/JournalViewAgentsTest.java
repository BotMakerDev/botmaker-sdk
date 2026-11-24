// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.internal.screens.MapWalk;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What each agent's card says, read off a fixed journal (spec 2026-11-24-explore-agents-design § The Explore page). */
class JournalViewAgentsTest {

    private static final Duration HEARTBEAT = Duration.ofSeconds(60);
    private static final Stop UNKNOWN = new Stop("unknown-1", StopKind.UNKNOWN_SCREEN, "", "Home.shop→?",
            "/frames/unknown-1.png", List.of(), List.of(), Map.of(), List.of("shop"), "a screen not on the map", 6_000);
    private static final Stop FAULT = new Stop("fault-1", StopKind.FAULT, "Home", "Home.bag→Bag", "", List.of(),
            List.of(), Map.of(), List.of(), "judged wrong", 2_000);

    @Test
    void theExplorerWaitsOnTheStopItIsLookingAt() {
        AgentSummary explorer = crewRun().agentSummary(Agent.EXPLORER, 20_000, HEARTBEAT);

        assertEquals(AgentSummary.State.WAITING, explorer.state());
        assertEquals("unknown-1", explorer.stopId());
        assertEquals("/frames/unknown-1.png", explorer.frame());
        assertEquals(2, explorer.calls(), "one call per press");
        assertEquals(Duration.ofSeconds(19), explorer.time());
        assertEquals(Optional.of(new ExploreEvent.Stopped(UNKNOWN, false, 6_000)), explorer.last());
    }

    @Test
    void theReaderWorksTheLeasedStopForItsSessionsTime() {
        AgentSummary reader = crewRun().agentSummary(Agent.READER, 20_000, HEARTBEAT);

        assertEquals(AgentSummary.State.WORKING, reader.state());
        assertEquals("unknown-1", reader.stopId());
        assertEquals("/frames/unknown-1.png", reader.frame());
        assertEquals(1, reader.calls());
        assertEquals(Duration.ofSeconds(13), reader.time(), "from its lease at 7 s to now");
    }

    @Test
    void theMechanicsLastWordIsItsBranchNotItsRelease() {
        AgentSummary mechanic = crewRun().agentSummary(Agent.MECHANIC, 20_000, HEARTBEAT);

        assertEquals(AgentSummary.State.IDLE, mechanic.state());
        assertEquals("", mechanic.stopId());
        assertEquals(Optional.of(new ExploreEvent.Branched("fault-1", "explore/fault-1", "fixed the walk", 4_000)),
                mechanic.last());
        assertEquals(Duration.ofMillis(2_500), mechanic.time(), "from its lease at 2 s to its release at 4.5 s");
    }

    @Test
    void aFailedSessionsReleaseIsItsLastWord() {
        List<ExploreEvent> events = new ArrayList<>(crewEvents());
        ExploreEvent.Released timeout = new ExploreEvent.Released("unknown-1", 1, false, "timeout", 9_000);
        events.add(timeout);

        AgentSummary reader = view(events).agentSummary(Agent.READER, 20_000, HEARTBEAT);

        assertEquals(AgentSummary.State.IDLE, reader.state());
        assertEquals(Optional.of(timeout), reader.last());
        assertEquals(Duration.ofSeconds(2), reader.time());
    }

    @Test
    void theCoordinatorIsSilentOnceItsHeartbeatIsOld() {
        JournalView run = crewRun();

        assertEquals(AgentSummary.State.WORKING, run.agentSummary(Agent.COORDINATOR, 20_000, HEARTBEAT).state());
        AgentSummary late = run.agentSummary(Agent.COORDINATOR, 70_000, HEARTBEAT);
        assertEquals(AgentSummary.State.SILENT, late.state(), "its beat at 5 s is 65 s old");
        assertEquals(2, late.calls(), "one call per lease handed");
        assertEquals(Optional.of(new ExploreEvent.Leased("unknown-1", StopRole.CARTOGRAPHER, 1, 127_000, 7_000)),
                late.last());
    }

    @Test
    void aRunWithoutAGoalHasNoCrew() {
        JournalView run = view(List.of(new ExploreEvent.Began(false, 1_000),
                new ExploreEvent.NewScreen("Home", null, null, 1_500)));

        for (Agent agent : List.of(Agent.READER, Agent.EVALUATOR, Agent.COORDINATOR, Agent.MECHANIC)) {
            assertEquals(AgentSummary.State.ABSENT, run.agentSummary(agent, 5_000, HEARTBEAT).state(), agent.id());
        }
        assertEquals(AgentSummary.State.WORKING, run.agentSummary(Agent.EXPLORER, 5_000, HEARTBEAT).state());
    }

    @Test
    void anEndedRunEndsEveryCard() {
        List<ExploreEvent> events = new ArrayList<>(crewEvents());
        events.add(new ExploreEvent.Released("unknown-1", 1, true, "", 9_000));
        events.add(new ExploreEvent.Ended(ExploreLoop.End.STOPPED, 10_000));
        JournalView run = view(events);

        for (Agent agent : Agent.CREW) {
            assertEquals(AgentSummary.State.ENDED, run.agentSummary(agent, 20_000, HEARTBEAT).state(), agent.id());
        }
        assertEquals(Duration.ofSeconds(9), run.agentSummary(Agent.EXPLORER, 20_000, HEARTBEAT).time());
    }

    @Test
    void aLeasePastItsTimeIsNoWorkAnyMore() {
        AgentSummary reader = crewRun().agentSummary(Agent.READER, 200_000, HEARTBEAT);

        assertEquals(AgentSummary.State.IDLE, reader.state(), "its worker is gone; the Coordinator takes it back");
        assertEquals("", reader.stopId());
        assertEquals(Duration.ofSeconds(120), reader.time(), "from its lease at 7 s to its end at 127 s");
    }

    @Test
    void thePathIsHowTheRunFirstCameToWhereItIs() {
        JournalView run = crewRun();

        assertEquals(List.of("Home", "Shop"), run.path());
        assertEquals(Optional.empty(), run.where(), "a stop on a screen not on the map is on none of it");
    }

    @Test
    void everyRecordHasItsAuthor() {
        assertEquals(Agent.UNKNOWN, JournalView.author(new ExploreEvent.Heartbeat(1)), "a heartbeat is nobody's line");
        assertEquals(Agent.COORDINATOR, JournalView.author(new ExploreEvent.Released("s", 1, true, "", 1)));
        assertEquals(Agent.MECHANIC, JournalView.author(new ExploreEvent.Branched("s", "b", "n", 1)));
        assertEquals(Agent.READER, JournalView.author(new ExploreEvent.EditApplied("edit-1", 1)));
    }

    /**
     * A goal run: from Home to Shop, a fault the Mechanic branched a fix for, then a screen not on the map the Reader
     * holds, the Coordinator's beat at 5 s.
     */
    private static List<ExploreEvent> crewEvents() {
        return List.of(
                new ExploreEvent.Began(false, "reach the shop", 1_000),
                new ExploreEvent.NewScreen("Home", null, null, 1_200),
                new ExploreEvent.Stopped(FAULT, false, 2_000),
                new ExploreEvent.Leased("fault-1", StopRole.MECHANIC, 1, 900_000, 2_000),
                new ExploreEvent.Branched("fault-1", "explore/fault-1", "fixed the walk", 4_000),
                new ExploreEvent.Released("fault-1", 1, true, "", 4_500),
                new ExploreEvent.Fixed("fault-1", "the Mechanic's branch", 4_600),
                new ExploreEvent.Heartbeat(5_000),
                new ExploreEvent.Tried("Home", "bag", ExploreLoop.Mode.UNTRIED, MapWalk.Arrival.AT_TARGET, 5_200),
                new ExploreEvent.Tried("Home", "shop", ExploreLoop.Mode.UNTRIED, MapWalk.Arrival.ELSEWHERE, 5_500),
                new ExploreEvent.NewScreen("Shop", "Home", "shop", 5_600),
                new ExploreEvent.Stopped(UNKNOWN, false, 6_000),
                new ExploreEvent.Leased("unknown-1", StopRole.CARTOGRAPHER, 1, 127_000, 7_000));
    }

    private static JournalView crewRun() {
        return view(crewEvents());
    }

    private static JournalView view(List<ExploreEvent> all) {
        return new JournalView(all, all, List.of());
    }
}
