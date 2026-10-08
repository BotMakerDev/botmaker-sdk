package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.internal.session.BotSession;
import com.botmaker.session.DesktopSession;
import com.botmaker.shared.capture.ScreenCapture;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * The whole virtual desktop (every monitor) as a {@link CaptureSource}. This is the ultimate
 * fallback source when no {@link Window} or {@link Monitor} is targeted and no project default is
 * configured. Prefer the factory {@link CaptureSource#desktop()} over constructing this directly.
 *
 * <p>While the bot drives a {@link BotSession} whose pixels are X11's or a VM's, the desktop is that session's
 * screen (a game VM's whole guest screen), where its clicks land too; otherwise it is this computer's.
 */
public final class Desktop implements CaptureSource {

    @Override
    public BufferedImage capture() {
        DesktopSession session = session();
        return session != null ? session.captureScreen() : ScreenCapture.captureDesktop();
    }

    @Override
    public Point origin() {
        DesktopSession session = session();
        java.awt.Point o = session != null ? session.screen().getLocation()
                : ScreenCapture.getVirtualScreenBounds().getLocation();
        return new Point(o.x, o.y);
    }

    /**
     * The bot's session when its screen is the desktop to read; {@code null} on this computer's. A session whose
     * pixels aren't on X11 (Waydroid's) is read through its own source, as {@code Source.current()} does.
     */
    private static DesktopSession session() {
        DesktopSession session = BotSession.get();
        if (session == null || !session.x11Capturable()) return null;
        Rectangle screen = session.screen();
        return screen == null ? null : session;
    }

    @Override
    public String toString() {
        return "Desktop[]";
    }
}
