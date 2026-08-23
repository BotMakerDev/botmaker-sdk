package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.vision.ImageClicker;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.ImageTemplateGroup;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Matches;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The popup guard's contract: it runs before a vision step that takes its own capture, it does not run twice
 * for one such step, and — the property that makes it usable at all — it does not recurse when the check is
 * itself written with {@code ImageFinder}/{@code ImageClicker}, which is the only way to write it.
 *
 * <p>Exercised through the real facades rather than by calling {@link PopupGuard#check()} directly, because the
 * thing that can silently break is the wiring: an overload that forgets the call (a vision step with no guard)
 * or one that delegates to another guarded overload (the check running twice per statement, at a full
 * screenshot each). Both are invisible to a unit test of this class alone.
 */
class PopupGuardTest {

    @AfterEach
    void clearTheProcessGlobalGuard() {
        PopupGuard.uninstall();
        PopupGuard.enabled(true);
    }

    /** Counts captures, so "how many vision steps happened" is observable. */
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

    private static BufferedImage noise() {
        BufferedImage bg = new BufferedImage(200, 150, BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(7);
        for (int y = 0; y < bg.getHeight(); y++) {
            for (int x = 0; x < bg.getWidth(); x++) {
                bg.setRGB(x, y, rnd.nextInt(0xFFFFFF));
            }
        }
        return bg;
    }

    /** Pure texture, absent from {@link #noise()} unless a test draws it in — most finds below are misses. */
    private static ImageTemplate templateNotIn(Path dir) throws Exception {
        BufferedImage patch = new BufferedImage(24, 24, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) patch.setRGB(x, y, ((x * 11) << 16) | ((y * 11) << 8) | 0x40);
        }
        Path file = dir.resolve("absent.png");
        ImageIO.write(patch, "png", file.toFile());
        return new ImageTemplate(file.toString());
    }

    @Test
    void withNothingInstalledEveryVisionStepIsUnchanged(@TempDir Path tmp) throws Exception {
        CountingSource source = new CountingSource(noise());
        assertFalse(PopupGuard.isEnabled(), "no check installed means no guard");
        assertFalse(ImageFinder.find(templateNotIn(tmp), source, 0.9));
        assertEquals(1, source.captures, "an unguarded find is one capture");
    }

    @Test
    void theGuardRunsOncePerVisionStep(@TempDir Path tmp) throws Exception {
        AtomicInteger runs = new AtomicInteger();
        PopupGuard.install(runs::incrementAndGet);
        ImageTemplate absent = templateNotIn(tmp);
        CountingSource source = new CountingSource(noise());

        ImageFinder.find(absent, source, 0.9);
        assertEquals(1, runs.get(), "the find must be guarded");

        ImageFinder.findAny(source, 0.9, absent, absent);
        assertEquals(2, runs.get(),
                "findAny is one statement: the overload that delegates must not guard as well");

        ImageClicker.click(absent, source);
        assertEquals(3, runs.get(), "a click that locates its own target is a vision step too");
    }

    @Test
    void theChecksOwnVisionCallsDoNotRecurse(@TempDir Path tmp) throws Exception {
        ImageTemplate absent = templateNotIn(tmp);
        CountingSource source = new CountingSource(noise());
        AtomicInteger runs = new AtomicInteger();

        // The shape a real check has: it looks for popups with the very API that invokes it. Without the
        // reentrancy flag this is unbounded recursion, not a slow bot.
        PopupGuard.install(() -> {
            runs.incrementAndGet();
            ImageFinder.whileFindAny(ImageTemplateGroup.of(absent), source, found -> {});
        });

        ImageFinder.find(absent, source, 0.9);

        assertEquals(1, runs.get(), "the check's own finds must not re-enter the guard");
    }

    @Test
    void aDisabledGuardIsSkippedAndCanBeTurnedBackOn(@TempDir Path tmp) throws Exception {
        AtomicInteger runs = new AtomicInteger();
        PopupGuard.install(runs::incrementAndGet);
        ImageTemplate absent = templateNotIn(tmp);
        CountingSource source = new CountingSource(noise());

        PopupGuard.enabled(false);
        assertFalse(PopupGuard.isEnabled());
        ImageFinder.find(absent, source, 0.9);
        assertEquals(0, runs.get(), "an activity that opted out must not be interrupted");

        PopupGuard.enabled(true);
        ImageFinder.find(absent, source, 0.9);
        assertEquals(1, runs.get());
    }

    /**
     * Clicking a match you already located must not first dismiss a popup: the coordinate was measured in an
     * earlier frame, and moving the screen underneath it is how a "safe" guard produces a misclick.
     */
    @Test
    void clickingAnAlreadyLocatedMatchIsNotGuarded(@TempDir Path tmp) throws Exception {
        // Locate it first, with no guard installed, so the match under test is a real one from a real frame.
        ImageTemplate present = templateNotIn(tmp);
        BufferedImage frame = noise();
        frame.getGraphics().drawImage(ImageIO.read(tmp.resolve("absent.png").toFile()), 40, 40, null);
        CountingSource source = new CountingSource(frame);
        MatchResult[] found = {Matches.none().get(null)};
        ImageFinder.ifFindAny(ImageTemplateGroup.of(present), source, m -> found[0] = m.get(present));
        MatchResult located = found[0];
        assertTrue(located.isFound(), "fixture: the patch must be findable in the frame");

        AtomicInteger runs = new AtomicInteger();
        PopupGuard.install(runs::incrementAndGet);
        ImageClicker.click(located, source);

        assertEquals(0, runs.get(), "click(MatchResult) is deliberately unguarded");
        assertEquals(1, source.clicks.size());
        assertFalse(Matches.none().get(present).isFound());
    }

    @Test
    void aWaiterIsGuardedOnEveryPollNotJustAtEntry(@TempDir Path tmp) throws Exception {
        AtomicInteger runs = new AtomicInteger();
        PopupGuard.install(runs::incrementAndGet);
        CountingSource source = new CountingSource(noise());

        assertFalse(com.botmaker.sdk.api.vision.ImageWaiter.waitFor(templateNotIn(tmp), source, 1));

        assertTrue(runs.get() > 1,
                "a popup that opens during the wait must be seen before the timeout burns, got " + runs.get());
    }
}
