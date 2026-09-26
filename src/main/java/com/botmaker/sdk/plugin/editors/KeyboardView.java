package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.interaction.Key;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The drawn keyboard both key editors open: a cap per {@link Key}, laid out by {@link KeyboardLayout}. Click a
 * cap, or press the real key while the keyboard has focus ({@link KeyCodes}); a search field dims every cap
 * that does not match, and Enter in it picks the one match. In one-key mode a key is the answer; in chord mode
 * modifiers toggle and one other key is kept ({@link Chord}). Every change goes to {@code onChange}; the view
 * writes nothing itself.
 */
final class KeyboardView {

    private static final double UNIT = 34;

    private final boolean chordMode;
    private final Consumer<Chord> onChange;
    private final Map<Key, ToggleButton> caps = new EnumMap<>(Key.class);
    private final Label shown = new Label();
    private final VBox root;
    private Chord chord;

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

        VBox board = new VBox(2);
        for (List<KeyboardLayout.Cap> row : KeyboardLayout.rows()) {
            HBox line = new HBox(2);
            for (KeyboardLayout.Cap cap : row) {
                line.getChildren().add(cap.key() == null ? gap(cap.width()) : cap(cap));
            }
            board.getChildren().add(line);
        }
        // The board takes keystrokes; a click anywhere on it gives it the focus back from the search field.
        board.setFocusTraversable(true);
        board.addEventFilter(KeyEvent.KEY_PRESSED, e -> KeyCodes.toKey(e.getCode()).ifPresent(key -> {
            e.consume();
            if (chordMode) {
                set(Chord.pressed(e.isControlDown(), e.isAltDown(), e.isShiftDown(), e.isMetaDown(), key));
            } else {
                choose(key);
            }
        }));
        board.setOnMousePressed(e -> board.requestFocus());

        Label hint = new Label(chordMode ? "Press the combination, or click the keys." : "Press a key, or click it.");
        hint.getStyleClass().add("dialog-hint-text");
        root = new VBox(8, search, board, hint, shown);
        refresh();
        board.sceneProperty().addListener((o, was, is) -> {
            if (is != null) board.requestFocus();
        });
    }

    Parent node() {
        return root;
    }

    private ToggleButton cap(KeyboardLayout.Cap cap) {
        Key key = cap.key();
        ToggleButton button = new ToggleButton(key.label());
        button.getStyleClass().add("mouse-diagram-key");
        button.setFocusTraversable(false);
        button.setMinWidth(UNIT * cap.width() - 2);
        button.setPrefWidth(UNIT * cap.width() - 2);
        button.setTooltip(new Tooltip(key.name()));
        button.setOnAction(e -> choose(key));
        caps.put(key, button);
        return button;
    }

    private static Region gap(double width) {
        Region gap = new Region();
        gap.setMinWidth(UNIT * width - 2);
        return gap;
    }

    private void choose(Key key) {
        set(chordMode ? chord.click(key) : new Chord(Set.of(), key));
    }

    private void set(Chord next) {
        chord = next;
        refresh();
        onChange.accept(next);
    }

    private void refresh() {
        caps.forEach((key, button) -> button.setSelected(key == chord.main() || chord.modifiers().contains(key)));
        shown.setText(chord.combo().map(Object::toString).orElse(chordMode ? "No keys chosen" : ""));
    }

    private void dim(Set<Key> hits) {
        caps.forEach((key, button) -> button.setOpacity(hits.contains(key) ? 1 : 0.35));
    }
}
