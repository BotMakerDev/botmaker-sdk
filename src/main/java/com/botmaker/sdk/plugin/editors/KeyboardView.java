package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.input.Key;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * The drawn keyboard both key editors open: a cap per {@link Key}, laid out by {@link KeyboardLayout}. Click a
 * cap, or press the real key while the keyboard has focus ({@link KeyCodes}); a search field dims every cap
 * that does not match, and Enter in it picks the one match. In one-key mode a key is the answer; in chord mode
 * each click or keystroke adds a key, shown as a chip beneath ({@link Chord}). Every change goes to
 * {@code onChange}; the view writes nothing itself.
 *
 * <p><b>The board grows with its window.</b> The cap size is worked out from the
 * board's width ({@link KeyboardLayout#unitFor}), so a narrow window still shows every cap and a wide one
 * draws them large; the window is given a minimum size the smallest board fits in.
 *
 * <p>A cap shows its short face ({@link KeyboardLayout#face}) in a font that
 * shrinks to fit ({@link KeyboardLayout#fontFor}) before anything is cut; QWERTY, AZERTY or QWERTZ is picked
 * above the board and remembered for this user; and a chip is dragged to change the order the keys are pressed
 * in, its ✕ taking it out.
 */
final class KeyboardView {

    private static final double SPACING = 2;
    /** This user's keyboard, remembered across projects: which keyboard is on the desk is not a project's. */
    private static final Preferences PREFS = Preferences.userNodeForPackage(KeyboardView.class);
    private static final String BOARD = "keyboard-board";

    private final boolean chordMode;
    private final Consumer<Chord> onChange;
    private final Map<Key, ToggleButton> caps = new EnumMap<>(Key.class);
    private final List<Sized> sized = new ArrayList<>();
    private final VBox board = new VBox(SPACING);
    private final FlowPane chips = new FlowPane(4, 4);
    private final Label empty = new Label();
    private final VBox root;
    private Chord chord;
    private Set<Key> hits = KeyboardLayout.matching("");
    private double unit = 34;

    /** A cap or a gap, and the key units it spans. */
    private record Sized(Region region, double width) {}

    KeyboardView(boolean chordMode, Chord initial, Consumer<Chord> onChange) {
        this.chordMode = chordMode;
        this.onChange = onChange;
        this.chord = initial;

        TextField search = new TextField();
        search.setPromptText("Search keys…");
        search.textProperty().addListener((o, was, is) -> dim(KeyboardLayout.matching(is)));
        search.setOnAction(e -> {
            Set<Key> found = KeyboardLayout.matching(search.getText());
            if (found.size() == 1) choose(found.iterator().next());
        });

        HBox layouts = new HBox();
        layouts.getStyleClass().add("choice-mode-bar");
        ToggleGroup group = new ToggleGroup();
        KeyboardLayout.Board chosen = remembered();
        KeyboardLayout.Board[] boards = KeyboardLayout.Board.values();
        for (int i = 0; i < boards.length; i++) {
            KeyboardLayout.Board each = boards[i];
            ToggleButton button = new ToggleButton(each.displayName());
            button.setToggleGroup(group);
            button.setSelected(each == chosen);
            button.setFocusTraversable(false);
            if (i == 0) button.getStyleClass().add("choice-mode-first");
            if (i == boards.length - 1) button.getStyleClass().add("choice-mode-last");
            button.setTooltip(new Tooltip("Draw the keys as a " + each.displayName() + " keyboard has them. "
                    + "A key is the same key on every layout: the cap labelled A writes A."));
            button.setOnAction(e -> {
                button.setSelected(true);
                remember(each);
                fill(each);
            });
            layouts.getChildren().add(button);
        }
        HBox top = new HBox(8, search, layouts);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);

        fill(chosen);
        // The board takes keystrokes; a click anywhere on it gives it the focus back from the search field.
        board.setFocusTraversable(true);
        board.addEventFilter(KeyEvent.KEY_PRESSED, e -> KeyCodes.toKey(e.getCode()).ifPresent(key -> {
            e.consume();
            if (chordMode) {
                set(chord.press(e.isControlDown(), e.isAltDown(), e.isShiftDown(), e.isMetaDown(), key));
            } else {
                choose(key);
            }
        }));
        board.setOnMousePressed(e -> board.requestFocus());
        // The window gives its first field the focus, which is the search box, where a key pressed on opening
        // would be typed while the hint says to press it. The board takes the focus instead.
        board.sceneProperty().addListener((o, was, is) -> {
            if (is != null) Platform.runLater(board::requestFocus);
        });

        Label hint = new Label(chordMode
                ? "Click keys, or press them — each one is added, in order. Drag a chip to change the order; ✕ "
                        + "takes it out."
                : "Press a key, or click it.");
        hint.getStyleClass().add("dialog-hint-text");
        hint.setWrapText(true);
        empty.getStyleClass().add("dialog-hint-text");

        root = new VBox(8, top, board, hint);
        if (chordMode) {
            Button clear = new Button("Clear");
            clear.setOnAction(e -> set(Chord.EMPTY));
            HBox keys = new HBox(8, chips, clear);
            keys.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(chips, Priority.ALWAYS);
            root.getChildren().add(keys);
        }
        // Its own minimum, not the caps': a layout clamps a child to its minimum, so a root as wide as its caps
        // could never be handed less width, and the caps would never be told to shrink.
        root.setMinWidth(KeyboardLayout.MIN_UNIT * KeyboardLayout.WIDTH);

        // Sized off the root's width, which the window's width sets; the board's own width would follow the caps.
        root.widthProperty().addListener((o, was, is) -> resize(KeyboardLayout.unitFor(is.doubleValue())));
        resize(unit);
        root.sceneProperty().addListener((o, was, scene) -> {
            if (scene == null) return;
            board.requestFocus();
            scene.windowProperty().addListener((w, before, window) -> {
                if (window instanceof Stage stage) {
                    stage.setResizable(true);
                    stage.setMinWidth(KeyboardLayout.MIN_UNIT * KeyboardLayout.WIDTH + 80);
                    stage.setMinHeight(380);
                }
            });
        });
        refresh();
    }

    Parent node() {
        return root;
    }

    private static KeyboardLayout.Board remembered() {
        try {
            return KeyboardLayout.Board.fromId(PREFS.get(BOARD, KeyboardLayout.Board.QWERTY.id()));
        } catch (RuntimeException e) {
            return KeyboardLayout.Board.QWERTY;
        }
    }

    private static void remember(KeyboardLayout.Board board) {
        try {
            PREFS.put(BOARD, board.id());
        } catch (RuntimeException e) {
            // Not remembered; the board is still switched.
        }
    }

    /** The caps of {@code layout}, replacing whatever board was drawn; the selection and the search carry over. */
    private void fill(KeyboardLayout.Board layout) {
        board.getChildren().clear();
        caps.clear();
        sized.clear();
        for (List<KeyboardLayout.Cap> row : KeyboardLayout.rows(layout)) {
            HBox line = new HBox(SPACING);
            for (KeyboardLayout.Cap cap : row) {
                Region region = cap.key() == null ? new Region() : cap(cap.key());
                sized.add(new Sized(region, cap.width()));
                line.getChildren().add(region);
            }
            board.getChildren().add(line);
        }
        resize(unit);
        refresh();
        dim(hits);
    }

    private ToggleButton cap(Key key) {
        ToggleButton button = new ToggleButton(KeyboardLayout.face(key));
        button.getStyleClass().add("mouse-diagram-key");
        button.setFocusTraversable(false);
        button.setTextAlignment(TextAlignment.CENTER);
        button.setTextOverrun(OverrunStyle.CLIP);
        button.setTooltip(new Tooltip(key.label() + "  (" + key.name() + ")"));
        button.setOnAction(e -> choose(key));
        caps.put(key, button);
        return button;
    }

    /** Every cap and gap at {@code unit} pixels a key unit; a cap is as tall as a unit is wide. */
    private void resize(double unit) {
        this.unit = unit;
        double width = Math.floor(unit);
        for (Sized each : sized) {
            double w = width * each.width() - SPACING;
            each.region().setMinWidth(w);
            each.region().setPrefWidth(w);
            each.region().setMaxWidth(w);
            if (each.region() instanceof ToggleButton cap) {
                double h = width - SPACING;
                cap.setMinHeight(h);
                cap.setPrefHeight(h);
                // Fitted to the narrower side: a wide cap (Shift, Space) is still only a unit tall.
                double font = KeyboardLayout.fontFor(Math.min(w, h), cap.getText());
                cap.setStyle("-fx-font-size: " + Math.round(font) + "px; -fx-padding: 0;");
            }
        }
    }

    private void choose(Key key) {
        set(chordMode ? chord.click(key) : Chord.single(key));
    }

    private void set(Chord next) {
        chord = next;
        refresh();
        onChange.accept(next);
    }

    private void refresh() {
        caps.forEach((key, button) -> button.setSelected(chord.contains(key)));
        if (!chordMode) return;
        chips.getChildren().clear();
        List<Key> keys = chord.keys();
        for (int i = 0; i < keys.size(); i++) chips.getChildren().add(chip(keys.get(i), i));
        empty.setText("No keys chosen");
        if (keys.isEmpty()) chips.getChildren().add(empty);
    }

    /** A chosen key: its full name, dragged onto another chip to take that place, and ✕ to take it out. */
    private Node chip(Key key, int at) {
        Label name = new Label(key.label());
        Button remove = new Button("✕");
        remove.getStyleClass().add("row-icon-button");
        remove.setTooltip(new Tooltip("Take " + key.label() + " out"));
        remove.setOnAction(e -> set(chord.click(key)));
        HBox chip = new HBox(4, new Label("⠿"), name, remove);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("block-chip");
        Tooltip.install(chip, new Tooltip("Drag to change when " + key.label() + " is pressed"));
        chip.setOnDragDetected(e -> {
            Dragboard drag = chip.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(Integer.toString(at));
            drag.setContent(content);
            e.consume();
        });
        chip.setOnDragOver(e -> {
            if (e.getGestureSource() instanceof Node source && chips.getChildren().contains(source)) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        chip.setOnDragDropped(e -> {
            boolean done = false;
            try {
                int from = Integer.parseInt(e.getDragboard().getString());
                set(chord.move(from, at));
                done = true;
            } catch (RuntimeException ignored) {
                // Not one of these chips: nothing moves.
            }
            e.setDropCompleted(done);
            e.consume();
        });
        return chip;
    }

    private void dim(Set<Key> matched) {
        hits = matched;
        caps.forEach((key, button) -> button.setOpacity(matched.contains(key) ? 1 : 0.35));
    }
}
