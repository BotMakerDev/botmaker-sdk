// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;

/**
 * Whose records a run keeps ({@link DoneRecords}, 2026-11-15, the user's call): the emulator instance it plays on and
 * the game it launches, so two runs on two instances, or two games on one, never read each other's dailies. One
 * reading, the run's and 🎯 Goals', so both find the same rows.
 */
public final class DoneKey {

    private DoneKey() {}

    /** The instance {@code capture} reads, by name; {@code ""} when it reads no emulator. */
    public static String instance(CaptureSource capture) {
        if (capture == null || !(capture.base() instanceof EmulatorSource emulator)) return "";
        return emulator.instanceName() == null ? "" : emulator.instanceName().strip();
    }

    /**
     * The game {@code spec} launches: an emulator app's package, whichever instance and page it names, else the spec as one
     * launcher is written ({@link InstalledGame#canonical}); {@code ""} for none.
     */
    public static String game(String spec) {
        if (spec == null || spec.isBlank()) return "";
        LaunchSpec parsed = LaunchSpec.parse(spec);
        if (parsed != null && parsed.kind() == LaunchKind.EMULATOR_APP && parsed.emulatorPackage() != null) {
            return LaunchKind.EMULATOR_APP.id() + ":" + parsed.emulatorPackage();
        }
        return InstalledGame.canonical(spec);
    }

    /** The running bot's instance: where it reads now. */
    static String runInstance() {
        return instance(Source.current());
    }

    /** The running bot's game: what it launches. */
    static String runGame() {
        return game(InstalledGame.launchTarget());
    }
}
