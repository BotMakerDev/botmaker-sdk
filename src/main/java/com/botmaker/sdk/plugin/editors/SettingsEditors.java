package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Editors;
import javafx.scene.Node;

import java.lang.reflect.Parameter;

/**
 * The editor for an argument passed to a {@code BotSettings} setter — a number that has a range, shown as that
 * range instead of as a place to type any number at all, or a tick for a flag.
 *
 * <p>Every one of these settings is a number whose <em>scale</em> is the thing nobody knows. A confidence of
 * {@code 0.8} means something only once you can see where it sits between 0 and 1; a found-delay of
 * {@code 500} means something only next to the fact that it is milliseconds. A free-typed literal says
 * neither, and accepts {@code 80} for a confidence — a value the SDK will clamp and the author will never
 * find out about.
 *
 * <p>The pill, the dialog, the spinner-or-slider division and the clamping are the toolkit's
 * {@link Editors#boundedPill} and {@link Editors#flag}. What each setting is called and what its range is
 * comes from {@link SettingHints}, keyed by the setter as a method reference.
 */
public final class SettingsEditors {

    private SettingsEditors() {}

    /** The editor for the setting this slot is passed to; {@code null} when it is not a setter's argument. */
    public static Node setting(ValueContext ctx) {
        Parameter parameter = ctx.slot().flatMap(SlotContext::parameter).orElse(null);
        SettingHints.Hint setting = parameter == null ? null : SettingHints.of(parameter);
        if (setting == null) return null;
        Class<?> type = parameter.getType();
        if (type == boolean.class || type == Boolean.class) return Editors.flag(ctx, setting.label());
        boolean whole = type == int.class || type == long.class || type == Integer.class || type == Long.class;
        return Editors.boundedPill(ctx, new Editors.NumberRange(setting.label(), setting.prompt(), setting.unit(),
                whole, setting.min(), setting.max(), setting.step(), setting.fallback()));
    }
}
