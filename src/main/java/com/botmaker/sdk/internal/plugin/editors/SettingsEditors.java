package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.ValueContext;
import com.botmaker.plugin.toolkit.Fields;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Styles;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The editor for a {@code BotSettings} setter's one argument — a number that has a range, shown as that
 * range instead of as a place to type any number at all.
 *
 * <p>Every one of these settings is a number whose <em>scale</em> is the thing nobody knows. A confidence of
 * {@code 0.8} means something only once you can see where it sits between 0 and 1; a found-delay of
 * {@code 500} means something only next to the fact that it is milliseconds. A free-typed literal says
 * neither, and accepts {@code 80} for a confidence — a value the SDK will clamp and the author will never
 * find out about.
 *
 * <p><b>The table is the dispatch.</b> {@link #bounds} is asked twice: once by {@link CallSites#BOT_SETTING}
 * to decide whether this editor applies at all, and once here to build it. A setter with no entry is not
 * claimed, so adding one is one row rather than a row and a predicate that must agree with it.
 */
final class SettingsEditors {

    private SettingsEditors() {}

    /** How a setting is asked for: a whole count, a fraction of one, or a yes/no. */
    private enum Kind { WHOLE, FRACTION, FLAG }

    /**
     * One setting's range and its words.
     *
     * @param label    the setting's name as a person would say it, not as the setter spells it
     * @param prompt   the sentence above the control, which is where the unit and the limit are stated
     * @param unit     appended to the pill's own text, so a delay reads {@code 500 ms} on the block itself
     * @param fallback what an unreadable or absent value opens on — the SDK's own default for that setting
     */
    private record Bound(Kind kind, String label, String prompt, String unit,
                         double min, double max, double step, double fallback) {}

    /**
     * The bounded setters, and everything known about each.
     *
     * <p>The ceiling on the two delays is ten minutes rather than {@code Integer.MAX_VALUE}: a spinner whose
     * range is the whole int is a spinner with no scale, and a bot waiting longer than that between checks is
     * not tuning a delay, it is writing a different bot.
     */
    static Bound bounds(String setter) {
        return switch (setter == null ? "" : setter) {
            case "setFoundDelay" -> new Bound(Kind.WHOLE, "Delay after a match",
                    "Milliseconds (≥ 0):", " ms", 0, 600_000, 50, 500);
            case "setNotFoundDelay" -> new Bound(Kind.WHOLE, "Delay after no match",
                    "Milliseconds (≥ 0):", " ms", 0, 600_000, 50, 200);
            case "setMaxRetryAttempts" -> new Bound(Kind.WHOLE, "Max stuck checks",
                    "Checks before considered stuck (≥ 1):", "", 1, 600_000, 1, 20);
            case "setDefaultConfidence" -> new Bound(Kind.FRACTION, "Match confidence",
                    "Confidence (0.0 – 1.0):", "", 0, 1, 0.05, 0.8);
            case "setCompareMargin" -> new Bound(Kind.FRACTION, "Compare margin",
                    "How far the right template must beat a look-alike (0.0 – 1.0):", "", 0, 1, 0.01, 0.05);
            case "enableRandomClicks" -> new Bound(Kind.FLAG, "Randomize click points", "", "", 0, 1, 1, 0);
            case "enableDebugMode" -> new Bound(Kind.FLAG, "Debug logging", "", "", 0, 1, 1, 0);
            default -> null;
        };
    }

    /** The editor for whichever setter this slot sits in. */
    static Node setting(ValueContext ctx) {
        Bound bound = bounds(ctx.asSlot() == null ? null : ctx.asSlot().enclosingMethod());
        if (bound == null) return null;
        return bound.kind() == Kind.FLAG ? flag(ctx, bound) : number(ctx, bound);
    }

    /**
     * A yes/no setting, written the moment it is ticked.
     *
     * <p>No modal, and no OK: there is nothing to get wrong about a checkbox, and a window asking a person to
     * confirm the tick they just made is a window. It carries the setting's name because
     * {@code enableDebugMode(true)} beside a bare box reads as though the box is the argument to something
     * else — which, without the label, is exactly what it looks like.
     */
    private static Node flag(ValueContext ctx, Bound bound) {
        CheckBox box = new CheckBox(bound.label());
        box.setSelected(Boolean.parseBoolean(Slots.raw(ctx)));
        box.setOnAction(e -> Slots.write(ctx, Boolean.toString(box.isSelected()),
                Boolean.toString(box.isSelected())));
        return box;
    }

    /**
     * A bounded number, behind a pill that shows the current one.
     *
     * <p>Whole counts get a spinner and fractions get a slider, which is {@link Fields}' own division and the
     * reason it draws them differently: 500 milliseconds is a quantity a person types, while 0.8 confidence is
     * a position a person finds. The value is committed on <i>OK</i> and not while dragging — this is a slot in
     * a bot's source, and a slider that rewrote the file on every pixel of the drag would fill the undo stack
     * with values nobody chose.
     */
    private static Node number(ValueContext ctx, Bound bound) {
        MenuButton pill = Pills.bare(label(ctx, bound));
        Pills.onOpen(pill, () -> java.util.List.of(Pills.item("Set " + bound.label().toLowerCase() + "…", () -> {
            double current = current(ctx, bound);
            if (bound.kind() == Kind.WHOLE) {
                Spinner<Integer> spinner = Fields.integer((int) Math.round(current),
                        (int) bound.min(), (int) bound.max());
                Modals.form(ctx, bound.label(), body(bound, spinner), () -> {
                    commit(ctx, bound, spinner.getValue());
                    pill.setText(label(ctx, bound));
                });
            } else {
                double[] picked = {current};
                HBox slider = Fields.bounded(current, bound.min(), bound.max(), bound.step(),
                        value -> picked[0] = value);
                Modals.form(ctx, bound.label(), body(bound, slider), () -> {
                    commit(ctx, bound, picked[0]);
                    pill.setText(label(ctx, bound));
                });
            }
        })));
        return pill;
    }

    private static VBox body(Bound bound, Node control) {
        return new VBox(6, Styles.on(new Label(bound.prompt()), Styles.CAPTION), control);
    }

    /** Writes the number the way a person would have typed it: a count as a count, a fraction as a decimal. */
    private static void commit(ValueContext ctx, Bound bound, double value) {
        double clamped = Math.clamp(value, bound.min(), bound.max());
        String literal = bound.kind() == Kind.WHOLE
                ? Long.toString(Math.round(clamped))
                : BigDecimal.valueOf(clamped).setScale(3, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString();
        Slots.write(ctx, literal, literal);
    }

    /**
     * The number in the slot, or the setting's own default.
     *
     * <p>Falling back to the default rather than to zero matters here: opening the editor on a slot holding a
     * variable and pressing OK would otherwise write {@code 0}, which for a confidence means "match anything".
     */
    private static double current(ValueContext ctx, Bound bound) {
        try {
            String raw = Slots.raw(ctx).replace("_", "").replaceAll("[lLdDfF]$", "");
            return raw.isBlank() ? bound.fallback() : Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return bound.fallback();
        }
    }

    /** What the pill says: the value as written, plus the unit — or the source text when it is not a number. */
    private static String label(ValueContext ctx, Bound bound) {
        String raw = Slots.raw(ctx);
        if (raw.isBlank()) return bound.label() + "…";
        try {
            Double.parseDouble(raw.replace("_", "").replaceAll("[lLdDfF]$", ""));
            return raw + bound.unit();
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}
