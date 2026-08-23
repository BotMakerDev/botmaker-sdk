package com.botmaker.sdk.internal.observe;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standalone (no Studio, no OpenCV) tests for the observer registry and dispatch. Exercises the part of the
 * SPI that does not need a real screen — registration, fan-out, the hot-path guard, and error isolation.
 */
class BotsTest {

    @Test
    void noObserversByDefault() {
        assertFalse(Bots.hasObservers());
    }

    @Test
    void registeredObserverReceivesMatchAndClick() {
        List<MatchEvent> matches = new ArrayList<>();
        List<ClickEvent> clicks = new ArrayList<>();
        BotObserver observer = new BotObserver() {
            @Override public void onMatch(MatchEvent e) { matches.add(e); }
            @Override public void onClick(ClickEvent e) { clicks.add(e); }
        };
        Bots.addObserver(observer);
        try {
            assertTrue(Bots.hasObservers());

            MatchEvent me = new MatchEvent(Surface.ofScreen(), new Rect(0, 0, 10, 10), null);
            Bots.fireMatch(me);
            ClickEvent ce = new ClickEvent(Surface.ofScreen(), new Point(3, 4), ClickEvent.LEFT);
            Bots.fireClick(ce);

            assertEquals(1, matches.size());
            assertSame(me, matches.get(0));
            assertEquals(1, clicks.size());
            assertSame(ce, clicks.get(0));
        } finally {
            Bots.removeObserver(observer);
        }
        assertFalse(Bots.hasObservers());
    }

    @Test
    void throwingObserverDoesNotBreakDispatch() {
        List<MatchEvent> seen = new ArrayList<>();
        BotObserver bad = new BotObserver() {
            @Override public void onMatch(MatchEvent e) { throw new IllegalStateException("boom"); }
        };
        BotObserver good = new BotObserver() {
            @Override public void onMatch(MatchEvent e) { seen.add(e); }
        };
        Bots.addObserver(bad);
        Bots.addObserver(good);
        try {
            Bots.fireMatch(new MatchEvent(Surface.ofScreen(), null, null));
            assertEquals(1, seen.size(), "a throwing observer must not stop others");
        } finally {
            Bots.removeObserver(bad);
            Bots.removeObserver(good);
        }
    }

    @Test
    void screenSurfaceIsNotAWindow() {
        Surface screen = Surface.ofScreen();
        assertFalse(screen.isWindow());
        assertNull(screen.title());

        Surface window = Surface.ofWindow("Notepad", new Rect(0, 0, 800, 600));
        assertTrue(window.isWindow());
        assertEquals("Notepad", window.title());
    }
}
