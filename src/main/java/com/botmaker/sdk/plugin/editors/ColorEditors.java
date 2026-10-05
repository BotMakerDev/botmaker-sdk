package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.screen.ColorSampler;
import com.botmaker.sdk.plugin.screen.EditorFrame;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import com.botmaker.sdk.plugin.source.SurfaceMenu;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
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
 * <p><b>The eyedropper asks where first and has two paths.</b> The user picks a surface
 * ({@link SurfaceMenu}: the bot's source, another window or screen, the whole desktop), and a frozen frame of
 * it opens {@link ColorSampler} — a loupe, and the ΔE spread of the surrounding patch, which is the only
 * honest suggestion a tolerance has ever had. When no frame comes back it says why and falls back to a live
 * pick off the screen ({@code ScreenCapture.pickColor}), so a project with no capture source still has a
 * working eyedropper and nobody is sent to a dialog before they can answer the question in front of them.
 *
 * <p>A value this editor cannot read — a variable, a call — is shown as
 * written beside the swatch, which otherwise sits at its default and would claim a colour the bot does not
 * use.
 *
 * <p><b>It replaced two editors that had drifted.</b> Studio drew a slot's colour with a swatch and a frozen
 * sampler, and a Parameters row's colour with a swatch and the live screen pick — same value, same question,
 * two answers, and only one of them offered the ΔE reading. One editor over a {@code ValueContext} serves
 * both places, which is what the contract's {@code ValueContext} was for.
 */
public final class ColorEditors {

    private ColorEditors() {}

    /** The editor for a {@code java.awt.Color}: a swatch and an eyedropper, in both places a value is edited. */
    public static Node color(ValueContext ctx) {
        ColorPicker picker = new ColorPicker();
        picker.getStyleClass().add("color-arg-picker");
        Color initial = current(ctx);
        if (initial != null) picker.setValue(initial);

        HBox box = new HBox(4);
        box.setAlignment(Pos.CENTER_LEFT);
        String unread = unreadSource(ctx);
        Label written = unread == null ? null : Styles.on(new Label(unread), Styles.CAPTION);
        if (written != null) {
            written.setTooltip(new Tooltip("Not a colour this editor can read. A pick replaces it."));
            box.getChildren().add(written);
        }

        // A ColorPicker fires its action whenever its value changes, the eyedropper's setValue included, so
        // this is where a colour is written; the eyedropper commits itself only for an unchanged value, which
        // fires no action.
        picker.setOnAction(e -> {
            commit(ctx, picker.getValue());
            box.getChildren().remove(written);
        });

        Button eyedropper = Pills.button("⌖", () -> pick(ctx, picked -> {
            if (picked.equals(picker.getValue())) {
                commit(ctx, picked);
                box.getChildren().remove(written);
            } else {
                picker.setValue(picked);
            }
        }));
        eyedropper.setTooltip(new Tooltip("Pick a colour off a window, a screen or the desktop"));
        eyedropper.getStyleClass().add("color-eyedropper");

        box.getChildren().addAll(picker, eyedropper);
        return box;
    }

    /** The expression to show beside the swatch when the value is one this editor cannot read, else null. */
    static String unreadSource(ValueContext ctx) {
        return current(ctx) == null && !Slots.isEmpty(ctx) ? Slots.raw(ctx) : null;
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
        // The user says where first (SurfaceMenu), so the loupe and the patch's ΔE spread
        // work on any window, screen or the desktop, not only on the bot's own source.
        SurfaceMenu.choose(services, surface -> {
            java.util.function.Consumer<EditorFrame> sample = frame -> ColorSampler.openOn(services, frame,
                    s -> onPicked.accept(fx(s.color())));
            java.util.function.Consumer<EditorFrame.Failure> fail =
                    failure -> fallBackToTheScreen(services, failure, onPicked);
            if (surface.botsOwn()) EditorFrame.grabAsync(services, sample, fail);
            else EditorFrame.grabAsync(services, surface.source(), sample, fail);
        });
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
        new ScreenCapture().pickColor(Modals.owner(services), pick -> {
            java.awt.Color c = pick.color();
            onPicked.accept(Color.rgb(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha() / 255.0));
        });
    }

    // commit and current are package-private rather than private for the reason GeometryEditors' label
    // helpers are: they are the halves of this editor that can be asserted without a JavaFX toolkit, and
    // they are the halves worth asserting — what is written is what the bot compiles, and what is read is
    // what the user sees claimed about a value they may not have set. See ColorEditorTest.

    /**
     * Writes the colour.
     *
     * <p>The value, not an expression: the host spells it through the {@code ComponentType} plugin-basics
     * declares for {@code java.awt.Color}, which writes the three components rather than
     * {@code Color.decode("#…")} — {@code decode} parses at class-initialisation time and can throw, and a
     * bot must not fail to start over its own configuration.
     *
     * <p>This editor <b>overrides</b> that plugin's own swatch, through {@code SlotEditor.forType}, because
     * it can sample a frozen frame of the capture target and that one cannot: the target's list is this
     * plugin's own file, and no code reads another plugin's file. Declaring the type and drawing it are
     * separate questions, which is why one plugin can answer the second for a type the other owns.
     */
    static void commit(ValueContext ctx, Color colour) {
        if (colour == null) return;
        ctx.set(new java.awt.Color(channel(colour.getRed()), channel(colour.getGreen()),
                channel(colour.getBlue())));
    }

    /**
     * The colour the value currently holds, or {@code null} to leave the swatch at its default.
     *
     * <p>A value may hold a named constant, a variable or a call — things this editor did not write — and
     * seeding the swatch from them is not possible, so it stays at its default and is overwritten on the
     * first pick. Showing a colour this editor cannot round-trip would claim a value the user never set.
     */
    static Color current(ValueContext ctx) {
        return ctx.value(java.awt.Color.class).map(ColorEditors::fx).orElse(null);
    }

    private static Color fx(java.awt.Color colour) {
        return Color.rgb(colour.getRed(), colour.getGreen(), colour.getBlue());
    }

    private static int channel(double zeroToOne) {
        return clamp((int) Math.round(zeroToOne * 255));
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }
}
