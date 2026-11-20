// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.run.DevReload;
import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.sdk.plugin.run.SdkRuns;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.ExploreLoop;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The explore tool over a journal a scripted run writes: what wait answers, what resume waits for. */
class ExploreToolsTest {

    @TempDir
    Path folder;

    private final AtomicBoolean done = new AtomicBoolean();

    @AfterEach
    void endTheRun() {
        done.set(true);
    }

    private Journal journal() {
        return new Journal(folder);
    }

    private static Stop stop(String id) {
        return new Stop(id, StopKind.WRONG_ARRIVAL, "Shop", "Shop.buy→Cart", "/tmp/frames/" + id + ".png",
                List.of(new Stop.Text("Buy 10", new Rect(5, 6, 70, 20))), List.of(), Map.of("gold", "120"),
                List.of("Home.shop"), "Shop.buy opened Bag, not Cart", 2);
    }

    private static String text(AgentReply reply) {
        return reply.parts().stream().filter(p -> p.kind() == AgentReply.Kind.TEXT).map(AgentReply.Part::text)
                .collect(Collectors.joining("\n"));
    }

    /** A run on its own thread that answers each command with {@code answer}, until the test ends. */
    private void run(Consumer<Journal.Command> answer) {
        Thread run = new Thread(() -> {
            while (!done.get()) {
                journal().takeCommand().ifPresent(answer);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        run.setDaemon(true);
        run.start();
    }

    /** status says how far the map is built: built screens with their median, open ones with their clock and list. */
    @Test
    void statusSaysTheMapBuild() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Built("HOME", 100_000, 2));
        journal.append(new ExploreEvent.Building("PRICING", 192_000, List.of("9 untried", "1 without verdict"), 3));

        String said = text(ExploreTools.status(journal));

        assertTrue(said.endsWith("\nmap: 1 built (median 1m40s), 1 open: PRICING 3m12s (9 untried, 1 without verdict)."),
                said);

        journal.append(new ExploreEvent.SetAside("PRICING", 192_000, List.of("9 untried"), 4));
        assertTrue(text(ExploreTools.status(journal)).endsWith("PRICING 3m12s set aside (9 untried, 1 without verdict)."));
    }

    @Test
    void waitAnswersTheOpenStop() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Stopped(stop("wrong_arrival-1"), false, 2));

        AgentReply reply = ExploreTools.waitFor(journal, Duration.ofSeconds(30));

