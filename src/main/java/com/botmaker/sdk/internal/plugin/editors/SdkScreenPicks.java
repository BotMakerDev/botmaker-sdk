package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.toolkit.Region;
import com.botmaker.plugin.toolkit.ScreenPicks;
import com.botmaker.sdk.internal.plugin.capture.ScreenCapture;
import javafx.scene.paint.Color;

import java.util.function.Consumer;

/**
 * The overlay, as the toolkit asks for it: one {@link ScreenCapture} per pick, over this machine's screens.
 *
 * <p>Over the screens and not over the project's capture target, deliberately. These three are the picks a
 * widget makes with nothing but a slot in hand — a region, a coordinate, a colour — and a slot does not say
 * which project it belongs to. An editor that wants the bot's own frame asks {@code EditorFrame} for it by
 * name, which is what the colour and picture editors do.
 *
 * <h2>Why it is a class of its own, and why it is reached through {@link #get()}</h2>
 *
 * <p>It was a private nested class of {@code SdkPlugin}, registered once through the toolkit's
 * {@code Editors.pickWith(…)} — <b>one static field shared by every plugin in the process, last writer
 * winning silently</b>. That static is deleted; the picker is an argument to the widget that needs it.
 *
 * <p>So it is held here instead, and held lazily, for the reason {@code SdkPlugin}'s constructor javadoc
 * gives at length: {@code ScreenPicks} is JavaFX-typed and {@code javafx-controls} is {@code optional} in
 * this module, so <b>linking it from a constructor makes the plugin unconstructible on a headless host</b>
 * — the CLI's {@code validate}, the registry's CI. Reaching it through a method means only a host that is
 * drawing an editor ever loads it, and such a host has JavaFX by definition.
 */
public final class SdkScreenPicks implements ScreenPicks {

    private static volatile ScreenPicks instance;

    private SdkScreenPicks() {
    }

    /**
     * The picker every widget in this plugin uses.
     *
     * <p>A plain double-checked read: a second instance under a race costs one allocation and nothing else,
     * because this holds no state — each pick opens its own overlay.
     */
    public static ScreenPicks get() {
        ScreenPicks local = instance;
        if (local == null) {
            local = new SdkScreenPicks();
            instance = local;
        }
        return local;
    }

    @Override
    public void region(Consumer<Region> onSelected) {
        new ScreenCapture().selectRegion(null,
                r -> onSelected.accept(new Region(r[0], r[1], r[2], r[3])));
    }

    @Override
    public void point(Consumer<Region> onPicked) {
        // A Region with no size: the toolkit has one coordinate type, and a point is a region whose width
        // and height are nobody's business.
        new ScreenCapture().pickPoint(null, p -> onPicked.accept(new Region(p[0], p[1], 0, 0)));
    }

    @Override
    public void color(Consumer<Color> onSampled) {
        new ScreenCapture().pickColor(null, pick -> {
            java.awt.Color c = pick.color();
            onSampled.accept(Color.rgb(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha() / 255.0));
        });
    }
}
