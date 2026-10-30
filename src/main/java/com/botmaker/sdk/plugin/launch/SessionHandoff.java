package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.RunProperty;
import com.botmaker.session.PrivateSession;
import com.botmaker.session.SessionBackend;
import com.botmaker.session.Sessions;
import com.botmaker.session.impl.AdoptedSession;
import com.botmaker.session.launch.BackgroundLauncher;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.application.Platform;

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
    /**
     * The launch target the offered display was brought up for: once the bot's target changes, that display runs
     * another game, and a launch must not take it for this one (2026-10-30, Firestone's display still held Sword x
     * Staff).
     */
    private static final RunProperty<String> TARGET = RunProperty.text("botmaker.session.target");

    private SessionHandoff() {}

    /**
     * Offers {@code session}, brought up for {@code launched}, to every later run; a {@code null} session withdraws
     * the offer. FX thread.
     */
    public static void offer(StudioServices services, PrivateSession session, LaunchSpec launched) {
        if (services == null) return;
        List<String> args = Sessions.handoffArguments(session);
        for (RunProperty<String> property : PROPERTIES) services.runs().set(property, value(args, property.name()));
        services.runs().set(TARGET, session == null || launched == null ? null : launched.spec());
    }

    /**
     * Whether the display offered runs another target than {@code wanted}. One with no target recorded (offered
     * before 2026-10-30) counts as another: nothing says which game it holds, and relaunching once costs less than
     * a bot looking at the wrong game.
     */
    public static boolean runsOther(StudioServices services, LaunchSpec wanted) {
        return services != null && differs(services.runs().get(TARGET).orElse(null), wanted);
    }

    static boolean differs(String launched, LaunchSpec wanted) {
        return launched == null || launched.isBlank() || !launched.equals(wanted.spec());
    }

    /**
     * The session {@code launcher} holds, or else the live display the offer still names, joined: this plugin reloaded
     * in place (a local build tried over MCP) starts with an empty holder while the game its old copy launched keeps
     * running there. Null when neither.
     */
    public static PrivateSession rejoin(StudioServices services, BackgroundLauncher launcher) {
        PrivateSession held = launcher.session();
        if (held != null && services != null) keepWindow(services, held);
        if (held != null || services == null) return held;
        String display = services.runs().get(DISPLAY).orElse(null);
        if (display == null || display.isBlank()) return null;
        String window = services.runs().get(WINDOW).orElse(null);
        SessionBackend backend = services.runs().get(BACKEND).flatMap(SessionBackend::fromId)
                .orElse(SessionBackend.GAMESCOPE);
        PrivateSession joined = launcher.adopt(display, window, backend);
        // A display that has gone is withdrawn, so the next look does not try to join it again.
        if (joined == null) offer(services, null, null);
        return joined;
    }

    /**
     * Offers the window {@code held} is attached to now. A store launcher's game is attached first to the launcher's
     * own window, then to the game's when it opens; the offer kept the first, which closes, so a plugin reloaded in
     * place could not join the display (2026-10-30, Balatro through Steam).
     */
    private static void keepWindow(StudioServices services, PrivateSession held) {
        String now = value(Sessions.handoffArguments(held), WINDOW.name());
        if (now == null || now.equals(services.runs().get(WINDOW).orElse(null))) return;
        Platform.runLater(() -> services.runs().set(WINDOW, now));
    }

    /** What {@code -D<name>=<value>} among {@code args} sets, or null. */
    static String value(List<String> args, String name) {
        String prefix = "-D" + name + "=";
        return args.stream().filter(a -> a.startsWith(prefix)).map(a -> a.substring(prefix.length())).findFirst()
                .orElse(null);
    }
}
