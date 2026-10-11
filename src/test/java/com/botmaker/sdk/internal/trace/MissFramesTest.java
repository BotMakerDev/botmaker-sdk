package com.botmaker.sdk.internal.trace;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a timed-out wait leaves behind: one boxed frame, throttled, bounded per run and across runs. */
class MissFramesTest {

    @TempDir
    Path bot;

    private final AtomicLong now = new AtomicLong(1_000_000);

    @BeforeEach
    void setUp() {
        MissFrames.start(bot.resolve(MissFrames.FOLDER), now::get);
    }

    @AfterEach
    void tearDown() {
        MissFrames.stop();
    }

    private static CaptureSource screen(int width, int height) {
        return new CaptureSource() {
            @Override
            public BufferedImage capture() {
                return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            }

            @Override
            public Point origin() {
                return new Point(0, 0);
            }
        };
    }

    @Test
    void aFailureSavesOneFrameTheLatestRunLists() {
        MissFrames.missed("hero not found", screen(64, 48), null);
        List<Path> frames = MissFrames.latest(bot);
        assertEquals(1, frames.size());
        assertEquals("01-hero-not-found.png", frames.get(0).getFileName().toString());
    }

    @Test
    void theSameFailureRepeatingSavesOncePerQuietSpell() {
        CaptureSource source = screen(16, 16);
        MissFrames.missed("hero not found", source, null);
        now.addAndGet(MissFrames.QUIET_MS - 1);
        MissFrames.missed("hero not found", source, null);
        assertEquals(1, MissFrames.latest(bot).size());
        now.addAndGet(1);
        MissFrames.missed("hero not found", source, null);
        assertEquals(2, MissFrames.latest(bot).size());
    }

    @Test
    void aRunKeepsItsNewestFrames() {
        CaptureSource source = screen(8, 8);
        for (int i = 1; i <= MissFrames.KEEP + 5; i++) MissFrames.missed("miss " + i, source, null);
        List<Path> frames = MissFrames.latest(bot);
        assertEquals(MissFrames.KEEP, frames.size());
        assertEquals("06-miss-6.png", frames.get(0).getFileName().toString());
    }

    @Test
    void aRunThatSavedNothingIsTheLatestAndEmpty() {
        MissFrames.missed("hero not found", screen(8, 8), null);
        MissFrames.stop();
        MissFrames.start(bot.resolve(MissFrames.FOLDER), now::get);
        assertTrue(MissFrames.latest(bot).isEmpty());
    }

    @Test
    void nothingSavesOutsideARunOrWhenTheRunIsStopping() {
        MissFrames.stop();
        MissFrames.missed("hero not found", screen(8, 8), null);
        MissFrames.start(bot.resolve(MissFrames.FOLDER), now::get);
        Thread.currentThread().interrupt();
        try {
            MissFrames.missed("hero not found", screen(8, 8), null);
        } finally {
            Thread.interrupted();
        }
        assertTrue(MissFrames.latest(bot).isEmpty());
    }

    @Test
    void onlyTheLatestRunsAreKept() throws IOException {
        MissFrames.stop();
        Path runs = bot.resolve(MissFrames.FOLDER);
        for (int i = 0; i < 7; i++) Files.createDirectories(runs.resolve("20200101-00000" + i));
        MissFrames.start(runs, now::get);
        try (Stream<Path> dirs = Files.list(runs)) {
            assertEquals(MissFrames.KEEP_RUNS, dirs.count());
        }
        assertTrue(Files.exists(runs.resolve("20200101-000006")));
        assertTrue(Files.notExists(runs.resolve("20200101-000002")));
    }

    @Test
    void aRegionIsBoxedOnTheWholeSurface() throws IOException {
        CaptureSource region = screen(100, 80).region(new Rect(20, 10, 30, 30));
        MissFrames.missed("hero not found", region, null);
        BufferedImage saved = ImageIO.read(MissFrames.latest(bot).get(0).toFile());
        assertEquals(100, saved.getWidth());
        assertEquals(80, saved.getHeight());
        assertEquals(new Color(0xe3, 0xa0, 0x08).getRGB(), saved.getRGB(20, 25));
        assertEquals(Color.BLACK.getRGB(), saved.getRGB(35, 25));
    }

    @Test
    void aFrameNameIsAShortSlug() {
        assertEquals("timed-out-waiting-for-text-play", MissFrames.slug("timed out waiting for text \"Play\""));
        assertEquals("miss", MissFrames.slug("!!!"));
        assertTrue(MissFrames.slug("x".repeat(80)).length() <= 40);
    }
}
