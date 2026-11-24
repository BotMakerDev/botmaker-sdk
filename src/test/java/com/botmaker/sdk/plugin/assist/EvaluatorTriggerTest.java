// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.ExploreLoop;
import com.botmaker.sdk.internal.explore.FindingKind;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.PressTag;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopKind;
import com.botmaker.sdk.internal.explore.StopRole;
import com.botmaker.sdk.internal.explore.WitnessCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Evaluator (plan 2026-11-24-explore-agents, phase 6): when it is started, what its findings do, and the run's
 * summary of what is left unresolved.
 */
class EvaluatorTriggerTest {

    @TempDir
    Path folder;

    /** Stalled after 3 presses, a safety net every 5. */
    private static final EvaluatorTrigger.Settings SMALL = new EvaluatorTrigger.Settings(3, 5);
    /** Stalled after 3 presses, the safety net far off. */
    private static final EvaluatorTrigger.Settings STALLS = new EvaluatorTrigger.Settings(3, 25);
    private static final Coordinator.Settings SETTINGS = new Coordinator.Settings(Duration.ofMillis(100), 3,
            Duration.ofSeconds(40), Duration.ofSeconds(10));
    private static final Coordinator.Crew EVALUATORS = new Coordinator.Crew(Set.of(StopRole.EVALUATOR), Set.of());

    private Journal journal() {
        Journal journal = new Journal(folder.resolve("run"));
        journal.append(new ExploreEvent.Began(false, "sign in to Google", 1));
        return journal;
    }

    private static void press(Journal journal, String screen, String by) {
        journal.append(new ExploreEvent.Tried(screen, by, ExploreLoop.Mode.UNTRIED, null, 2));
    }

