// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Role;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A website screen's route, and an element found by its role in a page's tree. */
class RoutesTest {

    @Test
    void aRouteMatchesItsPagesOnly() {
        assertTrue(Routes.matches("/case-studies/{slug}", "/case-studies/alpha"));
        assertTrue(Routes.matches("/case-studies/{slug}", "/case-studies/alpha/"));
        assertTrue(Routes.matches("/", "/"));
        assertTrue(Routes.matches("/", ""));
        assertTrue(Routes.matches("/pricing", "/pricing?plan=pro"));
        assertFalse(Routes.matches("/case-studies/{slug}", "/case-studies"));
        assertFalse(Routes.matches("/case-studies/{slug}", "/case-studies/alpha/more"));
        assertFalse(Routes.matches("/case-studies", "/blog"));
        assertFalse(Routes.matches("/", "/blog"));
    }

    @Test
    void aSetupKeepsAtMostThreeExamplesAndItsRouteTellsIt() {
        Setup page = Setup.named("Case").route("/case-studies/{slug}")
                .examples(List.of("/case-studies/a", "/case-studies/b", "/case-studies/c", "/case-studies/d"));
        assertEquals(3, page.examples().size());
        assertTrue(page.hasMarker());
        Markers set = Markers.of(new MapScreen(page, Map.of()));
        assertEquals("/case-studies/{slug}", set.route());
        assertTrue(set.keys().contains("route /case-studies/{slug}"));
    }

    @Test
    void aRoleIsFoundOnlyOnAWebsitesNodeOfThatRole() {
        UiNode link = new UiNode("", "Case studies", "Case studies", "web:link", "", new Rectangle(0, 0, 10, 10),
                Set.of(Trait.ENABLED, Trait.CLICKABLE), List.of());
        UiNode heading = new UiNode("", "Case studies", "Case studies", "web:heading", "", new Rectangle(0, 20, 10, 10),
                Set.of(Trait.ENABLED), List.of());
        UiNode field = new UiNode("", "", "Search", "web:searchbox", "", new Rectangle(0, 40, 10, 10),
                Set.of(Trait.ENABLED, Trait.CLICKABLE), List.of());
        UiNode root = new UiNode("", "", "", "web:RootWebArea", "", new Rectangle(0, 0, 100, 100), Set.of(),
                List.of(link, heading, field));
        ElementMatch.Matcher matcher = ElementMatch.in(new UiTree(List.of(root)));
        assertEquals(List.of(link), matcher.matches(Element.byRole(Role.LINK, Words.of("Case studies"))));
        assertEquals(List.of(heading), matcher.matches(Element.byRole(Role.HEADING, Words.of("Case studies"))));
        assertEquals(List.of(field), matcher.matches(Element.textField().label(Words.of("Search"))));
    }
}
