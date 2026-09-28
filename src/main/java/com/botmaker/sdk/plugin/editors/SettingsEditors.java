package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Editors;
import com.botmaker.sdk.api.bot.Setting;
import javafx.scene.Node;

import java.lang.reflect.Parameter;

/**
 * The editor for an argument passed to a {@link Setting} parameter — a number that has a range, shown as that
 * range instead of as a place to type any number at all, or a tick for a flag.
 *
 * <p>Every one of these settings is a number whose <em>scale</em> is the thing nobody knows. A confidence of
 * {@code 0.8} means something only once you can see where it sits between 0 and 1; a found-delay of
 * {@code 500} means something only next to the fact that it is milliseconds. A free-typed literal says
 * neither, and accepts {@code 80} for a confidence — a value the SDK will clamp and the author will never
 * find out about.
 *
 * <p>The pill, the dialog, the spinner-or-slider division and the clamping are the toolkit's
 * {@link Editors#boundedPill} and {@link Editors#flag}. What each setting is called and what its range is sits
 * on the parameter itself, in {@code BotSettings}, so a new setting is one annotation and no table here. Until
 * 2026-09-28 this class kept that table, keyed on the setters' names.
 */
public final class SettingsEditors {

    private SettingsEditors() {}

    /** The editor for the setting this slot is passed to; {@code null} when the parameter carries none. */
    public static Node setting(ValueContext ctx) {
        Parameter parameter = ctx.slot().flatMap(SlotContext::parameter).orElse(null);
        Setting setting = parameter == null ? null : parameter.getAnnotation(Setting.class);
        if (setting == null) return null;
        Class<?> type = parameter.getType();
        if (type == boolean.class || type == Boolean.class) return Editors.flag(ctx, setting.label());
        boolean whole = type == int.class || type == long.class || type == Integer.class || type == Long.class;
        return Editors.boundedPill(ctx, new Editors.NumberRange(setting.label(), setting.prompt(), setting.unit(),
                whole, setting.min(), setting.max(), setting.step(), setting.fallback()));
    }
}
