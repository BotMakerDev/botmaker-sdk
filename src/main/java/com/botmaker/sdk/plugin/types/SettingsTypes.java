package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.sdk.api.bot.BotSettings;

import java.util.List;

/**
 * The calls a {@code @Managed("settings")} value is written as:
 * {@code BotSettings.of(BotSettings.clicks(…), BotSettings.vision(…), BotSettings.runIn(…), retries, debug)}.
 *
 * <p>Four declarations rather than one for {@link FlowTypes}' reason: the host takes the expression apart into
 * typed parts, the ⚙ Bot Settings window changes one, and the rest goes back exactly as written. The parts are
 * grouped rather than eleven positional arguments so the Java reads as what it says — {@code clicks(500, 200,
 * true)} next to its neighbours, not the seventh number of a row. None is a {@code PluginType}: a setting is
 * never picked on its own, and the whole value has one window.
 *
 * <p>A call with the wrong number of arguments, or an argument of the wrong kind, is not this shape and builds
 * nothing: the host shows it read-only, exactly as written.
 */
public final class SettingsTypes {

    private SettingsTypes() {}

    /**
     * {@code BotSettings.of(Clicks, Vision, RunIn, int, boolean)}; {@code BotSettings.DEFAULTS} as itself.
     * The deprecated {@code debug} is still a part: the bot's Java writes it, and a value read must be written back.
     */
    @SuppressWarnings("deprecation")
    public static final DeclaredCall<BotSettings> SETTINGS = ComponentType.part(BotSettings.class)
            .writtenAs(BotSettings::of, BotSettings::clicks, BotSettings::vision, BotSettings::runIn,
                    BotSettings::maxRetryAttempts, BotSettings::debug)
            .constants(BotSettings.DEFAULTS);

    /** {@code BotSettings.clicks(int, int, boolean)}. */
    public static final DeclaredCall<BotSettings.Clicks> CLICKS = ComponentType.part(BotSettings.Clicks.class)
            .writtenAs(BotSettings::clicks, BotSettings.Clicks::foundDelay, BotSettings.Clicks::notFoundDelay,
                    BotSettings.Clicks::randomize);

    /** {@code BotSettings.vision(double, double)}. */
    public static final DeclaredCall<BotSettings.Vision> VISION = ComponentType.part(BotSettings.Vision.class)
            .writtenAs(BotSettings::vision, BotSettings.Vision::confidence, BotSettings.Vision::compareMargin);

    /** {@code BotSettings.runIn(Where, boolean, DisplayBackend, InputBackend)}. */
    public static final DeclaredCall<BotSettings.RunIn> RUN_IN = ComponentType.part(BotSettings.RunIn.class)
            .writtenAs(BotSettings::runIn, BotSettings.RunIn::where, BotSettings.RunIn::takeOver,
                    BotSettings.RunIn::displayBackend, BotSettings.RunIn::inputBackend);

    /** Every call a settings value is written as. */
    public static final List<ComponentType<?>> ALL = List.of(SETTINGS, CLICKS, VISION, RUN_IN);
}
