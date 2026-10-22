package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.geometry.Size;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenShotsTest {

    @Test
    void aShotIsKeptAtTheCapturesSizeAndOnlyTheFirstWhenMissing(@TempDir Path resources) {
        ScreenShots.save(resources, "HOME", new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB));
        assertEquals(new Size(1920, 1080), ScreenShots.size(ScreenShots.file(resources, "HOME")), "the frame as captured");

        ScreenShots.saveIfMissing(resources, "HOME", new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB));
        assertEquals(new Size(1920, 1080), ScreenShots.size(ScreenShots.file(resources, "HOME")),
                "a screen with a shot keeps it");

        ScreenShots.remove(resources, "HOME");
        assertFalse(Files.exists(ScreenShots.file(resources, "HOME")));
        assertEquals(new Size(), ScreenShots.size(ScreenShots.file(resources, "HOME")), "no shot, no size");
    }

    @Test
    void aLookHasItsOwnShot(@TempDir Path resources) {
        ScreenShots.save(resources, "EVENT", new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB));
        ScreenShots.save(resources, "EVENT", "Halloween 2026", new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB));

        Path look = ScreenShots.file(resources, "EVENT", "Halloween 2026");
        assertEquals(resources.resolve("screens/EVENT@halloween-2026.png"), look);
        assertEquals(new Size(100, 50), ScreenShots.size(look));
        assertEquals(new Size(40, 30), ScreenShots.size(ScreenShots.file(resources, "EVENT")), "the plain look's kept");
        assertEquals(ScreenShots.file(resources, "EVENT"), ScreenShots.file(resources, "EVENT", " "));
        assertEquals(look, ScreenShots.shown(resources, "EVENT", "Halloween 2026"));
        assertEquals(ScreenShots.file(resources, "EVENT"), ScreenShots.shown(resources, "EVENT", "Winter"),
                "a look with no shot of its own shows the screen's");

        ScreenShots.save(resources, "EVENT_2", "Halloween 2026", new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));
        ScreenShots.remove(resources, "EVENT");
        assertFalse(Files.exists(look), "a screen gone takes its looks' shots along");
        assertTrue(Files.exists(ScreenShots.file(resources, "EVENT_2", "Halloween 2026")), "not another screen's");
    }
}
