package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
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
    void findAllTemplatesReadsEveryTemplateFromASingleCapture(@TempDir Path tmp) throws Exception {
        BufferedImage mailPatch = patch(1), claimPatch = patch(2);
        ImageTemplate mail = write(tmp, "mail", mailPatch);
        ImageTemplate claim = write(tmp, "claim", claimPatch);
        ImageTemplate absent = write(tmp, "absent", patch(3));

        CountingSource source = new CountingSource(List.of(frameWith(mailPatch, claimPatch)));

        Matches found = ImageFinder.findAllTemplates(ImageTemplateGroup.of(mail, claim, absent), source, 0.7);

        assertEquals(1, source.captures, "three templates must cost one screenshot, not three");
        assertTrue(found.has(mail));
        assertTrue(found.has(claim));
        assertFalse(found.has(absent), "a template that is not on screen must not appear");
        assertEquals(2, found.count());
    }

    @Test
    void ifFindAnyPassesTheWholeCombinationAndSeedsVisionContext(@TempDir Path tmp) throws Exception {
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
        assertTrue(VisionContext.getLastMatches().hasAll(mail, claim));
        assertTrue(VisionContext.getLastMatch().isFound(), "best() must still seed the single-match slot");
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

        // Two frames still showing the popup, then a clear one — the loop must stop on the clear frame.
        CountingSource source = new CountingSource(
                List.of(frameWith(mailPatch), frameWith(mailPatch), noise()));

        int[] iterations = {0};
        ImageFinder.whileFindAny(ImageTemplateGroup.of(mail), source, found -> {
            iterations[0]++;
            assertTrue(found.has(mail));
        });

        assertEquals(2, iterations[0]);
        assertEquals(3, source.captures, "one capture per iteration plus the terminating check");
        assertTrue(VisionContext.getLastMatches().isEmpty(), "the context must reflect the final, clear frame");
    }

    @Test
    void clickTakesAnAlreadyLocatedMatchWithoutCapturingAgain(@TempDir Path tmp) throws Exception {
        BufferedImage mailPatch = patch(1);
        ImageTemplate mail = write(tmp, "mail", mailPatch);

        CountingSource source = new CountingSource(List.of(frameWith(mailPatch)));
        Matches found = ImageFinder.findAllTemplates(ImageTemplateGroup.of(mail), source, 0.7);
        int capturesAfterFind = source.captures;

        assertTrue(ImageClicker.click(found.get(mail), source));

        assertEquals(capturesAfterFind, source.captures, "clicking a known match must not re-capture");
        assertEquals(1, source.clicks.size());
        Point p = source.clicks.get(0);
        assertNotNull(p);
        assertTrue(p.x >= 20 && p.x <= 20 + SIZE && p.y >= 90 && p.y <= 90 + SIZE,
                "click must land inside the matched rectangle, got " + p);

        assertFalse(ImageClicker.click(Matches.none().get(mail), source),
                "clicking an absent template's miss is a no-op, not a crash");
    }
}
