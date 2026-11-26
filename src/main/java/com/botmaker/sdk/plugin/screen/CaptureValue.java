// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.screen;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.ManagedParam;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.capture.AutoCapture;
import com.botmaker.sdk.internal.capture.AutoPicks;
import com.botmaker.sdk.internal.capture.HostWindows;
import com.botmaker.sdk.internal.capture.CurrentSource;
import com.botmaker.sdk.internal.capture.Desktop;
import com.botmaker.sdk.internal.capture.AmbientSource;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.sdk.plugin.params.LaunchTargets;
import com.botmaker.sdk.plugin.params.ParamSlot;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.WindowIdentity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code @SdkValue(SdkValue.Id.CAPTURE)} value — where a bot reads pixels from, the parameter
 * {@code Sdk.captureSource}, which the Runner shows too.
 *
 * <p>This is the only copy of the answer. {@code Sdk.captureSource} is Java the bot compiles, so a
 * developer with no BotMaker installed can read it, change it and see the change take effect.
 *
 * <h2>It is allowed to fail quietly</h2>
 *
 * <p>A project may have no {@code Sdk.java}, and a user may have written {@code captureSource} by hand in a
 * shape the host does not read. In both cases there is nothing to write and nothing has gone wrong: the Java
 * the user wrote is still theirs.
 */
public final class CaptureValue {

    private CaptureValue() {}

    /**
     * The project's capture source, or {@code null} when its Java names none the host can read.
     *
     * <p>{@code null} covers an absent {@code Sdk.java}, a body the host will not read, an expression the
     * user wrote themselves, and {@code Source.current()}, which in the editor names nothing to look at.
     * Read on every call rather than held: a source changed in another window has to take effect at once.
     */
    public static CaptureSource current(StudioServices services) {
        return current(services, CaptureSource.desktop());
    }

    /**
     * As {@link #current}, but {@code null} where {@code current} answers the whole desktop for a launched game:
     * its window not picked yet, or its emulator instance not told. What a view that shows the screen to somebody
     * else reads: the desktop does not stand in for a game there.
     */
    public static CaptureSource shown(StudioServices services) {
        return current(services, null);
    }

    private static CaptureSource current(StudioServices services, CaptureSource unpicked) {
        CaptureSource written = written(services);
        Place dealt = read(services, SdkValues.PLACE, Place.class).orElse(null);
        LaunchSpec target = LaunchTargets.spec(services);
        // A website is read in a run's own browser, which Studio's views have not: they keep what the bot writes.
        if (target != null && target.kind() == LaunchKind.WEB) {
            return AutoCapture.isAutomatic(written, target) ? null : InstalledGame.onPlace(written, dealt);
        }
        return resolved(services, target, written, dealt, unpicked);
    }

    /**
     * {@code written} as a run of the bot decides it, the instance {@code dealt} and the window a run picked for the
     * game included; the whole desktop for a desktop game's window not picked yet or an instance that cannot be told.
     * Never probes the emulators: Studio's views ask it on the FX thread at each refresh. {@code null} only when a run
     * would stop ({@link AutoCapture.Decision.Fail}): a decision of no source is the whole desktop, which a view grabs.
     */
    static CaptureSource resolved(StudioServices services, LaunchSpec target, CaptureSource written,
                                  Place dealt) {
        return resolved(services, target, written, dealt, CaptureSource.desktop());
    }

    static CaptureSource resolved(StudioServices services, LaunchSpec target, CaptureSource written,
                                  Place dealt, CaptureSource unpicked) {
        AutoCapture.Decision decision = AutoCapture.decide(new AutoCapture.Inputs(null, target, written, dealt,
                savedWindow(services, target, written), List::of));
        return switch (decision) {
            case AutoCapture.Decision.Fixed fixed -> {
                CaptureSource source = fixed.source() == null ? CaptureSource.desktop() : fixed.source();
                // A launched game left to the automatic capture is never on "the desktop": it is just not told yet.
                boolean untold = target != null && target.kind() != LaunchKind.UNKNOWN
                        && AutoCapture.followsTarget(written, target) && source.base() instanceof Desktop;
                yield untold ? unpicked : source;
            }
            case AutoCapture.Decision.WatchLaunch watch -> unpicked;
            case AutoCapture.Decision.Fail fail -> null;
        };
    }

