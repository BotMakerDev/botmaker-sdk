package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.shared.launch.GameReadiness;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.UriLauncher;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Whether the game can start, asked before Studio launches it ({@link GameReadiness}): a download its launcher runs
 * is waited out, a signed-out launcher is offered to open for signing in, a game not installed is offered to
 * install. A run without Studio stops with the same reason instead (2026-10-19).
 */
public final class ReadinessPrompt {

    private static final ButtonType SIGN_IN = new ButtonType("Open Steam", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType INSTALL = new ButtonType("Install it", ButtonBar.ButtonData.OK_DONE);

    private ReadinessPrompt() {}

    /**
     * Runs {@code proceed} once {@code spec}'s game can start; {@code progress} hears a wait or a note. Otherwise
     * {@code refused} hears why, after the user's choice. FX thread; all three run on it.
     */
    static void clearThen(StudioServices services, Window owner, LaunchSpec spec, QuickLaunch.Report progress,
                          Runnable proceed, BiConsumer<Boolean, String> refused) {
        Async.load("game-readiness", () -> GameReadiness.of(spec), readiness -> {
            switch (readiness.state()) {
                case READY, UNKNOWN -> proceed.run();
                case UPDATE_PENDING -> {
                    progress.accept(true, readiness.reason());
                    proceed.run();
                }
                case UPDATING -> {
                    progress.accept(true, readiness.reason() + " Waiting for it to finish…");
                    Async.load("game-readiness-wait", () -> {
                        GameReadiness.awaitLaunchable(spec, GameReadiness.UPDATE_WAIT,
                                line -> Platform.runLater(() -> progress.accept(true, line)));
                        return true;
                    }, done -> proceed.run(), why -> refused.accept(false, why));
                }
                case SIGNED_OUT -> refused.accept(false, signIn(services, owner, spec, readiness));
                case NOT_INSTALLED -> refused.accept(false, install(services, owner, spec, readiness));
            }
        }, why -> proceed.run());
    }

    private static String signIn(StudioServices services, Window owner, LaunchSpec spec, GameReadiness readiness) {
        Optional<String> link = GameReadiness.signInLink(spec.kind());
        if (link.isEmpty() || !ask(services, owner, "Signed out", readiness.reason() + "\n\nOpen Steam to sign in?",
                SIGN_IN)) {
            return "Not launched: " + readiness.reason();
        }
        return UriLauncher.open(link.get()) ? "Sign in to Steam, ticking Remember me, then launch again."
                : "Couldn't open Steam: start it yourself and sign in, then launch again.";
    }

    private static String install(StudioServices services, Window owner, LaunchSpec spec, GameReadiness readiness) {
        Optional<String> link = GameReadiness.installLink(spec);
        if (link.isEmpty() || !ask(services, owner, "Not installed", readiness.reason() + "\n\nInstall it in Steam?",
                INSTALL)) {
            return "Not launched: " + readiness.reason();
        }
        return install(spec);
    }

    /**
     * Has Steam install {@code spec}'s game, and says what follows: Steam shows its own install dialog and
     * downloads it. Any thread: opening a link spawns the system's opener and returns.
     */
    public static String install(LaunchSpec spec) {
        Optional<String> link = GameReadiness.installLink(spec);
        if (link.isEmpty()) return spec.describe() + " has no installer Studio can open: install it in its launcher.";
        return UriLauncher.open(link.get())
                ? "Steam is installing " + spec.describe() + ": launch it once the download is done."
                : "Couldn't open Steam: install " + spec.describe() + " from Steam yourself.";
    }

    private static boolean ask(StudioServices services, Window owner, String title, String text, ButtonType yes) {
        Alert alert = services.theme().alert(Alert.AlertType.CONFIRMATION, text, ButtonType.CANCEL, yes);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == yes;
    }
}
