// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore.tree;

import com.botmaker.sdk.api.screens.Role;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import com.botmaker.shared.web.Guard;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a screen's tree settles on its own, over hand-built trees of a website and of an app. */
class TreeAnswerTest {

    private static final Rectangle BOX = new Rectangle(0, 0, 100, 20);
    private static final Set<Trait> PRESSABLE = EnumSet.of(Trait.ENABLED, Trait.CLICKABLE);

    @Test
    void aPageInputsAreFoundAsATesterFindsThemEachOnce() {
        UiTree page = page(
                web("banner", "", Set.of(), web("link", "Pricing", PRESSABLE)),
                web("main", "", Set.of(),
                        web("heading", "Build bots", Set.of()),
                        web("button", "Get started", PRESSABLE),
                        new UiNode("email-field", "", "", "web:textbox", "", BOX, PRESSABLE, List.of()),
                        web("button", "Disabled", EnumSet.of(Trait.ENABLED))),
                web("contentinfo", "", Set.of(), web("link", "Pricing", PRESSABLE)));

        List<Locator.Located> inputs = Locator.inputs(page);

        assertEquals(List.of("link \"Pricing\" [0]", "button \"Get started\"", "#email-field", "link \"Pricing\" [1]"),
                inputs.stream().map(l -> l.locator().line()).toList());
        assertEquals(Role.LINK, inputs.getFirst().locator().role());
    }

    @Test
    void siblingPagesOfOneTemplateAreOneRouteWithThreeExamples() {
        List<String> skeleton = List.of("banner", "main", "heading", "contentinfo");
        List<RouteGuess.Page> pages = List.of(
                new RouteGuess.Page("/case-studies/stormforge", skeleton),
                new RouteGuess.Page("/case-studies/hogwarts-legacy/", skeleton),
                new RouteGuess.Page("/case-studies/alpha?ref=x", skeleton),
                new RouteGuess.Page("/case-studies/beta", skeleton));

        RouteGuess.Route route = RouteGuess.of(pages).orElseThrow();

        assertEquals("/case-studies/{slug}", route.pattern());
        assertEquals(List.of("/case-studies/stormforge", "/case-studies/hogwarts-legacy", "/case-studies/alpha"),
                route.examples());
        assertTrue(route.matches("/case-studies/zeta"));
        assertEquals(route.pattern(), RouteGuess.routeOf("/case-studies/zeta", List.of(route)).pattern());
        assertEquals("/pricing", RouteGuess.routeOf("/pricing/", List.of(route)).pattern());
    }

    @Test
    void pagesOfTwoTemplatesOrTwoVaryingSegmentsAreNoRoute() {
        assertTrue(RouteGuess.of(List.of(new RouteGuess.Page("/a/x", List.of("main")),
                new RouteGuess.Page("/a/y", List.of("main", "form")))).isEmpty());
        assertTrue(RouteGuess.of(List.of(new RouteGuess.Page("/a/x", List.of()),
                new RouteGuess.Page("/b/y", List.of()))).isEmpty());
        assertTrue(RouteGuess.of(List.of(new RouteGuess.Page("/a/x", List.of()))).isEmpty());
    }

    @Test
    void aSkeletonKeepsLandmarksAndHeadingsTextsAside() {
        UiTree one = page(web("main", "", Set.of(), web("heading", "Stormforge", Set.of()),
                web("paragraph", "A game", Set.of())));
        UiTree two = page(web("main", "", Set.of(), web("heading", "Hogwarts Legacy", Set.of())));
        assertEquals(RouteGuess.skeleton(one), RouteGuess.skeleton(two));
        assertEquals(List.of("main", "heading"), RouteGuess.skeleton(one));
    }

