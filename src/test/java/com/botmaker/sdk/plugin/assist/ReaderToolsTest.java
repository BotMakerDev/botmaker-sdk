// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.PressTag;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopKind;
import com.botmaker.sdk.internal.explore.StopRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Reader's half of the explore tool (plan 2026-11-24-explore-agents, phase 5): look, rank_presses, its prompt. */
class ReaderToolsTest {

    @TempDir
    Path folder;

    private Journal journal(String goal) {
        Journal journal = new Journal(folder.resolve("run"));
        journal.append(new ExploreEvent.Began(false, goal, 1));
        return journal;
    }

    private static Stop stop(String id, String screen, String frame) {
        return new Stop(id, StopKind.UNKNOWN_SCREEN, screen, "", frame, List.of(), List.of(), Map.of(), List.of(),
                "a screen the map lacks", 2);
    }

    private static String text(AgentReply reply) {
        return reply.parts().stream().filter(p -> p.kind() == AgentReply.Kind.TEXT).map(AgentReply.Part::text)
                .collect(Collectors.joining("\n"));
    }

    private static List<ExploreEvent.Ranked> ranks(Journal journal) {
        return journal.events().stream().filter(ExploreEvent.Ranked.class::isInstance)
                .map(ExploreEvent.Ranked.class::cast).toList();
    }

    @Test
    void rankPressesWritesOneRankPerPressWithItsTag() {
        Journal journal = journal("sign in to Google");
        journal.append(new ExploreEvent.Stopped(stop("unknown_screen-1", "SETTINGS", ""), false, 2));

        AgentReply reply = ExploreTools.handle(new ExploreTools.RankPresses("unknown_screen-1", "IGNORED", List.of(
                new ExploreTools.Rank("Passwords & accounts", 10, "safe"),
                new ExploreTools.Rank("Google", 8, "safe"),
                new ExploreTools.Rank("Reset options", 0, "destructive"))), journal);

        assertTrue(!reply.isRefused(), text(reply));
        List<ExploreEvent.Ranked> ranks = ranks(journal);
        assertEquals(List.of("Passwords & accounts", "Google", "Reset options"),
                ranks.stream().map(ExploreEvent.Ranked::press).toList());
        assertTrue(ranks.stream().allMatch(r -> r.screen().equals("SETTINGS")));
        assertEquals(List.of(10, 8, 0), ranks.stream().map(ExploreEvent.Ranked::rank).toList());
        assertEquals(List.of(PressTag.SAFE, PressTag.SAFE, PressTag.DESTRUCTIVE),
                ranks.stream().map(ExploreEvent.Ranked::tag).toList());
    }

    @Test
    void aPressNotSaidWholeWritesNoRankAtAll() {
        Journal journal = journal("");
        journal.append(new ExploreEvent.Stopped(stop("unknown_screen-1", "SETTINGS", ""), false, 2));

        AgentReply reply = ExploreTools.handle(new ExploreTools.RankPresses("unknown_screen-1", null, List.of(
                new ExploreTools.Rank("Google", 8, "safe"),
                new ExploreTools.Rank("Ads", 3, "maybe"),
                new ExploreTools.Rank("Wi-Fi", 11, "safe"))), journal);

        assertTrue(reply.isRefused());
        assertTrue(text(reply).contains("Ads: tag safe, risky or destructive"), text(reply));
        assertTrue(text(reply).contains("Wi-Fi: rank 0 to 10"), text(reply));
        assertTrue(ranks(journal).isEmpty());
    }

    @Test
    void aStopOnAScreenTheMapLacksIsNamedBeforeItsPressesAreRanked() {
        Journal journal = journal("");
        journal.append(new ExploreEvent.Stopped(stop("unknown_screen-1", null, ""), false, 2));

        AgentReply reply = ExploreTools.handle(new ExploreTools.RankPresses("unknown_screen-1", null,
                List.of(new ExploreTools.Rank("Google", 8, "safe"))), journal);

        assertTrue(reply.isRefused());
        assertTrue(text(reply).contains("name it with apply_edit first"), text(reply));
        assertTrue(ranks(journal).isEmpty());

        // Named since: its presses are ranked on the name the Reader gave it.
        AgentReply named = ExploreTools.handle(new ExploreTools.RankPresses("unknown_screen-1", " SETTINGS ",
                List.of(new ExploreTools.Rank("Google", 8, "safe"))), journal);
        assertTrue(!named.isRefused(), text(named));
        assertEquals(List.of("SETTINGS"), ranks(journal).stream().map(ExploreEvent.Ranked::screen).toList());
    }

    @Test
    void lookGivesTheStopTheGoalAndItsFrame() throws Exception {
        Path frame = folder.resolve("frame.png");
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", frame.toFile());
        Journal journal = journal("sign in to Google");
        journal.append(new ExploreEvent.Stopped(stop("unknown_screen-1", "SETTINGS", frame.toString()), false, 2));

        AgentReply reply = ExploreTools.handle(new ExploreTools.Look("unknown_screen-1"), journal);

        String said = text(reply);
        assertTrue(said.startsWith("Goal: sign in to Google.\nunknown_screen-1: "), said);
        assertTrue(reply.parts().stream().anyMatch(p -> p.kind() == AgentReply.Kind.IMAGE), "the frame is shown");
        assertTrue(ExploreTools.handle(new ExploreTools.Look("nope-9"), journal).isRefused());
    }

    @Test
    void theRunSaysItsGoalInTheJournal() {
        assertEquals("sign in to Google", JournalView.of(journal("sign in to Google")).goal());
        Journal before = new Journal(folder.resolve("old"));
        before.append(new ExploreEvent.Began(false, 1));
        assertEquals("", JournalView.of(before).goal(), "an older line, with no goal, reads as none");
    }

    @Test
    void theReaderIsAskedAboutItsStopTowardTheGoal() {
        String prompt = WorkerRunner.prompt(WorkerRole.READER, stop("unknown_screen-1", "SETTINGS", ""),
                "sign in to Google");

        assertTrue(prompt.contains("stop unknown_screen-1 (" + StopKind.UNKNOWN_SCREEN.displayName() + " on SETTINGS)"),
                prompt);
        assertTrue(prompt.contains("The run's goal: sign in to Google."), prompt);
        assertTrue(prompt.contains("rank_presses") && prompt.contains("apply_edit") && prompt.contains("resume"));
        assertTrue(!prompt.contains("{"), "every slot is filled: " + prompt);
        assertTrue(WorkerRunner.prompt(WorkerRole.READER, stop("s-1", null, ""), "")
                .contains("none given; map what the screen is"));
    }

    @Test
    void onlyARunGivenAGoalGetsTheCrew() {
        AgentClient signedIn = new AgentClient() {
            @Override
            public String id() {
                return "fake";
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public List<String> command(String prompt, WorkerRole role, Path mcpConfig) {
                return List.of("true");
            }
        };

        assertEquals(Coordinator.Crew.SETTLER_ONLY, CoordinatorHost.crewFor(JournalView.of(journal("")), signedIn));
        Journal aimed = new Journal(folder.resolve("aimed"));
        aimed.append(new ExploreEvent.Began(false, "sign in to Google", 1));
        Coordinator.Crew crew = CoordinatorHost.crewFor(JournalView.of(aimed), signedIn);
        assertTrue(crew.present().containsAll(Set.of(StopRole.SETTLER, StopRole.CARTOGRAPHER)), crew.toString());
    }
}
