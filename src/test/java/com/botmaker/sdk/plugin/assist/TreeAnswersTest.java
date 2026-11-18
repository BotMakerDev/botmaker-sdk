// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Role;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Verdict;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopKind;
import com.botmaker.sdk.internal.explore.tree.Locator;
import com.botmaker.sdk.internal.explore.tree.Overlay;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.TreeAnswers.Planned;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a run settled off a tree, planned as the bot's screens, elements and texts, and the watch that resumes it. */
class TreeAnswersTest {

    private static final Locator BUY = new Locator(Role.BUTTON, "Buy now", null, null, null);
    private static final Locator DOCS = new Locator(Role.LINK, "Docs", null, null, null);
    private static final Locator HEADING = new Locator(Role.HEADING, "Pricing plans", null, null, null);
    private static final Locator TOGGLE = new Locator(Role.BUTTON, "Toggle", null, null, null);

    @Test
    void theFirstPageIsTheStartScreenWithItsInputs() {
        Planned planned = TreeAnswers.plan(ScreenGraph.of(Map.of()), List.of(
                new TreeAnswer.NewScreen("PricingPlans", "/pricing", List.of("/pricing"), List.of(HEADING), null),
                new TreeAnswer.NewInput("PricingPlans", "buyNow", BUY, "a buy"),
                new TreeAnswer.NewInput("PricingPlans", "docs", DOCS, null)), Map.of());

        assertEquals(1, planned.added().size());
        TreeAnswers.Added added = planned.added().getFirst();
        assertEquals("PRICING_PLANS", added.constant());
        Setup setup = added.screen().setup();
        assertTrue(setup.isStart(), "a map's first screen");
        assertEquals("/pricing", setup.route());
        assertEquals(List.of(HEADING.element()), setup.elements());
        Button buy = (Button) added.screen().input("buyNow").orElseThrow();
        assertEquals(Verdict.never("a buy"), buy.verdict());
        assertInstanceOf(Verdict.Free.class, added.screen().input("docs").orElseThrow().verdict());
        assertEquals(3, planned.elements().size(), planned.elements().toString());
        assertTrue(planned.words().containsAll(List.of(Words.of("Buy now"), Words.of("Docs"))));
        assertTrue(planned.changes().isEmpty());
        assertTrue(planned.skipped().isEmpty(), planned.skipped().toString());
    }

    @Test
    void aDialogAPressOpensIsReachedByItAndTheNoteMarksItsInput() {
        ScreenGraph graph = map();
        Planned planned = TreeAnswers.plan(graph, List.of(
                new TreeAnswer.NewScreen("Signup", "/", List.of("/"), List.of(),
                        new Overlay(Overlay.Kind.DIALOG, "Signup")),
                new TreeAnswer.NewWay("A", "open", "Signup"),
                new TreeAnswer.InputNote("A", "toggle", "Changes the theme")),
                Map.of("TOGGLE", TOGGLE.element()));

        Setup dialog = planned.added().getFirst().screen().setup();
        assertFalse(dialog.isPopup(), "a press opens it: a screen with its way, no popup");
        assertFalse(dialog.isStart());
        assertNotNull(dialog.from(), "its way in");
        MapScreen a = changed(graph, planned, "A");
        assertEquals("toggle: Changes the theme", a.setup().notes());
        assertTrue(a.input("toggle").orElseThrow().isTried(), "pressed and noted");
        assertTrue(planned.elements().isEmpty(), "the bot's own TOGGLE kept: " + planned.elements());
    }

    @Test
    void aSiblingPageWidensTheRouteAndAWayToAKnownScreenIsAdded() {
        Map<String, MapScreen> screens = new LinkedHashMap<>(map().screens());
        screens.put("CASE", new MapScreen(Setup.named("Case").route("/case/a").examples(List.of("/case/a")),
                Map.of()));
        ScreenGraph graph = ScreenGraph.of(screens);
        Planned planned = TreeAnswers.plan(graph, List.of(
                new TreeAnswer.NewRoute("CASE", "/case/{slug}", List.of("/case/a", "/case/b")),
                new TreeAnswer.NewWay("A", "open", "Case")), Map.of());

        MapScreen kase = changed(graph, planned, "CASE");
        assertEquals("/case/{slug}", kase.setup().route());
        assertEquals(List.of("/case/a", "/case/b"), kase.setup().examples());
        assertEquals(1, kase.setup().alsoFrom().size() + (kase.setup().from() == null ? 0 : 1), "its way in");
        assertTrue(planned.skipped().isEmpty(), planned.skipped().toString());
    }

    @Test
    void anAnswerOnAScreenTheMapLacksIsSkipped() {
        Planned planned = TreeAnswers.plan(map(), List.of(new TreeAnswer.InputNote("GONE", "x", "y")), Map.of());

        assertTrue(planned.empty());
        assertEquals(1, planned.skipped().size());
    }

    @Test
    void theWatchWritesASettledStopOnceAndResumesIt(@TempDir Path folder) {
        Journal journal = new Journal(folder);
        journal.append(new ExploreEvent.Began(1));
        List<TreeAnswer> answers = List.of(new TreeAnswer.InputNote("A", "toggle", "Does nothing a visitor sees"));
        journal.append(new ExploreEvent.Settled("settled-1", answers, 2));
        journal.append(new ExploreEvent.Stopped(new Stop("settled-1", StopKind.SETTLED, "A", "A.toggle", null,
                List.of(), List.of(), Map.of(), List.of(), "notes", 2), false, 2));
        List<String> written = new ArrayList<>();
        Set<String> done = new HashSet<>();

        TreeAnswerWatch.round(journal, done, (id, said) -> {
            written.add(id + " " + said.size());
            return Optional.empty();
        });
        assertEquals(Optional.of(new Journal.Command.Resume("settled-1", false)), journal.takeCommand());
        TreeAnswerWatch.round(journal, done, (id, said) -> {
            written.add(id);
            return Optional.empty();
        });

        assertEquals(List.of("settled-1 1"), written, "written once");
        assertFalse(journal.commandWaiting());
    }

    /** A, the start, with a button {@code open} and a free {@code toggle}. */
    private static ScreenGraph map() {
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("open", Button.of(new Locator(Role.BUTTON, "Open", null, null, null).element()).free());
        inputs.put("toggle", Button.of(TOGGLE.element()).free());
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", new MapScreen(Setup.named("A").start(), inputs));
        return ScreenGraph.of(screens);
    }

    /** The screen {@code constant} of {@code graph} with each of the plan's changes of it applied. */
    private static MapScreen changed(ScreenGraph graph, Planned planned, String constant) {
        MapScreen screen = graph.screens().get(constant);
        for (TreeAnswers.Change change : planned.changes()) {
            if (change.constant().equals(constant)) screen = change.change().apply(screen);
        }
        return screen;
    }
}
