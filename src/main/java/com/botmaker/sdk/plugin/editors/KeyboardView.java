package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.interaction.Key;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The drawn keyboard both key editors open: a cap per {@link Key}, laid out by {@link KeyboardLayout}. Click a
 * cap, or press the real key while the keyboard has focus ({@link KeyCodes}); a search field dims every cap
 * that does not match, and Enter in it picks the one match. In one-key mode a key is the answer; in chord mode
 * each click or keystroke adds a key, shown as a chip beneath ({@link Chord}). Every change goes to
 * {@code onChange}; the view writes nothing itself.
 *
 * <p><b>The board grows with its window (feedback 2, 2026-09-27).</b> The cap size is worked out from the
 * board's width ({@link KeyboardLayout#unitFor}), so a narrow window still shows every cap and a wide one
 * draws them large; the window is given a minimum size the smallest board fits in.
 */
final class KeyboardView {

    private static final double SPACING = 2;

    private final boolean chordMode;
    private final Consumer<Chord> onChange;
    private final Map<Key, ToggleButton> caps = new EnumMap<>(Key.class);
    private final List<Sized> sized = new ArrayList<>();
    private final FlowPane chips = new FlowPane(4, 4);
    private final Label empty = new Label();
    private final VBox root;
    private Chord chord;

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
            Set<Key> hits = KeyboardLayout.matching(search.getText());
            if (hits.size() == 1) choose(hits.iterator().next());
        });

        VBox board = new VBox(SPACING);
        for (List<KeyboardLayout.Cap> row : KeyboardLayout.rows()) {
            HBox line = new HBox(SPACING);
            for (KeyboardLayout.Cap cap : row) {
                Region region = cap.key() == null ? new Region() : cap(cap.key());
                sized.add(new Sized(region, cap.width()));
                line.getChildren().add(region);
            }
            board.getChildren().add(line);
        }
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

        Label hint = new Label(chordMode
                ? "Click keys, or press them — each one is added, in order. Click a chip to take it out."
                : "Press a key, or click it.");
        hint.getStyleClass().add("dialog-hint-text");
        hint.setWrapText(true);
        empty.getStyleClass().add("dialog-hint-text");

        root = new VBox(8, search, board, hint);
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
        resize(34);
        root.sceneProperty().addListener((o, was, scene) -> {
            if (scene == null) return;
            board.requestFocus();
            scene.windowProperty().addListener((w, before, window) -> {
                if (window instanceof Stage stage) {
                    stage.setResizable(true);
                    stage.setMinWidth(KeyboardLayout.MIN_UNIT * KeyboardLayout.WIDTH + 80);
                    stage.setMinHeight(360);
                }
            });
        });
        refresh();
    }

    Parent node() {
        return root;
    }

    private ToggleButton cap(Key key) {
        ToggleButton button = new ToggleButton(key.label());
        button.getStyleClass().add("mouse-diagram-key");
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(key.name()));
        button.setOnAction(e -> choose(key));
        caps.put(key, button);
        return button;
    }

    /** Every cap and gap at {@code unit} pixels a key unit; a cap is as tall as a unit is wide. */
    private void resize(double unit) {
        double width = Math.floor(unit);
        for (Sized each : sized) {
            double w = width * each.width() - SPACING;
            each.region().setMinWidth(w);
            each.region().setPrefWidth(w);
            each.region().setMaxWidth(w);
            if (each.region() instanceof ToggleButton cap) {
                cap.setMinHeight(width - SPACING);
                cap.setPrefHeight(width - SPACING);
                cap.setStyle("-fx-font-size: " + Math.round(Math.max(10, width * 0.36)) + "px;");
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
        for (Key key : chord.keys()) {
            Button chip = new Button(key.label() + "  ✕");
            chip.getStyleClass().add("block-chip");
            chip.setTooltip(new Tooltip("Take " + key.label() + " out"));
            chip.setOnAction(e -> set(chord.click(key)));
            chips.getChildren().add(chip);
        }
        empty.setText("No keys chosen");
        if (chord.keys().isEmpty()) chips.getChildren().add(empty);
    }

    private void dim(Set<Key> hits) {
        caps.forEach((key, button) -> button.setOpacity(hits.contains(key) ? 1 : 0.35));
    }
}
