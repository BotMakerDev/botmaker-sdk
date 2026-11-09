package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.bot.SettingParams;
import com.botmaker.sdk.plugin.params.ParamSlot;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The open project's settings, read off its Settings parameters ({@code params/…/Settings.java}, one method each,
 * 2026-11-09) and written back one parameter at a time. What the windows, the launcher and the assistant's tools
 * read in place of the {@code Sdk.settings()} method that held them all.
 */
public final class SettingsValue {

    private SettingsValue() {}

    /** The settings the project's parameters give, {@link BotSettings#DEFAULTS} for each it lacks or none open. */
    public static BotSettings current(StudioServices services) {
        if (services == null) return BotSettings.DEFAULTS;
        return SettingParams.from(field -> read(services, field));
    }

    /** The confidence a run holds a match to: the project's {@code confidence()} parameter, else the default. */
    public static double confidence(StudioServices services) {
        // Only the one parameter: probes and frames ask several times a second.
        return SettingParams.ALL.stream().filter(field -> field.param() == SdkValues.CONFIDENCE).findFirst()
                .flatMap(field -> read(services, field)).filter(Double.class::isInstance).map(Double.class::cast)
                .orElse(BotSettings.DEFAULTS.confidence());
    }

    /**
     * Writes each parameter {@code next} sets otherwise than {@code was}, writing the group's method first when the
     * project lacks it.
     *
     * @return why one was not written, empty when all were
     */
    public static Optional<String> write(StudioServices services, BotSettings was, BotSettings next) {
        for (SettingParams.Field<?> field : SettingParams.ALL) {
            Optional<String> refused = changed(services, field, was, next);
            if (refused.isPresent()) return refused;
        }
        return Optional.empty();
    }

    /** How a sentence names {@code field}'s parameter: {@code Settings.confidence()}. */
    public static String named(SettingParams.Field<?> field) {
        String[] words = field.id().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder method = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            method.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return "Settings." + method + "()";
    }

    private static <T> Optional<String> changed(StudioServices services, SettingParams.Field<T> field,
                                                BotSettings was, BotSettings next) {
        T value = field.read().apply(next);
        if (Objects.equals(field.read().apply(was), value)) return Optional.empty();
        ParamSlot.Opened opened = ParamSlot.openOrCreate(services, field.param(), named(field));
        if (opened.ctx().isEmpty()) return Optional.of(opened.refused());
        return opened.ctx().get().write(value);
    }

    private static Optional<?> read(StudioServices services, SettingParams.Field<?> field) {
        return ParamSlot.open(services, field.param()).flatMap(ctx -> {
            try {
                return ctx.value(field.param().type());
            } catch (RuntimeException unreadable) {
                return Optional.empty();
            }
        });
    }
}
