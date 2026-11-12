package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.plugin.SdkLog;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.launch.LaunchSpec;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
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
     * thread of its own: finding the instances reads this PC's emulators, and the write compiles.
     */
    public static void remember(StudioServices services) {
        Path project = services.projectDir();
        if (project == null || project.getFileName() == null) return;
        String bot = project.getFileName().toString();
        List<String> specs = LaunchTargetValue.all(services);
        Thread thread = new Thread(() -> {
            try {
                List<EmulatorInstance> known = Platforms.recent(Duration.ofMinutes(1));
                Optional<String> refused = HostConfig.seen(services, bot, targets(specs, known), keys(known));
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
     * The instances of {@code known} that the launch specs name; a target naming none of them is left out. A target
     * names an instance by its name alone, so two products' instances of one name are both the bot's.
     */
    static Set<InstanceKey> targets(List<String> specs, List<EmulatorInstance> known) {
        Set<InstanceKey> out = new LinkedHashSet<>();
        for (String spec : specs) {
            LaunchSpec parsed = LaunchSpec.parse(spec);
            String reference = parsed == null ? null : parsed.emulatorInstance();
            if (reference == null || reference.isBlank()) continue;
            for (EmulatorInstance instance : known) {
                if (reference.equals(instance.reference())) key(instance).ifPresent(out::add);
            }
        }
        return out;
    }
}
