package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.internal.bot.BotStuckException;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end contract for the group lambda helpers now that they hand over a {@link Matches}.
 *
 * <p>The property under test that no unit test of {@code Matches} alone can reach: <b>one capture per check</b>.
 * The obvious implementation — loop {@code findInternal} over the group — screenshots once per template, which
 * both costs N frames and lets two templates in "the same" answer describe different instants. So the capture
 * count is asserted directly, via a source that counts its own {@code capture()} calls.
 *
 * <p>Fixtures are generated (two distinguishable noise-embedded patches), so this runs headless; only OpenCV
 * template matching is exercised.
 */
class MatchesFindTest {

    private static final int SIZE = 48;

    /** Serves a scripted sequence of frames (last one repeats), counting captures and recording clicks. */
    private static final class CountingSource implements CaptureSource {
        private final List<BufferedImage> frames;
        int captures;
        final List<Point> clicks = new ArrayList<>();

        CountingSource(List<BufferedImage> frames) {
            this.frames = frames;
        }

        @Override public BufferedImage capture() {
            BufferedImage frame = frames.get(Math.min(captures, frames.size() - 1));
            captures++;
            return frame;
        }

        @Override public Point origin() {
            return new Point(0, 0);
        }

        @Override public void click(Point p) {
            clicks.add(p);
        }
    }

