package com.botmaker.sdk.plugin.screen;

import javafx.stage.Window;

/**
 * Where {@link ScreenOverlay} gets its pixels — the one thing the overlay needs from whoever knows what a
 * capture target is.
 *
 * <p>Two implementations: {@link DesktopSource} grabs this machine's screens, and {@link FrameShotSource}
 * hands over a frame {@link EditorFrame} already grabbed. The overlay knows neither.
 */
public interface ShotSource {

    /**
     * Resolves the capture target and grabs its pixels. <b>Blocking — call off the FX thread only.</b>
     *
     * @param owner the window a chooser or a warning would be owned by; may be {@code null}
     */
    Grab grab(Window owner);
}
