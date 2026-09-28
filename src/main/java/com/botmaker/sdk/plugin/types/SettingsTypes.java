package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.sdk.api.bot.BotSettings;

import java.util.List;

/**
 * The calls a {@code @Managed("settings")} value is written as (2026-09-27):
 * {@code BotSettings.of(BotSettings.clicks(…), BotSettings.vision(…), BotSettings.input(…),
 * BotSettings.session(…), retries, debug)}.
 *
 * <p>Five declarations rather than one for {@link FlowTypes}' reason: the host takes the expression apart into
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

    /** {@code BotSettings.of(Clicks, Vision, Input, Session, int, boolean)}; {@code BotSettings.DEFAULTS} as itself. */
    public static final DeclaredCall<BotSettings> SETTINGS = ComponentType.part(BotSettings.class)
            .writtenAs(BotSettings::of, BotSettings::clicks, BotSettings::vision, BotSettings::input,
                    BotSettings::session, BotSettings::maxRetryAttempts, BotSettings::debug)
            .constants(BotSettings.DEFAULTS);

    /** {@code BotSettings.clicks(int, int, boolean)}. */
    public static final DeclaredCall<BotSettings.Clicks> CLICKS = ComponentType.part(BotSettings.Clicks.class)
            .writtenAs(BotSettings::clicks, BotSettings.Clicks::foundDelay, BotSettings.Clicks::notFoundDelay,
                    BotSettings.Clicks::randomize);

    /** {@code BotSettings.vision(double, double)}. */
    public static final DeclaredCall<BotSettings.Vision> VISION = ComponentType.part(BotSettings.Vision.class)
            .writtenAs(BotSettings::vision, BotSettings.Vision::confidence, BotSettings.Vision::compareMargin);

    /** {@code BotSettings.input(boolean, InputBackend)}. */
    public static final DeclaredCall<BotSettings.Input> INPUT = ComponentType.part(BotSettings.Input.class)
            .writtenAs(BotSettings::input, BotSettings.Input::real, BotSettings.Input::linuxBackend);

    /** {@code BotSettings.session(boolean, DisplayBackend)}. */
    public static final DeclaredCall<BotSettings.Session> SESSION = ComponentType.part(BotSettings.Session.class)
            .writtenAs(BotSettings::session, BotSettings.Session::isolated, BotSettings.Session::backend);

    /** Every call a settings value is written as. */
    public static final List<ComponentType<?>> ALL = List.of(SETTINGS, CLICKS, VISION, INPUT, SESSION);
}
