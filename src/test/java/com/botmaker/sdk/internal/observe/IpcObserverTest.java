package com.botmaker.sdk.internal.observe;

import com.botmaker.plugin.api.run.RunChannel;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.screens.ScreenStep;
import com.botmaker.sdk.internal.trace.SdkRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The SDK's events as its own frames, sent on the run's channel and read back by the plugin half's decoder. */
class IpcObserverTest {

    private final List<byte[]> frames = new ArrayList<>();

    @BeforeEach
    void channel() {
        SdkRun.started(new RunChannel() {
            @Override
            public void frame(byte[] frame) {
                frames.add(frame);
            }
        });
    }

    @AfterEach
    void console() {
        SdkRun.ended();
    }

    private RunEvent read(int index) throws RunFrames.Unreadable {
        return RunFrames.decode(frames.get(index));
    }

    @Test
    void aClickAndAMatchAreSentAsFrames() throws Exception {
        IpcObserver observer = new IpcObserver();
        observer.onClick(new ClickEvent(Surface.ofScreen(), new Point(7, 8), ClickEvent.LEFT));
        observer.onMatch(new MatchEvent(Surface.ofWindow("Notepad", new Rect(10, 20, 300, 200)),
                new Rect(0, 0, 50, 50), null)); // no MatchResult → not-found

        RunEvent.Click click = assertInstanceOf(RunEvent.Click.class, read(0));
        assertEquals(7, click.x());
        assertEquals(8, click.y());
        assertEquals(ClickEvent.LEFT, click.button());
        assertNull(click.target().title()); // screen

        RunEvent.Match match = assertInstanceOf(RunEvent.Match.class, read(1));
        assertEquals("Notepad", match.target().title());
        assertEquals(10, match.target().x());
        assertEquals(300, match.target().width());
        assertEquals(new RunEvent.Rect(0, 0, 50, 50), match.region());
        assertNull(match.rect());
        assertFalse(match.found());
    }

    /** A bot may live under com.botmaker too; only our libraries are skipped when finding its line. */
    @Test
    void aBotUnderComBotmakerIsTheBotsOwnCodeButOurLibrariesAreNot() {
        assertFalse(IpcObserver.isLibrary("com.botmaker.farm.Collect"));
        assertFalse(IpcObserver.isLibrary("com.example.Farm"));
        assertTrue(IpcObserver.isLibrary("com.botmaker.sdk.api.input.Mouse"));
        assertTrue(IpcObserver.isLibrary("com.botmaker.shared.Diag"));
        assertTrue(IpcObserver.isLibrary("java.lang.Thread"));
    }

    @Test
    void aSwipeKeepsBothEndsAndItsDuration() throws Exception {
        new IpcObserver().onSwipe(new SwipeEvent(Surface.ofWindow("Nested Game", new Rect(100, 50, 640, 480)),
                new Point(200, 400), new Point(200, 120), 450));

        RunEvent.Swipe swipe = assertInstanceOf(RunEvent.Swipe.class, read(0));
        assertEquals(200, swipe.x1());
        assertEquals(400, swipe.y1());
        assertEquals(200, swipe.x2());
        assertEquals(120, swipe.y2());
        // The one field the two endpoints alone cannot imply — and the reason the record carries it.
        assertEquals(450, swipe.durationMs());
        assertEquals("Nested Game", swipe.target().title());
    }

    @Test
    void aStepRoundTrips() throws Exception {
        new IpcObserver().onScreen("HOME", "Home");
        assertEquals(new RunEvent.Step("", ScreenStep.action("HOME", "Home"), -1), read(0));
    }
}
