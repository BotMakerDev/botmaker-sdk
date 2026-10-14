package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A gesture on a picture is proved by the picture moving, and a list at its end answers false. */
class ImageScrollerTest {

    private static final int ITEM = 24;
    private static final int X = 60;

    /** A list showing one item, at {@code y}; a scroll or a drag moves it, as far as the list's ends allow. */
    private static class ListSource implements CaptureSource {
        private final BufferedImage background = noise();
        private final BufferedImage item;
        private final int top;
        private final int bottom;
        int y;
        final List<Point> gestures = new ArrayList<>();

        ListSource(BufferedImage item, int y, int top, int bottom) {
            this.item = item;
            this.y = y;
            this.top = top;
            this.bottom = bottom;
        }

        @Override public BufferedImage capture() {
            BufferedImage frame = new BufferedImage(background.getWidth(), background.getHeight(),
                    BufferedImage.TYPE_3BYTE_BGR);
            frame.getGraphics().drawImage(background, 0, 0, null);
            frame.getGraphics().drawImage(item, X, y, null);
            return frame;
        }

        @Override public Point origin() {
            return new Point(0, 0);
        }

        @Override public void scroll(Point at, int notches) {
            gestures.add(at);
            y = Math.clamp(y + notches * 10L, top, bottom);
        }

        @Override public void drag(Point from, Point to, long durationMs) {
            gestures.add(from);
            gestures.add(to);
            y = Math.clamp(y + (long) (to.y() - from.y()), top, bottom);
        }
    }

    @BeforeEach
    void quickPatience() {
        BotSettings.use(BotSettings.current().patience(400));
    }

    @AfterEach
    void defaults() {
        BotSettings.use(BotSettings.DEFAULTS);
    }

    @Test
    void aScrollOnThePictureThatMovesItIsTrue(@TempDir Path tmp) throws Exception {
        ListSource list = new ListSource(item(), 60, 0, 120);
        assertTrue(ImageScroller.scrollAt(picture(tmp), -3, list));
        assertEquals(30, list.y);
        assertEquals(new Point(X + ITEM / 2, 60 + ITEM / 2), list.gestures.getFirst(), "the wheel turns over it");
    }

    @Test
    void aListAtItsEndDoesNotMoveSoTheAnswerIsFalse(@TempDir Path tmp) throws Exception {
        ListSource list = new ListSource(item(), 0, 0, 120);
        assertFalse(ImageScroller.scrollAt(picture(tmp), -3, list));
    }

    @Test
    void aTwinFurtherDownTheListIsNoMove(@TempDir Path tmp) throws Exception {
        BufferedImage item = item();
        ListSource list = new ListSource(item, 0, 0, 120) {
            @Override public BufferedImage capture() {
                BufferedImage frame = super.capture();
                frame.getGraphics().drawImage(item, X, 80, null);
                return frame;
            }
        };
        assertFalse(ImageScroller.scrollAt(picture(tmp), -3, list), "the item scrolled at is still there");
    }

    @Test
    void noPatienceStillLooksOnce(@TempDir Path tmp) throws Exception {
        BotSettings.use(BotSettings.current().patience(0));
        ListSource list = new ListSource(item(), 60, 0, 120);
        assertTrue(ImageScroller.scrollAt(picture(tmp), -3, list));
    }

    @Test
    void aPictureNotOnTheScreenIsNotScrolled(@TempDir Path tmp) throws Exception {
        ListSource list = new ListSource(item(), 60, 0, 120);
        list.y = 1000;
        assertFalse(ImageScroller.scrollAt(picture(tmp), -3, list));
        assertTrue(list.gestures.isEmpty());
    }

    @Test
    void aDragIsAFractionOfTheScreen(@TempDir Path tmp) throws Exception {
        ListSource list = new ListSource(item(), 100, 0, 120);
        assertTrue(ImageScroller.drag(picture(tmp), 0, -0.4, list));
        assertEquals(40, list.y, "two fifths of a 150-pixel screen");
    }

    @Test
    void aDragOntoAnotherPictureEndsOnIt(@TempDir Path tmp) throws Exception {
        BufferedImage slot = new BufferedImage(ITEM, ITEM, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < ITEM; y++) {
            for (int x = 0; x < ITEM; x++) slot.setRGB(x, y, ((y * 9) << 16) | 0x30 << 8 | (x * 9));
        }
        ListSource list = new ListSource(item(), 100, 0, 120) {
            @Override public BufferedImage capture() {
                BufferedImage frame = super.capture();
                frame.getGraphics().drawImage(slot, 150, 20, null);
                return frame;
            }
        };
        Path slotFile = tmp.resolve("slot.png");
        ImageIO.write(slot, "png", slotFile.toFile());
        assertTrue(ImageScroller.drag(picture(tmp), new ImageTemplate(slotFile.toString()), list));
        assertEquals(new Point(150 + ITEM / 2, 20 + ITEM / 2), list.gestures.get(1));
    }

    private static BufferedImage item() {
        BufferedImage patch = new BufferedImage(ITEM, ITEM, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < ITEM; y++) {
            for (int x = 0; x < ITEM; x++) patch.setRGB(x, y, ((x * 11) << 16) | ((y * 11) << 8) | 0x40);
        }
        return patch;
    }

    private static ImageTemplate picture(Path dir) throws Exception {
        Path file = dir.resolve("item.png");
        ImageIO.write(item(), "png", file.toFile());
        return new ImageTemplate(file.toString());
    }

    private static BufferedImage noise() {
        BufferedImage bg = new BufferedImage(200, 150, BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(7);
        for (int y = 0; y < bg.getHeight(); y++) {
            for (int x = 0; x < bg.getWidth(); x++) bg.setRGB(x, y, rnd.nextInt(0xFFFFFF));
        }
        return bg;
    }
}
