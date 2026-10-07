package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.session.display.BackendInstall;
import com.botmaker.session.display.SessionBackends;
import com.botmaker.session.SessionBackend;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Window;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The offer to install a private display's backend that isn't there: the system's password prompt
 * ({@code pkexec}) and the package manager, or, on an image-based system, the command to copy.
 *
 * <p>Studio's rpm and deb recommend both backends, so this is for a user who removed one or installed Studio
 * another way. Declining changes nothing: a run that needs the backend then stops and says so.
 */
public final class BackendInstallPrompt {

    private static final ButtonType INSTALL = new ButtonType("Install", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType COPY = new ButtonType("Copy command", ButtonBar.ButtonData.OK_DONE);

    private BackendInstallPrompt() {}

    /**
     * Asks, then installs {@code backend} off the FX thread. {@code done} hears, on the FX thread, one sentence
     * on what happened and whether the backend is now there; it is not called when the user cancels.
     */
    public static void offer(StudioServices services, Window owner, SessionBackend backend,
                             Done done) {
        String name = backend.binaryName();
        Optional<BackendInstall> install = BackendInstall.forBackend(backend);
        if (install.isEmpty()) {
            inform(services, owner, name, "This system's package manager isn't one BotMaker knows. To run the "
                    + "game in a private display, " + SessionBackends.installHint(backend) + ".");
            return;
        }
        BackendInstall how = install.get();
        if (!how.runsHere()) {
            String why = how.needsReboot()
                    ? "This system installs packages into a new image, so run this in a terminal and restart:"
                    : "pkexec isn't installed, so BotMaker can't ask for your password. Run this in a terminal:";
            Alert alert = alert(services, owner, Alert.AlertType.INFORMATION, why + "\n\n" + how.describe(), COPY,
                    ButtonType.CLOSE);
            alert.setHeaderText(name + " isn't installed");
            if (alert.showAndWait().orElse(ButtonType.CLOSE) == COPY) copy(how.describe());
            return;
        }
        if (INSTALLING.get()) {
            done.accept(false, "An install is already running — wait for it to finish.");
            return;
        }
        Alert ask = alert(services, owner, Alert.AlertType.CONFIRMATION, "The game runs in a private display "
                + "only with " + name + ". BotMaker will run\n\n" + how.describe() + "\n\nand your system asks for "
                + "your password. You can remove it later with your package manager.", ButtonType.CANCEL, INSTALL);
        ask.setHeaderText("Install " + name + "?");
        if (ask.showAndWait().orElse(ButtonType.CANCEL) != INSTALL) return;
        // One at a time: a second package manager would fail on the first one's lock and report it as failed.
        if (!INSTALLING.compareAndSet(false, true)) {
            done.accept(false, "An install is already running — wait for it to finish.");
            return;
        }
        done.accept(false, "Installing " + name + "…");
        Async.load("install-" + backend.id(), () -> {
            try {
                return how.run() && SessionBackends.isAvailable(backend);
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            } finally {
                INSTALLING.set(false);
            }
        }, ok -> done.accept(ok, ok ? name + " is installed." : name + " wasn't installed — the password prompt "
                + "was closed, or the package manager failed. Run " + how.describe() + " in a terminal to see why."),
                why -> done.accept(false, "Couldn't install " + name + ": " + why));
    }

    /** Whether an install this process started is still running. */
    private static final AtomicBoolean INSTALLING = new AtomicBoolean();

    /** What the install did: whether the backend is there now, and one sentence for a status line. */
    @FunctionalInterface
    public interface Done {
        void accept(boolean installed, String message);
    }

    private static void inform(StudioServices services, Window owner, String name, String text) {
        Alert alert = alert(services, owner, Alert.AlertType.INFORMATION, text, ButtonType.CLOSE);
        alert.setHeaderText(name + " isn't installed");
        alert.showAndWait();
    }

    private static Alert alert(StudioServices services, Window owner, Alert.AlertType type, String text,
                               ButtonType... buttons) {
        Alert alert = services.theme().alert(type, text, buttons);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle("Private display");
        return alert;
    }

    private static void copy(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
