package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.History;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.sdk.plugin.emulator.host.HostBook;
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
            if (!history.canUndo()) return new Face("↶", "Nothing to take back", false);
            String label = history.undoLabel();
            if (HostConfig.followsTheEmulator(label)) {
                return new Face("↶", "Cannot take back: " + label + " — it followed the emulator", false);
            }
            return HostBook.startedTheBook(label)
                    ? new Face("↶", "Cannot take back: " + label + " — it started this PC's account book", false)
                    : new Face("↶", "Take back: " + label, true);
        }

        static Face redo(History history) {
            return history.canRedo()
                    ? new Face("↷", "Do again: " + history.redoLabel(), true)
                    : new Face("↷", "Nothing to do again", false);
        }
    }

    /** Both buttons' faces, read together. */
    private record Faces(Face undo, Face redo) {}

    private final StudioServices services;
    private final Runnable afterStep;
    private final Button undo = new Button("↶");
    private final Button redo = new Button("↷");
    private final HBox node = new HBox(4, undo, redo);
    private AutoCloseable listening;

    /** {@code afterStep} reads the window again after a step, on the FX thread. */
    public HostUndo(StudioServices services, Runnable afterStep) {
        this.services = services;
        this.afterStep = afterStep;
        undo.setDisable(true);
        redo.setDisable(true);
        undo.getStyleClass().add("em-icon");
        redo.getStyleClass().add("em-icon");
        undo.setOnAction(e -> step(true));
        redo.setOnAction(e -> step(false));
    }

    public Node node() {
        return node;
    }

    /** ↶, as its key asks: nothing while there is nothing to undo. */
    public void undo() {
        if (!undo.isDisabled()) step(true);
    }

    /** ↷, as its key asks: nothing while there is nothing to redo. */
    public void redo() {
        if (!redo.isDisabled()) step(false);
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
        face(undo, faces.undo(), "Ctrl+Z");
        face(redo, faces.redo(), "Ctrl+Y");
    }

    private static void face(Button button, Face face, String key) {
        button.setText(face.text());
        button.setTooltip(new Tooltip(face.tip() + " (" + key + ")"));
        button.setDisable(!face.enabled());
    }
}
