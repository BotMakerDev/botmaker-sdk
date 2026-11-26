// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.view;

import com.botmaker.plugin.api.run.ViewTarget;
import com.botmaker.sdk.api.capture.CaptureSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdkRunViewTest {

    private static final SdkRunView.Probes NOTHING_OPEN = new SdkRunView.Probes(w -> Optional.empty(), n -> Optional.empty());

    private static SdkRunView of(CaptureSource source, SdkRunView.Probes probes) {
        Supplier<CaptureSource> shown = () -> source;
        return new SdkRunView(shown, probes);
    }

    @Test
    void anOpenWindowIsTheTargetWithoutAReason() {
        SdkRunView view = of(CaptureSource.window("", "Firestone"), new SdkRunView.Probes(
                window -> Optional.of(new ViewTarget.Window("77", "Firestone — level 4")), n -> Optional.empty()));

        assertEquals(Optional.of(new ViewTarget.Window("77", "Firestone — level 4")), view.target());
        assertTrue(view.reason().isEmpty());
    }

    @Test
    void aClosedWindowShowsNothingAndSaysSoNeverTheDesktop() {
        SdkRunView view = of(CaptureSource.window("", "Firestone"), NOTHING_OPEN);

        assertTrue(view.target().isEmpty());
        assertEquals(Optional.of("The window “Firestone” is not open on the computer."), view.reason());
    }

    @Test
    void anEmulatorIsItsAdbSerialAndAMissingOneSaysSo() {
        CaptureSource emulator = CaptureSource.emulator("Pixel 10");

        assertEquals(Optional.of(new ViewTarget.Emulator("127.0.0.1:5555")),
                of(emulator, new SdkRunView.Probes(w -> Optional.empty(), n -> Optional.of("127.0.0.1:5555"))).target());
        SdkRunView missing = of(emulator, NOTHING_OPEN);
        assertTrue(missing.target().isEmpty());
        assertEquals(Optional.of("The emulator “Pixel 10” is not running or cannot be reached."), missing.reason());
    }

    @Test
    void aMonitorAndTheDesktopAreTargetsOfTheirOwn() {
        assertEquals(Optional.of(new ViewTarget.Monitor(1)), of(CaptureSource.monitor(1), NOTHING_OPEN).target());
        assertEquals(Optional.of(new ViewTarget.Desktop()), of(CaptureSource.desktop(), NOTHING_OPEN).target());
    }

    @Test
    void aSourceNobodyPickedShowsNothing() {
        SdkRunView view = of(null, NOTHING_OPEN);

        assertTrue(view.target().isEmpty());
        assertTrue(view.reason().isPresent());
    }
}
