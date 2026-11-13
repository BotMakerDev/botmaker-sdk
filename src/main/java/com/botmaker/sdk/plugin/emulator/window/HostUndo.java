package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.History;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

/**
 * ↶ and ↷ over this PC's values ({@link StudioServices#hostHistory()}), for the Emulators and Accounts windows: one
 * step per change either window made, whichever made it. Never the bot's undo, which never takes these back. A step
 * that followed the emulator ({@link HostConfig#followsTheEmulator}) is where ↶ stops. The history is git, so it is
 * read and stepped off the FX thread.
 */
public final class HostUndo {

    /** What a button says and whether it can be pressed, read off the history. */
    record Face(String text, String tip, boolean enabled) {

        static Face undo(History history) {
            if (!history.canUndo()) return new Face("↶ Undo", "Nothing to take back", false);
            String label = history.undoLabel();
            return HostConfig.followsTheEmulator(label)
                    ? new Face("↶ Undo", "Cannot take back: " + label + " — it followed the emulator", false)
                    : new Face("↶ Undo", "Take back: " + label, true);
        }

        static Face redo(History history) {
            return history.canRedo()
                    ? new Face("↷ Redo", "Do again: " + history.redoLabel(), true)
                    : new Face("↷ Redo", "Nothing to do again", false);
        }
    }

    /** Both buttons' faces, read together. */
    private record Faces(Face undo, Face redo) {}

    private final StudioServices services;
    private final Runnable afterStep;
    private final Button undo = new Button("↶ Undo");
    private final Button redo = new Button("↷ Redo");
    private final HBox node = new HBox(4, undo, redo);
    private AutoCloseable listening;

    /** {@code afterStep} reads the window again after a step, on the FX thread. */
    public HostUndo(StudioServices services, Runnable afterStep) {
        this.services = services;
        this.afterStep = afterStep;
        undo.setDisable(true);
        redo.setDisable(true);
        undo.setOnAction(e -> step(true));
        redo.setOnAction(e -> step(false));
    }

    public Node node() {
        return node;
    }

    /** Follows the history while the window shows; {@link #stop} when it hides. */
    public void start() {
        stop();
        listening = services.hostHistory().onChanged(this::show);
        show();
    }

    public void stop() {
        if (listening == null) return;
        try {
            listening.close();
        } catch (Exception ignored) {
            // the host's listener is gone already
        }
        listening = null;
    }

    private void step(boolean back) {
        undo.setDisable(true);
        redo.setDisable(true);
        Async.load(back ? "host-undo" : "host-redo", () -> {
            History history = services.hostHistory();
            if (back) history.undo();
            else history.redo();
            return faces();
        }, faces -> {
            apply(faces);
            afterStep.run();
        });
    }

    /** Reads both faces off the FX thread and shows them on it; safe from any thread. */
    private void show() {
        Async.load("host-history", this::faces, this::apply);
    }

    private Faces faces() {
        History history = services.hostHistory();
        return new Faces(Face.undo(history), Face.redo(history));
    }

    private void apply(Faces faces) {
        face(undo, faces.undo());
        face(redo, faces.redo());
    }

    private static void face(Button button, Face face) {
        button.setText(face.text());
        button.setTooltip(new Tooltip(face.tip()));
        button.setDisable(!face.enabled());
    }
}
