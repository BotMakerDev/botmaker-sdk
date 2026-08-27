package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.SlotContext;
import com.botmaker.plugin.api.ValueContext;
import com.botmaker.plugin.toolkit.Fields;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.authoring.WireText;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * A length of time — and, where the call allows it, the "somewhere in this range" toggle that is the reason a
 * wait is a typed value at all.
 *
 * <p>A wait is the one argument in the SDK whose <em>unit is invisible in the number</em>: {@code 2} is two
 * seconds or two milliseconds depending on which method it was typed into, a thousandfold difference that
 * reads identically on the block. So the length is entered as one box per unit rather than as a number whose
 * unit lives in the method name.
 *
 * <p><b>The range is a different call, not a different value</b>, which is why this editor is the one that
 * needed {@link SlotContext#replaceEnclosingCall}. The SDK once shipped a {@code Duration} of its own that
 * could itself be a range; it does not, and the humanized wait is {@code Wait.between(min, max)}. Ticking
 * <i>Random range</i> therefore rewrites the whole call — {@code Wait.time(x)} becomes
 * {@code Wait.between(x, y)} and back — rather than nesting something inside the slot, which is not what an
 * author would have typed. Outside a {@code Wait} call there is no call to restructure, so the toggle is not
 * offered and only the length is editable.
 *
 * <p><b>It draws differently in the two places the host edits values, on purpose.</b> A slot in a bot's source
 * is a few centimetres of a block, so it is a pill that opens the fields; a row of the Parameters window is a
 * whole row with nothing else in it, so it is the fields themselves. Same editor, same reading of the same
 * value — {@link Slots} is what lets one piece of code write {@code Duration.ofSeconds(2)} in one place and
 * {@code 2s} in the other.
 *
 * <p>Commits the shortest form that says what was chosen: {@code Duration.ofSeconds(2)} rather than
 * {@code Duration.ofMillis(2000)}. {@code java.time.Duration}'s factories take whole numbers, so a fraction
 * goes in the next unit down ({@code 1.5} seconds → {@code ofMillis(1500)}) rather than being truncated.
 * Reading is the inverse, and an untouched value is written back exactly as it was read, so opening the
 * editor and pressing OK is a no-op on the source.
 */
final class DurationEditor {

    /** The fully-qualified name the slot form is written and imported as. */
    private static final String FQN = "java.time.Duration";

    /** What a slot holding something unreadable opens on. */
    private static final long DEFAULT_MILLIS = 1000L;

    private DurationEditor() {}

    /** The editor: fields for a Parameters row, a pill that opens them for a slot in source. */
    static Node duration(ValueContext ctx) {
        return ctx.asSlot() == null ? row(ctx) : pill(ctx);
    }

    // --- the Parameters window: the fields themselves ------------------------------------------------------

    /**
     * Four boxes and the total spelled out beside them, written on every keystroke.
     *
     * <p>Writing continuously is right here and wrong for a slot: a row's value is a stored string with no
     * undo history behind it, while a slot is source code where every write is an edit somebody may want to
     * take back.
     */
    private static Node row(ValueContext ctx) {
        long millis = WireText.duration(ctx.single()).toMillis();
        Label preview = Styles.on(new Label(WireText.spellDuration(millis)), Styles.CAPTION);
        HBox fields = Fields.duration(millis, total -> {
            preview.setText(WireText.spellDuration(total));
            ctx.set(WireText.spellDuration(total));
        });
        fields.getChildren().add(preview);
        return fields;
    }

    // --- a slot in a bot's source: a pill over a small modal -----------------------------------------------

    private static Node pill(ValueContext ctx) {
        MenuButton button = Pills.bare(slotLabel(ctx));
        Pills.onOpen(button, () -> List.of(Pills.item("Edit duration…", () -> {
            Span current = span(ctx);
            open(ctx, current, !waitArguments(ctx).isEmpty(), () -> button.setText(slotLabel(ctx)));
        })));
        return button;
    }

    /** The modal: one length, a second when the range is ticked, and the toggle when the call allows it. */
    private static void open(ValueContext ctx, Span current, boolean rangeable, Runnable onWritten) {
        long[] from = {current.from()};
        long[] to = {current.to()};

        HBox first = Fields.duration(current.from(), value -> from[0] = value);
        HBox second = Fields.duration(current.to(), value -> to[0] = value);
        HBox upper = new HBox(6, Styles.on(new Label("to"), Styles.CAPTION), second);

        CheckBox range = new CheckBox("Random range");
        range.setSelected(current.range());
        range.setVisible(rangeable);
        range.setManaged(rangeable);
        range.setTooltip(new Tooltip("Wait a different amount within the range each time — a bot that always "
                                     + "waits exactly the same is the easiest kind to spot. Writes "
                                     + "Wait.between(…)."));

        // Bound rather than merely disabled: the second end is meaningless for a fixed duration, and a
        // greyed-out number still reads as part of the value.
        upper.visibleProperty().bind(range.selectedProperty());
        upper.managedProperty().bind(range.selectedProperty());

        VBox body = new VBox(8, first, upper, range);
        Modals.form(ctx, "Duration", body, () -> {
            write(ctx, current, from[0], to[0], range.isSelected());
            onWritten.run();
        });
    }

    /**
     * Commits what the modal now says.
     *
     * <p>An end the user did not touch is written back as it was <em>read</em>, not rebuilt from its
     * millisecond total: opening on {@code Duration.ofSeconds(120)} and pressing OK must not quietly rewrite
     * the source to {@code ofMinutes(2)}. An inverted range is shown back the way round it reads, and a range
     * whose ends are equal is not a range.
     */
    static void write(ValueContext ctx, Span current, long from, long to, boolean range) {
        String lowSource = from == current.from() ? current.fromSource() : code(from);
        String highSource = to == current.to() ? current.toSource() : code(to);
        if (from > to) {
            long swapMillis = from;
            from = to;
            to = swapMillis;
            String swapSource = lowSource;
            lowSource = highSource;
            highSource = swapSource;
        }

        SlotContext slot = ctx.asSlot();
        if (slot == null) {
            ctx.set(WireText.spellDuration(from));
            return;
        }

        boolean rewritable = !waitArguments(ctx).isEmpty();
        if (rewritable && range && to > from) {
            // The simple name, not the qualified one: the call being rewritten is already a Wait call, so the
            // import that makes it legal is by definition present.
            slot.replaceEnclosingCall("Wait.between(" + lowSource + ", " + highSource + ")", FQN);
        } else if (rewritable && "between".equals(slot.enclosingMethod())) {
            // Un-ticking the range: the call has to shrink back to one argument, which no edit confined to
            // this slot could say — dropping the far end is a change to the call, not to the value in it.
            slot.replaceEnclosingCall("Wait.time(" + lowSource + ")", FQN);
        } else {
            slot.replaceWith(lowSource, FQN);
        }
    }

    // --- reading what is there -----------------------------------------------------------------------------

    /** One length or two, each kept as the source wrote it so an untouched end survives a round trip. */
    record Span(long from, String fromSource, long to, String toSource, boolean range) {}

    /** The span the modal opens on: both ends of a {@code Wait.between}, or the one length of anything else. */
    static Span span(ValueContext ctx) {
        List<String> ends = waitArguments(ctx);
        if (ends.size() == 2) {
            Long low = millis(ends.get(0));
            Long high = millis(ends.get(1));
            if (low != null && high != null) {
                return new Span(low, ends.get(0), high, ends.get(1), true);
            }
        }
        String own = Slots.raw(ctx);
        Long value = millis(own);
        long resolved = value == null ? DEFAULT_MILLIS : value;
        String source = value == null ? code(DEFAULT_MILLIS) : own;
        return new Span(resolved, source, resolved, source, false);
    }

    /**
     * The arguments of the enclosing wait, when this editor may rewrite that call — meaning it is a
     * {@code Wait.time}/{@code Wait.between} of the right arity and <em>every</em> argument is a length this
     * editor can show.
     *
     * <p>A {@code between} with a variable at one end is left alone: rewriting the call would discard that
     * end, and keeping it is worth more than the toggle.
     */
    static List<String> waitArguments(ValueContext ctx) {
        SlotContext slot = ctx.asSlot();
        if (slot == null) return List.of();
        String call = slot.enclosingSource();
        String method = slot.enclosingMethod();
        String owner = slot.enclosingClass();
        String simple = Wait.class.getSimpleName();
        if (call == null || owner == null || !(owner.equals(simple) || owner.endsWith("." + simple))) {
            return List.of();
        }
        List<String> args = Slots.arguments(call);
        boolean shaped = ("time".equals(method) && args.size() == 1)
                         || ("between".equals(method) && args.size() == 2);
        if (!shaped) return List.of();
        for (String arg : args) {
            if (millis(arg) == null) return List.of();
        }
        return args;
    }

    /**
     * A {@code Duration.ofMillis(n)} / {@code ofSeconds(n)} / {@code ofMinutes(n)} / {@code ofHours(n)} as a
     * millisecond total, or {@code null} for anything else.
     *
     * <p>Null for a variable, an arithmetic expression or {@code Duration.ZERO} — all of which are perfectly
     * good code that this editor has no way to show, and rewriting one of them into a number the user never
     * typed is the worst thing it could do.
     */
    static Long millis(String source) {
        String s = source == null ? "" : source.trim();
        long unit = unitOf(s);
        if (unit == 0) return null;
        List<String> args = Slots.arguments(s);
        if (args.size() != 1) return null;
        try {
            double amount = Double.parseDouble(args.getFirst().replace("_", "").replaceAll("[lLdDfF]$", ""));
            return Math.round(amount * unit);
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    /** How many milliseconds one of the factory's units is, or {@code 0} when the call is not one of them. */
    private static long unitOf(String source) {
        int open = source.indexOf('(');
        String head = open < 0 ? source : source.substring(0, open);
        if (head.endsWith(".ofMillis")) return 1L;
        if (head.endsWith(".ofSeconds")) return 1000L;
        if (head.endsWith(".ofMinutes")) return 60_000L;
        if (head.endsWith(".ofHours")) return 3_600_000L;
        return 0L;
    }

    /** A millisecond total as the coarsest factory call that still says it exactly. */
    static String code(long millis) {
        if (millis != 0 && millis % 3_600_000L == 0) return "Duration.ofHours(" + millis / 3_600_000L + ")";
        if (millis != 0 && millis % 60_000L == 0) return "Duration.ofMinutes(" + millis / 60_000L + ")";
        if (millis != 0 && millis % 1000L == 0) return "Duration.ofSeconds(" + millis / 1000L + ")";
        return "Duration.ofMillis(" + millis + ")";
    }

    /**
     * What the pill says: the whole length spelled canonically ({@code 4h30m}), not the number and unit it
     * happens to be stored in.
     *
     * <p>{@code Duration.ofMinutes(270)} on a block <em>is</em> four and a half hours, and that is what a
     * person reading the block wants to know. What the source says is {@link #code}'s business, and the two
     * are deliberately separate now that one length can span several units.
     */
    static String slotLabel(ValueContext ctx) {
        String raw = Slots.raw(ctx);
        Long value = millis(raw);
        if (value != null) return WireText.spellDuration(value);
        return raw.isBlank() ? "Choose duration…" : raw;
    }
}
