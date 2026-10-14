package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.RunProperty;
import com.botmaker.session.PrivateSession;
import com.botmaker.session.SessionBackend;
import com.botmaker.session.Sessions;
import com.botmaker.session.impl.AdoptedSession;
import com.botmaker.session.launch.BackgroundLauncher;

import java.util.List;

/**
 * Offers the private display a launch brought up to every later run, so the bot joins it instead of starting a
 * second one, which the single-instance launcher would hand to this one (Heroic refused it outright, 2026-10-13).
 *
 * <p><b>Run properties, not the host's hand-off.</b> Studio offers its own {@code BackgroundLauncher}'s session to
 * a run, but this plugin's {@code BackgroundLauncher} is another class on another loader, and since ▶ Launch moved
 * into the plugin it is the one holding the session: Studio's side offered nothing. A run property reaches the run
 * as the same {@code -D} the hand-off writes ({@link Sessions#handoffArguments}). One left behind by a session that
 * has gone is declined by the run, which adopts only a display that still answers.
 */
public final class SessionHandoff {

    private static final RunProperty<String> DISPLAY = RunProperty.text(AdoptedSession.DISPLAY_PROPERTY);
    private static final RunProperty<String> WINDOW = RunProperty.text(AdoptedSession.WINDOW_PROPERTY);
    private static final RunProperty<String> BACKEND = RunProperty.text(AdoptedSession.BACKEND_PROPERTY);
    private static final List<RunProperty<String>> PROPERTIES = List.of(DISPLAY, WINDOW, BACKEND);

    private SessionHandoff() {}

    /** Offers {@code session} to every later run; {@code null} withdraws the offer. FX thread. */
    public static void offer(StudioServices services, PrivateSession session) {
        if (services == null) return;
        List<String> args = Sessions.handoffArguments(session);
        for (RunProperty<String> property : PROPERTIES) services.runs().set(property, value(args, property.name()));
    }

    /**
     * The session {@code launcher} holds, or else the live display the offer still names, joined: this plugin reloaded
     * in place (a local build tried over MCP) starts with an empty holder while the game its old copy launched keeps
     * running there. Null when neither.
     */
    public static PrivateSession rejoin(StudioServices services, BackgroundLauncher launcher) {
        PrivateSession held = launcher.session();
        if (held != null || services == null) return held;
        String display = services.runs().get(DISPLAY).orElse(null);
        if (display == null || display.isBlank()) return null;
        String window = services.runs().get(WINDOW).orElse(null);
        SessionBackend backend = services.runs().get(BACKEND).flatMap(SessionBackend::fromId)
                .orElse(SessionBackend.GAMESCOPE);
        PrivateSession joined = launcher.adopt(display, window, backend);
        // A display that has gone is withdrawn, so the next look does not try to join it again.
        if (joined == null) offer(services, null);
        return joined;
    }

    /** What {@code -D<name>=<value>} among {@code args} sets, or null. */
    static String value(List<String> args, String name) {
        String prefix = "-D" + name + "=";
        return args.stream().filter(a -> a.startsWith(prefix)).map(a -> a.substring(prefix.length())).findFirst()
                .orElse(null);
    }
}
