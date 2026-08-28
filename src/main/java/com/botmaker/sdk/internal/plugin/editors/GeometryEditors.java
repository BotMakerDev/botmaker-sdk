package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.ValueContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;

import java.util.List;

/**
 * The three editors for the SDK's geometry types, which are one editor three times: a pill showing the
 * current numbers, a way to take them off the screen, and a way to type them.
 *
 * <p>Taking them off the screen is what these exist for. Nobody knows that a health bar is 240 pixels wide;
 * they know where its ends are. So {@code Rect} drags a rectangle, {@code Point} uses the magnifier, and
 * {@code Size} drags a rectangle and throws the origin away — which is exactly how a person measures
 * something on a screen.
 */
public final class GeometryEditors {

    private GeometryEditors() {}

    /** A {@code Rect}: drag a region on screen, or type {@code x, y, width, height}. */
    public static Node rect(ValueContext ctx) {
        MenuButton pill = Pills.bare(rectLabel(ctx));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Select on screen…", () -> ctx.services().capture().selectRegion(r -> {
                    Slots.writeConstructor(ctx, Rect.class, r.x(), r.y(), r.width(), r.height());
                    pill.setText(rectLabel(ctx));
                })),
                Pills.separator(),
                Pills.item("Edit values…", () -> Modals.numbers(ctx, "Rect",
                        new String[]{"x", "y", "width", "height"}, Slots.ints(ctx, 4), picked -> {
                            Slots.writeConstructor(ctx, Rect.class, picked);
                            pill.setText(rectLabel(ctx));
                        }))));
        return pill;
    }

    /** A {@code Point}: click one pixel under a magnifier, or type {@code x, y}. */
    public static Node point(ValueContext ctx) {
        MenuButton pill = Pills.bare(pointLabel(ctx));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Pick on screen…", () -> ctx.services().capture().pickPoint(p -> {
                    Slots.writeConstructor(ctx, Point.class, p.x(), p.y());
                    pill.setText(pointLabel(ctx));
                })),
                Pills.separator(),
                Pills.item("Edit values…", () -> Modals.numbers(ctx, "Point",
                        new String[]{"x", "y"}, Slots.ints(ctx, 2), picked -> {
                            Slots.writeConstructor(ctx, Point.class, picked);
                            pill.setText(pointLabel(ctx));
                        }))));
        return pill;
    }

    /** A {@code Size}: measure by dragging over the thing, or type {@code width, height}. */
    public static Node size(ValueContext ctx) {
        MenuButton pill = Pills.bare(sizeLabel(ctx));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Measure on screen…", () -> ctx.services().capture().selectRegion(r -> {
                    Slots.writeConstructor(ctx, Size.class, r.width(), r.height());
                    pill.setText(sizeLabel(ctx));
                })),
                Pills.separator(),
                Pills.item("Edit values…", () -> Modals.numbers(ctx, "Size",
                        new String[]{"width", "height"}, Slots.ints(ctx, 2), picked -> {
                            Slots.writeConstructor(ctx, Size.class, picked);
                            pill.setText(sizeLabel(ctx));
                        }))));
        return pill;
    }

    // Package-private rather than private: the label is the one piece of these editors that can be asserted
    // without a JavaFX toolkit, and it is the piece worth asserting — the number a user reads off the
    // collapsed pill is read back out of what the last pick wrote, and getting it wrong shows one coordinate
    // while the bot runs another. See GeometryLabelTest.
    static String rectLabel(ValueContext ctx) {
        if (Slots.isEmpty(ctx)) return "Choose region…";
        int[] v = Slots.ints(ctx, 4);
        return holdsNumbers(ctx, 4) ? v[0] + ", " + v[1] + "  " + v[2] + "×" + v[3] : Slots.raw(ctx);
    }

    static String pointLabel(ValueContext ctx) {
        if (Slots.isEmpty(ctx)) return "Choose point…";
        int[] v = Slots.ints(ctx, 2);
        return holdsNumbers(ctx, 2) ? v[0] + ", " + v[1] : Slots.raw(ctx);
    }

    static String sizeLabel(ValueContext ctx) {
        if (Slots.isEmpty(ctx)) return "Choose size…";
        int[] v = Slots.ints(ctx, 2);
        return holdsNumbers(ctx, 2) ? v[0] + " × " + v[1] : Slots.raw(ctx);
    }

    /**
     * Whether the value is a set of coordinates at all, rather than something else that happens to parse to
     * zeroes — a variable, a call, a computed expression.
     *
     * <p>Without the check a slot holding {@code bounds} would label itself {@code 0, 0  0×0}, which claims a
     * value the user never set. Showing the raw text instead is honest, and it is what the picker this
     * replaced did: rewriting somebody's {@code target.center()} into "0, 0" is a lie about what the bot does.
     *
     * <p>Two things it deliberately does <em>not</em> check. A <b>missing</b> argument is fine and reads as
     * zero — {@code new Point(10)} is what a freshly inserted block looks like before the user picks, and
     * labelling it {@code 10, 0} is the right answer. And the <b>type being constructed</b> is nobody's
     * business here: which editor a slot gets was decided by its declared type one layer up, so this reads
     * positionally and a second type check would be dead code.
     */
    private static boolean holdsNumbers(ValueContext ctx, int n) {
        if (ctx.asSlot() == null) {
            List<String> parts = ctx.value();
            if (parts.size() < n) return false;
            for (int i = 0; i < n; i++) {
                if (!isNumber(parts.get(i))) return false;
            }
            return true;
        }
        String raw = Slots.raw(ctx);
        if (!raw.startsWith("new ")) return false;
        List<String> args = Slots.arguments(raw);
        for (int i = 0; i < Math.min(n, args.size()); i++) {
            String arg = args.get(i).trim();
            if (!arg.isEmpty() && !isNumber(arg)) return false;
        }
        return true;
    }

    /** A whole number as a person writes one, with a Java {@code long} suffix or digit separators allowed. */
    private static boolean isNumber(String text) {
        String s = text == null ? "" : text.trim().replace("_", "");
        if (s.endsWith("L") || s.endsWith("l")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c) && !(i == 0 && (c == '-' || c == '+'))) return false;
        }
        return !(s.length() == 1 && (s.charAt(0) == '-' || s.charAt(0) == '+'));
    }
}
