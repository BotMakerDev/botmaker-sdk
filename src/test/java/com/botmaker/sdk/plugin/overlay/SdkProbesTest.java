package com.botmaker.sdk.plugin.overlay;

import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import com.botmaker.plugin.api.overlay.ProbeContext;
import com.botmaker.plugin.api.overlay.ProbeResult;
import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Executable;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The picture probes on generated frames: found where it is, missing where it is not, and never a click. */
class SdkProbesTest {

    private static final int SIZE = 48;
    private static final Area WATCHED = new Area(1000, 500, 400, 300);

    @TempDir
    Path dir;

    @Test
    void aPictureOnScreenIsFoundInDesktopPixels() throws Exception {
        ProbeResult result = SdkProbes.find(context(withPatchAt(120, 80), picture()));
        assertEquals(ProbeResult.State.FOUND, result.state(), result.text());
        assertEquals(Optional.of(new Area(1120, 580, SIZE, SIZE)), result.area());
        assertTrue(result.text().startsWith("found 1.00 at 1120,580"), result.text());
    }

    @Test
    void aClickSaysWhereItWouldLand() throws Exception {
        ProbeResult result = SdkProbes.wouldClick(context(withPatchAt(120, 80), picture()));
        assertEquals(ProbeResult.State.FOUND, result.state());
        assertTrue(result.text().startsWith("would click 1144,604"), result.text());
    }

    @Test
    void aPictureNotOnScreenIsMissingWithItsBestScore() throws Exception {
        ProbeResult result = SdkProbes.find(context(noise(), picture()));
        assertEquals(ProbeResult.State.MISSING, result.state(), result.text());
        assertTrue(result.text().contains("needs"), result.text());
        assertEquals(ProbeResult.State.FOUND, SdkProbes.gone(context(noise(), picture())).state());
    }

    @Test
    void noFrameOrNoPictureCannotTell() throws Exception {
        assertEquals(ProbeResult.State.UNKNOWN, SdkProbes.find(context(null, picture())).state());
        assertEquals(ProbeResult.State.UNKNOWN, SdkProbes.find(context(noise(), null)).state());
    }

    private Path picture() throws Exception {
        Path file = dir.resolve("ore.png");
        ImageIO.write(patch(), "png", file.toFile());
        return file;
    }

    private static ProbeContext context(BufferedImage frame, Path picture) {
        return new ProbeContext() {
            @Override
            public StudioServices services() {
                return SERVICES;
            }

            @Override
            public Executable call() {
                return null;
            }

            @Override
            public <T> Optional<T> argument(int index, Class<T> type) {
                if (picture == null || index != 0) return Optional.empty();
                return Optional.of(type.cast(new ImageTemplate(picture.toString())));
            }

            @Override
            public Optional<BufferedImage> frame() {
                return Optional.ofNullable(frame);
            }

            @Override
            public Optional<Area> watchedArea() {
                return Optional.of(WATCHED);
            }
        };
    }

    /** A project with no {@code Sdk.settings()}: the probe holds a match to the SDK's default confidence. */
    private static final StudioServices SERVICES = new StudioServices() {
        @Override public Path projectDir() { return null; }
        @Override public Path resourcesDir() { return null; }
        @Override public Theme theme() { return null; }
        @Override public Dialogs dialogs() { return null; }
    };

    private static BufferedImage withPatchAt(int x, int y) {
        BufferedImage frame = noise();
        frame.getGraphics().drawImage(patch(), x, y, null);
        return frame;
    }

    private static BufferedImage patch() {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                img.setRGB(x, y, ((x * 5) & 0xFF) << 16 | ((y * 5) & 0xFF) << 8 | ((x * 3 + y * 7) & 0xFF));
            }
        }
        return img;
    }

    private static BufferedImage noise() {
        BufferedImage bg = new BufferedImage(WATCHED.width(), WATCHED.height(), BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(11);
        for (int y = 0; y < bg.getHeight(); y++) {
            for (int x = 0; x < bg.getWidth(); x++) bg.setRGB(x, y, rnd.nextInt(0xFFFFFF));
        }
        return bg;
    }
}
