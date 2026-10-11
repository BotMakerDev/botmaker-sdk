package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.internal.vision.BotScreen;
import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;

import java.awt.Dimension;
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
        assertEquals("default_template", new ImageTemplate("images/default_template.png").id());
        assertEquals("btn", new ImageTemplate("btn").id()); // no extension
    }

    @Test
    void whereItWasCutIsPartOfTheValue() {
        ImageTemplate plain = new ImageTemplate("images/ok.png");
        ImageTemplate cut = plain.at(new Rect(10, 20, 30, 40)).in(new Size(1920, 1080));
        assertEquals(new Rect(10, 20, 30, 40), cut.place());
        assertEquals(new Size(1920, 1080), cut.frame());
        assertEquals("ok", cut.id());
        assertEquals(cut, new ImageTemplate("images/ok.png").in(new Size(1920, 1080)).at(new Rect(10, 20, 30, 40)));
        assertNotEquals(plain, cut);
        assertEquals(plain, plain.at(new Rect(0, 0, 0, 0)).in(new Size(0, 0)), "an empty place or frame is none");
    }

    @Test
    void itsAuthoredSizeIsItsOwnFrameElseTheBotsScreenElseNone() {
        try {
            BotScreen.use(null);
            assertNull(new ImageTemplate("images/ok.png").authoredSize());
            BotScreen.use(new Size(1280, 720));
            assertEquals(new Dimension(1280, 720), new ImageTemplate("images/ok.png").authoredSize());
            assertEquals(new Dimension(1920, 1080),
                    new ImageTemplate("images/ok.png").in(new Size(1920, 1080)).authoredSize());
        } finally {
            BotScreen.use(null);
        }
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
