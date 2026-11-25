// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Field;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Role;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Verdict;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.internal.bot.BotConstants;
import com.botmaker.sdk.internal.bot.BotConstants.InputName;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopKind;
import com.botmaker.sdk.internal.explore.tree.Effect;
import com.botmaker.sdk.internal.explore.tree.Locator;
import com.botmaker.sdk.internal.explore.tree.Overlay;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.screens.InputStatus;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** An input left to the crew is written with no verdict; the guard's refusal still is one. */
    @Test
    void anUnjudgedInputIsWrittenWithNoVerdict() {
        Planned planned = TreeAnswers.plan(ScreenGraph.of(Map.of()), List.of(
                new TreeAnswer.NewScreen("Home", null, List.of(), List.of(HEADING), null),
                new TreeAnswer.NewInput("Home", "toggle", TOGGLE, null, null, true),
                new TreeAnswer.NewInput("Home", "buyNow", BUY, "a buy", null, true)), Map.of());

        MapScreen home = planned.added().getFirst().screen();
        assertFalse(home.input("toggle").orElseThrow().verdict() instanceof Verdict.Free);
        assertEquals(Verdict.never("a buy"), home.input("buyNow").orElseThrow().verdict());
    }

    /** A pane swapped inside a screen is written as its panel, reached by the press that swapped it. */
    @Test
    void aPanelIsWrittenInsideItsScreen() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", new MapScreen(Setup.named("Home").start(), Map.of("docs",
                Button.of(DOCS.element()).free())));
        Planned planned = TreeAnswers.plan(ScreenGraph.of(screens), List.of(
                new TreeAnswer.NewScreen("Wifi", null, List.of(), List.of(HEADING), null, false, "HOME"),
                new TreeAnswer.NewWay("HOME", "docs", "Wifi")), Map.of());

        Setup wifi = planned.added().getFirst().screen().setup();
        assertTrue(wifi.isPanel(), wifi.toString());
        assertNotNull(wifi.from());
        assertTrue(planned.skipped().isEmpty(), planned.skipped().toString());
    }

    @Test
    void aRowFoundByTheTextItHoldsKeepsThoseWords() {
        Locator row = new Locator(null, null, null, null, null, "Passwords & accounts");
        Planned planned = TreeAnswers.plan(ScreenGraph.of(Map.of()), List.of(
                new TreeAnswer.NewScreen("Settings", null, List.of(), List.of(HEADING), null),
                new TreeAnswer.NewInput("Settings", "passwordsAccounts", row, null)), Map.of());

        assertTrue(planned.words().contains(Words.of("Passwords & accounts")), planned.words().toString());
    }

    /** A new input's rendered picture becomes its element's shot, copied in by the write; a held element keeps its own. */
    @Test
    void aRenderedPictureIsTheNewElementsShot() {
        Planned planned = TreeAnswers.plan(ScreenGraph.of(Map.of()), List.of(
                new TreeAnswer.NewScreen("Pricing", "/pricing", List.of("/pricing"), List.of(), null),
                new TreeAnswer.NewInput("Pricing", "buyNow", BUY, null, "/run/inputs/Pricing.buyNow.png"),
                new TreeAnswer.NewInput("Pricing", "docs", DOCS, null, "/run/inputs/Pricing.docs.png")),
                Map.of("DOCS", DOCS.element().shot("elements/docs.png")));

        Button buy = (Button) planned.added().getFirst().screen().input("buyNow").orElseThrow();
        String shot = buy.element().shot();
        assertEquals(Map.of(shot, "/run/inputs/Pricing.buyNow.png"), planned.pictures());
        assertEquals(shot, planned.elements().values().iterator().next().shot());
        Button docs = (Button) planned.added().getFirst().screen().input("docs").orElseThrow();
        assertEquals("elements/docs.png", docs.element().shot());
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

    /** An input pressed again says only what it does now: a new note replaces its old one, a way drops it. */
    @Test
    void aPressAgainReplacesWhatTheInputsNoteSaid() {
        Map<String, MapScreen> screens = new LinkedHashMap<>(map().screens());
        screens.compute("A", (c, s) -> s.setup(x -> x.notes("open: Fails: blocked write: POST /monitoring\n"
                + "toggle: Fails: console error: CORS\nopener: Does nothing a visitor sees")));
        screens.put("CASE", new MapScreen(Setup.named("Case").route("/case"), Map.of()));
        ScreenGraph graph = ScreenGraph.of(screens);
        Planned planned = TreeAnswers.plan(graph, List.of(new TreeAnswer.NewWay("A", "open", "Case"),
                new TreeAnswer.InputNote("A", "toggle", "Changes the theme")), Map.of());

        assertEquals("opener: Does nothing a visitor sees\ntoggle: Changes the theme",
                changed(graph, planned, "A").setup().notes());
    }

    /** An input an older read made twice goes with its note; the one kept stays as it was. */
    @Test
    void anInputMadeTwiceGoesWithItsNote() {
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("home", Button.of(new Locator(Role.LINK, "Home", null, null, null).element()).free());
        inputs.put("home2", Button.of(new Locator(Role.BUTTON, "Home", null, null, null).element()).free().tried());
        Map<String, MapScreen> screens = new LinkedHashMap<>(map().screens());
        screens.put("CASE", new MapScreen(Setup.named("Case").route("/case").from(BotConstants.input("A", "open"))
                .notes("home2: Does nothing a visitor sees"), inputs));
        ScreenGraph graph = ScreenGraph.of(screens);
        Planned planned = TreeAnswers.plan(graph, List.of(new TreeAnswer.SameInput("CASE", "home2", "home")),
                Map.of());

        MapScreen kase = changed(graph, planned, "CASE");
        assertEquals(List.of("home"), List.copyOf(kase.inputs().keySet()));
        assertEquals(null, kase.setup().notes(), "its note gone with it");
    }

    /**
     * An input made twice that a way still goes through is merged all the same: the way goes through the input
     * kept, once, and the copy is taken off.
     */
    @Test
    void aWayThroughTheCopyGoesThroughTheInputKept() {
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("pricing", Button.of(new Locator(Role.LINK, "Pricing", null, null, null).element()).free());
        inputs.put("pricing2", Button.of(new Locator(Role.BUTTON, "Pricing", null, null, null).element()).free());
        Map<String, MapScreen> screens = new LinkedHashMap<>(map().screens());
        screens.put("HOME", new MapScreen(Setup.named("Home").route("/").from(BotConstants.input("A", "open")),
                inputs));
        screens.put("PRICING", new MapScreen(Setup.named("Pricing").route("/pricing")
                .from(BotConstants.input("HOME", "pricing2"))
                .alsoFrom(List.of(Way.of(BotConstants.input("HOME", "pricing")))), Map.of()));
        ScreenGraph graph = ScreenGraph.of(screens);
        Planned planned = TreeAnswers.plan(graph, List.of(new TreeAnswer.SameInput("HOME", "pricing2", "pricing")),
                Map.of());

        MapScreen pricing = changed(graph, planned, "PRICING");
        assertEquals(List.of(new InputName("HOME", "pricing")),
                pricing.ways().stream().map(w -> BotConstants.inputName(w.via())).toList());
        assertEquals(List.of("pricing"), List.copyOf(changed(graph, planned, "HOME").inputs().keySet()));
    }

    /** A link that became the way back drops its old note too, so no run presses it again for it. */
    @Test
    void aWayBackDropsTheInputsNote() {
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("home", Button.of(new Locator(Role.LINK, "Home", null, null, null).element()).free().tried());
        Map<String, MapScreen> screens = new LinkedHashMap<>(map().screens());
        screens.put("CASE", new MapScreen(Setup.named("Case").route("/case").from(BotConstants.input("A", "open"))
                .notes("home: Fails: blocked write: POST /monitoring"), inputs));
        ScreenGraph graph = ScreenGraph.of(screens);
        Planned planned = TreeAnswers.plan(graph, List.of(new TreeAnswer.NewWay("CASE", "home", "A")), Map.of());

        MapScreen kase = changed(graph, planned, "CASE");
        assertEquals(null, kase.setup().notes(), "no note left");
        assertTrue(kase.setup().back() != null, "its way back");
    }

    /**
     * A page settled off the tree and reached later by two links of A hangs under A by the first: a link of it back to
     * A, the start, is its way back, never a way into the start that is refused.
     */
    @Test
    void aPageReachedOnlyFromAGoesBackToIt() {
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("home", Button.of(new Locator(Role.LINK, "Home", null, null, null).element()).free().tried());
        Map<String, MapScreen> screens = new LinkedHashMap<>(map().screens());
        screens.put("CASE", new MapScreen(Setup.named("Case").route("/case").alsoFrom(List.of(
                Way.of(BotConstants.input("A", "open")), Way.of(BotConstants.input("A", "toggle")))), inputs));
        ScreenGraph graph = ScreenGraph.of(screens);
        Planned planned = TreeAnswers.plan(graph, List.of(new TreeAnswer.NewWay("CASE", "home", "A")), Map.of());

        MapScreen kase = changed(graph, planned, "CASE");
        assertTrue(planned.skipped().isEmpty(), planned.skipped().toString());
        assertEquals(BotConstants.input("A", "open"), kase.setup().fromInput(), "hung under A by its first way");
        assertEquals(1, kase.setup().alsoFrom().size());
        assertTrue(kase.setup().back() != null, "its way back");
    }

    /**
     * A link two screens down back to the start (a site's Home in every page's header) is no way and no fault: the
     * input is noted as leading there and tried, and nothing is skipped.
     */
    @Test
    void aLinkToTheStartFromDeeperIsNotedNotSkipped() {
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("home", Button.of(new Locator(Role.LINK, "Home", null, null, null).element()).free());
        inputs.put("deeper", Button.of(new Locator(Role.LINK, "Deeper", null, null, null).element()).free());
        Map<String, MapScreen> screens = new LinkedHashMap<>(map().screens());
        screens.put("CASE", new MapScreen(Setup.named("Case").route("/case").from(BotConstants.input("A", "open"))
                .back(BotConstants.input("CASE", "home")), inputs));
        screens.put("DEEP", new MapScreen(Setup.named("Deep").route("/deep")
                .from(BotConstants.input("CASE", "deeper")), inputs));
        ScreenGraph graph = ScreenGraph.of(screens);
        Planned planned = TreeAnswers.plan(graph, List.of(new TreeAnswer.NewWay("DEEP", "home", "A")), Map.of());

        assertTrue(planned.skipped().isEmpty(), planned.skipped().toString());
        MapScreen deep = changed(graph, planned, "DEEP");
        assertEquals("home: Leads to A", deep.setup().notes());
        assertTrue(deep.input("home").orElseThrow().isTried(), "tried, so no run presses it as new");
        assertEquals(BotConstants.input("CASE", "deeper"), deep.setup().fromInput(), "its way in unchanged");
    }

    /** A way to a page no way reached yet is its tree's way in. */
    @Test
    void theFirstWayToAPageIsItsTreesWayIn() {
        Map<String, MapScreen> screens = new LinkedHashMap<>(map().screens());
        screens.put("CASE", new MapScreen(Setup.named("Case").route("/case"), Map.of()));
        ScreenGraph graph = ScreenGraph.of(screens);
        Planned planned = TreeAnswers.plan(graph, List.of(new TreeAnswer.NewWay("A", "open", "Case")), Map.of());

        assertEquals(BotConstants.input("A", "open"), changed(graph, planned, "CASE").setup().fromInput());
    }

    @Test
    void aLinkUpToTheScreenItWasReachedFromIsItsWayBack() {
        Map<String, MapScreen> screens = new LinkedHashMap<>(map().screens());
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("up", Button.of(new Locator(Role.LINK, "All", null, null, null).element()).free());
        screens.put("CASE", new MapScreen(Setup.named("Case").fromWay(Way.of(BotConstants.input("A", "open"))),
                inputs));
        ScreenGraph graph = ScreenGraph.of(screens);

        Planned planned = TreeAnswers.plan(graph, List.of(new TreeAnswer.NewWay("CASE", "up", "A")), Map.of());

        assertTrue(planned.skipped().isEmpty(), planned.skipped().toString());
        assertNotNull(changed(graph, planned, "CASE").setup().back(), "its way back");
    }

    @Test
    void aFormsRuleIsTheWhenOfTheInputItEnablesAndItsNote() {
        ScreenGraph graph = map();
        Planned planned = TreeAnswers.plan(graph, List.of(new TreeAnswer.InputRule("A", "open", List.of(
                TreeAnswer.FieldCheck.valid("toggle", Field.Kind.EMAIL),
                TreeAnswer.FieldCheck.atLeast("toggle", 8)), "Enabled when toggle holds an email")), Map.of());

        assertTrue(planned.skipped().isEmpty(), planned.skipped().toString());
        MapScreen a = changed(graph, planned, "A");
        Condition.All when = assertInstanceOf(Condition.All.class, a.input("open").orElseThrow().when());
        Field.Valid valid = assertInstanceOf(Field.Valid.class, when.conditions().get(0));
        assertEquals(BotConstants.input("A", "toggle"), valid.input().get(), "the sibling, named lazily");
        assertEquals(Field.Kind.EMAIL, valid.kind());
        assertEquals(8, assertInstanceOf(Field.AtLeast.class, when.conditions().get(1)).characters());
        assertEquals("open: Enabled when toggle holds an email", a.setup().notes());

        // A second rule on the same input keeps the first: the author's or an earlier run's .when is never replaced.
        ScreenGraph ruled = ScreenGraph.of(Map.of("A", a));
        Planned again = TreeAnswers.plan(ruled, List.of(new TreeAnswer.InputRule("A", "open",
                List.of(TreeAnswer.FieldCheck.filled("toggle")), "Enabled once toggle is filled")), Map.of());
        assertEquals(when, changed(ruled, again, "A").input("open").orElseThrow().when());
    }

    @Test
    void anAnswerOnAScreenTheMapLacksIsSkipped() {
        Planned planned = TreeAnswers.plan(map(), List.of(new TreeAnswer.InputNote("GONE", "x", "y")), Map.of());

        assertTrue(planned.empty());
        assertEquals(1, planned.skipped().size());
    }

    @Test
    void discoversWebsiteBecomesTheBotsLaunchTarget() {
        Planned planned = TreeAnswers.plan(ScreenGraph.of(Map.of()),
                List.of(new TreeAnswer.NewTarget(" web:https://site.test/ ")), Map.of());

        assertFalse(planned.empty(), "a target alone is written");
        assertEquals(List.of("web:https://site.test/"), planned.targets());
        assertEquals(List.of(), planned.skipped());
    }

    @Test
    void theWatchWritesASettledStopOnceAndResumesIt(@TempDir Path folder) {
        Journal journal = new Journal(folder);
        journal.append(new ExploreEvent.Began(false, 1));
        List<TreeAnswer> answers = List.of(new TreeAnswer.InputNote("A", "toggle", "Does nothing a visitor sees"));
        journal.append(new ExploreEvent.Settled("settled-1", answers, 2));
        journal.append(new ExploreEvent.Stopped(new Stop("settled-1", StopKind.SETTLED, "A", "A.toggle", null,
                List.of(), List.of(), Map.of(), List.of(), "notes", 2), false, 2));
        List<String> written = new ArrayList<>();
        Set<String> done = new HashSet<>();

        TreeAnswerWatch.round(journal, done, (id, said) -> {
            written.add(id + " " + said.size());
            return new TreeAnswers.Written(Optional.empty(), List.of());
        });
        assertEquals(Optional.of(new Journal.Command.Resume("settled-1", false)), journal.takeCommand());
        TreeAnswerWatch.round(journal, done, (id, said) -> {
            written.add(id);
            return new TreeAnswers.Written(Optional.empty(), List.of());
        });

        assertEquals(List.of("settled-1 1"), written, "written once");
        assertFalse(journal.commandWaiting());
    }

    @Test
    void theWatchResumesWithWhatItSkipped(@TempDir Path folder) {
        Journal journal = new Journal(folder);
        journal.append(new ExploreEvent.Began(true, 1));
        journal.append(new ExploreEvent.Settled("settled-1", List.of(new TreeAnswer.InputNote("GONE", "x", "y")), 2));
        journal.append(new ExploreEvent.Stopped(new Stop("settled-1", StopKind.SETTLED, "A", "A.toggle", null,
                List.of(), List.of(), Map.of(), List.of(), "notes", 2), false, 2));

        TreeAnswerWatch.round(journal, new HashSet<>(), (id, said) -> new TreeAnswers.Written(Optional.empty(),
                List.of("x: no screen GONE\nin the map.")));

        assertEquals(Optional.of(new Journal.Command.Resume("settled-1", false, List.of("x: no screen GONE in the map."))),
                journal.takeCommand(), "one line each, read back as written");
    }

    @Test
    void applyReturnsWhatItSkipped() {
        Planned planned = TreeAnswers.plan(map(), List.of(new TreeAnswer.InputNote("GONE", "x", "y")), Map.of());

        assertEquals(List.of("x: no screen GONE in the map."), planned.skipped());
    }

    @Test
    void oneUnwritableValueKeepsTheOthers() {
        Planned planned = TreeAnswers.plan(map(), List.of(new TreeAnswer.InputNote("A", "toggle", "Does nothing"),
                new TreeAnswer.NewScreen("Shop", "/shop", List.of("/shop"), List.of(), null)), Map.of());
        List<String> written = new ArrayList<>();

        List<String> unwritten = TreeAnswers.write(planned, new TreeAnswers.Writes() {
            @Override
            public Optional<String> text(Words words) {
                return Optional.empty();
            }

            @Override
            public Optional<String> elements() {
                return Optional.empty();
            }

            @Override
            public Optional<String> element(String name, Element element) {
                return Optional.empty();
            }

            @Override
            public Optional<String> screen(TreeAnswers.Added added) {
                return Optional.of("it does not compile");
            }

            @Override
            public Optional<String> change(TreeAnswers.Change change) {
                written.add(change.constant());
                return Optional.empty();
            }
        });

        assertEquals(List.of("could not write the screen " + planned.added().getFirst().constant()
                + ": it does not compile"), unwritten);
        assertEquals(List.of("A"), written, "the change after it written all the same");
    }

    /** A, the start, with a button {@code open} and a free {@code toggle}. */
    /**
     * A press that failed writes the input's own trait, why it failed, and a plain note; a later press that takes clears
     * it. An old {@code field: Fails: x} note is not read: that input is untried.
     */
    @Test
    void aFailWritesTheTypedTraitAndAPlainNote() {
        Map<String, MapScreen> screens = new LinkedHashMap<>(map().screens());
        screens.compute("A", (c, s) -> s.setup(x -> x.notes("open: Fails: console error: CORS")));
        ScreenGraph graph = ScreenGraph.of(screens);
        assertEquals(InputStatus.UNTRIED, graph.status("A", "open"), "an old note line is not read");

        Planned failed = TreeAnswers.plan(graph, List.of(TreeAnswer.InputNote.of("A", "toggle",
                new Effect.Fails("blocked write: POST /monitoring"))), Map.of());
        MapScreen a = changed(graph, failed, "A");
        assertEquals("blocked write: POST /monitoring", a.input("toggle").orElseThrow().failed());
        assertTrue(a.setup().notes().contains("toggle: press failed, blocked write: POST /monitoring"),
                a.setup().notes());
        Map<String, MapScreen> after = new LinkedHashMap<>(screens);
        after.put("A", a);
        assertEquals(InputStatus.FAILED, ScreenGraph.of(after).status("A", "toggle"));

        ScreenGraph failedGraph = ScreenGraph.of(after);
        Planned took = TreeAnswers.plan(failedGraph, List.of(new TreeAnswer.InputNote("A", "toggle",
                "Changes the theme")), Map.of());
        assertNull(changed(failedGraph, took, "A").input("toggle").orElseThrow().failed(), "a press that took");
    }

    /** A new screen its tree showed whole is written all cut; one with a part that scrolls is not. */
    @Test
    void aScreenItsTreeShowedWholeIsWrittenAllCut() {
        Planned planned = TreeAnswers.plan(ScreenGraph.of(Map.of()), List.of(
                new TreeAnswer.NewScreen("Shop", "/shop", List.of("/shop"), List.of(), null, true),
                new TreeAnswer.NewScreen("List", "/list", List.of("/list"), List.of(), null, false)), Map.of());

        assertTrue(planned.added().get(0).screen().setup().isAllCut());
        assertFalse(planned.added().get(1).screen().setup().isAllCut());
    }

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