    /** A deterministic, well-textured patch; {@code seed} makes two patches unlike each other. */
    private static BufferedImage patch(int seed) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int r = (x * 5 + seed * 37) & 0xFF;
                int g = (y * 5 + seed * 91) & 0xFF;
                int b = (x * 3 + y * 7 + seed * 13) & 0xFF;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private static BufferedImage noise() {
        BufferedImage bg = new BufferedImage(400, 300, BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(11);
        for (int y = 0; y < bg.getHeight(); y++) {
            for (int x = 0; x < bg.getWidth(); x++) {
                bg.setRGB(x, y, rnd.nextInt(0xFFFFFF));
            }
        }
        return bg;
    }

    private static BufferedImage frameWith(BufferedImage... patchesAt) {
        BufferedImage bg = noise();
        int x = 20;
        for (BufferedImage p : patchesAt) {
            bg.getGraphics().drawImage(p, x, 90, null);
            x += 150;
        }
        return bg;
    }

    private static ImageTemplate write(Path dir, String name, BufferedImage img) throws Exception {
        Path file = dir.resolve(name + ".png");
        ImageIO.write(img, "png", file.toFile());
        return new ImageTemplate(file.toString());
    }

    @Test
    void findFrameReadsEveryTemplateFromASingleCapture(@TempDir Path tmp) throws Exception {
        BufferedImage mailPatch = patch(1), claimPatch = patch(2);
        ImageTemplate mail = write(tmp, "mail", mailPatch);
        ImageTemplate claim = write(tmp, "claim", claimPatch);
        ImageTemplate absent = write(tmp, "absent", patch(3));

        CountingSource source = new CountingSource(List.of(frameWith(mailPatch, claimPatch)));

        Matches found = ImageFinder.findFrame(ImageTemplateGroup.of(mail, claim, absent), source, 0.7).matches();

        assertEquals(1, source.captures, "three templates must cost one screenshot, not three");
        assertTrue(found.has(mail));
        assertTrue(found.has(claim));
        assertFalse(found.has(absent), "a template that is not on screen must not appear");
        assertEquals(2, found.count());
    }

    @Test
    void ifFindAnyPassesTheWholeCombinationAndSeedsVision(@TempDir Path tmp) throws Exception {
        BufferedImage mailPatch = patch(1), claimPatch = patch(2);
        ImageTemplate mail = write(tmp, "mail", mailPatch);
        ImageTemplate claim = write(tmp, "claim", claimPatch);

        CountingSource source = new CountingSource(List.of(frameWith(mailPatch, claimPatch)));

        boolean ran = ImageFinder.ifFindAny(ImageTemplateGroup.of(mail, claim), source, found -> {
            // The whole point of the retype: the second visible template is reachable, not swallowed.
            assertTrue(found.hasAll(mail, claim));
            assertTrue(found.get(claim).isFound());
        });

        assertTrue(ran);
        assertEquals(1, source.captures);
        assertTrue(Vision.lastMatches().hasAll(mail, claim));
        assertTrue(Vision.lastMatch().isFound(), "best() must still seed the single-match slot");
    }

    @Test
    void ifFindAllRunsOnlyWhenEveryTemplateIsPresent(@TempDir Path tmp) throws Exception {
        BufferedImage mailPatch = patch(1), claimPatch = patch(2);
        ImageTemplate mail = write(tmp, "mail", mailPatch);
        ImageTemplate claim = write(tmp, "claim", claimPatch);
        ImageTemplateGroup group = ImageTemplateGroup.of(mail, claim);

        CountingSource partial = new CountingSource(List.of(frameWith(mailPatch)));
        assertFalse(ImageFinder.ifFindAll(group, partial, m -> {}), "one of two present is not all");

        CountingSource complete = new CountingSource(List.of(frameWith(mailPatch, claimPatch)));
        boolean[] ran = {false};
        assertTrue(ImageFinder.ifFindAll(group, complete, m -> {
            ran[0] = true;
            assertEquals(2, m.count());
        }));
        assertTrue(ran[0]);
    }

    @Test
    void whileFindAnyLoopsUntilTheFrameIsClearAndThenClearsTheContext(@TempDir Path tmp) throws Exception {
        BufferedImage mailPatch = patch(1);
        ImageTemplate mail = write(tmp, "mail", mailPatch);

        // Two frames still showing the popup, then a clear one — the loop must stop on the clear frame. No
        // patience: the wait for a round's action to show takes captures of its own (see the tests below).
        BotSettings.use(BotSettings.current().patience(0));
        CountingSource source = new CountingSource(
                List.of(frameWith(mailPatch), frameWith(mailPatch), noise()));

        int[] iterations = {0};
        ImageFinder.whileFindAny(ImageTemplateGroup.of(mail), source, found -> {
            iterations[0]++;
            assertTrue(found.has(mail));
        });

        assertEquals(2, iterations[0]);
        assertEquals(3, source.captures, "one capture per iteration plus the terminating check");
        assertTrue(Vision.lastMatches().isEmpty(), "the context must reflect the final, clear frame");
    }

    @AfterEach
    void defaultSettings() {
        BotSettings.use(BotSettings.DEFAULTS);
    }

    @Test
    void aLoopWhoseActionChangesNothingIsStuckAfterItsRounds(@TempDir Path tmp) throws Exception {
        BufferedImage mailPatch = patch(1);
        ImageTemplate mail = write(tmp, "mail", mailPatch);
        BotSettings.use(BotSettings.current().maxRounds(3).patience(0));
        CountingSource source = new CountingSource(List.of(frameWith(mailPatch)));

        int[] acted = {0};
        BotStuckException stuck = assertThrows(BotStuckException.class,
                () -> ImageFinder.whileFindAny(ImageTemplateGroup.of(mail), source, found -> acted[0]++));
        assertEquals(3, acted[0], "it acts maxRounds times, then the bot is stuck");
        assertTrue(stuck.getMessage().contains("whileFindAny(mail)"), stuck.getMessage());
        assertTrue(Vision.lastMatches().isEmpty(), "a loop that ran out leaves no match behind");

        int[] tried = {0};
        ImageTemplate gold = write(tmp, "gold", patch(2));
        assertThrows(BotStuckException.class, () -> ImageFinder.untilFind(gold, source, () -> tried[0]++));
        assertEquals(3, tried[0]);
    }

    @Test
    void aRoundWaitsForWhatItActedOnToChangeAndNoLonger(@TempDir Path tmp) throws Exception {
        BufferedImage mailPatch = patch(1);
        ImageTemplate mail = write(tmp, "mail", mailPatch);
        BotSettings.use(BotSettings.current().patience(5000));
        BufferedImage shown = frameWith(mailPatch);
        // The action's answer comes on the third look after it: the round waits for it, then the loop sees it gone.
        CountingSource source = new CountingSource(List.of(shown, shown, shown, noise()));

        long start = System.nanoTime();
        int[] acted = {0};
        ImageFinder.whileFindAny(ImageTemplateGroup.of(mail), source, found -> acted[0]++);
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertEquals(1, acted[0], "one action: the loop looked again only once the box had changed");
        assertTrue(ms < 4500, "it waits for the change, not the whole patience: " + ms + " ms");
    }

    @Test
    void untilFindLooksForItsPictureAfterEachActionBeforeActingAgain(@TempDir Path tmp) throws Exception {
        BufferedImage goldPatch = patch(2);
        ImageTemplate gold = write(tmp, "gold", goldPatch);
        BotSettings.use(BotSettings.current().patience(5000));
        // Not there, then there on the second look after the one action.
        CountingSource source = new CountingSource(List.of(noise(), noise(), frameWith(goldPatch)));

        int[] acted = {0};
        ImageFinder.untilFind(gold, source, () -> acted[0]++);
        assertEquals(1, acted[0], "the picture came within the patience: no second action");
    }

    /**
     * {@code lit} greyed out, as a game draws a disabled button: its shape kept, its colour half gone, so that a
     * plain match takes it for {@code lit} at the default confidence as Firestone's grey Claim (0.81) is.
     */
    private static BufferedImage greyed(BufferedImage lit) {
        BufferedImage grey = new BufferedImage(lit.getWidth(), lit.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < lit.getHeight(); y++) {
            for (int x = 0; x < lit.getWidth(); x++) {
                int rgb = lit.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int v = (r + g + b) / 3;
                grey.setRGB(x, y, ((r + v) / 2 << 16) | ((g + v) / 2 << 8) | (b + v) / 2);
            }
        }
        return grey;
    }

    @Test
    void whileFindCompareStopsAtTheLookAlike(@TempDir Path tmp) throws Exception {
        BufferedImage litPatch = patch(1), greyPatch = greyed(litPatch);
        ImageTemplate lit = write(tmp, "lit", litPatch);
        ImageTemplate grey = write(tmp, "grey", greyPatch);

        // A lit button beside its grey twin, then the grey twin alone once the lit one was claimed.
        CountingSource source = new CountingSource(List.of(frameWith(litPatch, greyPatch), frameWith(greyPatch)));
        assertTrue(ImageFinder.find(lit, new CountingSource(List.of(frameWith(greyPatch)))),
                "the twin must fool a plain match, or this test proves nothing");

        int[] iterations = {0};
        ImageFinder.whileFindCompare(ImageTemplateGroup.of(lit), ImageTemplateGroup.of(grey), source, found -> {
            iterations[0]++;
            assertTrue(found.has(lit));
            assertTrue(found.get(lit).rect().x() < 150, "only the lit button is acted on, got " + found.get(lit).rect());
        });

        assertEquals(1, iterations[0]);
        assertTrue(Vision.lastMatches().isEmpty());
    }

    @Test
    void reMatchingACompareFrameDropsTheLookAlike(@TempDir Path tmp) throws Exception {
        BufferedImage litPatch = patch(1), greyPatch = greyed(litPatch);
        ImageTemplate lit = write(tmp, "lit", litPatch);
        ImageTemplate grey = write(tmp, "grey", greyPatch);
        BufferedImage pixels = frameWith(litPatch, greyPatch);
        CountingSource source = new CountingSource(List.of(pixels));

        // What clickAllLast re-matches: the grey twin counts on its own, never beside its bad picture.
        assertEquals(2, ImageFinder.findAllIn(pixels, lit, source, 0.8).size());
        assertEquals(1, ImageFinder.findAllIn(pixels, lit, ImageTemplateGroup.of(grey), source, 0.8).size());
    }

    @Test
    void clickTakesAnAlreadyLocatedMatchWithoutCapturingAgain(@TempDir Path tmp) throws Exception {
        BufferedImage mailPatch = patch(1);
        ImageTemplate mail = write(tmp, "mail", mailPatch);

        CountingSource source = new CountingSource(List.of(frameWith(mailPatch)));
        Matches found = ImageFinder.findFrame(ImageTemplateGroup.of(mail), source, 0.7).matches();
        int capturesAfterFind = source.captures;

        assertTrue(ImageClicker.click(found.get(mail), source));

        assertEquals(capturesAfterFind, source.captures, "clicking a known match must not re-capture");
        assertEquals(1, source.clicks.size());
        Point p = source.clicks.get(0);
        assertNotNull(p);
        assertTrue(p.x() >= 20 && p.x() <= 20 + SIZE && p.y() >= 90 && p.y() <= 90 + SIZE,
                "click must land inside the matched rectangle, got " + p);

        assertFalse(ImageClicker.click(Matches.none().get(mail), source),
                "clicking an absent template's miss is a no-op, not a crash");
    }
}
