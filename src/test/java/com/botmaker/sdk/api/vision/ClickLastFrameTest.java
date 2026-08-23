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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frame-scoped click verbs — {@link ImageClicker#clickLast()} (the best one),
 * {@link ImageClicker#clickEachLast()} (one per visible template) and {@link ImageClicker#clickAllLast()}
 * (every occurrence of each) — act on the matches the enclosing group check already found, and click nothing
 * at all outside one.
 *
 * <p>The point of the feature is the capture count, so that is what is asserted throughout: a group check whose
 * callback clicks costs <em>one</em> capture, where {@code found -> ImageClicker.click(template)} would cost
 * two — and that holds even for {@code clickAllLast()}, which asks a finer question (where else is this?) of
 * the screenshot the check already took rather than taking a second one. The source is a fake that serves a
 * fixed frame and records clicks (the same shape as {@code ImageClickerRoutingTest}), which also keeps the
 * test headless — no display, only OpenCV matching.
 */
class ClickLastFrameTest {

    private static final int TEMPLATE_SIZE = 48;

    /** Serves one fixed frame; counts captures and records clicks. Origin (0,0), like an emulator. */
    private static final class CountingSource implements CaptureSource {
        private final BufferedImage frame;
        int captures;
        final List<Point> clicks = new ArrayList<>();

        CountingSource(BufferedImage frame) {
            this.frame = frame;
        }

        @Override public BufferedImage capture() {
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

    /** A deterministic, well-textured patch OpenCV can locate uniquely inside noise. */
    private static BufferedImage patch(int seed) {
        BufferedImage patch = new BufferedImage(TEMPLATE_SIZE, TEMPLATE_SIZE, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < TEMPLATE_SIZE; y++) {
            for (int x = 0; x < TEMPLATE_SIZE; x++) {
                int r = (x * 5 + seed * 31) & 0xFF;
                int g = (y * 5 + seed * 17) & 0xFF;
                int b = (x * 3 + y * 7 + seed * 53) & 0xFF;
                patch.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return patch;
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

    private static ImageTemplate writeTemplate(Path dir, String name, BufferedImage image) throws Exception {
        Path file = dir.resolve(name);
        ImageIO.write(image, "png", file.toFile());
        return new ImageTemplate(file.toString());
    }

    /** An empty frame over a source — enough to assert the scoping, with nothing to click. */
    private static VisionContext.Frame frameOver(CaptureSource source) {
        return new VisionContext.Frame(Matches.of(List.of()), source, null, ImageTemplateGroup.of());
    }

    @Test
    void clickLastActsOnTheFrameWithoutCapturingAgain(@TempDir Path tmp) throws Exception {
        BufferedImage marker = patch(1);
        ImageTemplate template = writeTemplate(tmp, "marker.png", marker);
        BufferedImage screen = noise();
        int offsetX = 150, offsetY = 90;
        screen.getGraphics().drawImage(marker, offsetX, offsetY, null);
        CountingSource source = new CountingSource(screen);

        boolean ran = ImageFinder.ifFindAny(ImageTemplateGroup.of(template), source,
                found -> assertTrue(ImageClicker.clickLast(), "the frame had a match"));

        assertTrue(ran, "the template is on screen, so the callback runs");
        assertEquals(1, source.captures, "the callback's click must reuse the frame, not capture a second time");
        assertEquals(1, source.clicks.size());
        Point clicked = source.clicks.getFirst();
        assertTrue(clicked.x() >= offsetX && clicked.x() <= offsetX + TEMPLATE_SIZE, "click x " + clicked.x());
        assertTrue(clicked.y() >= offsetY && clicked.y() <= offsetY + TEMPLATE_SIZE, "click y " + clicked.y());
    }

    @Test
    void clickEachLastClicksEveryTemplateOfTheFrameOnce(@TempDir Path tmp) throws Exception {
        BufferedImage first = patch(1);
        BufferedImage second = patch(2);
        ImageTemplate a = writeTemplate(tmp, "a.png", first);
        ImageTemplate b = writeTemplate(tmp, "b.png", second);
        BufferedImage screen = noise();
        screen.getGraphics().drawImage(first, 20, 30, null);
        screen.getGraphics().drawImage(second, 220, 180, null);
        CountingSource source = new CountingSource(screen);

        int[] clicked = new int[1];
        ImageFinder.ifFindAny(ImageTemplateGroup.of(a, b), source,
                found -> clicked[0] = ImageClicker.clickEachLast());

        assertEquals(2, clicked[0], "both templates were visible in the frame");
        assertEquals(2, source.clicks.size());
        assertEquals(1, source.captures, "one capture for the whole iteration");
    }

    /** The filtered overload: the branch matched both, and acts on the one it named. */
    @Test
    void clickEachLastActsOnlyOnTheTemplatesItWasGiven(@TempDir Path tmp) throws Exception {
        BufferedImage first = patch(1);
        BufferedImage second = patch(2);
        ImageTemplate a = writeTemplate(tmp, "a.png", first);
        ImageTemplate b = writeTemplate(tmp, "b.png", second);
        BufferedImage screen = noise();
        screen.getGraphics().drawImage(first, 20, 30, null);
        screen.getGraphics().drawImage(second, 220, 180, null);
        CountingSource source = new CountingSource(screen);

        int[] clicked = new int[1];
        ImageFinder.ifFindAny(ImageTemplateGroup.of(a, b), source,
                found -> clicked[0] = ImageClicker.clickEachLast(b));

        assertEquals(1, clicked[0], "only the named template was clicked");
        assertEquals(1, source.clicks.size());
        Point p = source.clicks.getFirst();
        assertTrue(p.x() >= 220 && p.x() <= 220 + TEMPLATE_SIZE, "the click landed on b, at " + p);
        assertEquals(1, source.captures, "filtering must not cost a capture");
    }

    /**
     * {@code clickEachLast} against {@code clickAllLast}: the same frame, the same template drawn three times,
     * and the two verbs give one click and three. The three cost no extra capture — they are re-matched against
     * the frame's own pixels.
     */
    @Test
    void clickAllLastClicksEveryOccurrenceWithoutCapturingAgain(@TempDir Path tmp) throws Exception {
        BufferedImage chest = patch(4);
        ImageTemplate template = writeTemplate(tmp, "chest.png", chest);
        BufferedImage screen = noise();
        screen.getGraphics().drawImage(chest, 20, 20, null);
        screen.getGraphics().drawImage(chest, 150, 120, null);
        screen.getGraphics().drawImage(chest, 300, 220, null);
        CountingSource source = new CountingSource(screen);

        int[] each = new int[1];
        int[] all = new int[1];
        ImageFinder.ifFindAny(ImageTemplateGroup.of(template), source, found -> {
            each[0] = ImageClicker.clickEachLast();
            all[0] = ImageClicker.clickAllLast();
        });

        assertEquals(1, each[0], "a frame's Matches holds the best match of each template, so: one");
        assertEquals(3, all[0], "every occurrence of it in the same frame");
        assertEquals(4, source.clicks.size());
        assertEquals(1, source.captures, "the finer question is asked of the frame, not of the screen");
    }

    /** A template the frame never saw is not searched for by the filter — it can only narrow, never widen. */
    @Test
    void clickAllLastIgnoresATemplateTheFrameDidNotSee(@TempDir Path tmp) throws Exception {
        BufferedImage present = patch(1);
        ImageTemplate visible = writeTemplate(tmp, "visible.png", present);
        ImageTemplate absent = writeTemplate(tmp, "absent.png", patch(9));
        BufferedImage screen = noise();
        screen.getGraphics().drawImage(present, 40, 40, null);
        CountingSource source = new CountingSource(screen);

        int[] clicked = new int[1];
        ImageFinder.ifFindAny(ImageTemplateGroup.of(visible, absent), source,
                found -> clicked[0] = ImageClicker.clickAllLast(absent));

        assertEquals(0, clicked[0], "it was not in the frame, so there is nothing of it to click");
        assertTrue(source.clicks.isEmpty());
    }

    @Test
    void outsideAGroupCallbackTheFrameVerbsClickNothingAndSaySo(@TempDir Path tmp) throws Exception {
        BufferedImage marker = patch(1);
        ImageTemplate template = writeTemplate(tmp, "marker.png", marker);
        BufferedImage screen = noise();
        screen.getGraphics().drawImage(marker, 60, 60, null);
        CountingSource source = new CountingSource(screen);

        // A real find, so VisionContext.getLastMatch() holds a genuine coordinate: the point is that a *found*
        // match is still not something clickLast() will act on once its frame is over.
        ImageFinder.ifFindAny(ImageTemplateGroup.of(template), source, found -> { });
        assertFalse(VisionContext.inFrame(), "the callback has returned");

        // Quiet, not loud: a bot that drifts out of a callback carries on. It still clicks nothing.
        assertFalse(ImageClicker.clickLast());
        assertEquals(0, ImageClicker.clickEachLast());
        assertEquals(0, ImageClicker.clickAllLast());
        assertEquals(0, ImageClicker.clickEachLast(template));
        assertEquals(0, ImageClicker.clickAllLast(template));
        assertTrue(source.clicks.isEmpty(), "nothing may be clicked outside a frame");
    }

    @Test
    void theFrameIsScopedToTheCallbackAndNestsBackToTheOuterOne() {
        CountingSource source = new CountingSource(noise());
        VisionContext.Frame outer = frameOver(source);
        VisionContext.Frame inner = frameOver(source);

        assertFalse(VisionContext.inFrame(), "no frame before anything runs");
        VisionContext.runInFrame(outer, m -> {
            assertTrue(VisionContext.inFrame());
            VisionContext.runInFrame(inner, n -> assertTrue(VisionContext.inFrame()));
            assertTrue(VisionContext.inFrame(), "a nested check must restore the outer frame, not clear it");
        });
        assertFalse(VisionContext.inFrame(), "and the outermost callback leaves none behind");

        // A callback that throws must not leak its frame either — the finally is the whole guarantee.
        assertThrows(RuntimeException.class, () -> VisionContext.runInFrame(outer, m -> {
            throw new RuntimeException("boom");
        }));
        assertFalse(VisionContext.inFrame());
    }
}
