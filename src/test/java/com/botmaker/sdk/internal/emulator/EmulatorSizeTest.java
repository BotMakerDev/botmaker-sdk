// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.emulator;

import com.botmaker.sdk.api.geometry.Size;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What {@code wm} answers, and the density a smaller display is given. */
class EmulatorSizeTest {

    @Test
    void theDisplaysOwnSizeIsReadPastAnOverride() {
        assertEquals(Optional.of(new Size(1080, 1920)),
                EmulatorSize.physical("Physical size: 1080x1920\nOverride size: 720x1280\n"));
        assertEquals(Optional.empty(), EmulatorSize.physical("error: no display"));
        assertEquals(OptionalInt.of(420), EmulatorSize.density("Physical density: 420\nOverride density: 280"));
        assertEquals(OptionalInt.empty(), EmulatorSize.density(null));
    }

    @Test
    void theDensityScalesWithTheDisplay() {
        // 1080×1920 at 420 dpi shown at 720×1280: two thirds the pixels, two thirds the density.
        assertEquals(280, EmulatorSize.scaledDensity(420, new Size(1080, 1920), new Size(720, 1280)));
        // Another shape scales by the smaller side, so nothing is laid out past the edge.
        assertEquals(210, EmulatorSize.scaledDensity(420, new Size(1080, 1920), new Size(540, 1280)));
    }

    @Test
    void aSourceThatIsNoEmulatorIsLeftAlone() {
        assertFalse(EmulatorSize.showAt(new EmulatorSource(""), new Size(1280, 720)));
        assertFalse(EmulatorSize.holdAt(null, new Size(1280, 720)));
    }

    /** A Waydroid-like guest: 1080×1920 at 180 dpi, portrait, with an override the user set. */
    static final class FakeDisplay implements EmulatorSize.Display {
        final List<String> said = new ArrayList<>();

        @Override
        public String shell(String command) {
            said.add(command);
            return switch (command) {
                case "wm size" -> "Physical size: 1080x1920\nOverride size: 900x1600\n";
                case "wm density" -> "Physical density: 180\n";
                default -> "";
            };
        }

        @Override
        public BufferedImage screencap() {
            return new BufferedImage(9, 16, BufferedImage.TYPE_INT_RGB);
        }

        @Override
        public String endpoint() {
            return "192.0.2.1:5555";
        }
    }

    /**
     * The tools bring the emulator to the bot's size once, however many looks follow, and the project's close puts
     * back what the user had: their override size, and the density reset (2026-11-27).
     */
    @Test
    void theToolsSetTheSizeOnceAndPutTheUsersBackOnClose() {
        FakeDisplay display = new FakeDisplay();
        Size bot = new Size(720, 1280);
        assertTrue(EmulatorSize.hold("waydroid", display, bot));
        assertTrue(EmulatorSize.hold("waydroid", display, bot));
        assertEquals(List.of("wm size", "wm density", "wm size 720x1280", "wm density 120"), display.said);
        display.said.clear();
        EmulatorSize.releaseAll();
        assertEquals(List.of("wm size 900x1600", "wm density reset"), display.said);
        display.said.clear();
        EmulatorSize.releaseAll();
        assertTrue(display.said.isEmpty(), "released once");
    }

    /** A second size of one emulator still puts back the user's own on close, not the tools' first. */
    @Test
    void aSecondSizeKeepsTheUsersRestore() {
        FakeDisplay display = new FakeDisplay();
        assertTrue(EmulatorSize.hold("waydroid", display, new Size(720, 1280)));
        assertTrue(EmulatorSize.hold("waydroid", display, new Size(540, 960)));
        display.said.clear();
        EmulatorSize.releaseAll();
        assertEquals(List.of("wm size 900x1600", "wm density reset"), display.said);
    }
}
