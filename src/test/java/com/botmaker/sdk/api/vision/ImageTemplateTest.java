package com.botmaker.sdk.api.vision;

import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests {@link ImageTemplate} — config derivation and the lazily-owned OpenCV {@link Mat} that was
 * folded in from the old internal {@code Template} wrapper.
 */
class ImageTemplateTest {

    private static final String IMAGE = "src/main/resources/images/default_template.png";

    private static ImageTemplate template() {
        assumeTrue(Files.exists(Path.of(IMAGE)), "default_template.png is required for these tests");
        return new ImageTemplate(IMAGE);
    }

    @Test
    void derivesIdFromFileName() {
        assertEquals("default_template", new ImageTemplate("images/default_template.png").getId());
        assertEquals("btn", new ImageTemplate("btn").getId()); // no extension
    }

    @Test
    void defaultAndCustomThreshold() {
        assertEquals(0.8, new ImageTemplate(IMAGE).getThreshold(), 1e-9);
        assertEquals(0.95, new ImageTemplate(IMAGE, 0.95).getThreshold(), 1e-9);

        ImageTemplate t = new ImageTemplate(IMAGE);
        t.setThreshold(0.5);
        assertEquals(0.5, t.getThreshold(), 1e-9);
    }

    @Test
    void blankPathRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ImageTemplate("  "));
        assertThrows(IllegalArgumentException.class, () -> new ImageTemplate(null));
    }

    @Test
    void getMatLoadsImageLazily() {
        try (ImageTemplate t = template()) {
            Mat mat = t.getMat();
            assertFalse(mat.empty());
            assertEquals(32, t.width());
            assertEquals(32, t.height());
            // Same Mat instance returned while loaded.
            assertSame(mat, t.getMat());
        }
    }

    @Test
    void unloadReleasesAndReloads() {
        ImageTemplate t = template();
        Mat first = t.getMat();
        assertFalse(first.empty());

        t.unload();
        Mat second = t.getMat(); // must transparently reload
        assertFalse(second.empty());
        assertEquals(32, second.cols());

        t.close();
    }

    @Test
    void missingFileThrowsOnLoad() {
        ImageTemplate t = new ImageTemplate("does/not/exist.png");
        assertThrows(RuntimeException.class, t::getMat);
    }
}
