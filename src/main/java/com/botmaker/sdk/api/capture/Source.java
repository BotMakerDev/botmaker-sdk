package com.botmaker.sdk.api.capture;

import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.internal.capture.Desktop;
import com.botmaker.sdk.internal.capture.SessionSource;
import com.botmaker.sdk.internal.config.ProjectDefaults;
import com.botmaker.session.ActiveSession;
import com.botmaker.session.DesktopSession;

/**
 * The SDK's global, ambient <em>capture source</em> — the "where" that every no-source vision and
 * mouse call looks at. This lets bots read cleanly: {@code ImageFinder.find(button)} instead of
 * threading a {@link CaptureSource} through every call.
 *
 * <p>On first use the current source initialises to the <strong>project default source</strong> (as
 * configured in Studio and baked into the generated bot), falling back to the whole {@link Desktop}
 * when none is configured. Override it at runtime with {@link #set(CaptureSource)} — for example to
 * point the whole bot at a game {@link Window} once, up front — and every subsequent no-source call
 * follows until it is changed again.
 *
 * <p>Methods that <em>do</em> take an explicit {@link CaptureSource} always use that argument and
 * ignore this global; the global only fills in the no-source overloads.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): both are offered. A two-method facade has nothing
 * to trim, and the pair is the read and the write of one property — which is the shape the rest of this sweep
 * hides <em>arguments</em> in favour of, not one it hides.
 */
@Palette(category = "capture", categoryLabel = "Capture", icon = "🎯", order = 60)
public final class Source {

    private static volatile CaptureSource current;

    /**
     * Whether a bot has explicitly {@link #set(CaptureSource) pinned} a source. When it has, that pin wins even
     * over an {@link ActiveSession}; when it hasn't, an active session's owned window is the ambient source (and
     * otherwise the project default). {@code set(null)} clears the pin, handing control back to that automatic
     * choice.
     */
    private static volatile boolean pinned;

    private Source() {}

    /**
     * The current global capture source. When a bot drives a private {@link DesktopSession} (a nested
     * {@code :N} display) whose pixels are readable over X11 ({@link DesktopSession#x11Capturable()}) and hasn't
     * pinned a source, this is the session's owned window (a {@link SessionSource}); otherwise it initialises
     * lazily to the project default (or the whole {@link Desktop} when none is configured). Never {@code null}.
     */
    public static CaptureSource current() {
        if (!pinned) {
            DesktopSession session = ActiveSession.get();
            // A session whose pixels are not on X11 (gamescope hosting a Wayland-only client such as Waydroid)
            // would hand back a valid frame of an empty root — black, no error, and every find silently missing.
            // Falling through to the project default reaches the source that *can* see those pixels, which for a
            // Waydroid bot is its EmulatorSource over ADB.
            if (session != null && session.x11Capturable()) {
                return new SessionSource(session);
            }
        }
        CaptureSource c = current;
        if (c == null) {
            synchronized (Source.class) {
                c = current;
                if (c == null) {
                    c = resolveDefault();
                    current = c;
                }
            }
        }
        return c;
    }

    /**
     * Pin the global capture source until it is changed again — this wins even while an {@link ActiveSession}
     * is running. Passing {@code null} clears the pin: the source reverts to the active session's window if one
     * is running, else the project default (or the {@link Desktop}).
     */
    public static void set(CaptureSource source) {
        if (source == null) {
            pinned = false;
            current = resolveDefault();
        } else {
            pinned = true;
            current = source;
        }
        Debug.log("[Source] set -> " + (pinned ? current : "(auto)"));
    }

    private static CaptureSource resolveDefault() {
        CaptureSource projectDefault = ProjectDefaults.source();
        CaptureSource resolved = projectDefault != null ? projectDefault : CaptureSource.desktop();
        Debug.log("[Source] default -> " + resolved);
        return resolved;
    }
}