    /** The window a run picked for {@code target}, read only when an automatic capture of a desktop game asks it. */
    private static Optional<CaptureSource> savedWindow(StudioServices services, LaunchSpec target,
                                                       CaptureSource written) {
        if (services == null || !AutoCapture.wantsSavedWindow(written, target)) return Optional.empty();
        try {
            HostWindows kept = services.hostValues().open(AutoPicks.VALUE)
                    .flatMap(ctx -> ctx.value(HostWindows.class)).orElse(null);
            return AutoPicks.shown(kept).saved(target);
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * The capture source as the bot writes it, before {@code place()} moves an emulator's onto the instance it
     * names: what a rewrite of the parameter starts from, and what the source picker preselects.
     */
    public static CaptureSource written(StudioServices services) {
        return read(services, SdkValues.CAPTURE, CaptureSource.class)
                .filter(source -> !(source instanceof CurrentSource)).orElse(null);
    }

    private static <T> Optional<T> read(StudioServices services, ManagedParam<T> parameter, Class<T> type) {
        return ParamSlot.open(services, parameter).flatMap(ctx -> {
            try {
                return ctx.value(type);
            } catch (RuntimeException unreadable) {
                return Optional.empty();
            }
        });
    }

    /**
     * Writes {@code source} into {@code Sdk.captureSource}, asking the host to write the parameter first when the
     * project lacks it ({@link ParamSlot}: a parameter since 2026-10-13).
     *
     * @return empty when written, else the sentence to show
     */
    public static Optional<String> write(StudioServices services, CaptureSource source) {
        ParamSlot.Opened opened = ParamSlot.openOrCreate(services, SdkValues.CAPTURE, "Sdk.captureSource");
        return opened.ctx().map(ctx -> ctx.write(source)).orElseGet(() -> Optional.of(opened.refused()));
    }

    // pointHere(ActionContext), the overlay's ⌖ Point bot here, stood here until 2026-11-13: the item is on the bar
    // now, where no window is under the HUD, and opens the source picker (SdkToolbarItems.POINT_HERE).

    /**
     * The open window titled {@code title} as a source, by its class and, when that class is generic, its title
     * ({@link WindowIdentity}); empty when no such window is open, it has no class, or the windows cannot be listed.
     * Only {@linkplain OpenWindows#pickable pickable} windows, and the exact title ignoring case: a nearest title
     * would pick "#tools | Firestone Idle RPG - Discord" for "Firestone".
     */
    public static Optional<CaptureSource> windowTitled(String title) {
        return windowTitled(title, NativeControllerFactory.get());
    }

    /** As {@link #windowTitled(String)}, among {@code controller}'s windows: a private display's, say. */
    public static Optional<CaptureSource> windowTitled(String title, NativeController controller) {
        if (title == null || title.isBlank()) return Optional.empty();
        List<GenericWindow> open;
        try {
            open = OpenWindows.pickable(controller.getAllWindows());
        } catch (RuntimeException | LinkageError noWindows) {
            return Optional.empty();
        }
        return open.stream()
                .filter(w -> w.getTitle().trim().equalsIgnoreCase(title.trim()))
                .map(WindowIdentity::of).filter(Objects::nonNull)
                .findFirst().map(NamedWindow::of);
    }

    /**
     * Points capture at the device a launch target runs on, when it is an emulator or phone app
     * ({@code emu-app:<package>@<instance>}): there the target says where its pixels are. Any other target leaves
     * capture alone: an automatic capture picks a desktop game's window by watching its launch on the first run
     * ({@link AutoCapture}, 2026-11-25), and a source set by hand stays. A source set by hand is pointed at an
     * emulator app's device. Call it on the JavaFX application thread.
     *
     * @return the device's reference when capture now follows it, else empty
     */
    public static Optional<String> follow(StudioServices services, String target) {
        LaunchSpec spec = target == null ? null : LaunchSpec.parse(target);
        if (spec == null || spec.kind() != LaunchKind.EMULATOR_APP || spec.emulatorInstance() == null) {
            return Optional.empty();
        }
        String instance = spec.emulatorInstance();
        // Already that device, maybe narrowed to a region of it: kept as written; automatic (desktop() with a target,
        // a region of either) follows it by itself.
        CaptureSource now = written(services);
        if (AutoCapture.followsTarget(now, spec)) return Optional.of(instance);
        if (now != null && now.base() instanceof EmulatorSource already && already.instanceName().equals(instance)) {
            return Optional.of(instance);
        }
        point(services, new EmulatorSource(instance));
        return Optional.of(instance);
    }

    /** The emulator instance capture follows, by name; empty when it follows none. */
    public static Optional<String> followed(StudioServices services) {
        CaptureSource written = written(services);
        // Automatic follows the target's instance: the one current() reads, before instance() moves it.
        CaptureSource now = written instanceof AmbientSource
                ? resolved(services, LaunchTargets.spec(services), written, null) : written;
        return now != null && now.base() instanceof EmulatorSource device ? Optional.ofNullable(device.instanceName())
                : Optional.empty();
    }

    /**
     * Points capture at an instance's new name when it follows one renamed ({@code moves}: old name → new), keeping
     * the region it is narrowed to. Call it on the JavaFX application thread.
     *
     * @return empty when written or nothing changed, else why nothing was
     */
    public static Optional<String> renamed(StudioServices services, Map<String, String> moves) {
        CaptureSource now = written(services);
        // Automatic names no instance of its own (its base() would be this process's source): nothing to rename.
        if (now == null || now instanceof AmbientSource || !(now.base() instanceof EmulatorSource device)) {
            return Optional.empty();
        }
        String renamed = moves.get(device.instanceName());
        if (renamed == null) return Optional.empty();
        CaptureSource base = new EmulatorSource(renamed);
        Rect region = now.subRegion();
        return write(services, region == null ? base : CaptureSource.region(base, region));
    }

    /** The instance the bot's {@code place()} names, when it is an Android place naming one. */
    public static Optional<String> instanceNamed(StudioServices services) {
        return read(services, SdkValues.PLACE, Place.class).filter(Place::namesAndroid).map(Place::name);
    }

    /**
     * Points the bot's {@code place()} at an instance's new name when it names one renamed ({@code moves}: old
     * name → new). Call it on the JavaFX application thread.
     *
     * @return empty when written or nothing changed, else why nothing was
     */
    public static Optional<String> instanceRenamed(StudioServices services, Map<String, String> moves) {
        String renamed = instanceNamed(services).map(moves::get).orElse(null);
        if (renamed == null) return Optional.empty();
        return ParamSlot.open(services, SdkValues.PLACE).map(ctx -> ctx.write(Place.android(renamed)))
                .orElse(Optional.empty());
    }

    /**
     * Points {@code Sdk.captureSource} at {@code source}, asking the host to write the parameter first when the
     * project lacks it; does nothing when there is still no such parameter to write to. Call it on the
     * JavaFX application thread.
     *
     * @param source the project's capture source, or null for the whole desktop
     */
    public static void point(StudioServices services, CaptureSource source) {
        point(services, source, null);
    }

    /**
     * As {@link #point(StudioServices, CaptureSource)}, narrowed to {@code region} — a rectangle in the
     * <em>source's own</em> pixel space, so the narrowing survives the window moving. {@code null} is the
     * whole source, which is what almost every pick means.
     */
    public static void point(StudioServices services, CaptureSource source, java.awt.Rectangle region) {
        CaptureSource base = source == null ? CaptureSource.desktop() : source;
        CaptureSource value = region != null && region.width > 0 && region.height > 0
                ? CaptureSource.region(base, new Rect(region.x, region.y, region.width, region.height))
                : base;
        // A pick is the user asking for a source to be kept, so a project with no Sdk.java gets one first.
        write(services, value).ifPresent(refused -> {
            if (services != null) services.status(refused);
        });
    }
}
