package com.botmaker.sdk.internal.emulator;

import com.botmaker.sdk.api.geometry.Size;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    }
}
