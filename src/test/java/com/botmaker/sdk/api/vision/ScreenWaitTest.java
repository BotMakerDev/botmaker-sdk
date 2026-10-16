package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The screen waits, on a source whose frames are drawn by capture number. */
class ScreenWaitTest {

    private static BufferedImage filled(Color color) {
        BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 64, 48);
        g.dispose();
        return image;
    }

    private static CaptureSource frames(IntFunction<BufferedImage> byCapture) {
        AtomicInteger captures = new AtomicInteger();
        return new CaptureSource() {
            @Override
            public BufferedImage capture() {
                return byCapture.apply(captures.getAndIncrement());
            }

            @Override
            public Point origin() {
                return new Point(0, 0);
            }
        };
    }

    @Test
    void aScreenThatSettlesIsStable() {
        CaptureSource loading = frames(n -> filled(n < 4 && n % 2 == 0 ? Color.BLACK : Color.GRAY));
        assertTrue(ScreenWait.waitStable(loading, Duration.ofMillis(300), Duration.ofSeconds(3)));
    }

    @Test
    void aScreenThatKeepsMovingIsNotStable() {
        CaptureSource flashing = frames(n -> filled(n % 2 == 0 ? Color.BLACK : Color.WHITE));
        assertFalse(ScreenWait.waitStable(flashing, Duration.ofMillis(300), Duration.ofMillis(600)));
    }

    @Test
    void aChangeIsSeenAgainstTheFirstFrame() {
        CaptureSource clicked = frames(n -> filled(n < 3 ? Color.GRAY : Color.BLUE));
        assertTrue(ScreenWait.waitChange(clicked, Duration.ofSeconds(2)));
        CaptureSource still = frames(n -> filled(Color.GRAY));
        assertFalse(ScreenWait.waitChange(still, Duration.ofMillis(400)));
    }
}