    private static String text(AgentReply reply) {
        return reply.parts().stream().filter(p -> p.kind() == AgentReply.Kind.TEXT).map(AgentReply.Part::text)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void itIsDueAfterKPressesWithNoNewScreenNorBetterRank() {
        Journal journal = journal();
        journal.append(new ExploreEvent.NewScreen("HOME", null, null, 2));
        journal.append(new ExploreEvent.Ranked("HOME", "Settings", 9, PressTag.SAFE, 2));
        press(journal, "HOME", "Settings");
        press(journal, "HOME", "Wi-Fi");
        assertEquals(Optional.empty(), EvaluatorTrigger.due(JournalView.of(journal), STALLS), "only 2 presses");

        journal.append(new ExploreEvent.NewScreen("SETTINGS", "HOME", "Settings", 3));
        press(journal, "SETTINGS", "A");
        press(journal, "SETTINGS", "B");
        assertEquals(Optional.empty(), EvaluatorTrigger.due(JournalView.of(journal), STALLS), "a new screen is progress");

        journal.append(new ExploreEvent.Ranked("SETTINGS", "Accounts", 10, PressTag.SAFE, 3));
        press(journal, "SETTINGS", "C");
        press(journal, "SETTINGS", "D");
        assertEquals(Optional.empty(), EvaluatorTrigger.due(JournalView.of(journal), STALLS), "a better rank is progress");

        journal.append(new ExploreEvent.Ranked("SETTINGS", "Ads", 2, PressTag.RISKY, 3));
        press(journal, "SETTINGS", "E");
        assertEquals(Optional.of(EvaluatorTrigger.Why.STALLED), EvaluatorTrigger.due(JournalView.of(journal), STALLS));
    }

    @Test
    void itIsNeverDuePerPressOnceItRan() {
        Journal journal = journal();
        for (int i = 0; i < 3; i++) press(journal, "HOME", "p" + i);
        assertEquals(Optional.of(EvaluatorTrigger.Why.STALLED), EvaluatorTrigger.due(JournalView.of(journal), SMALL));

        journal.append(new ExploreEvent.Leased(EvaluatorTrigger.idOf(1), StopRole.EVALUATOR, 1, 99, 3));
        press(journal, "HOME", "p3");
        assertEquals(Optional.empty(), EvaluatorTrigger.due(JournalView.of(journal), SMALL), "the next press is not due");
        press(journal, "HOME", "p4");
        assertEquals(Optional.empty(), EvaluatorTrigger.due(JournalView.of(journal), SMALL));
        press(journal, "HOME", "p5");
        assertEquals(Optional.of(EvaluatorTrigger.Why.STALLED), EvaluatorTrigger.due(JournalView.of(journal), SMALL),
                "K more stalled presses");
    }

    @Test
    void theSafetyNetFiresEveryNPressesWhateverTheProgress() {
        Journal journal = journal();
        for (int i = 0; i < 5; i++) {
            journal.append(new ExploreEvent.NewScreen("S" + i, null, null, 2));
            press(journal, "S" + i, "next");
            Optional<EvaluatorTrigger.Why> due = EvaluatorTrigger.due(JournalView.of(journal), SMALL);
            assertEquals(i < 4 ? Optional.empty() : Optional.of(EvaluatorTrigger.Why.SAFETY_NET), due, "press " + i);
        }
    }

    @Test
    void theCoordinatorLeasesOneEvaluationAtATimeAndTakesBackALateOne() {
        Journal journal = journal();
        for (int i = 0; i < 3; i++) press(journal, "HOME", "p" + i);
        Coordinator coordinator = new Coordinator(SETTINGS, EVALUATORS, SMALL);

        List<Coordinator.Action> actions = coordinator.step(JournalView.of(journal), 50, false);
        Coordinator.Action.Evaluate evaluate = new Coordinator.Action.Evaluate(EvaluatorTrigger.idOf(1),
                EvaluatorTrigger.Why.STALLED, 150);
        assertTrue(actions.contains(evaluate), actions.toString());
        AtomicInteger runs = new AtomicInteger();
        CoordinatorHost.carryOut(journal, JournalView.of(journal), actions, () -> 50, null, Runnable::run, null,
                (j, id, why, limit) -> {
                    runs.incrementAndGet();
                    return new WorkerRunner.Result(true, "");
                });
        assertEquals(1, runs.get());
        assertEquals(Optional.empty(), EvaluatorTrigger.held(JournalView.of(journal)), "released once it finished");

        // A second one, still out when its lease runs out, is taken back.
        for (int i = 3; i < 6; i++) press(journal, "HOME", "p" + i);
        journal.append(new ExploreEvent.Leased(EvaluatorTrigger.idOf(2), StopRole.EVALUATOR, 1, 300, 200));
        assertTrue(coordinator.step(JournalView.of(journal), 299, false).stream()
                .noneMatch(a -> a instanceof Coordinator.Action.Evaluate), "one at a time");
        assertTrue(coordinator.step(JournalView.of(journal), 300, false)
                .contains(new Coordinator.Action.Requeue(EvaluatorTrigger.idOf(2), 1)));
    }

    @Test
    void noEvaluatorInTheCrewNoEvaluation() {
        Journal journal = journal();
        for (int i = 0; i < 10; i++) press(journal, "HOME", "p" + i);

        assertTrue(new Coordinator(SETTINGS, Coordinator.Crew.SETTLER_ONLY, SMALL)
                .step(JournalView.of(journal), 50, false).stream()
                .noneMatch(a -> a instanceof Coordinator.Action.Evaluate));
    }

    @Test
    void theRunIsEvaluatedOnceAtItsEndThenSummed() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Finding(FindingKind.DRIFT, "SETTINGS", "off toward Wi-Fi", 3));
        journal.append(new ExploreEvent.Finding(FindingKind.NO_WITNESS, "done-1", "no check held", 3));
        journal.append(new ExploreEvent.Ended(ExploreLoop.End.BUDGET, 4));
        AtomicInteger runs = new AtomicInteger();
        CoordinatorHost.Evaluator grader = (j, id, why, limit) -> {
            runs.incrementAndGet();
            assertEquals(EvaluatorTrigger.Why.END, why);
            assertFalse(ExploreTools.recordFinding(j, "bad_map", "ACCOUNTS is two screens", "frame-7", "ACCOUNTS")
                    .isRefused(), "the end's Evaluator writes findings into the ended run");
            return new WorkerRunner.Result(true, "");
        };

