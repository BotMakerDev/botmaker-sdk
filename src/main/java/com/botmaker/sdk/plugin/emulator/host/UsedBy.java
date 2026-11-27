// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.plugin.SdkLog;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.application.Platform;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which bots launch which instance, remembered each time a bot is opened (the user's call, 2026-11-12: a plugin
 * reads only the open bot, so the Emulators window's <i>Used by</i> lists the bots seen so far). A bot not opened since
 * its targets changed shows the ones it had then.
 */
public final class UsedBy {

    private UsedBy() {}

    /**
     * Writes the open bot beside the instances its {@code launchTargets} name, and takes it off the others, on a
     * thread of its own: finding the instances reads this PC's emulators, and the write compiles. A target naming an
     * instance BotMaker renamed is pointed at the new name first ({@link InstanceRenames}), back on the UI thread.
     */
    public static void remember(StudioServices services) {
        Path project = services.projectDir();
        if (project == null || project.getFileName() == null) return;
        String bot = project.getFileName().toString();
        List<String> specs = LaunchTargetValue.all(services);
        String placed = CaptureValue.instanceNamed(services).orElse(null);
        Thread thread = new Thread(() -> {
            try {
                HostLabels renames = HostConfig.renames(services);
                // a rename a minute old may still be the old name in the last read: read again when one is recorded
                List<EmulatorInstance> known = renames.list().isEmpty() ? Platforms.recent(Duration.ofMinutes(1))
                        : Platforms.discoverAll();
                Map<String, String> moves = InstanceRenames.moves(renames, InstanceRenames.names(known));
                String place = placed == null ? null : moves.getOrDefault(placed, placed);
                // with no place yet, a renamed target keeps its instance under the new name: still that instance
                List<String> launched = InstanceRenames.respelled(specs, moves, place);
                if (!moves.isEmpty()) {
                    Platform.runLater(() -> InstanceRenames.handOff(services, moves).ifPresent(services::status));
                }
                Optional<String> refused = HostConfig.seen(services, bot, targets(launched, place, known), keys(known));
                refused.ifPresent(why -> SdkLog.warn("The bot was not noted beside its emulator instances: " + why));
            } catch (RuntimeException failed) {
                SdkLog.warn("The bot was not noted beside its emulator instances: " + failed.getMessage());
            }
        }, "emulators-used-by");
        thread.setDaemon(true);
        thread.start();
    }

    /** The instances found on this PC, as keys; one of no known product has none. */
    private static Set<InstanceKey> keys(List<EmulatorInstance> known) {
        Set<InstanceKey> out = new LinkedHashSet<>();
        for (EmulatorInstance instance : known) key(instance).ifPresent(out::add);
        return out;
    }

    private static Optional<InstanceKey> key(EmulatorInstance instance) {
        try {
            return Optional.of(InstanceKey.of(instance));
        } catch (IllegalArgumentException unnamed) {
            return Optional.empty();
        }
    }

    /**
     * The instances of {@code known} that the launch specs play on: the one an emulator app names, else, as targets
     * are written since 2026-11-27, the one the bot's {@code place()} names ({@code placed}, or {@code null}); a
     * target playing on none of them is left out. An instance is named by its name alone, so two products' instances
     * of one name are both the bot's.
     */
    static Set<InstanceKey> targets(List<String> specs, String placed, List<EmulatorInstance> known) {
        Set<InstanceKey> out = new LinkedHashSet<>();
        for (String spec : specs) {
            LaunchSpec parsed = LaunchSpec.parse(spec);
            if (parsed == null || parsed.kind() != LaunchKind.EMULATOR_APP) continue;
            String reference = parsed.emulatorInstance() != null ? parsed.emulatorInstance() : placed;
            if (reference == null || reference.isBlank()) continue;
            for (EmulatorInstance instance : known) {
                if (reference.equals(instance.reference())) key(instance).ifPresent(out::add);
            }
        }
        return out;
    }
}
