// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.params;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.RunProperty;
import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;

import java.util.List;
import java.util.Optional;

/**
 * What the bot launches on this computer, read from its parameters: the half of {@code settings.LaunchTargetValue}
 * that only reads, here so the capture source ({@code screen.CaptureValue}), which the launch target's writes name,
 * reads it too (2026-11-25).
 */
public final class LaunchTargets {

    /** What a computer launched before the bot's Java said it: passed to every run as -D, so it still wins. */
    private static final RunProperty<String> OLD_RUN_PROPERTY = RunProperty.text(InstalledGame.LAUNCH_TARGET);

    private LaunchTargets() {}

    /** The bot's launchers, preferred first; empty when it lists none or the parameter does not read. */
    public static List<String> all(StudioServices services) {
        return ParamSlot.open(services, SdkValues.LAUNCH_TARGETS).flatMap(ctx -> ctx.value(List.class))
                .map(InstalledGame::specs).orElse(List.of());
    }

    /** The launcher the person running the bot picked, or {@code ""} for none: the first this computer can launch. */
    public static String chosen(StudioServices services) {
        if (services == null) return "";
        return ParamSlot.open(services, SdkValues.LAUNCH_TARGET).flatMap(ctx -> ctx.value(LaunchChoice.class))
                .map(LaunchChoice::spec).orElse("");
    }

    /**
     * The spec a run on this computer launches, or {@code null} for none: the run property a computer kept before,
     * while it is there, else the runner's pick when the bot lists it and this system can start it, else the first of
     * the bot's launchers this system can start.
     */
    public static String current(StudioServices services) {
        if (services == null) return null;
        Optional<String> old = services.runs().get(OLD_RUN_PROPERTY);
        if (old.isPresent()) return old.get();
        return InstalledGame.pick(all(services), chosen(services), Os.current());
    }

    /**
     * {@link #current} as a run plays it: an emulator app on the instance the bot's {@code place()} names
     * ({@link InstalledGame#launchedOn}), which wins over an {@code @<instance>} an older bot wrote. What a check, a
     * launch from Studio or a view of the device reads; {@link #current} is the spec as the bot holds it.
     */
    public static String played(StudioServices services) {
        return InstalledGame.launchedOn(current(services), placeInstance(services).orElse(null));
    }

    /** {@link #played}, parsed, or {@code null} when there is none or it does not parse. */
    public static LaunchSpec spec(StudioServices services) {
        String spec = played(services);
        return spec == null ? null : LaunchSpec.parse(spec);
    }

    /** The instance the bot's {@code place()} names, or empty when it names none or no Android place. */
    public static Optional<String> placeInstance(StudioServices services) {
        return ParamSlot.open(services, SdkValues.PLACE).flatMap(ctx -> {
            try {
                return ctx.value(Place.class);
            } catch (RuntimeException unreadable) {
                return Optional.empty();
            }
        }).filter(Place::namesAndroid).map(Place::name);
    }

    /** The run property {@link #current} reads first, which a launch target's first write clears. */
    public static RunProperty<String> oldRunProperty() {
        return OLD_RUN_PROPERTY;
    }
}
