package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.internal.config.ProjectDefaults;
import com.botmaker.shared.launch.LaunchSpec;

/**
 * What this machine launches for the open bot — the {@code botmaker.launch.target} run property.
 *
 * <p>A fact about this computer rather than the bot: a Steam app id or an emulator instance means nothing on
 * someone else's machine, so it is never in the bot's files. The host keeps it beside the project, out of git
 * and out of a published archive, and starts every run with it as {@code -Dbotmaker.launch.target=…}, which
 * the bot's {@code Target} reads. It used to be a {@code launch.target} key in the bot's
 * {@code botmaker-project.properties}, and so went wherever the project went.
 *
 * <p>Beside {@link BotSettingsWindow} rather than in {@code launch}: the emulator picker writes it and the
 * launch button reads it, and neither package may name the other.
 */
public final class LaunchTargetValue {

    private LaunchTargetValue() {}

    /** The raw spec, or {@code null} when this machine has none for the bot. */
    public static String current(StudioServices services) {
        if (services == null) return null;
        String spec = services.runs().property(ProjectDefaults.LAUNCH_TARGET);
        return spec == null || spec.isBlank() ? null : spec.trim();
    }

    /** The parsed spec, or {@code null} when there is none or it does not parse. */
    public static LaunchSpec spec(StudioServices services) {
        String spec = current(services);
        return spec == null ? null : LaunchSpec.parse(spec);
    }

    /** Makes {@code spec} what this machine launches for the bot; {@code null} clears it. */
    public static void set(StudioServices services, String spec) {
        if (services != null) services.runs().setProperty(ProjectDefaults.LAUNCH_TARGET, spec);
    }
}
