package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.bot.BotSettings;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
 * <p>A call with the wrong number of arguments is not this shape and builds nothing: the host shows it
 * read-only, exactly as written.
 */
public final class SettingsTypes {

    private SettingsTypes() {}

    /** {@code BotSettings.of(Clicks, Vision, Input, Session, int, boolean)}. */
    public static final ComponentType<BotSettings> SETTINGS = new Fixed<>(BotSettings.class,
            SdkTypes.method(BotSettings.class, "of", BotSettings.Clicks.class, BotSettings.Vision.class,
                    BotSettings.Input.class, BotSettings.Session.class, int.class, boolean.class)) {
        @Override
        public List<Object> components(BotSettings s) {
            BotSettings v = s == null ? BotSettings.DEFAULTS : s;
            return List.of(v.clicks(), v.vision(), v.input(), v.session(), v.maxRetryAttempts(), v.debug());
        }

        @Override
        public BotSettings build(List<Object> parts) {
            if (parts.size() != 6) return null;
            return BotSettings.of(
                    parts.get(0) instanceof BotSettings.Clicks c ? c : null,
                    parts.get(1) instanceof BotSettings.Vision v ? v : null,
                    parts.get(2) instanceof BotSettings.Input i ? i : null,
                    parts.get(3) instanceof BotSettings.Session s ? s : null,
                    whole(parts.get(4), BotSettings.DEFAULT_MAX_RETRY_ATTEMPTS),
                    flag(parts.get(5), true));
        }

        /** {@code BotSettings.DEFAULTS}: a bot on the defaults says so, rather than spelling them out. */
        @Override
        public List<Field> constants() {
            try {
                return List.of(BotSettings.class.getField("DEFAULTS"));
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("BotSettings.DEFAULTS is gone", e);
            }
        }
    };

    /** {@code BotSettings.clicks(int, int, boolean)}. */
    public static final ComponentType<BotSettings.Clicks> CLICKS = new Fixed<>(BotSettings.Clicks.class,
            SdkTypes.method(BotSettings.class, "clicks", int.class, int.class, boolean.class)) {
        @Override
        public List<Object> components(BotSettings.Clicks c) {
            BotSettings.Clicks v = c == null ? BotSettings.DEFAULTS.clicks() : c;
            return List.of(v.foundDelay(), v.notFoundDelay(), v.randomize());
        }

        @Override
        public BotSettings.Clicks build(List<Object> parts) {
            return parts.size() != 3 ? null : BotSettings.clicks(
                    whole(parts.get(0), BotSettings.DEFAULT_FOUND_DELAY),
                    whole(parts.get(1), BotSettings.DEFAULT_NOT_FOUND_DELAY),
                    flag(parts.get(2), BotSettings.DEFAULT_RANDOMIZE_CLICKS));
        }
    };

    /** {@code BotSettings.vision(double, double)}. */
    public static final ComponentType<BotSettings.Vision> VISION = new Fixed<>(BotSettings.Vision.class,
            SdkTypes.method(BotSettings.class, "vision", double.class, double.class)) {
        @Override
        public List<Object> components(BotSettings.Vision v) {
            BotSettings.Vision x = v == null ? BotSettings.DEFAULTS.vision() : v;
            return List.of(x.confidence(), x.compareMargin());
        }

        @Override
        public BotSettings.Vision build(List<Object> parts) {
            return parts.size() != 2 ? null : BotSettings.vision(
                    real(parts.get(0), BotSettings.DEFAULT_CONFIDENCE),
                    real(parts.get(1), BotSettings.DEFAULT_COMPARE_MARGIN));
        }
    };

    /** {@code BotSettings.input(boolean, InputBackend)}. */
    public static final ComponentType<BotSettings.Input> INPUT = new Fixed<>(BotSettings.Input.class,
            SdkTypes.method(BotSettings.class, "input", boolean.class, BotSettings.InputBackend.class)) {
        @Override
        public List<Object> components(BotSettings.Input i) {
            BotSettings.Input v = i == null ? BotSettings.DEFAULTS.input() : i;
            return List.of(v.real(), v.linuxBackend());
        }

        @Override
        public BotSettings.Input build(List<Object> parts) {
            return parts.size() != 2 ? null : BotSettings.input(flag(parts.get(0), false),
                    parts.get(1) instanceof BotSettings.InputBackend b ? b : BotSettings.InputBackend.AUTO);
        }
    };

    /** {@code BotSettings.session(boolean, DisplayBackend)}. */
    public static final ComponentType<BotSettings.Session> SESSION = new Fixed<>(BotSettings.Session.class,
            SdkTypes.method(BotSettings.class, "session", boolean.class, BotSettings.DisplayBackend.class)) {
        @Override
        public List<Object> components(BotSettings.Session s) {
            BotSettings.Session v = s == null ? BotSettings.DEFAULTS.session() : s;
            return List.of(v.isolated(), v.backend());
        }

        @Override
        public BotSettings.Session build(List<Object> parts) {
            return parts.size() != 2 ? null : BotSettings.session(flag(parts.get(0), true),
                    parts.get(1) instanceof BotSettings.DisplayBackend b ? b : BotSettings.DisplayBackend.AUTO);
        }
    };

    /** Every call a settings value is written as. */
    public static final List<ComponentType<?>> ALL = List.of(SETTINGS, CLICKS, VISION, INPUT, SESSION);

    private abstract static class Fixed<C> implements ComponentType<C> {

        private final Class<C> type;
        private final Method factory;

        Fixed(Class<C> type, Method factory) {
            this.type = type;
            this.factory = factory;
        }

        @Override
        public final Class<C> type() {
            return type;
        }

        @Override
        public final Executable factory() {
            return factory;
        }

        @Override
        public final List<Class<?>> componentTypes() {
            return SdkTypes.parts(factory);
        }
    }

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
