package com.botmaker.sdk.internal.bot;

import com.botmaker.plugin.api.source.ManagedParam;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.SdkValue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The settings parameters as one {@link BotSettings}: each of the Settings group's methods ({@code foundDelay()} to
 * {@code inputBackend()}, 2026-11-09) is one field, and a field the bot marks none of, or marks with a value of
 * another type, keeps {@link BotSettings#DEFAULTS}'. The one reading a run ({@code InstalledGame}) and the plugin's
 * windows share, each handing how it reads one parameter.
 */
public final class SettingParams {

    /** One parameter: how it is read off a {@link BotSettings}, and the copy it makes with another value. */
    public record Field<T>(ManagedParam<T> param, Function<BotSettings, T> read,
                           BiFunction<BotSettings, T, BotSettings> apply) {

        /** The mark a bot's method carries: the constant the parameter's id ends with. */
        public SdkValue.Id id() {
            return SdkValue.Id.valueOf(param.id().substring(param.id().lastIndexOf('.') + 1));
        }

        /** {@code settings} with this field set to {@code value}, unchanged when it is not of the parameter's type. */
        BotSettings applied(BotSettings settings, Object value) {
            return param.type().isInstance(value) ? apply.apply(settings, param.type().cast(value)) : settings;
        }
    }

    /** Every settings parameter, in the group's order. */
    public static final List<Field<?>> ALL = List.of(
            new Field<>(SdkValues.FOUND_DELAY, BotSettings::foundDelay, BotSettings::foundDelay),
            new Field<>(SdkValues.NOT_FOUND_DELAY, BotSettings::notFoundDelay, BotSettings::notFoundDelay),
            new Field<>(SdkValues.RANDOM_CLICKS, BotSettings::randomizeClicks, BotSettings::randomizeClicks),
            new Field<>(SdkValues.CONFIDENCE, BotSettings::confidence, BotSettings::confidence),
            new Field<>(SdkValues.COMPARE_MARGIN, BotSettings::compareMargin, BotSettings::compareMargin),
            new Field<>(SdkValues.MAX_ROUNDS, BotSettings::maxRounds, BotSettings::maxRounds),
            new Field<>(SdkValues.PATIENCE, BotSettings::patience, BotSettings::patience),
            new Field<>(SdkValues.FROZEN_AFTER, BotSettings::frozenAfter, BotSettings::frozenAfter),
            new Field<Duration>(SdkValues.STUCK_AFTER, BotSettings::stuckAfter, BotSettings::stuckAfter),
            new Field<>(SdkValues.RESTART_CAP, BotSettings::restartCap, BotSettings::restartCap),
            new Field<Duration>(SdkValues.RESTARTS_WITHIN, BotSettings::restartsWithin, BotSettings::restartsWithin),
            new Field<>(SdkValues.GAME_RUNS_IN, BotSettings::where, BotSettings::where),
            new Field<>(SdkValues.TAKE_OVER, BotSettings::takeOver, BotSettings::takeOver),
            new Field<>(SdkValues.DISPLAY_BACKEND, s -> s.runIn().displayBackend(), BotSettings::displayBackend),
            new Field<>(SdkValues.INPUT_BACKEND, s -> s.runIn().inputBackend(), BotSettings::inputBackend));

    private SettingParams() {}

    /**
     * The settings {@code read} gives, from {@link BotSettings#DEFAULTS}: {@code read} answers one parameter's value,
     * empty when the bot has none.
     */
    public static BotSettings from(Function<Field<?>, Optional<?>> read) {
        BotSettings settings = BotSettings.DEFAULTS;
        for (Field<?> field : ALL) {
            Optional<?> value = read.apply(field);
            if (value.isPresent()) settings = field.applied(settings, value.get());
        }
        return settings;
    }
}
