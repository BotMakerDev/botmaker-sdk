// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.view;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.RunView;
import com.botmaker.plugin.api.run.ViewTarget;
import com.botmaker.plugin.api.run.ViewedRun;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.internal.capture.Monitor;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.observe.RunEvent;
import com.botmaker.sdk.internal.observe.RunFrames;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.sdk.plugin.screen.CaptureValue;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the host's Remote Pilot shows of the bot's run (pilot in core, spec {@code 2026-11-26-pilot-in-core-design.md}):
 * the surface the bot's capture settings name — its game window, emulator instance, monitor or the desktop — and why
 * there is none. Never the whole desktop for a game whose window or emulator is missing: a pilot may be reachable over
 * a public link, so nothing stands in for the game ({@link CaptureValue#shown}).
 */
final class SdkRunView implements RunView {

    /** Where the surface's facts come from; the live ones are the open windows and this PC's emulators. */
    record Probes(Function<NamedWindow, Optional<ViewTarget.Window>> window,
                  Function<String, Optional<String>> serialOf) {}

    /** The target, or the reason there is none: exactly one is present. */
    private record Seen(Optional<ViewTarget> target, Optional<String> reason) {

        static Seen of(ViewTarget target) {
            return new Seen(Optional.of(target), Optional.empty());
        }

        static Seen because(String reason) {
            return new Seen(Optional.empty(), Optional.of(reason));
        }
    }

    private final Probes probes;
    private volatile Supplier<CaptureSource> source = () -> null;

    SdkRunView(Probes probes) {
        this.probes = probes;
    }

    /** A view reading {@code source} as the bot's capture source: for the tests. */
    SdkRunView(Supplier<CaptureSource> source, Probes probes) {
        this(probes);
        this.source = source;
    }

    @Override
    public void start(StudioServices services, Runnable changed) {
        source = () -> CaptureValue.shown(services);
    }

    /**
     * A run started with an emulator instance named ({@code InstalledGame.CAPTURE_EMULATOR}, as the Google sign-in
     * is, one per instance) shows that instance, as the run itself reads it; any other run shows what the project's
     * capture settings name.
     */
    @Override
    public void start(StudioServices services, ViewedRun run, Runnable changed) {
        String instance = run.properties().get(InstalledGame.CAPTURE_EMULATOR);
        if (instance != null && !instance.isBlank()) {
            CaptureSource device = CaptureSource.emulator(instance.strip());
            source = () -> device;
            return;
        }
        start(services, changed);
    }

    @Override
    public Optional<ViewTarget> target() {
        return seen().target();
    }

    @Override
    public Optional<String> reason() {
        return seen().reason();
    }

    /** The frame the bot's agent wrote, as the message the phone draws; empty for a step, which is drawn nowhere. */
    @Override
    public Optional<String> overlay(byte[] frame) {
        if (frame == null || frame.length == 0) return Optional.empty();
        try {
            RunEvent event = RunFrames.decode(frame);
            return event != null && event.drawn() ? Optional.of(TelemetrySerializer.telemetryJson(event))
                    : Optional.empty();
        } catch (RunFrames.Unreadable unreadable) {
            return Optional.empty();
        }
    }

    private Seen seen() {
        CaptureSource shown = source.get();
        if (shown == null) {
            return Seen.because("The game's window or device is not known yet. Run the bot once, or pick a capture source.");
        }
        NamedWindow window = CaptureLabels.window(shown);
        if (window != null) {
            return probes.window().apply(window).map(Seen::of)
                    .orElseGet(() -> Seen.because("The window “" + window.label() + "” is not open on the computer."));
        }
        String emulator = CaptureLabels.emulatorName(shown);
        if (emulator != null) {
            return probes.serialOf().apply(emulator).map(serial -> Seen.of(ViewTarget.emulator(serial)))
                    .orElseGet(() -> Seen.because("The emulator “" + emulator + "” is not running or cannot be reached."));
        }
        if (CaptureLabels.whole(shown) instanceof Monitor) {
            return Seen.of(ViewTarget.monitor(CaptureLabels.monitorIndex(shown)));
        }
        return Seen.of(ViewTarget.desktop());
    }
}
