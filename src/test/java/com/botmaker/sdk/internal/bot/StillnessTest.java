package com.botmaker.sdk.internal.bot;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A game is frozen only when its picture stays still after input was sent to it. */
class StillnessTest {

    private static final BufferedImage DARK = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
    private static final BufferedImage LIGHT = filled(Color.WHITE);

    @Test
    void aStillPictureWithNoInputIsNeverFrozen() {
        Stillness still = new Stillness();
        still.saw(DARK, 0);
        still.saw(DARK, 120_000);

        assertEquals(0, still.stillAfterInput());
    }

    @Test
    void inputThatKeepsComingOnAStillPictureCountsFromTheFirstToTheLastSeen() {
        Stillness still = new Stillness();
        still.saw(DARK, 0);
        still.acted(10_000);
        still.saw(DARK, 40_000);
        still.acted(70_000);
        still.saw(DARK, 80_000);

        assertEquals(60_000, still.stillAfterInput());
    }

    @Test
    void oneInputThenAWaitIsNeverFrozen() {
        Stillness still = new Stillness();
        still.saw(DARK, 0);
        still.acted(10_000);
        still.saw(DARK, 300_000);

        assertEquals(0, still.stillAfterInput());
    }

    @Test
    void inputNoFrameFollowedYetCountsOnlyToTheLastFrame() {
        Stillness still = new Stillness();
        still.saw(DARK, 0);
        still.acted(10_000);
        still.saw(DARK, 20_000);
        still.acted(90_000);

        assertEquals(10_000, still.stillAfterInput());
    }

    @Test
    void aPictureThatMovesClearsTheInputWaitingOnIt() {
        Stillness still = new Stillness();
        still.saw(DARK, 0);
        still.acted(10_000);
        still.saw(LIGHT, 70_000);

        assertEquals(0, still.stillAfterInput());
    }

    @Test
    void anInputBeforeAnyFrameWaitsOnNothing() {
        Stillness still = new Stillness();
        still.acted(0);
        still.saw(DARK, 70_000);

        assertEquals(0, still.stillAfterInput());
    }

    private static BufferedImage filled(Color color) {
        BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 320, 180);
        g.dispose();
        return image;
    }
}
