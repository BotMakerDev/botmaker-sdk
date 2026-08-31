package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.ValueContext;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.sdk.authoring.WireText;
import com.botmaker.sdk.internal.plugin.capture.ColorSampler;
import com.botmaker.sdk.internal.plugin.capture.EditorFrame;
import com.botmaker.sdk.internal.plugin.capture.ScreenCapture;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

/**
 * A colour — as a palette swatch, and as an eyedropper, which is the one that gets used.
 *
 * <p>The palette answers <em>which colour do I want</em>. A bot author does not want a colour: they have a
 * pixel on screen and need the value that matches it, and guessing that off a wheel is hopeless because game
 * art is shaded, compressed and anti-aliased — the red of a health bar is never {@code Color.RED}. So the
 * swatch is there for the rare case where somebody does know, and everything else about this editor is about
 * getting the number off a real image.
 *
 * <p><b>The eyedropper has two paths and the better one needs a capture target.</b> With one configured it
 * opens {@link ColorSampler} over a frozen frame of the target — a loupe, and the ΔE spread of the
 * surrounding patch, which is the only honest suggestion a tolerance has ever had. Without one it falls back
 * to the host's live screen pick ({@code Capture.sampleColor}), which is what Studio's Parameters window did
 * before this editor existed. The fallback matters more than it looks: it means a project that has not set a
 * capture target still has a working eyedropper, so this editor never has to send anybody to a dialog before
 * they can answer the question in front of them.
 *
 * <p><b>It replaced two editors that had drifted.</b> Studio drew a slot's colour with a swatch and a frozen
 * sampler, and a Parameters row's colour with a swatch and the live screen pick — same value, same question,
 * two answers, and only one of them offered the ΔE reading. One editor over a {@code ValueContext} serves
 * both places, which is what the contract's {@code ValueContext} was for.
 */
final class ColorEditors {

    private ColorEditors() {}

    /** The editor for a {@code java.awt.Color}: a swatch and an eyedropper, in both places a value is edited. */
    static Node color(ValueContext ctx) {
        ColorPicker picker = new ColorPicker();
        picker.getStyleClass().add("color-arg-picker");
        Color initial = current(ctx);
        if (initial != null) picker.setValue(initial);

        picker.setOnAction(e -> commit(ctx, picker.getValue()));

        Button eyedropper = Pills.button("⌖", () -> pick(ctx, picked -> {
            picker.setValue(picked);
            commit(ctx, picked);
        }));
        eyedropper.setTooltip(new Tooltip("Pick a colour off a frame of the game"));
        eyedropper.getStyleClass().add("color-eyedropper");

        HBox box = new HBox(4, picker, eyedropper);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * Runs whichever eyedropper this project can offer, and reports the pixel on the JavaFX thread.
     *
     * <p>The frozen-frame sampler is tried first and the live screen pick is the fallback, rather than the
     * other way round, because the frozen frame is the only one that can also say how much the patch varies —
     * and because sampling the live screen while the game is behind the editor picks the editor's own pixels.
     */
    private static void pick(ValueContext ctx, java.util.function.Consumer<Color> onPicked) {
        StudioServices services = ctx.services();
        EditorFrame.grabAsync(services,
                frame -> ColorSampler.openOn(services, frame,
                        sample -> onPicked.accept(fx(sample.color()))),
                failure -> fallBackToTheScreen(services, failure, onPicked));
    }

    /**
     * No frame of the target, so pick off the screen instead — and say so, once, rather than silently
     * sampling something else.
     *
     * <p>The distinction {@link EditorFrame.Failure} draws is what makes this worth a sentence: *no target
     * configured* and *the target came back blank* send a user to two different places, and on a Wayland
     * session the second happens to targets that are configured perfectly well.
     */
    private static void fallBackToTheScreen(StudioServices services, EditorFrame.Failure failure,
                                            java.util.function.Consumer<Color> onPicked) {
        Alert alert = services.theme().alert(Alert.AlertType.INFORMATION);
        alert.setTitle("No frame to sample");
        alert.setHeaderText(failure.headline());
        alert.setContentText(failure.detail() + "\n\nPicking off the screen instead — the game has to be "
                + "visible, and there is no magnifier or tolerance reading on this path.");
        alert.showAndWait();
        new ScreenCapture().pickColor(services.dialogs().owner(), pick -> {
            java.awt.Color c = pick.color();
            onPicked.accept(Color.rgb(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha() / 255.0));
        });
    }

    // commit and current are package-private rather than private for the reason GeometryEditors' label
    // helpers are: they are the halves of this editor that can be asserted without a JavaFX toolkit, and
    // they are the halves worth asserting — what is written is what the bot compiles, and what is read is
    // what the user sees claimed about a value they may not have set. See ColorEditorTest.

    /** Writes the colour: the constructor call in a slot, the hex in a stored row. */
    static void commit(ValueContext ctx, Color colour) {
        if (colour == null) return;
        int r = channel(colour.getRed());
        int g = channel(colour.getGreen());
        int b = channel(colour.getBlue());
        // Fully qualified in the expression and named again as the import, which is the combination the
        // contract documents as always safe. The components rather than Color.decode("#…"): decode parses at
        // class-initialisation time and can throw, and a bot must not fail to start over its own configuration.
        Slots.write(ctx, "new java.awt.Color(" + r + ", " + g + ", " + b + ")", hex(r, g, b), "java.awt.Color");
    }

    /**
     * The colour the value currently holds, or {@code null} to leave the swatch at its default.
     *
     * <p>The two halves answer differently and both are right. A <b>slot</b> may hold a named constant, a
     * variable or a call — things this editor did not write — and seeding the swatch from them is not
     * possible, so it stays at its default and is overwritten on the first pick. A <b>row</b> always holds
     * text, and {@link WireText#color} is total: unreadable text reads as white, which is what the project
     * file means by it and what the running bot will use.
     */
    static Color current(ValueContext ctx) {
        if (ctx.asSlot() == null) return fx(WireText.color(ctx.single()));
        if (!Slots.holdsNumbers(ctx, 3)) return null;
        int[] rgb = Slots.ints(ctx, 3);
        return Color.rgb(clamp(rgb[0]), clamp(rgb[1]), clamp(rgb[2]));
    }

    private static Color fx(java.awt.Color colour) {
        return Color.rgb(colour.getRed(), colour.getGreen(), colour.getBlue());
    }

    private static String hex(int r, int g, int b) {
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private static int channel(double zeroToOne) {
        return clamp((int) Math.round(zeroToOne * 255));
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }
}
