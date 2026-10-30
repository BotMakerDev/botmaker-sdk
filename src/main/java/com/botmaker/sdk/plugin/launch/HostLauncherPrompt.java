package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.sdk.plugin.pictures.ResourceMove;
import com.botmaker.session.PrivateSession;
import com.botmaker.session.launch.BackgroundLauncher;
import com.botmaker.shared.launch.HostLauncherProbe;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * The offer to close a store launcher open on this desktop before a private-display launch. Steam, Heroic and
 * Faugus run one copy per user: a second start hands the game to the copy already open, which puts it on the
 * desktop. Before, the launch was refused with "close Steam and try again"; now Studio asks and closes it
 * (2026-10-19, the user's call). A bot run without Studio has no one to ask, and stops with that sentence.
 *
 * <p>A copy in another of BotMaker's private displays takes the launch the same way, to that display: one a
 * project opened earlier left running (2026-10-30, TrackMania's launch went to the Steam of Balatro's display).
 * Studio offers to close that display first.
 */
final class HostLauncherPrompt {

    private static final ButtonType CLOSE = new ButtonType("Close it and launch", ButtonBar.ButtonData.OK_DONE);

    /** How long a launcher is given to quit: Steam saves its state and ends its helpers first. */
    private static final Duration WAIT = Duration.ofSeconds(30);

    /** How often a closing display is looked at again. */
    private static final long POLL_MS = 500;

    private HostLauncherPrompt() {}

    /**
     * Runs {@code proceed} when no launcher of {@code spec}'s kind is open on this desktop or in another private
     * display, or once the user had them closed. Otherwise {@code refused} hears why and nothing launches. FX
     * thread; both run on it.
     */
    static void clearThen(StudioServices services, Window owner, LaunchSpec spec, Runnable proceed,
                          BiConsumer<Boolean, String> refused) {
        if (!HostLauncherProbe.routesThroughDaemon(spec.kind())) {
            proceed.run();
            return;
        }
        // This project's own display is not another one: its launch is refused as already running, as before.
        String ours = ownSession(services);
        Async.load("other-session-probe", () -> HostLauncherProbe.inSessions(spec).stream()
                .filter(each -> !each.session().equals(ours)).toList(), held -> {
            if (held.isEmpty()) {
                clearDesktopThen(services, owner, spec, proceed, refused);
                return;
            }
            if (!askDisplays(services, owner, held)) {
                refused.accept(false, "Not launched: " + HostLauncherProbe.sessionRefusal(spec, held));
                return;
            }
            Async.load("other-session-close", () -> closeAndWait(spec, held), closed -> {
                if (closed) clearDesktopThen(services, owner, spec, proceed, refused);
                else refused.accept(false, "Not launched: the other private display is still closing after "
                        + WAIT.toSeconds() + " s. " + HostLauncherProbe.sessionRefusal(spec, held));
            }, why -> refused.accept(false, "Couldn't close the other private display: " + why));
        }, why -> clearDesktopThen(services, owner, spec, proceed, refused));
    }

    /** {@link #clearThen}'s half for a launcher open on this desktop. */
    private static void clearDesktopThen(StudioServices services, Window owner, LaunchSpec spec, Runnable proceed,
                                         BiConsumer<Boolean, String> refused) {
        // The launcher that is open, which for a Lutris game on the Steam runner can be Steam. Off the FX thread:
        // knowing the runner can start `lutris` to list its games.
        Async.load("host-launcher-probe", () -> HostLauncherProbe.openLauncher(spec), open -> {
            if (open.isEmpty()) {
                proceed.run();
                return;
            }
            LaunchKind kind = open.get();
            if (!ask(services, owner, kind)) {
                refused.accept(false, "Not launched: " + kind.productName() + " stays open on your desktop. Close "
                        + "it, or launch on this desktop, to play.");
                return;
            }
            Async.load("host-launcher-close", () -> HostLauncherProbe.closeAndWait(kind, WAIT), closed -> {
                if (closed) proceed.run();
                else refused.accept(false, kind.productName() + " is still open: close it yourself, then launch "
                        + "again.");
            }, why -> refused.accept(false, "Couldn't close " + kind.productName() + ": " + why));
        }, why -> proceed.run());
    }

    /** Stops each session of {@code held}, and waits up to {@link #WAIT} for none to hold the launcher. Blocking. */
    private static boolean closeAndWait(LaunchSpec spec, List<HostLauncherProbe.InSession> held) {
        Set<String> sessions = held.stream().map(HostLauncherProbe.InSession::session).collect(Collectors.toSet());
        sessions.forEach(BackgroundLauncher::stopSession);
        long until = System.nanoTime() + WAIT.toNanos();
        while (HostLauncherProbe.inSessions(spec).stream().anyMatch(s -> sessions.contains(s.session()))) {
            if (System.nanoTime() > until) return false;
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /** The id of the private display this project's ▶ Launch holds, or null. */
    private static String ownSession(StudioServices services) {
        if (services.resourcesDir() == null) return null;
        PrivateSession session = BackgroundLauncher.forProject(ResourceMove.resourcesRoot(services.resourcesDir()))
                .session();
        return session == null ? null : session.sessionId();
    }

    private static boolean ask(StudioServices services, Window owner, LaunchKind kind) {
        String product = kind.productName();
        Alert alert = services.theme().alert(Alert.AlertType.CONFIRMATION, product + " runs one copy per user, so "
                + "the copy open on your desktop would take this launch and show the game there.\n\nClose "
                + product + " and launch the game in the private display? A game " + product + " runs now closes "
                + "with it.", ButtonType.CANCEL, CLOSE);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle("Private display");
        alert.setHeaderText(product + " is open on your desktop");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == CLOSE;
    }

    private static boolean askDisplays(StudioServices services, Window owner, List<HostLauncherProbe.InSession> held) {
        String product = held.getFirst().launcher().productName();
        String where = held.stream().map(HostLauncherProbe.InSession::display).filter(d -> !d.isEmpty()).distinct()
                .collect(Collectors.joining(", "));
        String display = where.isEmpty() ? "another private display" : "the private display " + where;
        Alert alert = services.theme().alert(Alert.AlertType.CONFIRMATION, product + " runs one copy per user, so "
                + "the copy in " + display + ", which BotMaker started earlier, would take this launch and show the "
                + "game there.\n\nClose that display and launch the game in a new one? The game in it closes with "
                + "it.", ButtonType.CANCEL, CLOSE);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle("Private display");
        alert.setHeaderText(product + " runs in " + display);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == CLOSE;
    }
}
