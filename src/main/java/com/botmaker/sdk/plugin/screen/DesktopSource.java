package com.botmaker.sdk.plugin.screen;

import com.botmaker.shared.capture.ScreenCapture;
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
 * on this machine's screens</em>, which is what a pick with no project behind it means. The second, <em>show
 * me what the bot looks at</em>, is {@link EditorFrame}'s, handed to the overlay as a {@link FrameShotSource}.
 *
 * <p>The pixels come from {@code botmaker-shared}'s {@link ScreenCapture}, the one desktop grab a bot and an
 * editor share, so the overlay and the bot never disagree about whether the screen is readable at all.
 */
public final class DesktopSource implements ShotSource {

    /**
     * Grabs every monitor in one pass. With a single screen the whole desktop <em>is</em> that screen, so the
     * shot is finished here; with several, the pixels go back for the FX-thread chooser to crop, because a
     * modal dialog cannot be shown from this thread. One grab and not one per monitor: under Wayland a grab
     * can be a portal confirmation, and N grabs are N dialogs.
     */
    @Override
    public Grab grab(Window owner) {
        BufferedImage desktop = ScreenCapture.captureDesktop();
        if (desktop == null) return Grab.failed();

        List<Screen> screens = Screen.getScreens();
        if (screens.size() > 1) return new Grab(null, desktop);

        Rectangle2D bounds = screens.isEmpty()
                ? Screens.virtualScreenBounds(List.of(Screen.getPrimary()))
                : screens.getFirst().getBounds();
        return new Grab(new ScreenShot(desktop, bounds, true, !EditorFrame.usable(desktop)), null);
    }
}
