package com.botmaker.sdk.internal.plugin.capture;

import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.capture.NativeControllerFactory;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.function.BooleanSupplier;

/**
 * Keeps a plugin's own overlay stage stacked above a fullscreen game.
 *
 * <p>Moved out of Studio's {@code ui/app/overlay/OverlayToolbars} on 2026-08-30 with the two capture
 * surfaces that call it. It is here rather than in {@code botmaker-plugin-toolkit} because the raise itself
 * is {@code botmaker-shared}'s — {@link NativeControllerFactory} — and the toolkit may not name a BotMaker
 * upstream other than the contract. It is not on the contract either, and deliberately: any plugin can
 * depend on shared and do this for itself, so the host is not the only possible source.
 */
public final class OverlayStage {

    private OverlayStage() {}

    /**
     * Ask the window manager to stack {@code stage} <em>above fullscreen</em> windows. A JavaFX
     * {@code setAlwaysOnTop} stage still hides behind a fullscreen game (its {@code _NET_WM_STATE_ABOVE} loses
     * to {@code _NET_WM_STATE_FULLSCREEN}); the native layer promotes it via notification window-type + raise.
     *
     * <p>Bridges JavaFX→native by a unique window <b>title</b> (invisible on a transparent stage): we tag the
     * stage, then the native controller finds the matching X11 client window and applies the EWMH hints.
     * Best-effort — a no-op on Windows/Wayland or a WM that ignores the hints. Re-asserted on focus <em>and</em>
     * on a low-frequency timer so it survives a capture-surface toggle or the game re-fullscreening/re-raising
     * itself; the first promotion remaps the window once, later ticks are the cheap raise path (no flicker).
     * Safe to call on any {@link Stage}.
     */
    public static void promoteAboveFullscreen(Stage stage) {
        promoteAboveFullscreen(stage, () -> true);
    }

    /**
     * As {@link #promoteAboveFullscreen(Stage)}, but the periodic re-raise is skipped while {@code enabled}
     * returns false.
     *
     * <p>This exists because the re-assert is what makes two promoted overlays unstackable: an overlay HUD
     * raising itself every 750 ms shoves a second window opened <em>from</em> it back underneath within the
     * second, no matter where it was placed or how it was promoted. The owner it should logically have is not
     * an option either: JavaFX hides owned windows with their owner, and such a HUD is deliberately hidden
     * while a capture surface is up, with the second window kept alive to host it. So the HUD stands down
     * instead, for as long as the other window is open.
     */
    public static void promoteAboveFullscreen(Stage stage, BooleanSupplier enabled) {
        String existing = stage.getTitle();
        final String title = (existing == null || existing.isEmpty())
                ? "__bm_overlay_" + Long.toHexString(System.nanoTime()) : existing;
        if (existing == null || existing.isEmpty()) stage.setTitle(title);
        Runnable promote = () -> {
            try {
                NativeControllerFactory.get().promoteOverlayAboveFullscreen(title);
            } catch (Throwable ignored) {
                // best-effort; the overlay still shows (just possibly under a fullscreen window)
            }
        };
        // Defer so the native window/title exists, and re-assert whenever the overlay regains focus.
        Platform.runLater(promote);
        stage.focusedProperty().addListener((o, was, now) -> { if (now) promote.run(); });
        // Continuously re-assert while shown — defends against the fullscreen app re-raising itself.
        Timeline keepOnTop = new Timeline(new KeyFrame(javafx.util.Duration.millis(750), e -> {
            if (enabled.getAsBoolean()) promote.run();
        }));
        keepOnTop.setCycleCount(Animation.INDEFINITE);
        keepOnTop.play();
        // Stop when the overlay is no longer showing (an additive listener — won't clobber a caller's onHidden).
        stage.showingProperty().addListener((o, was, showing) -> { if (!showing) keepOnTop.stop(); });
    }

    /** The translucent pill every overlay panel and mini-toolbar is drawn on. */
    public static final String PANEL = "-fx-background-color: rgba(20,24,33,0.92); -fx-background-radius: 8;";

    /**
     * Shows {@code bar} as a small floating toolbar just above {@code over}, and returns its stage.
     *
     * <p>Three properties this centralises, all of which a capture tool needs and none of which are the
     * default:
     * <ul>
     *   <li><b>Draggable by its body.</b> A button consumes its own mouse press, so the drag only ever starts
     *       from the bar or a label — which is what lets a toolbar be moved without a title bar to grab.</li>
     *   <li><b>Ownerless.</b> Deliberately not owned by any host window: JavaFX hides an owned window with
     *       its owner, and the user must be able to minimise the editor and go on capturing.</li>
     *   <li><b>Unthemed.</b> {@link Styles#UNTHEMED} opts the bar out of the host's chrome — it paints its
     *       own pill over a live game, and a scene background from the shell would show up around it as an
     *       opaque rectangle.</li>
     * </ul>
     *
     * <p>It is tucked <em>inside</em> the top of {@code over} when there is no room above it, so a target at
     * the top edge of the screen does not put its toolbar off-screen.
     */
    public static Stage bar(Region bar, java.awt.Rectangle over) {
        bar.getStyleClass().add(Styles.UNTHEMED);
        Scene scene = new Scene(bar, Color.TRANSPARENT);
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setScene(scene);
        installDrag(bar, stage);
        stage.show();
        stage.sizeToScene();
        double height = stage.getHeight();
        stage.setX(over.x);
        stage.setY(over.y - height - 4 >= 0 ? over.y - height - 4 : over.y + 4);
        promoteAboveFullscreen(stage);
        return stage;
    }

    /** Makes dragging on {@code handle} move {@code stage} (tracks the press offset from the stage origin). */
    public static void installDrag(Node handle, Stage stage) {
        final double[] offset = new double[2];
        handle.setOnMousePressed(e -> {
            offset[0] = e.getScreenX() - stage.getX();
            offset[1] = e.getScreenY() - stage.getY();
        });
        handle.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - offset[0]);
            stage.setY(e.getScreenY() - offset[1]);
        });
    }
}
