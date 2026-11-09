package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.toolkit.Editors;
import com.botmaker.plugin.toolkit.Editors.Pick;
import com.botmaker.plugin.toolkit.Editors.TupleSpec;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import javafx.scene.Node;

/**
 * The three editors for the SDK's geometry types, which are one editor three times
 * <em>literally</em>: {@link Editors#tuplePill} is the shape, and what is
 * left here is the three tables that say which numbers each type has and how a person reads them.
 *
 * <p>That split is the toolkit's rule 4 doing its job. A pill over a few whole numbers with a way to take
 * them off the screen is a shape any plugin's coordinates want; that a {@code Rect} is an origin plus a size
 * and reads {@code 10, 20  640×480} is the SDK's own knowledge about its own API, and stays.
 *
 * <p>Taking them off the screen is what these exist for. Nobody knows that a health bar is 240 pixels wide;
 * they know where its ends are. So {@code Rect} drags a rectangle, {@code Point} uses the magnifier, and
 * {@code Size} drags a rectangle and throws the origin away — which is exactly how a person measures
 * something on a screen.
 */
public final class GeometryEditors {

    private GeometryEditors() {}

    /** {@code 10, 20  640×480} — the reading order of a rectangle: where it is, then how big it is. */
    private static final TupleSpec RECT = new TupleSpec("Rect",
            new String[]{"x", "y", "width", "height"}, "Choose region…", Pick.REGION,
            v -> v[0] + ", " + v[1] + "  " + v[2] + "×" + v[3]);

    private static final TupleSpec POINT = new TupleSpec("Point",
            new String[]{"x", "y"}, "Choose point…", Pick.POINT,
            v -> v[0] + ", " + v[1]);

    private static final TupleSpec SIZE = new TupleSpec("Size",
            new String[]{"width", "height"}, "Choose size…", Pick.MEASURE,
            v -> v[0] + " × " + v[1]);

    // Each asks the host for the declaration it edits (ValueContext.declared): `types` names `editors`, and
    // `editors` naming `types` back would make the two one package in two places (PluginLayersTest).

    /** A {@code Rect}: drag a region on screen, or type {@code x, y, width, height}. */
    public static Node rect(ValueContext ctx) {
        return tuple(ctx, Rect.class, spec(RECT, ctx));
    }

    /** A {@code Point}: click one pixel under a magnifier, or type {@code x, y}. */
    public static Node point(ValueContext ctx) {
        return tuple(ctx, Point.class, spec(POINT, ctx));
    }

    /** A {@code Size}: measure by dragging over the thing, or type {@code width, height}. */
    public static Node size(ValueContext ctx) {
        // SIZE, never spec(SIZE, …): a size is the same inside a window and on the desktop.
        return tuple(ctx, Size.class, SIZE);
    }

    /** The tuple pill over the host's declaration of {@code type}; read-only on a host that has none to give. */
    private static <T> Node tuple(ValueContext ctx, Class<T> type, TupleSpec spec) {
        return ctx.declared(type)
                .<Node>map(declared -> Editors.tuplePill(ctx, declared, spec, SdkScreenPicks.forSlot(ctx)))
                .orElseGet(() -> Pills.bare(Slots.sourceOr(ctx, spec.placeholder())));
    }

    /**
     * A row's numbers are inside the surface it was picked on, and its pill says so; a slot's say nothing,
     * because the call it sits in already says which ({@code PickSpace}).
     */
    static TupleSpec spec(TupleSpec base, ValueContext ctx) {
        if (ctx.slot().isPresent()) return base;
        return new TupleSpec(base.title(), base.labels(), base.placeholder(), base.pick(),
                v -> base.label().apply(v) + " in window");
    }

    // Package-private rather than private: the label is the one piece of these editors that can be asserted
    // without a JavaFX toolkit, and it is the piece worth asserting — the number a user reads off the
    // collapsed pill is read back out of what the last pick wrote, and getting it wrong shows one coordinate
    // while the bot runs another. See GeometryLabelTest.
    static String rectLabel(ValueContext ctx, ComponentType<Rect> type) {
        return Editors.tupleLabel(ctx, type, spec(RECT, ctx));
    }

    static String pointLabel(ValueContext ctx, ComponentType<Point> type) {
        return Editors.tupleLabel(ctx, type, spec(POINT, ctx));
    }

    static String sizeLabel(ValueContext ctx, ComponentType<Size> type) {
        return Editors.tupleLabel(ctx, type, SIZE);
    }
}
