package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.shared.launch.HostLauncherProbe;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.time.Duration;
import java.util.function.BiConsumer;

/**
 * The offer to close a store launcher open on this desktop before a private-display launch. Steam, Heroic and
 * Faugus run one copy per user: a second start hands the game to the copy already open, which puts it on the
 * desktop. Before, the launch was refused with "close Steam and try again"; now Studio asks and closes it
 * (2026-10-19, the user's call). A bot run without Studio has no one to ask, and stops with that sentence.
 */
final class HostLauncherPrompt {

    private static final ButtonType CLOSE = new ButtonType("Close it and launch", ButtonBar.ButtonData.OK_DONE);

    /** How long a launcher is given to quit: Steam saves its state and ends its helpers first. */
    private static final Duration WAIT = Duration.ofSeconds(30);

    private HostLauncherPrompt() {}

    /**
     * Runs {@code proceed} when no launcher of {@code spec}'s kind is open on this desktop, or once the user had it
     * closed. Otherwise {@code refused} hears why and nothing launches. FX thread; both run on it.
     */
    static void clearThen(StudioServices services, Window owner, LaunchSpec spec, Runnable proceed,
                          BiConsumer<Boolean, String> refused) {
        if (!HostLauncherProbe.routesThroughDaemon(spec.kind())) {
            proceed.run();
            return;
        }
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
}
