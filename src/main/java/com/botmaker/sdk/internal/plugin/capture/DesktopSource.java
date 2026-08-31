package com.botmaker.sdk.internal.plugin.capture;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Window;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The {@link ShotSource} that names no capture target — <b>the whole desktop, and the monitor chooser when
 * there is more than one.</b>
 *
 * <p>There are two questions an editor-time capture can be asked, and this is the first: <em>show me what is
 * on this machine's screens</em>, which is what a pick with no project behind it means — a colour, a
 * coordinate, a region cropped out of whatever the user can see. The second, <em>show me what the bot looks
 * at</em>, is {@link EditorFrame}'s; it resolves {@code capture.json} and may raise a window first, and it is
 * asked for by name rather than through this interface because it answers asynchronously and reports its
 * failures in sentences a user can act on.
 *
 * <p>The overlay consumes a {@link ScreenShot} and asks nothing about where it came from, which is what let
 * it move out of the editor at all.
 *
 * <p>Stateless, so a single instance would do; it is constructed per {@link ScreenOverlay} because the
 * interface is the seam and a seam with an implicit singleton behind it reads as though it had state.
 */
public final class DesktopSource implements ShotSource {

    /**
     * Grabs every monitor in one pass and decides whether the frame is usable as it stands.
     *
     * <p>One grab and not one per monitor, for the reason {@link DesktopGrab} exists at all: under Wayland a
     * grab goes through the portal, and N grabs are N confirmation dialogs. With a single screen the whole
     * desktop <em>is</em> that screen, so the shot is finished here; with several, the pixels go back for the
     * FX-thread chooser to crop, because a modal dialog cannot be shown from this thread.
     */
    @Override
    public Grab grab(Window owner) {
        BufferedImage desktop = DesktopGrab.grabVirtualDesktop();
        if (desktop == null) return Grab.failed();

        List<Screen> screens = Screen.getScreens();
        if (screens.size() > 1) return new Grab(null, desktop);

        Rectangle2D bounds = screens.isEmpty()
                ? Screens.virtualScreenBounds(List.of(Screen.getPrimary()))
                : screens.get(0).getBounds();
        return new Grab(new ScreenShot(desktop, bounds, true, DesktopGrab.looksBlank(desktop)), null);
    }

    /**
     * Always {@code null}: a desktop grab came from no window, so a picture captured through it records no
     * window title. {@link EditorFrame} answers it from the capture target it resolved.
     */
    @Override
    public String title() {
        return null;
    }
}
