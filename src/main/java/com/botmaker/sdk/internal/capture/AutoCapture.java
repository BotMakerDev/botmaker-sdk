// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.sdk.internal.web.WebSource;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Where a run reads its pixels (2026-11-25, spec {@code 2026-11-25-auto-capture-design.md}), from what the run is given:
 * the first of these that applies.
 *
 * <ol>
 *   <li>The instance a host gave the run ({@code botmaker.capture.emulator}).</li>
 *   <li>A website target: the run's own browser.</li>
 *   <li>A capture set by hand, moved onto a dealt instance when it is an emulator's.</li>
 *   <li>Automatic with an emulator or phone app target: the instance dealt, else the one the target names, else
 *       the one running.</li>
 *   <li>Automatic with a desktop game target: the window picked on an earlier run, else the one its launch opens.</li>
 *   <li>Nothing launched: the whole desktop.</li>
 * </ol>
 *
 * Pure: the running instances are asked only when the target names none and no instance was dealt.
 */
public final class AutoCapture {

    /** What the run is given; {@code capture} {@code null} when the bot marks none. */
    public record Inputs(String emulatorProperty, LaunchSpec target, CaptureSource capture, Object dealt,
                         Optional<CaptureSource> savedWindow, Supplier<List<String>> runningInstances) {}

    /** The answer: a source to read, a launch to watch for its window, or why the run cannot start. */
    public sealed interface Decision permits Decision.Fixed, Decision.WatchLaunch, Decision.Fail {

        /**
         * Read {@code source} ({@code null}: the whole desktop); {@code why} is the trace's line, {@code null} for a
         * source the run did not choose, and {@code playsOn} the instance an emulator app is launched on, or {@code ""}.
         */
        record Fixed(CaptureSource source, String why, String playsOn) implements Decision {}

        /**
         * Launch {@code target} and read the window it opens, narrowed to {@code regions} (innermost first) when the
         * capture is a region of automatic.
         */
        record WatchLaunch(LaunchSpec target, List<Rect> regions) implements Decision {

            public WatchLaunch {
                regions = List.copyOf(regions);
            }

            /** {@code window} as the run reads it: narrowed to the regions the capture names. */
            public CaptureSource around(CaptureSource window) {
                CaptureSource source = window;
                for (Rect sub : regions) source = CaptureSource.region(source, sub);
                return source;
            }
        }

        record Fail(String message) implements Decision {}
    }

    private AutoCapture() {}

    public static Decision decide(Inputs in) {
        // A region of the automatic source is the region of what it decides (pinned as it is, it would read itself).
        if (in.capture() instanceof RegionSource region && unwrapped(region) instanceof AmbientSource) {
            Decision whole = decide(new Inputs(in.emulatorProperty(), in.target(), region.parent(), in.dealt(),
                    in.savedWindow(), in.runningInstances()));
            return switch (whole) {
                case Decision.Fixed fixed -> new Decision.Fixed(CaptureSource.region(fixed.source() == null
                        ? CaptureSource.desktop() : fixed.source(), region.sub()), fixed.why(), fixed.playsOn());
                // The window is not up yet: the region waits for it, and is applied once it is picked.
                case Decision.WatchLaunch watch -> new Decision.WatchLaunch(watch.target(),
                        Stream.concat(watch.regions().stream(), Stream.of(region.sub())).toList());
                case Decision.Fail fail -> fail;
            };
        }
        LaunchSpec target = in.target();
        if (in.emulatorProperty() != null && !in.emulatorProperty().isBlank()) {
            CaptureSource device = CaptureSource.emulator(in.emulatorProperty().strip());
            return new Decision.Fixed(device, null, deviceOf(target, device));
        }
        if (target != null && target.kind() == LaunchKind.WEB) {
            return new Decision.Fixed(WebSource.over(target.token()), null, "");
        }
        CaptureSource capture = in.capture();
        if (!isAutomatic(capture, target)) {
            CaptureSource moved = onPlace(capture, in.dealt());
            String playsOn = moved != capture ? android(in.dealt()) : deviceOf(target, moved);
            return new Decision.Fixed(moved, null, playsOn);
        }
        if (target == null || target.kind() == LaunchKind.UNKNOWN) return new Decision.Fixed(null, null, "");
        if (target.kind() == LaunchKind.EMULATOR_APP) return onDevice(target, in);
        return in.savedWindow()
                .<Decision>map(window -> new Decision.Fixed(window, "capture: " + said(window)
                        + ", picked on an earlier run", ""))
                .orElseGet(() -> new Decision.WatchLaunch(target, List.of()));
    }

