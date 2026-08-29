package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.bot.ActivityRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a debug run says, and — the half that is easy to lose — what a quiet run doesn't.
 *
 * <p>Two properties are worth a test each. <b>Silence when off:</b> every trace added here goes through
 * {@link Debug}, so a production run with {@code Debug.disable()} must print nothing at all; a single
 * {@code System.out.println} that slipped past the switch would be invisible to a maintainer reading the diff
 * and obvious to a user watching a bot they asked to be quiet. <b>One line per burst:</b> the vision trace is
 * deliberately not one line per event, so the test that matters is the one counting lines rather than reading
 * them.
 */
class DebugTraceTest {

    @AfterEach
    void restoreTheGlobals() {
        Debug.enable();
        PopupGuard.uninstall();
        PopupGuard.enabled(true);
        ActivityRegistry.clear();
    }

    /** Runs {@code body} with stdout and stderr captured, and returns everything it printed. */
    private static String printed(Runnable body) {
        PrintStream realOut = System.out;
        PrintStream realErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream sink = new PrintStream(captured, true, StandardCharsets.UTF_8);
        System.setOut(sink);
        System.setErr(sink);
        try {
            body.run();
        } finally {
            System.setOut(realOut);
            System.setErr(realErr);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    private static List<String> linesMatching(String output, String needle) {
        return output.lines().filter(l -> l.contains(needle)).toList();
    }

    // --- Activity ---

    private enum Outcome { DEFAULT, BAG_FULL }

    private static final class Mining extends Activity<Outcome> {
        @Override public boolean isEnabled() { return true; }
        @Override public Outcome run() { return Outcome.BAG_FULL; }
    }

    private static final class Collapsing extends Activity<Outcome> {
        @Override public boolean isEnabled() { return true; }
        @Override public Outcome run() { throw new BotStuckException("no ore"); }
    }

    @Test
    void anActivityReportsWhichOutcomeItReached() {
        Mining mining = new Mining();
        String output = printed(mining::execute);

        List<String> lines = linesMatching(output, "[Activity]");
        assertEquals(1, lines.size(), "one activity, one line: " + output);
        assertTrue(lines.get(0).contains("Mining → BAG_FULL"),
                "the line names the activity and the outcome the flow will route on: " + lines.get(0));
    }

    @Test
    void aStuckActivityReportsThatInsteadOfAnOutcome() {
        Collapsing collapsing = new Collapsing();
        String output = printed(() -> {
            try {
                collapsing.execute();
            } catch (BotStuckException expected) {
                // the supervisor's business; here we only care that it was announced first
            }
        });

        List<String> lines = linesMatching(output, "[Activity]");
        assertEquals(1, lines.size(), output);
        assertTrue(lines.get(0).contains("Collapsing → stuck: no ore"), lines.get(0));
    }

    @Test
    void aQuietRunSaysNothingAboutActivities() {
        Mining mining = new Mining();
        Debug.disable();

        assertEquals("", printed(mining::execute), "Debug.disable() means silence, not less noise");
    }

    // --- PopupGuard ---

    @Test
    void aSlowPopupCheckIsCalledOutWhileTheFastOnesAreNot() {
        PopupGuard.install(() -> {});
        String quiet = printed(PopupGuard::check);
        assertEquals("", quiet, "a check that costs nothing is not news: " + quiet);

        PopupGuard.install(() -> {
            try {
                Thread.sleep(600);   // over the threshold at which a guard becomes the reason a bot looks hung
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        String slow = printed(PopupGuard::check);

        List<String> lines = linesMatching(slow, "[Popup] check took");
        assertEquals(1, lines.size(), "a slow check gets its own line: " + slow);
    }

    @Test
    void aQuietRunSaysNothingAboutPopups() {
        Debug.disable();
        PopupGuard.install(() -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertEquals("", printed(PopupGuard::check));
    }

    // --- Vision ---

    private static BufferedImage noise() {
        BufferedImage bg = new BufferedImage(200, 150, BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(11);
        for (int y = 0; y < bg.getHeight(); y++) {
            for (int x = 0; x < bg.getWidth(); x++) bg.setRGB(x, y, rnd.nextInt(0xFFFFFF));
        }
        return bg;
    }

    /** A source whose frame the test swaps, so the same template can miss and then hit. */
    private static final class SwappableSource implements CaptureSource {
        BufferedImage frame;

        SwappableSource(BufferedImage frame) { this.frame = frame; }

        @Override public BufferedImage capture() { return frame; }
        @Override public Point origin() { return new Point(0, 0); }
        @Override public void click(Point p) {}
    }

    /**
     * A patch under a name no other test uses — the miss counter is process-global and keyed by template id,
     * so a shared id would let another test's finds be counted into this one's run.
     */
    private static ImageTemplate tracedPatch(Path dir) throws Exception {
        BufferedImage patch = new BufferedImage(24, 24, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) patch.setRGB(x, y, ((x * 9) << 16) | ((y * 7) << 8) | 0x20);
        }
        Path file = dir.resolve("trace_probe.png");
        ImageIO.write(patch, "png", file.toFile());
        return new ImageTemplate(file.toString());
    }

    @Test
    void aWaitPrintsOneLineForTheWholeRunOfMissesAndOneForTheHit(@TempDir Path tmp) throws Exception {
        ImageTemplate probe = tracedPatch(tmp);
        SwappableSource source = new SwappableSource(noise());
        BufferedImage withPatch = noise();
        withPatch.getGraphics().drawImage(ImageIO.read(tmp.resolve("trace_probe.png").toFile()), 40, 40, null);

        String output = printed(() -> {
            for (int i = 0; i < 12; i++) {
                ImageFinder.find(probe, source, 0.9);   // twelve polls of a wait loop, all misses
            }
            source.frame = withPatch;                   // …and then the thing appears
            assertTrue(ImageFinder.find(probe, source, 0.9), "fixture: the patch must be findable");
        });

        List<String> misses = linesMatching(output, "trace_probe not found");
        assertEquals(1, misses.size(), "twelve misses are one line, not twelve: " + output);
        assertTrue(misses.get(0).contains("×12"), "and the count is the information: " + misses.get(0));

        List<String> hits = linesMatching(output, "[Vision] find trace_probe →");
        assertEquals(1, hits.size(), "the hit is the interesting event and always prints: " + output);
    }

    @Test
    void aQuietRunSaysNothingAboutVision(@TempDir Path tmp) throws Exception {
        ImageTemplate probe = tracedPatch(tmp);
        SwappableSource source = new SwappableSource(noise());
        Debug.disable();

        assertEquals("", printed(() -> {
            for (int i = 0; i < 5; i++) ImageFinder.find(probe, source, 0.9);
        }));
    }
}