        String said = text(reply);
        assertFalse(reply.isRefused(), said);
        assertTrue(said.startsWith("The run waits on this stop:\nwrong_arrival-1: "), said);
        assertTrue(said.contains("Missing: Shop.buy opened Bag, not Cart"), said);
        assertTrue(said.contains("Frame: /tmp/frames/wrong_arrival-1.png"), said);
        assertTrue(said.contains("\"Buy 10\" (5,6 70x20)"), said);
        assertTrue(said.contains("Facts: gold = 120"), said);
        assertTrue(journal.events().getLast() instanceof ExploreEvent.Claude(String call, long at)
                && call.equals(JournalView.WAIT), "the call is in the journal, for the Explore tab");
    }

    @Test
    void waitTimesOutWithCounts() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.NewScreen("Home", null, null, 2));
        journal.append(new ExploreEvent.Claude(JournalView.WAIT, 3));
        journal.append(new ExploreEvent.Tried("Home", "shop", null, null, 4));

        String said = text(ExploreTools.waitFor(journal, Duration.ofMillis(300)));

        assertTrue(said.startsWith("Still running after 0 s: 1 screens found, 1 presses"), said);
        assertTrue(said.contains("on Home"), said);
    }

    @Test
    void aParkedStopIsSaidOnceThenWaitBlocks() {
        Journal journal = journal();
        Stop parked = stop("wrong_arrival-1");
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Stopped(parked, false, 2));
        journal.append(new ExploreEvent.Parked(parked.id(), 3));
        journal.queue(List.of(parked));

        assertTrue(text(ExploreTools.waitFor(journal, Duration.ofSeconds(20))).contains("parked"));
        String again = text(ExploreTools.waitFor(journal, Duration.ofMillis(300)));

        assertTrue(again.startsWith("Still running") && again.contains("1 parked"), again);
    }

    @Test
    void anEndedRunIsSaidBeforeItsParkedStops() {
        Journal journal = journal();
        Stop parked = stop("wrong_arrival-1");
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Parked(parked.id(), 3));
        journal.queue(List.of(parked));
        journal.append(new ExploreEvent.Ended(ExploreLoop.End.BUDGET, 60_001));

        String said = text(ExploreTools.waitFor(journal, Duration.ofSeconds(20)));

        assertTrue(said.startsWith("The run ended (Budget spent)") && said.contains("1 min in"), said);
        assertTrue(ExploreTools.resume(journal, parked.id(), false, Duration.ofSeconds(1)).isRefused());
    }

    /** A screen found while a wait blocked is said by that wait only; the next one blocks for what comes after. */
    @Test
    void aScreenFoundDuringAWaitIsSaidOnce() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Began(false, 1));
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                return;
            }
            journal().append(new ExploreEvent.NewScreen("Shop", null, null, 5));
        });

        assertTrue(text(ExploreTools.waitFor(journal, Duration.ofSeconds(20))).contains("Shop"));
        String again = text(ExploreTools.waitFor(journal, Duration.ofMillis(300)));

        assertTrue(again.startsWith("Still running"), again);
    }

    @Test
    void waitWakesOnAStop() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Began(false, 1));
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                return;
            }
            journal().append(new ExploreEvent.Stopped(stop("crash-1"), false, 5));
        });
        long start = System.nanoTime();

        String said = text(ExploreTools.waitFor(journal, Duration.ofSeconds(20)));

        assertTrue(said.contains("crash-1: "), said);
        assertTrue(System.nanoTime() - start < Duration.ofSeconds(10).toNanos(), "woken, not timed out");
    }

    @Test
    void resumeAnswersRefusedWithErrors() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Stopped(stop("wrong_arrival-1"), false, 2));
        run(command -> {
            if (command instanceof Journal.Command.Resume(String id, boolean ignore, List<String> skipped)) {
                journal().append(new ExploreEvent.Reloaded(id, 1, List.of("Shop.java:3: ';' expected"), 3));
            }
        });

        AgentReply reply = ExploreTools.resume(journal, "wrong_arrival-1", false, Duration.ofSeconds(20));

        assertTrue(reply.isRefused());
        assertTrue(text(reply).contains("- Shop.java:3: ';' expected"), text(reply));
        assertEquals("wrong_arrival-1", JournalView.of(journal).open().map(Stop::id).orElse(null),
                "the stop stays open");
    }

    @Test
    void twoResumesGiveOneGeneration() throws Exception {
        Journal journal = journal();
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Stopped(stop("wrong_arrival-1"), false, 2));
        AtomicInteger reloads = new AtomicInteger();
        run(command -> {
            if (command instanceof Journal.Command.Resume(String id, boolean ignore, List<String> skipped)) {
                journal().append(new ExploreEvent.Reloaded(id, 1 + reloads.incrementAndGet(), List.of(), 3));
            }
        });

        CompletableFuture<AgentReply> first = CompletableFuture.supplyAsync(
                () -> ExploreTools.resume(journal(), "wrong_arrival-1", false, Duration.ofSeconds(20)));
        CompletableFuture<AgentReply> second = CompletableFuture.supplyAsync(
                () -> ExploreTools.resume(journal(), "wrong_arrival-1", false, Duration.ofSeconds(20)));

        String one = text(first.get());
        String two = text(second.get());
        assertEquals(1, reloads.get(), "one generation loaded");
        assertTrue(one.contains("generation 2"), one);
        assertTrue(two.contains("generation 2"), two);
        assertTrue(one.startsWith("Already resumed") != two.startsWith("Already resumed"), one + " / " + two);
    }

    @Test
    void resumeOfAParkedStopReopensIt() {
        Journal journal = journal();
        Stop parked = stop("wrong_arrival-1");
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Stopped(parked, false, 2));
        journal.append(new ExploreEvent.Parked(parked.id(), 3));
        journal.queue(List.of(parked));
        run(command -> {
            if (command instanceof Journal.Command.Resume(String id, boolean ignore, List<String> skipped)) {
                journal().append(new ExploreEvent.Reloaded(id, 2, List.of(), 4));
                journal().queue(List.of());
                journal().append(new ExploreEvent.Fixed(id, "reloaded as generation 2", 5));
            }
        });

        String waited = text(ExploreTools.waitFor(journal, Duration.ofSeconds(20)));
        assertTrue(waited.contains("The oldest of 1 parked (queue lists them):\nwrong_arrival-1: "), waited);
        assertTrue(ExploreTools.resume(journal, "crash-9", false, Duration.ofSeconds(1)).isRefused(),
                "a stop neither open nor parked");

        String said = text(ExploreTools.resume(journal, parked.id(), false, Duration.ofSeconds(20)));

        assertTrue(said.startsWith("Resumed: reloaded as generation 2"), said);
        assertTrue(JournalView.of(journal).queue().isEmpty());
    }

    /** A dev run held on {@code stop}, which Claude called a fault. */
    private Journal heldOn(Stop stop) {
        Journal journal = journal();
        journal.append(new ExploreEvent.Began(true, 1));
        journal.append(new ExploreEvent.Stopped(stop, false, 2));
        journal.append(new ExploreEvent.Faulted(stop.id(), "claude", "judged a toggle as navigation", "", "2.0.0-SNAPSHOT",
                3));
        return journal;
    }

    @Test
    void resumeOnAFaultIsRefused() {
        Journal journal = heldOn(stop("wrong_arrival-1"));

        AgentReply reply = ExploreTools.resume(journal, "wrong_arrival-1", false, Duration.ofSeconds(1));

        assertTrue(reply.isRefused());
        assertTrue(text(reply).contains("a fault resumes through reload_sdk"), text(reply));
        assertFalse(journal.commandWaiting(), "nothing asked of the run");
        String waited = text(ExploreTools.waitFor(journal, Duration.ofSeconds(1)));
        assertTrue(waited.contains("Discover fault (claude): judged a toggle as navigation"), waited);
        assertTrue(waited.contains("SDK: 2.0.0-SNAPSHOT") && waited.contains("reload_sdk"), waited);
    }

    @Test
    void resumeIgnoreDropsAFault() {
        Journal journal = heldOn(stop("wrong_arrival-1"));
        run(command -> {
            if (command instanceof Journal.Command.Resume(String id, boolean ignore, List<String> skipped) && ignore) {
                journal().append(new ExploreEvent.Reloaded(id, 2, List.of(), 4));
                journal().append(new ExploreEvent.Fixed(id, "ignored", 5));
            }
        });

        String said = text(ExploreTools.resume(journal, "wrong_arrival-1", true, Duration.ofSeconds(20)));

        assertTrue(said.startsWith("Resumed: reloaded as generation 2"), said);
        assertTrue(JournalView.of(journal).open().isEmpty(), "the fault is dropped");
    }

    @Test
    void faultActionWritesTheCommand() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Began(true, 1));
        journal.append(new ExploreEvent.Stopped(stop("wrong_arrival-1"), false, 2));
        List<Journal.Command> taken = new CopyOnWriteArrayList<>();
        run(command -> {
            taken.add(command);
            if (command instanceof Journal.Command.Fault(String id, String why)) {
                journal().append(new ExploreEvent.Faulted(id, "claude", why, "", "", 3));
            }
        });

        String said = text(ExploreTools.fault(journal, "wrong_arrival-1", " judged a toggle\nas navigation ",
                Duration.ofSeconds(20)));

        assertTrue(said.startsWith("Held wrong_arrival-1 as a Discover fault: judged a toggle as navigation."), said);
        assertEquals(List.of(new Journal.Command.Fault("wrong_arrival-1", "judged a toggle as navigation")), taken);
        assertTrue(JournalView.of(journal).held(stop("wrong_arrival-1")));
    }

    @Test
    void aFaultIsRefusedOutOfDevMode() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Stopped(stop("wrong_arrival-1"), false, 2));

        AgentReply reply = ExploreTools.fault(journal, "wrong_arrival-1", "judged wrong", Duration.ofSeconds(1));

        assertTrue(reply.isRefused());
        assertTrue(text(reply).contains("released SDK"), text(reply));
        assertFalse(journal.commandWaiting());
    }

    /** A host that records what it was asked to reload, and answers {@code answer}. */
    private static StudioServices reloading(List<RunEntry> asked, DevReload answer) {
        return new StudioServices() {
            @Override public Path projectDir() { return Path.of("."); }
            @Override public Path resourcesDir() { return Path.of("."); }
            @Override public Theme theme() { return null; }
            @Override public Dialogs dialogs() { return Dialogs.NONE; }

            @Override
            public DevReload devReload(RunEntry entry) {
                asked.add(entry);
                return answer;
            }
        };
    }

    @Test
    void reloadSdkCallsTheHost() {
        Journal journal = heldOn(stop("wrong_arrival-1"));
        List<RunEntry> asked = new CopyOnWriteArrayList<>();

        AgentReply reply = ExploreTools.reloadSdk(journal, "wrong_arrival-1", reloading(asked, new DevReload.Started()));

        assertFalse(reply.isRefused(), text(reply));
        assertTrue(text(reply).contains("explore wait answers the replay"), text(reply));
        assertEquals(1, asked.size());
        assertEquals(SdkRuns.KEEP_EXPLORING, asked.getFirst().title());
        assertFalse(journal.commandWaiting(), "the host restarts the run, not a command");

        String refused = text(ExploreTools.reloadSdk(journal, "wrong_arrival-1",
                reloading(asked, new DevReload.Refused("com.botmaker.sdk.SdkPlugin — p/Gone is not on the classpath"))));
        assertTrue(refused.contains("The host did not reload: com.botmaker.sdk.SdkPlugin — p/Gone"), refused);
    }

    @Test
    void reloadSdkOnAPlainStopIsRefused() {
        Journal journal = journal();
        journal.append(new ExploreEvent.Began(true, 1));
        journal.append(new ExploreEvent.Stopped(stop("wrong_arrival-1"), false, 2));
        List<RunEntry> asked = new CopyOnWriteArrayList<>();

        AgentReply reply = ExploreTools.reloadSdk(journal, "wrong_arrival-1", reloading(asked, new DevReload.Started()));

        assertTrue(reply.isRefused());
        assertTrue(text(reply).contains("No Discover fault wrong_arrival-1 is held"), text(reply));
        assertTrue(asked.isEmpty(), "the host is not asked");
    }
}
