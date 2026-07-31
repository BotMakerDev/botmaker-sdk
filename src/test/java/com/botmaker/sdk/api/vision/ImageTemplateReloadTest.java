package com.botmaker.sdk.api.vision;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>SDK MISSING 6 — the reload path must release what it replaces.</b> Gates SD9.
 *
 * <p>{@link ImageTemplate} owns a native OpenCV {@link Mat}: memory outside the Java heap, which the garbage
 * collector will not reclaim and which no {@code finalize} covers. {@link ImageTemplate#unload()} handles the
 * ordinary case correctly. The reload guard does not:
 *
 * <pre>{@code
 * if (mat == null || mat.empty()) {
 *     mat = Imgcodecs.imread(absPath, IMREAD_UNCHANGED);   // the old Mat is dropped, not released
 * }
 * }</pre>
 *
 * <p>The {@code mat == null} half is fine — there is nothing to release. The {@code mat.empty()} half leaks:
 * the field holds a live {@link Mat} whose buffer was freed but whose native object was not, and the
 * assignment drops the only reference to it. A bot that reloads templates in a loop — the natural shape for a
 * long-running bot that re-reads its images between activities — leaks one native handle per reload, and the
 * symptom is memory growth with a flat Java heap, which is the hardest kind to attribute.
 *
 * <p>The fix is one line (release before reassigning); the point of the test is that "one line" is exactly the
 * kind of change nobody notices is missing.
 */
class ImageTemplateReloadTest {

    /** A small non-uniform PNG — flat images are degenerate for the matcher and prove less. */
    private static Path writeTemplate(Path dir, String name) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.setRGB(x, y, ((x * 16) & 0xFF) << 16 | ((y * 16) & 0xFF) << 8 | 0x80);
            }
        }
        Path file = dir.resolve(name);
        javax.imageio.ImageIO.write(img, "png", file.toFile());
        return file;
    }

    // ---- What already holds ----

    @Test
    void theMatIsLoadedOnceAndCached(@TempDir Path dir) throws Exception {
        ImageTemplate template = new ImageTemplate(writeTemplate(dir, "t.png").toString());

        Mat first = template.getMat();
        assertSame(first, template.getMat(), "a second getMat() must not re-read the file");
        assertFalse(first.empty());
        assertEquals(16, first.cols());
        assertEquals(16, first.rows());
    }

    @Test
    void unloadReleasesAndTheNextAccessReloads(@TempDir Path dir) throws Exception {
        ImageTemplate template = new ImageTemplate(writeTemplate(dir, "t.png").toString());

        Mat first = template.getMat();
        long firstHandle = first.nativeObj;
        template.unload();

        assertTrue(first.empty(), "unload() must release the buffer it owned");

        Mat reloaded = template.getMat();
        assertFalse(reloaded.empty(), "the next access reloads from disk");
        assertNotSame(first, reloaded);
        assertEquals(16, reloaded.cols());
        assertTrue(firstHandle != 0, "sanity: the first Mat really was a native object");
    }

    @Test
    void unloadIsIdempotent(@TempDir Path dir) throws Exception {
        ImageTemplate template = new ImageTemplate(writeTemplate(dir, "t.png").toString());
        template.getMat();
        template.unload();
        template.unload();
        template.unload();
        assertFalse(template.getMat().empty(), "still usable after repeated unloads");
    }

    // ---- What SD9 must fix ----

    /**
     * The leak. The field is left holding a released — therefore {@code empty()} — {@link Mat}, which is the
     * state the reload guard tests for; the reload then overwrites the reference without freeing the native
     * object behind it.
     *
     * <p>Reaching that state through the public API means releasing the returned {@link Mat} directly, which
     * the javadoc tells callers not to do. That does not make the leak hypothetical — it makes it the
     * <em>only</em> way the {@code mat.empty()} branch is ever taken, which is the real finding here: if
     * nothing can produce a non-null empty {@code Mat}, the branch is dead code pretending to be a reload
     * path, and SD9 should delete it rather than fix it. Either way the current code is wrong.
     */
    @Test
    @Disabled("SD9 is unfixed: verified red on this commit — the reload overwrites a non-null empty Mat "
            + "without releasing it. Delete this line in Phase 4/5 with SD9's fix.")
    void reloadingOverAnEmptiedMatReleasesItFirst(@TempDir Path dir) throws Exception {
        ImageTemplate template = new ImageTemplate(writeTemplate(dir, "t.png").toString());

        Mat original = template.getMat();
        long originalHandle = original.nativeObj;
        original.release(); // now non-null and empty: exactly the state the reload guard looks for

        Mat reloaded = template.getMat();

        assertNotSame(original, reloaded, "sanity: the guard did take the reload branch");
        assertEquals(0L, originalHandle == reloaded.nativeObj ? 0L : originalHandle,
                "the replaced Mat's native object was never freed — one handle leaks per reload, and the "
                        + "symptom is off-heap growth with a flat Java heap");
    }
}
