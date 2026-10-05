package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.bot.BotSettings;

/**
 * What a running bot was told about itself from outside its own code: the launch target this machine gave it,
 * and the session half of its {@link BotSettings}.
 *
 * <p>The session answers are the bot's {@code @Managed("settings")} value, which
 * {@code Bot.run} installs before anything reads them; the launch target is a fact about <em>this machine</em>
 * rather than the bot, so it is never in the bot's files at all — Studio starts the bot with
 * {@code -D}{@value #LAUNCH_TARGET}, and a bot run by hand passes its own.
 */
public final class ProjectDefaults {

    /** The system property carrying what this machine launches, in the {@code launch.target} spec grammar. */
    public static final String LAUNCH_TARGET = "botmaker.launch.target";

    private ProjectDefaults() {}

    /**
     * The raw launch-target spec, or {@code null} when none was given — {@code internal.launch.Target} parses it via
     * {@code internal.launch.LaunchTarget}. Kept as a raw string so this reader stays free of the launch facade.
     */
    public static String launchTarget() {
        String spec = System.getProperty(LAUNCH_TARGET);
        return spec == null || spec.isBlank() ? null : spec.trim();
    }

    /** Whether the bot's settings ask for a private display — {@code true} unless they say otherwise. */
    public static boolean sessionIsolated() {
        return BotSettings.current().session().isolated();
    }

    /** The pinned display backend's id, or {@code null} when the settings let the launch kind pick. */
    public static String sessionBackend() {
        BotSettings.DisplayBackend backend = BotSettings.current().session().backend();
        return backend == BotSettings.DisplayBackend.AUTO ? null : backend.id();
    }

    /**
     * The lenient boolean every BotMaker on/off switch accepts: {@code true}/{@code 1}/{@code yes}/{@code on}
     * and {@code false}/{@code 0}/{@code no}/{@code off}; {@code null} for blank or anything else, meaning
     * "unset", so the caller keeps its own default. For the switches that arrive as a system property or an
     * environment variable.
     */
    public static Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase()) {
            case "true", "1", "yes", "on" -> Boolean.TRUE;
            case "false", "0", "no", "off" -> Boolean.FALSE;
            default -> null;
        };
    }
}
