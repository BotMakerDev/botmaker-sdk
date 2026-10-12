package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.RunProperty;
import com.botmaker.sdk.internal.session.SessionBootstrap;

/**
 * Which game VM this computer runs the open bot's game in: the {@code botmaker.session.vm} run property. A fact
 * about this computer, like {@link LaunchTargetValue}: the VM is set up here, and its name means nothing on
 * another one. The bot's Java says only that the game runs in a VM ({@code BotSettings.Where.VM}).
 */
public final class VmChoice {

    /** The run property, its text the VM's name. */
    static final RunProperty<String> PROPERTY = RunProperty.of(SessionBootstrap.VM_PROPERTY, String::trim, name -> name);

    private VmChoice() {}

    /** The VM's name, or {@code null} when this computer names none for the bot. */
    public static String current(StudioServices services) {
        return services == null ? null : services.runs().get(PROPERTY).orElse(null);
    }

    /** Makes {@code name} the bot's VM on this computer; {@code null} clears it. */
    public static void set(StudioServices services, String name) {
        if (services != null) services.runs().set(PROPERTY, name);
    }
}
