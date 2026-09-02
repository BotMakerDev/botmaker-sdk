package com.botmaker.sdk.internal.plugin.record;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.input.InputEvent;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The macro recorder: pick the window to record against, press Record (or {@value RecordHotkey#KEY_NAME} from
 * inside the game), act, press Stop, and read back the Java it would have taken to write what you did.
 *
 * <p><b>It replaces Studio's Record mode, and what it does not do is the point of the change.</b> Until
 * 2026-09-02 recording lived inside the editor's program-shape overlay and inserted its blocks at that
 * overlay's cursor — which meant the editor held {@link MacroTranslator}, and therefore held five SDK class
 * literals, deciding on this plugin's behalf that a click is a {@code Mouse}. That is a vocabulary, and the
 * platform's rule is that a host owns capabilities and a plugin owns vocabularies.
 *
 * <p>So the recorder came here whole, and the cursor went with the overlay. <b>The accepted cost is that a
 * recording is delivered as text rather than inserted where you were working.</b> The contract has no
 * "insert these statements at the cursor" capability and should not grow one lightly: where the cursor is,
 * and what a statement means in the tree under it, is the editor's own model, and a surface shaped to this
 * one caller is the back door the platform exists to close. {@code Sources} is find-and-replace, not append.
 * If insertion is worth a capability it should be designed as one, with a second caller in mind.
 *
 * <p>Linux/X11 only, like the engine behind it — {@link RecordingSession#isSupported()} decides, and the
 * window says so rather than showing a button that does nothing.
 */
public final class MacroRecorderDialog {

    private final StudioServices services;
    private final Window owner;

    private final ComboBox<GenericWindow> target = new ComboBox<>();
    private final TextArea output = new TextArea();
    private final Label status = new Label();
    private Button recordBtn;
    private Button stopBtn;
    private Button copyBtn;
    private Stage stage;

    private RecordingSession session;
    private RecordHotkey hotkey;

    /** The recorder window's own screen bounds, republished from FX so the native thread may read them. */
    private volatile Rectangle selfBounds = new Rectangle();

    /** The target's origin, re-probed at the start of every session — see {@link #start()}. */
    private Rectangle windowBounds;

    /** Set while a coalesced status refresh is already queued, so one FX runnable serves a burst of input. */
    private final AtomicBoolean statusQueued = new AtomicBoolean();

    private MacroRecorderDialog(StudioServices services, Window owner) {
        this.services = services;
        this.owner = owner;
    }

    /** Opens a recorder window. Not single-instance: it owns no port and no display. */
    public static void open(StudioServices services, Window owner) {
        new MacroRecorderDialog(services, owner).show();
    }

    private void show() {
        session = new RecordingSession(() -> selfBounds, count -> requestStatusRefresh());
        // The global hotkey is watched on a second XRecord connection, so this session sees its presses too —
        // and would record the very key the user pressed to stop recording. See RecordHotkey.
        session.ignoreKeysym(RecordHotkey.KEYSYM);

        target.setPromptText("Window to record against");
        target.setButtonCell(windowCell());
        target.setCellFactory(list -> windowCell());
        target.setMaxWidth(Double.MAX_VALUE);
        Button refresh = new Button("⟳");
        refresh.setTooltip(new Tooltip("Look for windows again"));
        refresh.setOnAction(e -> reloadWindows());
        HBox targetRow = new HBox(6, target, refresh);
        HBox.setHgrow(target, Priority.ALWAYS);
        targetRow.setAlignment(Pos.CENTER_LEFT);

        recordBtn = new Button("● Record");
        recordBtn.setOnAction(e -> togglePrimary());
        stopBtn = new Button("■ Stop");
        stopBtn.setDisable(true);
        stopBtn.setOnAction(e -> stopAndTranslate());
        copyBtn = new Button("Copy");
        copyBtn.setDisable(true);
        copyBtn.setOnAction(e -> copy());
        HBox buttons = new HBox(6, recordBtn, stopBtn, copyBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        output.setEditable(false);
        output.setPromptText("The Java for what you did shows up here when you stop recording.");
        output.setPrefRowCount(14);
        VBox.setVgrow(output, Priority.ALWAYS);

        target.valueProperty().addListener((o, was, now) -> refreshAvailability());

        VBox root = new VBox(8, targetRow, buttons, output, status);
        root.setPadding(new Insets(12));
        stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Record Macro");
        stage.setScene(services.theme().scene(root, 560, 460));
        stage.setOnHidden(e -> release());

        // The recorder window is on the same screen as the thing being recorded, so its own clicks must not
        // land in the recording. RecordingSession polls this rectangle from the native thread; keep it fresh
        // from FX rather than reading stage properties over there.
        Runnable trackSelf = () -> selfBounds = new Rectangle(
                (int) stage.getX(), (int) stage.getY(), (int) stage.getWidth(), (int) stage.getHeight());
        stage.xProperty().addListener((o, a, b) -> trackSelf.run());
        stage.yProperty().addListener((o, a, b) -> trackSelf.run());
        stage.widthProperty().addListener((o, a, b) -> trackSelf.run());
        stage.heightProperty().addListener((o, a, b) -> trackSelf.run());
        stage.setOnShown(e -> trackSelf.run());

        reloadWindows();
        refreshAvailability();

        hotkey = new RecordHotkey(this::toggleFromHotkey);
        hotkey.start();
        stage.show();
    }

    private static ListCell<GenericWindow> windowCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(GenericWindow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                Rectangle r = item.getRect();
                setText(item.getTitle() + (r == null ? "" : "  (" + r.width + "×" + r.height + ")"));
            }
        };
    }

    /** Every titled window on the desktop, largest first — the game is rarely the smallest thing open. */
    private void reloadWindows() {
        GenericWindow was = target.getValue();
        List<GenericWindow> windows = new ArrayList<>();
        try {
            for (GenericWindow w : NativeControllerFactory.get().getAllWindows()) {
                if (w.getTitle() == null || w.getTitle().isBlank()) continue;
                if (w.getRect() == null || w.getRect().width <= 0 || w.getRect().height <= 0) continue;
                windows.add(w);
            }
        } catch (RuntimeException | Error e) {
            status.setText("Couldn't list windows: " + e.getMessage());
        }
        windows.sort((a, b) -> Long.compare(
                (long) b.getRect().width * b.getRect().height, (long) a.getRect().width * a.getRect().height));
        target.getItems().setAll(windows);
        if (was != null) {
            for (GenericWindow w : windows) {
                if (w.getTitle().equals(was.getTitle())) {
                    target.setValue(w);
                    break;
                }
            }
        }
    }

    /** Enables Record when there is a recorder and a window to record against — and says which is missing. */
    private void refreshAvailability() {
        if (recordBtn == null || session.isRecording()) return;
        String blocker;
        if (!RecordingSession.isSupported()) {
            blocker = "Recording is available on Linux (X11) only";
        } else if (target.getValue() == null) {
            // Recording translates clicks to window-relative coordinates, so it needs a window.
            blocker = "Pick the window you're going to be clicking in";
        } else {
            blocker = null;
        }
        recordBtn.setDisable(blocker != null);
        recordBtn.setTooltip(new Tooltip(blocker != null ? blocker
                : "Record real clicks and keys — or press " + RecordHotkey.KEY_NAME + " from inside the game"));
        if (blocker != null) status.setText(blocker);
    }

    /**
     * The global hotkey's action: begin a session, or finish the running one. Pausing is left to the button —
     * a shortcut pressed from inside the game has to have one unambiguous meaning, and "stop" is the one worth
     * not having to reach for the window to reach.
     */
    private void toggleFromHotkey() {
        if (session.isRecording()) {
            stopAndTranslate();
            return;
        }
        if (recordBtn.isDisable()) {
            // Say why nothing happened. The button carries the same explanation in a tooltip, but the point of
            // the hotkey is that the user is not looking at the button.
            Tooltip why = recordBtn.getTooltip();
            status.setText(why != null ? why.getText() : "Recording isn't available here.");
            return;
        }
        start();
    }

    private void togglePrimary() {
        if (!session.isRecording()) {
            start();
        } else {
            session.setPaused(!session.isPaused());
            recordBtn.setText(session.isPaused() ? "▶ Resume" : "⏸ Pause");
            updateStatus();
        }
    }

    /**
     * Starts a session against freshly probed window bounds.
     *
     * <p><b>Why it re-probes.</b> The origin a recorded click is made relative to is the target window's
     * top-left corner. Probing it once when the recorder opened and reusing it at stop offset every recorded
     * coordinate by however far the window had moved since, with nothing on screen to say so.
     */
    private void start() {
        GenericWindow window = target.getValue();
        if (session.isRecording() || window == null || !RecordingSession.isSupported()) return;
        try {
            NativeControllerFactory.get().focusWindow(window);  // act on the target, not on whatever had focus
        } catch (RuntimeException | Error ignored) {
            // Raising is a courtesy; a window manager that refuses it does not invalidate the recording.
        }
        reloadWindows();                                       // re-probe bounds through a fresh enumeration
        GenericWindow fresh = target.getValue();
        windowBounds = fresh != null ? fresh.getRect() : window.getRect();

        try {
            session.start();
        } catch (RuntimeException | Error ex) {
            services.theme().alert(javafx.scene.control.Alert.AlertType.WARNING,
                    "Couldn't start input recording: " + ex.getMessage()).showAndWait();
            return;
        }
        output.clear();
        copyBtn.setDisable(true);
        recordBtn.setText("⏸ Pause");
        stopBtn.setDisable(false);
        updateStatus();
    }

    /** Stops recording and writes the translated Java into the output box. */
    private void stopAndTranslate() {
        if (!session.isRecording()) return;
        List<InputEvent> events = session.stop();
        recordBtn.setText("● Record");
        stopBtn.setDisable(true);
        refreshAvailability();

        GenericWindow window = target.getValue();
        String title = window == null ? null : window.getTitle();
        Rectangle b = windowBounds != null ? windowBounds : new Rectangle();
        MacroTranslator.Macro macro = MacroTranslator.translate(events,
                new MacroTranslator.WindowRef(title, b.x, b.y, b.width, b.height));

        if (macro.isEmpty()) {
            output.clear();
            copyBtn.setDisable(true);
            status.setText("Nothing to write down — no recognizable actions were recorded.");
            return;
        }
        output.setText(macro.java());
        copyBtn.setDisable(false);
        int lines = macro.statements().size();
        status.setText("Recorded " + lines + (lines == 1 ? " line" : " lines") + " — copy it into your bot.");
    }

    private void copy() {
        ClipboardContent content = new ClipboardContent();
        content.putString(output.getText());
        Clipboard.getSystemClipboard().setContent(content);
        status.setText("Copied.");
    }

    /** While a session runs the status line is the recorder's; stopping leaves whatever the translation said. */
    private void updateStatus() {
        if (!session.isRecording()) return;
        status.setText((session.isPaused() ? "Paused" : "Recording")
                + " — " + session.actionCount() + " actions");
    }

    /**
     * Queues one status refresh per FX pulse. The recorder reports every press from its native thread, and a
     * {@code Platform.runLater} apiece floods the FX queue during a fast burst. The count is read when the
     * runnable finally executes, so coalescing loses nothing but the intermediate frames.
     */
    private void requestStatusRefresh() {
        if (statusQueued.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                statusQueued.set(false);
                updateStatus();
            });
        }
    }

    /** Drops both native listeners. A session running when the window closes is abandoned, not translated. */
    private void release() {
        if (session.isRecording()) session.stop();
        if (hotkey != null) {
            hotkey.close();
            hotkey = null;
        }
    }
}
