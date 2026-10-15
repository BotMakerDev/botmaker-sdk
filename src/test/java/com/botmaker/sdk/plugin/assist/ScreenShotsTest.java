package com.botmaker.sdk.plugin.assist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ScreenShotsTest {

    @Test
    void aShotIsKeptAtTheCapturesSizeAndOnlyTheFirstWhenMissing(@TempDir Path resources) {
        ScreenShots.save(resources, "HOME", new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB));
        assertEquals(1920, ScreenShots.width(ScreenShots.file(resources, "HOME")), "the frame as captured");

        ScreenShots.saveIfMissing(resources, "HOME", new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB));
        assertEquals(1920, ScreenShots.width(ScreenShots.file(resources, "HOME")), "a screen with a shot keeps it");

        ScreenShots.remove(resources, "HOME");
        assertFalse(Files.exists(ScreenShots.file(resources, "HOME")));
        assertEquals(0, ScreenShots.width(ScreenShots.file(resources, "HOME")), "no shot, no width");
    }
}