    /**
     * Whether {@code capture} leaves the choice to the game: automatic, the ambient source, none, or the whole desktop
     * while a game is launched (a whole desktop is never where a launched game is; what the private parameters' move
     * of 2026-11-21 seeded).
     */
    public static boolean isAutomatic(CaptureSource capture, LaunchSpec target) {
        if (capture == null || capture instanceof AmbientSource) return true;
        return capture instanceof Desktop && target != null && target.kind() != LaunchKind.UNKNOWN;
    }

    /**
     * Whether {@link #decide} can use a window picked on an earlier run: an automatic capture (a region of it too) of a
     * desktop game. Else the pick is not read at all.
     */
    public static boolean wantsSavedWindow(CaptureSource capture, LaunchSpec target) {
        if (target == null) return false;
        LaunchKind kind = target.kind();
        if (kind == LaunchKind.UNKNOWN || kind == LaunchKind.WEB || kind == LaunchKind.EMULATOR_APP) return false;
        return followsTarget(capture, target);
    }

    /** {@link #isAutomatic} of {@code capture}, or of the source a region of it narrows. */
    public static boolean followsTarget(CaptureSource capture, LaunchSpec target) {
        return isAutomatic(capture instanceof RegionSource region ? unwrapped(region) : capture, target);
    }

    /**
     * {@code capture} on the instance {@code place} names, when the place is an Android one naming an instance and the
     * capture is an emulator's (a region of it kept); else {@code capture} as it is: a PC place leaves it.
     */
    public static CaptureSource onPlace(CaptureSource capture, Object place) {
        String instance = android(place);
        if (instance.isEmpty() || capture == null || !(capture.base() instanceof EmulatorSource)) return capture;
        CaptureSource moved = CaptureSource.emulator(instance);
        Rect region = capture.subRegion();
        return region == null ? moved : moved.region(region);
    }

    /** The instance {@code place} names when it is an Android place naming one; else {@code ""}. */
    public static String android(Object place) {
        return place instanceof Place named && named.namesAndroid() ? named.name() : "";
    }

    private static CaptureSource unwrapped(CaptureSource source) {
        CaptureSource s = source;
        while (s instanceof RegionSource region) s = region.parent();
        return s;
    }

    /** A window as the trace says it: {@code window "Sword x Staff" (class steam_app_1)}. */
    public static String said(CaptureSource window) {
        if (!(window instanceof NamedWindow named)) return String.valueOf(window);
        return "window \"" + named.label() + "\" (class " + named.windowClass() + ")";
    }

    /**
     * The instance an emulator app naming none (as targets are written since 2026-11-27) is launched on when the run
     * reads {@code source}: its device; {@code ""} when the target names one, is another kind, or the source is no
     * device's.
     */
    private static String deviceOf(LaunchSpec target, CaptureSource source) {
        if (target == null || target.kind() != LaunchKind.EMULATOR_APP || target.emulatorInstance() != null) return "";
        return source != null && source.base() instanceof EmulatorSource device ? device.instanceName() : "";
    }

    private static Decision onDevice(LaunchSpec target, Inputs in) {
        String named = target.emulatorInstance();
        String instance;
        if (!android(in.dealt()).isEmpty()) instance = android(in.dealt());
        else if (named != null && !named.isBlank()) instance = named;
        else {
            List<String> running = in.runningInstances().get();
            // None up yet (or none asked, as in Studio's views): the whole desktop, and the launch starts what it can.
            if (running.isEmpty()) {
                return new Decision.Fixed(null, "capture: the whole desktop, no instance runs and the target names none",
                        "");
            }
            if (running.size() > 1) {
                return new Decision.Fail("which instance? the target names none and " + running.size() + " run");
            }
            instance = running.getFirst();
        }
        return new Decision.Fixed(CaptureSource.emulator(instance), "capture: " + instance + ", from the game's target",
                instance.equals(named) ? "" : instance);
    }
}
