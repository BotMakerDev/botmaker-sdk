package com.botmaker.sdk.internal.plugin.capture;

import com.botmaker.shared.capture.NativeControllerFactory;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.stage.Stage;

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
}