    @Test
    void eachPressGetsTheEffectItsTreesShow() {
        UiTree home = page(web("main", "", Set.of(), web("button", "Talk to us", PRESSABLE),
                web("checkbox", "News", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE, Trait.CHECKABLE))));
        UiTree dialog = page(web("main", "", Set.of(), web("button", "Talk to us", PRESSABLE),
                web("checkbox", "News", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE, Trait.CHECKABLE))),
                web("dialog", "Talk to us", Set.of(), web("button", "Close", PRESSABLE)));
        UiTree checked = page(web("main", "", Set.of(), web("button", "Talk to us", PRESSABLE),
                web("checkbox", "News", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE, Trait.CHECKABLE, Trait.CHECKED))));

        Effect.State before = Effect.State.of(home, "/");
        assertEquals(new Effect.Navigates("/pricing"),
                Effect.judge(before, Effect.State.of(home, "/pricing/"), List.of()));
        assertEquals(new Effect.Opens(new Overlay(Overlay.Kind.DIALOG, "Talk to us")),
                Effect.judge(before, Effect.State.of(dialog, "/"), List.of()));
        assertEquals(new Effect.Exits("https://x.com/nunu"), Effect.judge(before, before,
                List.of(new Guard.Stopped(Guard.Kind.EXIT, "https://x.com/nunu", "GET"))));
        assertEquals(new Effect.Fails("blocked write: POST https://nunu.ai/api/signup"), Effect.judge(before, before,
                List.of(new Guard.Stopped(Guard.Kind.WRITE, "https://nunu.ai/api/signup", "POST"))));
        // A page that came is the press's way, whatever telemetry the guard blocked beside it.
        assertEquals(new Effect.Navigates("/pricing"), Effect.judge(before, Effect.State.of(home, "/pricing"),
                List.of(new Guard.Stopped(Guard.Kind.WRITE, "https://nunu.ai/monitoring", "POST"))));
        assertEquals(new Effect.ShowsError("Enter a valid email"), Effect.judge(before,
                new Effect.State(home, "/", List.of("Enter a valid email"), List.of()), List.of()));
        assertEquals(new Effect.Changes(List.of("checkbox \"News\" checkable checked clickable enabled")),
                Effect.judge(before, Effect.State.of(checked, "/"), List.of()));
        assertEquals(new Effect.Nothing(), Effect.judge(before, Effect.State.of(home, "/#top"), List.of()));
        Effect closed = Effect.judge(Effect.State.of(dialog, "/"), before, List.of());
        assertInstanceOf(Effect.Changes.class, closed);
        assertTrue(((Effect.Changes) closed).parts().contains("closes dialog \"Talk to us\""));
    }

    @Test
    void anUnknownPageBecomesAScreenNamedAndMarkedByItsRouteAlone() {
        UiTree page = page(web("banner", "", Set.of(), web("link", "Home", PRESSABLE)),
                web("main", "", Set.of(), web("heading", "Our case studies", Set.of()),
                        web("heading", "Stormforge", Set.of()), web("button", "Buy now", PRESSABLE)));

        TreeAnswer.NewScreen screen = TreeAnswer.screen(page, "/case-studies/{slug}",
                List.of("/case-studies/stormforge"), Set.of("CaseStudiesPage"));

        assertEquals("CaseStudiesPage2", screen.name());
        assertEquals("/case-studies/{slug}", screen.route());
        // Its headings are content a page changes (a hero cycling its headline): never what tells it.
        assertTrue(screen.markers().isEmpty());
        assertTrue(screen.overlay().none());
        assertEquals("Home", TreeAnswer.screen(page(web("heading", "Solve your game testing", Set.of())), "/",
                List.of("/"), Set.of()).name());

        List<TreeAnswer.NewInput> inputs = TreeAnswer.inputs(screen.name(), page, Set.of(), Set.of("home"),
                input -> "Buy now".equals(input.locator().name()) ? Optional.of("its name says buy") : Optional.empty());
        assertEquals(List.of("home2", "buyNow"), inputs.stream().map(TreeAnswer.NewInput::field).toList());
        assertNull(inputs.getFirst().never());
        assertEquals("its name says buy", inputs.get(1).never());
    }

    @Test
    void aDialogIsAnOverlayScreenNamedAndMarkedByItself() {
        UiTree page = page(web("main", "", Set.of(), web("heading", "Home", Set.of())),
                web("dialog", "Talk to us", Set.of(), web("button", "Close", PRESSABLE)));

        TreeAnswer.NewScreen screen = TreeAnswer.screen(page, "/", List.of("/"), Set.of());

        assertEquals("TalkToUs", screen.name());
        assertEquals(Overlay.Kind.DIALOG, screen.overlay().kind());
        assertEquals("dialog \"Talk to us\"", screen.markers().getFirst().line());
    }

    @Test
    void aWayIsWrittenOnlyToAScreenTheBotHasAndAnyOtherEffectIsANote() {
        Effect goes = new Effect.Navigates("/pricing");
        assertEquals(Optional.of(new TreeAnswer.NewWay("Home", "pricing", "Pricing")),
                TreeAnswer.effect("Home", "pricing", goes, e -> Optional.of("Pricing")));
        assertTrue(TreeAnswer.effect("Home", "pricing", goes, e -> Optional.empty()).isEmpty());
        assertEquals(Optional.of(new TreeAnswer.InputNote("Home", "x", "Leaves the site for https://x.com")),
                TreeAnswer.effect("Home", "x", new Effect.Exits("https://x.com"), e -> Optional.empty()));
    }

    /** An Android screen as uiautomator recorded it: ids first, labels, then texts; no route, no overlay. */
    @Test
    void anAppStopIsAnsweredFromItsRecordedTree() {
        UiTree shop = new UiTree(List.of(app("android.widget.FrameLayout", "", "", "", Set.of(),
                app("android.widget.TextView", "com.game:id/title", "Shop", "", Set.of()),
                app("android.widget.ImageButton", "com.game:id/close", "", "Close", PRESSABLE),
                app("android.widget.Button", "", "Buy gems", "", PRESSABLE),
                app("android.widget.Button", "", "", "Settings", PRESSABLE),
                app("android.widget.EditText", "", "", "", Set.of(Trait.ENABLED)))));

        TreeAnswer.NewScreen screen = TreeAnswer.screen(shop, null, List.of(), Set.of());
        List<TreeAnswer.NewInput> inputs = TreeAnswer.inputs(screen.name(), shop, Set.of(), Set.of(),
                l -> Optional.empty());

        assertEquals("Screen", screen.name());
        assertNull(screen.route());
        assertEquals(List.of("#title"), screen.markers().stream().map(Locator::line).toList());
        assertEquals(List.of("#close", "text \"Buy gems\"", "\"Settings\""),
                inputs.stream().map(i -> i.locator().line()).toList());
        assertFalse(inputs.stream().anyMatch(i -> i.locator().role() != null));
        assertEquals(new Effect.Nothing(), Effect.judge(Effect.State.of(shop, ""), Effect.State.of(shop, ""), null));
    }

    @Test
    void whatTheReviewFoundStaysFixed() {
        // A one-segment route names its screen without a heading.
        assertEquals("Page", TreeAnswer.screen(page(), "/{slug}", List.of("/a"), Set.of()).name());
        // Keywords and java.lang names are never a field or a class.
        assertEquals("newInput", TreeAnswer.fieldName("New"));
        assertEquals("StringScreen", TreeAnswer.className("string"));
        // A hash route is a path; any other fragment is not.
        assertEquals("/pricing", RouteGuess.bare("/#/pricing?x=1"));
        assertEquals("/", RouteGuess.bare("/#top"));
        // An app's activity read on one side only is no navigation; two activities are, kept as they are.
        UiTree any = page();
        assertEquals(new Effect.Nothing(), Effect.judge(Effect.State.of(any, ""), Effect.State.of(any, "com.a.Main"),
                List.of()));
        assertEquals(new Effect.Navigates("com.a.Shop"), Effect.judge(Effect.State.of(any, "com.a.Main"),
                Effect.State.of(any, "com.a.Shop"), List.of()));
        // An input the screen already has is not added again.
        UiTree start = page(web("button", "Get started", PRESSABLE));
        Locator known = Locator.inputs(start).getFirst().locator();
        assertTrue(TreeAnswer.inputs("Home", start, Set.of(known), Set.of("getStarted"), l -> Optional.empty())
                .isEmpty());
        // A dialog with no name is marked by what is in it, never by the page's headings under it.
        UiTree dialog = page(web("heading", "Home", Set.of()),
                web("dialog", "", Set.of(), web("heading", "Pick a plan", Set.of())));
        assertEquals(List.of("dialog", "heading \"Pick a plan\""),
                TreeAnswer.screen(dialog, "/", List.of("/"), Set.of()).markers().stream().map(Locator::line).toList());
        // A field's typed text is not its locator.
        UiTree field = new UiTree(List.of(app("android.widget.EditText", "", "foo", "", PRESSABLE)));
        assertTrue(Locator.inputs(field).isEmpty());
    }

    private static UiTree page(UiNode... parts) {
        return new UiTree(List.of(new UiNode("", "", "", "web:RootWebArea", "", BOX, Set.of(), List.of(parts))));
    }

    private static UiNode web(String role, String name, Set<Trait> traits, UiNode... children) {
        return new UiNode("", name, name, "web:" + role, "", BOX, traits, List.of(children));
    }

    private static UiNode app(String cls, String id, String text, String label, Set<Trait> traits, UiNode... children) {
        return new UiNode(id, text, label, cls, "com.game", BOX, traits, List.of(children));
    }
}
