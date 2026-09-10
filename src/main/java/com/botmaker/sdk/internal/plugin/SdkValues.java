package com.botmaker.sdk.internal.plugin;

import com.botmaker.plugin.api.value.Range;
import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueChoice;
import com.botmaker.plugin.api.value.ValueType;
import com.botmaker.plugin.basics.values.BasicsValueTypes;
import com.botmaker.sdk.internal.authoring.SdkValueTypes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The coercion rules a parameter editor runs: canonicalise, clamp, prune, default.
 *
 * <p><b>Why these are here now.</b> They were Studio's, in {@code project/activity/ValueWire}, and the rule
 * about them has always been that <em>coercion belongs to the editor, where a user can watch it happen</em> —
 * which is why {@code VariableModel} is plain data and normalises nothing. On 2026-09-10 the maintainer's
 * call moved the Parameters editor itself into this plugin, so the editor is here, and the rules came with
 * it. The rule did not change; the editor did.
 *
 * <h2>What each one is for</h2>
 *
 * <ul>
 *   <li><b>Canonicalising</b> — {@code store(parse(wire))} through the owning type's codec, so the editor
 *       shows the value the bot will actually get rather than the text somebody happened to type, and so
 *       {@code "10 "} and {@code "10"} are one value rather than two.</li>
 *   <li><b>Clamping</b> — a declared {@link Range} is advice to a widget and a clamp when a value is
 *       normalised, <em>never</em> a validation that can fail. A value outside the range is pulled to the
 *       nearest bound, because the alternative is a project that will not save because somebody tightened a
 *       limit after the fact.</li>
 *   <li><b>Pruning</b> — an option the author has just deleted must stop being a stored value, or the bot
 *       runs on a setting that appears nowhere in the UI that set it.</li>
 * </ul>
 *
 * <h2>The catalog is this plugin's own, and that is a real limit</h2>
 *
 * <p>The editor merges every loaded plugin's vocabulary; a plugin can only see its own and whatever it
 * compiles against — here, {@link SdkValueTypes} and {@link BasicsValueTypes}, exactly the pair
 * {@code Authoring} merges to read the project file. A value of a <em>third</em> plugin's type filed in this
 * plugin's section is therefore left untouched rather than canonicalised, which is precisely what
 * {@link ValueCatalog#normalize} does for an id nothing registered, and for the same reason: never rewrite
 * a value you cannot read.
 */
public final class SdkValues {

    private SdkValues() {}

    /** The seventeen this plugin can read — its own eight and plugin #2's nine. */
    public static final ValueCatalog CATALOG = BasicsValueTypes.CATALOG.merge(SdkValueTypes.CATALOG);

    /**
     * A fresh value of {@code type}: the type's own default for a single value, nothing for a list.
     *
     * <p>An empty list rather than one empty item, because a list a user has not filled in has no items —
     * seeding one would put a blank row in every new list-shaped parameter.
     */
    public static List<String> defaultValue(ValueChoice type) {
        if (type == null || type.isList()) return List.of();
        return List.of(CATALOG.defaultItem(type.type().id()));
    }

    /** The values a type supplies itself — an enum's constants. Empty for a type whose set is the author's. */
    public static List<String> fixedOptions(ValueType type) {
        return type == null ? List.of() : type.options();
    }

    /**
     * The declared choices as the type actually stores them: each canonicalised, duplicates dropped, order
     * kept. Empty when the shape declares no set.
     *
     * <p>Every choice is itself a value of the base type, so it goes through the same normaliser a value
     * does — otherwise the radio button is labelled with one spelling and the stored value matches neither.
     */
    public static List<String> normalizeOptions(List<String> options, ValueChoice type, Range bounds) {
        if (type == null || !type.hasOptions() || options == null) return List.of();
        return options.stream()
                .filter(Objects::nonNull)
                .map(option -> item(option, type.type(), bounds == null ? Range.NONE : bounds))
                .distinct()
                .toList();
    }

    /**
     * A stored value, canonicalised, clamped and constrained to what is still on offer.
     *
     * @param value   the stored wire form, one entry per item
     * @param type    what kind of value, and in what shape
     * @param options the declared choices, for an option-bearing shape
     * @param bounds  the declared range, for a bounded number
     */
    public static List<String> normalize(List<String> value, ValueChoice type, List<String> options,
                                         Range bounds) {
        if (type == null) return value == null ? List.of() : List.copyOf(value);
        List<String> safe = value == null ? List.of() : value.stream().filter(Objects::nonNull).toList();
        List<String> choices = normalizeOptions(options, type, bounds);
        Range range = bounds == null ? Range.NONE : bounds;

        if (!type.isList()) {
            return List.of(constrain(item(safe.isEmpty() ? null : safe.getFirst(), type.type(), range),
                    choices));
        }
        // An option-bearing list follows the declaration order, not the file's: two projects that picked the
        // same choices in a different order must write the same line, or a diff shows a change nobody made.
        if (!choices.isEmpty()) {
            LinkedHashSet<String> chosen = safe.stream()
                    .map(each -> item(each, type.type(), range))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return choices.stream().filter(chosen::contains).toList();
        }
        return safe.stream().map(each -> item(each, type.type(), range)).toList();
    }

    /** {@code value} if it is still on offer, else the first thing that is. Unconstrained when nothing is. */
    private static String constrain(String value, List<String> choices) {
        if (choices.isEmpty()) return value;
        return choices.contains(value) ? value : choices.getFirst();
    }

    /**
     * One item, canonicalised by its own codec and then clamped to the declared range.
     *
     * <p>Clamping runs <em>after</em> the codec and is re-canonicalised afterwards, so the two cannot
     * disagree about the spelling of the result — a clamp that produced {@code "5"} for a decimal would
     * otherwise store text its own reader normalises to {@code "5.0"} on the very next read.
     */
    private static String item(String wire, ValueType type, Range bounds) {
        String canonical = CATALOG.normalize(type.id(), wire);
        if (!type.bounded() || bounds.isEmpty()) return canonical;
        return CATALOG.normalize(type.id(), clamp(canonical, bounds));
    }

    private static String clamp(String canonical, Range bounds) {
        double value = number(canonical, 0.0);
        double min = number(bounds.min(), Double.NEGATIVE_INFINITY);
        double max = number(bounds.max(), Double.POSITIVE_INFINITY);
        double clamped = Math.max(min, Math.min(max, value));
        // Written back through the plain double spelling and read by the type's own codec, which is what
        // turns it into an int again for a whole number.
        return clamped == value ? canonical : Double.toString(clamped);
    }

    private static double number(String text, double fallback) {
        if (text == null || text.isBlank()) return fallback;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