        assertTrue(EvaluatorTrigger.atEnd(JournalView.of(journal)));
        CoordinatorHost.close(journal, grader, Duration.ofSeconds(5), () -> 9);
        CoordinatorHost.close(journal, grader, Duration.ofSeconds(5), () -> 10);

        assertEquals(1, runs.get(), "once at the end");
        assertFalse(EvaluatorTrigger.atEnd(JournalView.of(journal)));
        List<ExploreEvent.Summary> sums = journal.events().stream().filter(ExploreEvent.Summary.class::isInstance)
                .map(ExploreEvent.Summary.class::cast).toList();
        assertEquals(1, sums.size());
        assertEquals(List.of(FindingKind.NO_WITNESS, FindingKind.BAD_MAP),
                sums.getFirst().unresolved().stream().map(ExploreEvent.Finding::kind).toList(),
                "a drift was acted on at once; what is left is summed");
    }

    @Test
    void aRunThatWitnessedItsGoalLeavesNoNoWitnessUnresolved() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Finding(FindingKind.NO_WITNESS, "done-1", "no check held", 3));
        journal.append(new ExploreEvent.Witnessed("sign in to Google", "done-2",
                WitnessCheck.TEXT, "Sign in", 4));
        journal.append(new ExploreEvent.Ended(ExploreLoop.End.DONE, 5));

        CoordinatorHost.close(journal, null, Duration.ofSeconds(1), () -> 9);

        assertEquals(List.of(), JournalView.of(journal).unresolved());
        assertTrue(journal.events().stream().anyMatch(ExploreEvent.Summary.class::isInstance));
    }

    @Test
    void aDriftLowersTheRanksUnderItsScreenAndParksNothing() {
        Journal journal = journal();
        journal.append(new ExploreEvent.NewScreen("SETTINGS", null, null, 2));
        journal.append(new ExploreEvent.NewScreen("WIFI", "SETTINGS", "Wi-Fi", 2));
        journal.append(new ExploreEvent.NewScreen("ACCOUNTS", "SETTINGS", "Accounts", 2));
        journal.append(new ExploreEvent.NewScreen("ADVANCED", "WIFI", "More", 2));
        journal.append(new ExploreEvent.Ranked("WIFI", "Scan", 6, PressTag.SAFE, 2));
        journal.append(new ExploreEvent.Ranked("ADVANCED", "Proxy", 2, PressTag.SAFE, 2));
        journal.append(new ExploreEvent.Ranked("ADVANCED", "Reset", 1, PressTag.DESTRUCTIVE, 2));
        journal.append(new ExploreEvent.Ranked("ACCOUNTS", "Google", 9, PressTag.SAFE, 2));
        journal.append(new ExploreEvent.Ranked("WIFI", "Saved", 5, PressTag.SAFE, 2));
        press(journal, "WIFI", "Saved");
        journal.append(new ExploreEvent.Leased(EvaluatorTrigger.idOf(1), StopRole.EVALUATOR, 1, Long.MAX_VALUE, 3));

        AgentReply reply = ExploreTools.recordFinding(journal, "drift", "the run is lost in Wi-Fi", "", "WIFI");

        assertFalse(reply.isRefused(), text(reply));
        Map<String, Integer> ranks = JournalView.of(journal).openRanks().stream()
                .collect(Collectors.toMap(r -> r.screen() + "." + r.press(), ExploreEvent.Ranked::rank));
        assertEquals(Map.of("WIFI.Scan", 3, "ADVANCED.Proxy", 1, "ADVANCED.Reset", 1, "ACCOUNTS.Google", 9), ranks,
                "the subtree drops but stays ranked toward the goal, the rest keeps its rank, a pressed one is not "
                        + "added back");
        assertEquals(2, journal.events().stream().filter(e -> e instanceof ExploreEvent.Ranked r && r.at() > 3).count(),
                "a rank that would not move is not written again");
        assertTrue(JournalView.of(journal).openRanks().stream().filter(r -> r.press().equals("Reset"))
                .allMatch(r -> r.tag() == PressTag.DESTRUCTIVE), "a lowered press keeps its tag");
        assertEquals(FindingKind.DRIFT, JournalView.of(journal).findings().getLast().kind());
    }

    /** Device drive 2026-11-24, F3: a wrong map is the Reader's stop, on the frame the Evaluator judged. */
    @Test
    void aBadMapIsTheReadersStopOnTheFrameJudged() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Pictured("HOME", "frames/7.png", Map.of(), 2));
        journal.append(new ExploreEvent.Leased(EvaluatorTrigger.idOf(1), StopRole.EVALUATOR, 1, Long.MAX_VALUE, 3));

        AgentReply reply = ExploreTools.recordFinding(journal, "bad_map", "PRIVATE_DNS shows on a sub-page", "", "HOME");

        assertFalse(reply.isRefused(), text(reply));
        Stop flagged = JournalView.of(journal).foundBadMaps().getFirst();
        assertEquals("bad_map-1", flagged.id());
        assertEquals(StopKind.BAD_MAP, flagged.kind());
        assertEquals(StopRole.CARTOGRAPHER, flagged.role());
        assertEquals("HOME", flagged.screen());
        assertEquals("frames/7.png", flagged.framePath());
        assertEquals("PRIVATE_DNS shows on a sub-page", flagged.missing());
        assertEquals(Optional.of(flagged), JournalView.of(journal).stopOf("bad_map-1"), "the edit writer finds it");
        assertTrue(ExploreTools.recordFinding(journal, "bad_map", "x", "", " ").isRefused(),
                "a bad_map names its screen");

        journal.append(new ExploreEvent.Pictured("WIFI", "frames/8.png", Map.of(), 4));
        assertFalse(ExploreTools.recordFinding(journal, "bad_map", "two screens", "", "ACCOUNTS").isRefused());
        assertEquals("", JournalView.of(journal).foundBadMaps().getLast().framePath(),
                "never another screen's frame: the run saved none of ACCOUNTS");

        journal.append(new ExploreEvent.Released("bad_map-1", 1, true, "", 5));
        assertEquals(List.of("two screens"), JournalView.of(journal).unresolved().stream()
                .map(ExploreEvent.Finding::why).toList(), "a redrawn map is resolved");

        journal.append(new ExploreEvent.Ended(ExploreLoop.End.BUDGET, 6));
        assertFalse(ExploreTools.recordFinding(journal, "bad_map", "late", "HOME", "").isRefused(),
                "the end's grading needs no screen");
        assertEquals(2, JournalView.of(journal).foundBadMaps().size(), "nobody redraws after the end");
    }

    @Test
    void aFindingIsRefusedUnlessSaidWhole() {
        Journal journal = journal();
        assertTrue(ExploreTools.recordFinding(journal, "bad_map", "x", "", "").isRefused(),
                "the Evaluator's alone: no evaluation under way");
        journal.append(new ExploreEvent.Leased(EvaluatorTrigger.idOf(1), StopRole.EVALUATOR, 1, Long.MAX_VALUE, 3));

        assertTrue(ExploreTools.recordFinding(journal, "no_witness", "x", "", "").isRefused(), "code's kind");
        assertTrue(ExploreTools.recordFinding(journal, "drift", "x", "", "").isRefused(), "a drift names its screen");
        assertTrue(ExploreTools.recordFinding(journal, "stuck", " ", "", "").isRefused(), "a finding says why");
        assertTrue(JournalView.of(journal).findings().isEmpty());

        journal.append(new ExploreEvent.Ended(ExploreLoop.End.STOPPED, 4));
        assertTrue(ExploreTools.recordFinding(journal, "stuck", "x", "", "").isRefused(),
                "a stuck ends nothing in an ended run");
        journal.append(new ExploreEvent.Released(EvaluatorTrigger.idOf(1), 1, true, "", 4));
        assertTrue(ExploreTools.recordFinding(journal, "bad_map", "x", "", "").isRefused(),
                "an ended run nobody evaluates takes no finding");
    }

    @Test
    void theRunsOwnStuckIsNotTheEvaluators() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Finding(FindingKind.STUCK, "", "2 press(es) wait on you", 3));

        assertTrue(JournalView.of(journal).stuckSaid().isEmpty());
        assertTrue(new Coordinator(SETTINGS, EVALUATORS, SMALL).step(JournalView.of(journal), 50, false).stream()
                .noneMatch(a -> a instanceof Coordinator.Action.End), "the run ends on its own");
    }

    @Test
    void anEvaluatorsStuckEndsTheRun() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Leased(EvaluatorTrigger.idOf(1), StopRole.EVALUATOR, 1, Long.MAX_VALUE, 3));
        assertFalse(ExploreTools.recordFinding(journal, "stuck", "the same three screens over and over", "", "")
                .isRefused());
        Coordinator coordinator = new Coordinator(SETTINGS, EVALUATORS, SMALL);

        List<Coordinator.Action> actions = coordinator.step(JournalView.of(journal), 50, false);

        assertTrue(actions.contains(new Coordinator.Action.End(ExploreLoop.End.STUCK,
                "the same three screens over and over")), actions.toString());
        CoordinatorHost.carryOut(journal, JournalView.of(journal), actions, () -> 50, null, Runnable::run);
        assertEquals(1, JournalView.of(journal).findings().size(), "the end is not said twice");
        assertEquals(Optional.of(ExploreLoop.End.STUCK), JournalView.of(journal).ended());
    }

    @Test
    void proposeDoneAsksTheRunToCheckItsWitness() {
        Journal journal = journal();

        AgentReply reply = ExploreTools.proposeDone(journal, " Signed in ", Duration.ofMillis(50));

        assertFalse(reply.isRefused(), text(reply));
        assertEquals(Optional.of(new Journal.Command.Done("Signed in")), journal.takeCommand());
        assertTrue(text(reply).contains("not checked"), text(reply));

        journal.append(new ExploreEvent.Stopped(new Stop("settled-1", StopKind.SETTLED, "A", "A.x", null, List.of(),
                List.of(), Map.of(), List.of(), "notes", 4), false, 4));
        assertTrue(ExploreTools.proposeDone(journal, "Signed in", Duration.ofMillis(50)).isRefused(),
                "a paused run would drop it");

        journal.append(new ExploreEvent.Ended(ExploreLoop.End.STOPPED, 4));
        assertTrue(ExploreTools.proposeDone(journal, "", Duration.ofMillis(50)).isRefused(), "an ended run");
    }

    @Test
    void theEvaluatorIsAskedWhyItWasStarted() {
        String prompt = EvaluatorPrompt.of(EvaluatorTrigger.idOf(2), "20261124-101010", "sign in to Google",
                EvaluatorTrigger.Why.STALLED);

        assertTrue(prompt.contains("The run's goal: sign in to Google."), prompt);
        assertTrue(prompt.contains("`20261124-101010`"), "pinned to its run: " + prompt);
        assertTrue(prompt.contains(EvaluatorTrigger.Why.STALLED.displayName()), prompt);
        assertTrue(prompt.contains("progress") && prompt.contains("record_finding") && prompt.contains("propose_done"));
        assertTrue(!prompt.contains("{"), "every slot is filled: " + prompt);
        assertTrue(EvaluatorPrompt.of(EvaluatorTrigger.idOf(3), "r", "g", EvaluatorTrigger.Why.END).contains("bad_map"));
        assertEquals(WorkerRole.EVALUATOR, WorkerRole.of(StopRole.EVALUATOR).orElseThrow());
    }
}
