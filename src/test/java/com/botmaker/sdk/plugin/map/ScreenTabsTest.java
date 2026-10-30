package com.botmaker.sdk.plugin.map;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenTabsTest {

    @Test
    void aScreenOpenedReplacesThePreviewUntilItsTabIsKept() {
        ScreenTabs tabs = new ScreenTabs();
        tabs.open("HOME", true);
        tabs.open("TOWN", true);
        assertEquals(List.of(new ScreenTabs.Open("TOWN", false)), tabs.tabs(), "one preview, replaced");
        tabs.keep("TOWN");
        tabs.open("GUILD", true);
        assertEquals(List.of(new ScreenTabs.Open("TOWN", true), new ScreenTabs.Open("GUILD", false)), tabs.tabs());
        tabs.open("TOWN", true);
        assertEquals(2, tabs.tabs().size(), "a kept tab is brought to the front, not opened again");
        assertTrue(tabs.isPreview("GUILD"));
        tabs.close("TOWN");
        assertEquals(List.of(new ScreenTabs.Open("GUILD", false)), tabs.tabs());
    }

    @Test
    void backAndForwardGoThroughTheScreensLookedAt() {
        ScreenTabs tabs = new ScreenTabs();
        for (String screen : List.of("HOME", "TOWN", "GUILD")) tabs.open(screen, true);
        assertFalse(tabs.canForward());
        assertEquals(Optional.of("TOWN"), tabs.back());
        tabs.open("TOWN", false);
        assertEquals(Optional.of("HOME"), tabs.back());
        assertFalse(tabs.canBack());
        assertEquals(Optional.of("TOWN"), tabs.forward());
        tabs.open("MAP", true);
        assertFalse(tabs.canForward(), "a new screen drops the steps ahead");
        assertEquals(List.of("TOWN", "HOME"), tabs.recent("MAP"));
    }

    @Test
    void recentNamesEachScreenOnceAndAtMostFive() {
        ScreenTabs tabs = new ScreenTabs();
        for (String screen : List.of("A", "B", "A", "C", "D", "E", "F", "G")) tabs.open(screen, true);
        assertEquals(List.of("F", "E", "D", "C", "A"), tabs.recent("G"));
    }

    @Test
    void aScreenDeletedLeavesItsTabAndTheSteps() {
        ScreenTabs tabs = new ScreenTabs();
        tabs.open("HOME", true);
        tabs.keep("HOME");
        for (String screen : List.of("TOWN", "HOME", "GUILD")) tabs.open(screen, true);
        tabs.retain(Set.of("HOME", "GUILD"));
        assertEquals(List.of(new ScreenTabs.Open("HOME", true), new ScreenTabs.Open("GUILD", false)), tabs.tabs());
        assertEquals(Optional.of("HOME"), tabs.back(), "HOME, TOWN, HOME is one HOME once TOWN went");
        assertFalse(tabs.canBack());
        tabs.retain(Set.of());
        assertTrue(tabs.tabs().isEmpty());
        assertFalse(tabs.canBack());
        assertFalse(tabs.canForward());
    }
}
