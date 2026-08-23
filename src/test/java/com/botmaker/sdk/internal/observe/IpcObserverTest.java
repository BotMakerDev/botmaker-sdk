package com.botmaker.sdk.internal.observe;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.shared.ipc.TelemetryClient;
import com.botmaker.shared.ipc.TelemetryEvent;
import com.botmaker.shared.ipc.TelemetryServer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the bridge's SDK-event → shared-wire translation end to end over a real loopback socket
 * (the {@code botmaker-shared} channel), without needing a subprocess or environment variables.
 */
class IpcObserverTest {

    @Test
    void translatesAndShipsClickAndMatch() throws Exception {
        BlockingQueue<TelemetryEvent> received = new ArrayBlockingQueue<>(8);
        try (TelemetryServer server = new TelemetryServer("t", received::offer);
             TelemetryClient client = new TelemetryClient(server.port(), "t")) {

            IpcObserver observer = new IpcObserver(client);

            observer.onClick(new ClickEvent(Surface.ofScreen(), new Point(7, 8), ClickEvent.LEFT));
            observer.onMatch(new MatchEvent(
                    Surface.ofWindow("Notepad", new Rect(10, 20, 300, 200)),
                    new Rect(0, 0, 50, 50),
                    null)); // no MatchResult → not-found

            TelemetryEvent first = received.poll(3, TimeUnit.SECONDS);
            TelemetryEvent.Click click = assertInstanceOf(TelemetryEvent.Click.class, first);
            assertEquals(7, click.x());
            assertEquals(8, click.y());
            assertEquals(ClickEvent.LEFT, click.button());
            assertNull(click.target().title()); // screen

            TelemetryEvent second = received.poll(3, TimeUnit.SECONDS);
            TelemetryEvent.Match match = assertInstanceOf(TelemetryEvent.Match.class, second);
            assertEquals("Notepad", match.target().title());
            assertEquals(10, match.target().x());
            assertEquals(300, match.target().width());
            assertEquals(new TelemetryEvent.Rect(0, 0, 50, 50), match.region());
            assertNull(match.rect());
            assertFalse(match.found());
        }
    }

    @Test
    void aSwipeCrossesTheWireWithBothEndsAndItsDuration() throws Exception {
        BlockingQueue<TelemetryEvent> received = new ArrayBlockingQueue<>(8);
        try (TelemetryServer server = new TelemetryServer("t", received::offer);
             TelemetryClient client = new TelemetryClient(server.port(), "t")) {

            new IpcObserver(client).onSwipe(new SwipeEvent(
                    Surface.ofWindow("Nested Game", new Rect(100, 50, 640, 480)),
                    new Point(200, 400), new Point(200, 120), 450));

            TelemetryEvent.Swipe swipe =
                    assertInstanceOf(TelemetryEvent.Swipe.class, received.poll(3, TimeUnit.SECONDS));
            assertEquals(200, swipe.x1());
            assertEquals(400, swipe.y1());
            assertEquals(200, swipe.x2());
            assertEquals(120, swipe.y2());
            // The one field the two endpoints alone cannot imply — and the reason the record carries it.
            assertEquals(450, swipe.durationMs());
            assertEquals("Nested Game", swipe.target().title());
        }
    }
}
