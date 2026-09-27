package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.toolkit.Types;
import com.botmaker.sdk.api.bot.BotSettings;

import java.util.List;

import static com.botmaker.plugin.toolkit.Types.method;

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
 * <p>A call with the wrong number of arguments is not this shape and builds nothing: the host shows it
 * read-only, exactly as written. A part of the wrong kind reads as that setting's default, not as zero.
 */
public final class SettingsTypes {

    private SettingsTypes() {}

    /** {@code BotSettings.of(Clicks, Vision, Input, Session, int, boolean)}; {@code BotSettings.DEFAULTS} as itself. */
    public static final ComponentType<BotSettings> SETTINGS = Types.call(BotSettings.class,
                    method(BotSettings.class, "of", BotSettings.Clicks.class, BotSettings.Vision.class,
                            BotSettings.Input.class, BotSettings.Session.class, int.class, boolean.class),
                    s -> {
                        BotSettings v = s == null ? BotSettings.DEFAULTS : s;
                        return List.of(v.clicks(), v.vision(), v.input(), v.session(), v.maxRetryAttempts(),
                                v.debug());
                    },
                    parts -> parts.size() != 6 ? null : BotSettings.of(
                            Types.part(parts, 0, BotSettings.Clicks.class),
                            Types.part(parts, 1, BotSettings.Vision.class),
                            Types.part(parts, 2, BotSettings.Input.class),
                            Types.part(parts, 3, BotSettings.Session.class),
                            whole(parts.get(4), BotSettings.DEFAULT_MAX_RETRY_ATTEMPTS),
                            flag(parts.get(5), true)))
            .constants(Types.constant(BotSettings.class, "DEFAULTS"));

    /** {@code BotSettings.clicks(int, int, boolean)}. */
    public static final ComponentType<BotSettings.Clicks> CLICKS = Types.call(BotSettings.Clicks.class,
            method(BotSettings.class, "clicks", int.class, int.class, boolean.class),
            c -> {
                BotSettings.Clicks v = c == null ? BotSettings.DEFAULTS.clicks() : c;
                return List.of(v.foundDelay(), v.notFoundDelay(), v.randomize());
            },
            parts -> parts.size() != 3 ? null : BotSettings.clicks(
                    whole(parts.get(0), BotSettings.DEFAULT_FOUND_DELAY),
                    whole(parts.get(1), BotSettings.DEFAULT_NOT_FOUND_DELAY),
                    flag(parts.get(2), BotSettings.DEFAULT_RANDOMIZE_CLICKS)));

    /** {@code BotSettings.vision(double, double)}. */
    public static final ComponentType<BotSettings.Vision> VISION = Types.call(BotSettings.Vision.class,
            method(BotSettings.class, "vision", double.class, double.class),
            v -> {
                BotSettings.Vision x = v == null ? BotSettings.DEFAULTS.vision() : v;
                return List.of(x.confidence(), x.compareMargin());
            },
            parts -> parts.size() != 2 ? null : BotSettings.vision(
                    real(parts.get(0), BotSettings.DEFAULT_CONFIDENCE),
                    real(parts.get(1), BotSettings.DEFAULT_COMPARE_MARGIN)));

    /** {@code BotSettings.input(boolean, InputBackend)}. */
    public static final ComponentType<BotSettings.Input> INPUT = Types.call(BotSettings.Input.class,
            method(BotSettings.class, "input", boolean.class, BotSettings.InputBackend.class),
            i -> {
                BotSettings.Input v = i == null ? BotSettings.DEFAULTS.input() : i;
                return List.of(v.real(), v.linuxBackend());
            },
            parts -> parts.size() != 2 ? null : BotSettings.input(flag(parts.get(0), false),
                    parts.get(1) instanceof BotSettings.InputBackend b ? b : BotSettings.InputBackend.AUTO));

    /** {@code BotSettings.session(boolean, DisplayBackend)}. */
    public static final ComponentType<BotSettings.Session> SESSION = Types.call(BotSettings.Session.class,
            method(BotSettings.class, "session", boolean.class, BotSettings.DisplayBackend.class),
            s -> {
                BotSettings.Session v = s == null ? BotSettings.DEFAULTS.session() : s;
                return List.of(v.isolated(), v.backend());
            },
            parts -> parts.size() != 2 ? null : BotSettings.session(flag(parts.get(0), true),
                    parts.get(1) instanceof BotSettings.DisplayBackend b ? b : BotSettings.DisplayBackend.AUTO));

    /** Every call a settings value is written as. */
    public static final List<ComponentType<?>> ALL = List.of(SETTINGS, CLICKS, VISION, INPUT, SESSION);

    private static int whole(Object part, int fallback) {
        return part instanceof Number n ? n.intValue() : fallback;
    }

    private static double real(Object part, double fallback) {
        return part instanceof Number n ? n.doubleValue() : fallback;
    }

    private static boolean flag(Object part, boolean fallback) {
        return part instanceof Boolean b ? b : fallback;
    }
}
