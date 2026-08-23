package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.capture.CaptureSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>B9's gate.</b> A template whose PNG is missing, renamed or never copied must report <em>that</em>, not
 * "the image is not on screen".
 *
 * <p>{@link ImageTemplate#getMat()} already produces the right diagnostic — {@code "Failed to load image
 * template. Path: …"} — and every public path swallows it. {@code ImageFinder.findInternal} catches
 * {@link Exception} and returns {@link MatchResult#notFound()}, two lines below a comment explaining that
 * {@link Error} is deliberately <em>not</em> caught "so it cannot masquerade as 'not found'". The exception
 * masquerades.
 *
 * <p>The cost is not a missing message; it is a wrong one. The bot does not fail — it runs forever doing
 * nothing, reporting the image is absent. {@code ImageWaiter.waitFor} spins to its timeout, {@code ImageClicker}
 * never clicks, an {@code Activity} guarded on the find never fires. The user's diagnosis is "my template
 * doesn't match well enough", and they spend the session lowering a confidence threshold on an image that was
 * never loaded. Reached by renaming a PNG, or by cloning a bot project without its {@code images/} directory —
 * which the SDK's own {@code .gitignore} excludes.
 *
 * <p>The contract these tests assert: <b>"could not load" and "not on screen" must be distinguishable by the
 * bot</b>, not merely by someone reading a debug stream. Whether that is an exception or a third
 * {@code MatchResult} state is SD3's call; either satisfies this.
 */
class MissingTemplateTest {

    /**
     * A source that yields a real, structured frame — so nothing here can fail for want of a screen.
     *
     * <p>Deliberately <b>not</b> a uniform fill. Normalised correlation is degenerate on a zero-variance
     * region: a flat template against a flat frame scores 1.0 and "matches" everywhere, which would make an
     * absent-template test pass for entirely the wrong reason. A gradient has variance everywhere and matches
     * nothing in particular.
     */
    private static CaptureSource blankSource() {
        return new CaptureSource() {
            @Override
            public java.awt.image.BufferedImage capture() {
                java.awt.image.BufferedImage frame =
                        new java.awt.image.BufferedImage(320, 240, java.awt.image.BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < 240; y++) {
                    for (int x = 0; x < 320; x++) {
                        frame.setRGB(x, y, ((x * 7) & 0xFF) << 16 | ((y * 5) & 0xFF) << 8 | 0x40);
                    }
                }
                return frame;
            }

            @Override
            public com.botmaker.sdk.api.geometry.Point origin() {
                return new com.botmaker.sdk.api.geometry.Point(0, 0);
            }
        };
    }

    // ---- What already works: the diagnostic exists, one layer down. ----

    @Test
    void loadingAMissingTemplateSaysSoWithItsPath(@TempDir Path dir) {
        Path absent = dir.resolve("no-such-button.png");
        ImageTemplate template = new ImageTemplate(absent.toString());

        RuntimeException failure = assertThrows(RuntimeException.class, template::getMat,
                "getMat() is where the precise diagnostic lives, and it must keep throwing — every fix for "
                        + "B9 is about propagating this, not replacing it");
        assertNotNull(failure.getMessage());
        assertTrue(failure.getMessage().contains("no-such-button.png"),
                "the message must name the file the user has to go find: " + failure.getMessage());
    }

    @Test
    void loadingAFileThatIsNotAnImageAlsoFails(@TempDir Path dir) throws Exception {
        Path notAnImage = dir.resolve("button.png");
        Files.writeString(notAnImage, "this is not a PNG");

        assertThrows(RuntimeException.class, new ImageTemplate(notAnImage.toString())::getMat,
                "a corrupt or truncated PNG is the same user-visible problem as a missing one");
    }

    // ---- What B9 breaks: the public API answers the wrong question. ----

    @Test
    @Disabled("B9 is unfixed: verified red on this commit — find() on a missing template returns false, "
            + "indistinguishable from 'not on screen'. Delete this line in Phase 4 with SD3's fix.")
    void findOnAMissingTemplateDoesNotReportItAsNotOnScreen(@TempDir Path dir) {
        ImageTemplate missing = new ImageTemplate(dir.resolve("no-such-button.png").toString());

        assertThrows(RuntimeException.class, () -> ImageFinder.find(missing, blankSource()),
                "find() returned normally for a template that could not be loaded. The bot cannot tell this "
                        + "from a genuine miss, so it waits, retries and never fires — and the user lowers the "
                        + "confidence threshold on an image that was never read off disk.");
    }

    @Test
    @Disabled("B9, as above — re-enable with SD3 in Phase 4.")
    void findAnyOnAMissingTemplateDoesNotReportItAsNotOnScreen(@TempDir Path dir) {
        ImageTemplate missing = new ImageTemplate(dir.resolve("gone.png").toString());

        assertThrows(RuntimeException.class, () -> ImageFinder.findAny(blankSource(), missing),
                "findAny() has the same swallow at a different call site; fixing findInternal alone is not "
                        + "enough, which is why B9 lists five locations");
    }

    /**
     * The distinction that must survive the fix. A template that <em>loads</em> and is genuinely absent from
     * the frame has to keep returning a normal not-found — otherwise SD3 has traded a silent wrong answer for
     * a bot that crashes every time it looks for something that is not there yet, which is most polls.
     */
    @Test
    void aTemplateThatLoadsAndIsAbsentIsStillAnOrdinaryMiss(@TempDir Path dir) throws Exception {
        Path real = dir.resolve("checker.png");
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(24, 24,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                img.setRGB(x, y, ((x / 4 + y / 4) % 2 == 0) ? 0x00FF00FF : 0x00003300);
            }
        }
        javax.imageio.ImageIO.write(img, "png", real.toFile());

        ImageTemplate template = new ImageTemplate(real.toString());
        // The gradient frame contains no magenta checkerboard. This must be false, not an exception.
        assertTrue(!ImageFinder.find(template, blankSource()),
                "an ordinary miss must stay an ordinary miss — the fix distinguishes 'could not load' from "
                        + "'not on screen', it does not make every miss loud");
    }
}
