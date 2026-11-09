package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The question {@link ResourceMove} asks when a project with the SDK's files at its resources' root opens: Move or Not
 * now, once a project a Studio session (2026-10-30, the user's call). A plugin has no banner of Studio's, so it is a
 * dialog; it asks after the project is open, never while it opens.
 */
public final class MoveOffer {

    /** The projects asked about this session, so Not now holds until Studio restarts. */
    private static final Set<Path> ASKED = ConcurrentHashMap.newKeySet();

    private MoveOffer() {}

    /** Looks for the SDK's old files off the FX thread, and asks when there are any. */
    public static void offer(StudioServices services) {
        Path project = services == null ? null : services.projectDir();
        if (project == null || !ASKED.add(project.toAbsolutePath().normalize())) return;
        Thread look = new Thread(() -> {
            Path folder = services.resourcesDir();
            int waiting = ResourceMove.waiting(folder);
            if (waiting > 0) Platform.runLater(() -> ask(services, folder, waiting));
        }, "sdk-files-look");
        look.setDaemon(true);
        look.start();
    }

    private static void ask(StudioServices services, Path folder, int waiting) {
        Path project = services.projectDir();
        String where = project == null ? folder.toString() : project.relativize(folder).toString().replace('\\', '/');
        ButtonType move = new ButtonType("Move", ButtonBar.ButtonData.OK_DONE);
        ButtonType later = new ButtonType("Not now", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = services.theme().alert(Alert.AlertType.CONFIRMATION, waiting + (waiting == 1 ? " file" : " files")
                + " of the SDK's — pictures, digits, screen shots — are at the resources' root, where another plugin's"
                + " could take their names. Move them into " + where + "/?\n\nThe bot finds them in either place;"
                + " until they move, Screens and its Pictures do not show them.", move, later);
        alert.setTitle("Move the SDK's files");
        alert.setHeaderText(null);
        services.dialogs().ownerWindow().ifPresent(alert::initOwner);
        if (alert.showAndWait().filter(move::equals).isEmpty()) return;
        Thread moving = new Thread(() -> {
            ResourceMove.Moved files;
            try {
                files = ResourceMove.moveFiles(folder);
            } catch (IOException e) {
                Platform.runLater(() -> services.status("The SDK's files stopped moving part-way: " + e.getMessage()
                        + ". Each loads from where it is."));
                return;
            }
            Platform.runLater(() -> {
                List<String> refused = new ArrayList<>();
                int paths = ResourceMove.rewritePaths(services, files.left(), refused);
                int left = files.left().size();
                services.status("Moved " + files.files() + (files.files() == 1 ? " file" : " files") + " into " + where
                        + "/" + (left > 0 ? " (" + left + " left at the root, where the bot still finds them: the folder"
                        + " has one of that name)" : "")
                        + (paths > 0 ? "; " + paths + " paths in the Java written short (↶ puts them back)" : "")
                        + (refused.isEmpty() ? "." : "; not rewritten: " + String.join(", ", refused) + "."));
            });
        }, "sdk-files-move");
        moving.setDaemon(true);
        moving.start();
    }
}
