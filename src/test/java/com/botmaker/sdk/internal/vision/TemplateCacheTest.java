package com.botmaker.sdk.internal.vision;

import com.botmaker.shared.opencv.OpenCvNative;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.Mat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateCacheTest {

    @BeforeAll
    static void loadOpenCv() {
        OpenCvNative.ensureLoaded();
    }

    private static Path png(Path dir, String name, int width, int height) throws IOException {
        Path file = dir.resolve(name);
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
        return file;
    }

    private static Mat load(Path dir, String path) {
        return TemplateCache.load(path, dir, TemplateCacheTest.class.getClassLoader());
    }

    @Test
    void aPictureIsDecodedOnceAndEachCallerOwnsItsCopy(@TempDir Path dir) throws IOException {
        Path file = png(dir, "ore.png", 6, 4);

        Mat first = load(dir, "ore.png");
        first.release();
        // Same size, same modified time, no longer an image: only a read would notice.
        FileTime modified = Files.getLastModifiedTime(file);
        Files.write(file, new byte[(int) Files.size(file)]);
        Files.setLastModifiedTime(file, modified);
        Mat second = load(dir, "ore.png");

        assertFalse(second.empty(), "releasing one copy leaves the kept pixels whole");
        assertEquals(6, second.cols());
        second.release();
    }

    @Test
    void aFileChangedOnDiskIsReadAgain(@TempDir Path dir) throws IOException {
        Path file = png(dir, "ore.png", 6, 4);
        load(dir, "ore.png").release();

        png(dir, "ore.png", 9, 5);
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 2000));
        Mat changed = load(dir, "ore.png");

        assertEquals(9, changed.cols());
        changed.release();
    }

    @Test
    void aMissingPictureNamesWhereItLooked(@TempDir Path dir) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> load(dir, "gone.png"));

        assertTrue(e.getMessage().contains(dir.resolve("gone.png").toString()), e.getMessage());
    }
}
