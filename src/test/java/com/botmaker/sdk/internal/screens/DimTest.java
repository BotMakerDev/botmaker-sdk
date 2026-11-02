package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Optional;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A button the game dims or greys is told from its usual picture alone (2026-11-02). */
class DimTest {

    private static final Color PORTAL = new Color(70, 140, 200);

    @Test
    void aDarkenedOrGreyedCopyIsDimmedAndTheSameButtonIsNot() {
        Dim.Tone lit = Dim.Tone.of(icon(PORTAL, 1), null);
        assertFalse(Dim.dimmed(lit, Dim.Tone.of(icon(PORTAL, 1), null)), "the button as cut");
        assertFalse(Dim.dimmed(lit, Dim.Tone.of(icon(PORTAL, 0.9), null)), "a little darker, as a glow fades");
        assertTrue(Dim.dimmed(lit, Dim.Tone.of(icon(PORTAL, 0.45), null)),
                "Sword x Staff's tutorial dims the rest of the screen to 0.45 of its light");
        assertTrue(Dim.dimmed(lit, Dim.Tone.of(icon(new Color(140, 140, 140), 1), null)), "greyed: its colour gone");
    }

    @Test
    void aGreyIconIsToldByItsLightAndADarkOneIsNeverGuessed() {
        Dim.Tone white = Dim.Tone.of(icon(new Color(230, 230, 230), 1), null);
        assertFalse(Dim.dimmed(white, Dim.Tone.of(icon(new Color(225, 225, 225), 1), null)), "no colour to lose");
        assertTrue(Dim.dimmed(white, Dim.Tone.of(icon(new Color(110, 110, 110), 1), null)), "darker");
        Dim.Tone black = Dim.Tone.of(icon(new Color(10, 10, 10), 1), null);
        assertFalse(Dim.dimmed(black, Dim.Tone.of(icon(new Color(3, 3, 3), 1), null)), "too dark to tell");
    }

    @Test
    void aToneIsReadInItsAreaAndSkipsWhatIsSeeThrough() {
        BufferedImage frame = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
        fill(frame, 0, 0, 20, 20, Color.BLACK);
        fill(frame, 20, 0, 20, 20, PORTAL);
        assertEquals(Dim.Tone.of(icon(PORTAL, 1), null), Dim.Tone.of(frame, new Rect(20, 0, 20, 20)));

        BufferedImage cut = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        fill(cut, 5, 5, 10, 10, PORTAL);
        assertEquals(Dim.Tone.of(icon(PORTAL, 1), null), Dim.Tone.of(cut, null), "its clear corners are no part of it");
        assertEquals(Dim.Tone.of(icon(PORTAL, 1), null), Dim.Tone.of(frame, new Rect(30, 10, 40, 40)),
                "an area past the frame is cut to it");
    }

    @Test
    void aMatchOfTheUsualPictureThatIsDimmedIsSeenDisabled() {
        Button portal = click("nav_portal");
        assertEquals(Optional.of(State.NORMAL), new Shows(false).seen(portal, frame()).map(Reach.Seen::state));
        assertEquals(Optional.of(State.DISABLED), new Shows(true).seen(portal, frame()).map(Reach.Seen::state),
                "dimmed by the tutorial: never pressed");
    }

    /** A reach that finds every picture at one place, dimmed or not. */
    private record Shows(boolean dim) implements Reach {
        @Override
        public Optional<BufferedImage> look() {
            return Optional.of(frame());
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
            return Optional.of(new Rect(0, 0, 10, 10));
        }

        @Override
        public boolean dimmed(ImageTemplate picture, Rect found, BufferedImage frame) {
            return dim;
        }

        @Override
        public void press(Point at, Gesture gesture, Duration hold) {}

        @Override
        public void keys(Combo combo) {}

        @Override
        public void type(String text, boolean clears, boolean submits) {}

        @Override
        public void scroll(Point at, int notches) {}

        @Override
        public void drag(Point from, Point to, Duration time) {}

        @Override
        public long nowMs() {
            return 0;
        }

        @Override
        public void pause(long ms) {}
    }

    private static BufferedImage frame() {
        return new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
    }

    /** A 20×20 icon of {@code colour}, its light times {@code light}. */
    private static BufferedImage icon(Color colour, double light) {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, 20, 20, new Color((int) (colour.getRed() * light), (int) (colour.getGreen() * light),
                (int) (colour.getBlue() * light)));
        return image;
    }

    private static void fill(BufferedImage image, int x, int y, int w, int h, Color colour) {
        for (int i = x; i < x + w; i++) {
            for (int j = y; j < y + h; j++) image.setRGB(i, j, colour.getRGB());
        }
    }
}
