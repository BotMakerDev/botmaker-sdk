package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Fields;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Slots;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.util.List;

/**
 * A length of time, entered as one box per unit.
 *
 * <p>A wait is the one argument in the SDK whose <em>unit is invisible in the number</em>: {@code 2} is two
 * seconds or two milliseconds depending on which method it was typed into, a thousandfold difference that
 * reads identically on the block. So the length is entered as one box per unit rather than as a number whose
 * unit lives in the method name.
 *
 * <p><b>It reads and writes a {@link Duration}, and nothing else.</b> The host reads the slot through
 * plugin-basics' {@code Duration} type and writes the value back the same way. A slot the host cannot read —
 * a variable, {@code Duration.ZERO}, a factory other than the one the host writes — keeps its own source as
 * the label, and opening the editor on it starts from one second.
 *
 * <p>Until 2026-09-23 it also offered <i>Random range</i>, which rewrote {@code Wait.time(x)} into
 * {@code Wait.between(a, b)} as Java text through the enclosing call. The contract no longer hands a plugin
 * the call as text, so the toggle went; {@code Wait.between} is in the palette.
 */
public final class DurationEditor {

    /** What a slot holding something unreadable opens on. */
    private static final long DEFAULT_MILLIS = 1000L;

    private DurationEditor() {}

    /** The editor: a pill that opens a small modal, wherever the value is. */
    public static Node duration(ValueContext ctx) {
        MenuButton button = Pills.bare(slotLabel(ctx));
        Pills.onOpen(button, () -> List.of(Pills.item("Edit duration…",
                () -> open(ctx, () -> button.setText(slotLabel(ctx))))));
        return button;
    }

    /** The modal: one length, committed on OK. */
    private static void open(ValueContext ctx, Runnable onWritten) {
        long current = millis(ctx);
        long[] chosen = {current};
        HBox field = Fields.duration(current, value -> chosen[0] = value);
        Modals.form(ctx, "Duration", new VBox(8, field), () -> {
            write(ctx, current, chosen[0]);
            onWritten.run();
        });
    }

    /**
     * Commits what the modal now says. An untouched length is not written at all, so opening the editor and
     * pressing OK leaves the file byte-identical.
     */
    static void write(ValueContext ctx, long before, long after) {
        if (after == before && ctx.value(Duration.class).isPresent()) return;
        ctx.set(Duration.ofMillis(Math.max(0, after)));
    }

    /** The length the slot holds, in milliseconds, or one second when the host could not read it. */
    static long millis(ValueContext ctx) {
        return ctx.value(Duration.class).map(Duration::toMillis).orElse(DEFAULT_MILLIS);
    }

    /**
     * What the pill says: the whole length spelled canonically ({@code 4h30m}), or the source as written when
     * the host could not read it.
     */
    static String slotLabel(ValueContext ctx) {
        return ctx.value(Duration.class).map(d -> spell(d.toMillis())).orElseGet(() -> {
            String raw = Slots.raw(ctx);
            return raw.isBlank() ? "Choose duration…" : raw;
        });
    }

    /**
     * {@code 0s}, {@code 250ms}, {@code 1m30s}, {@code 1h30m} — the largest units first, zeroes dropped.
     *
     * <p>A <b>label</b>, not a stored form: nothing reads this back, and the duration a person sees on a block
     * is not the unit the source happens to be written in.
     */
    static String spell(long millis) {
        if (millis <= 0) return "0s";
        StringBuilder out = new StringBuilder();
        long left = millis;
        left = unit(out, left, 3_600_000L, "h");
        left = unit(out, left, 60_000L, "m");
        left = unit(out, left, 1000L, "s");
        if (left > 0) out.append(left).append("ms");
        return out.toString();
    }

    private static long unit(StringBuilder out, long left, long size, String suffix) {
        long count = left / size;
        if (count > 0) out.append(count).append(suffix);
        return left - count * size;
    }
}
