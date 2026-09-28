package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.interaction.Combo;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.KeySequence;
import com.botmaker.sdk.api.interaction.MouseButton;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The three input enums a bot's own API names — a {@link Direction}, a {@link Key}, a {@link MouseButton} —
 * drawn as the shapes they are rather than as three dropdowns. A {@link Combo} (2026-09-26) shares the key's
 * drawn keyboard, in chord mode.
 *
 * <h2>They were Studio's, and that was the back door this platform exists to close</h2>
 *
 * <p>All three lived in the editor's {@code ValueEditors}, reached by a {@code switch} on the persisted ids
 * {@code "DIRECTION"}, {@code "KEY"} and {@code "MOUSE_BUTTON"}. A compass, a keyboard and a mouse are the
 * SDK's vocabulary, so the host was drawing one plugin's types by name — and a second plugin with an enum
 * of its own could only ever have had the read-only text field. Moving them here costs the host three
 * {@code switch} arms and gains every plugin the same treatment.
 *
 * <p><b>Every constant comes from {@code getEnumConstants()}.</b> Studio read a parallel list of option
 * names out of the value registry, which is how {@code DirectionPad}'s table came to hold only the screen
 * spelling — {@code UP}, {@code DOWN} — while the SDK's {@code Direction} had long been the compass, so
 * <em>every</em> constant missed its square and the pad degraded to a row of named buttons. A constant added
 * to the SDK now appears here with nothing else to update, and one the layout has no place for is still
 * reachable rather than silently absent.
 *
 * <p><b>The value is the constant, never its name.</b> {@link ValueContext#value(Class)} hands over the
 * enum and {@link ValueContext#set(Object)} takes one back; the host writes the constant's own Java. The
 * string round trip these editors used to do is what let a half-typed key name become a stored value.
 */
public final class InputEditors {

    private InputEditors() {}

    // --- direction -------------------------------------------------------------------------------------

    /** Where each constant sits on the pad, by column and row. Both spellings share their squares. */
    private record Cell(String name, String arrow, int column, int row) {}

    private static final List<Cell> CELLS = List.of(
            new Cell("UP_LEFT", "↖", 0, 0), new Cell("UP", "↑", 1, 0), new Cell("UP_RIGHT", "↗", 2, 0),
            new Cell("LEFT", "←", 0, 1), new Cell("RIGHT", "→", 2, 1),
            new Cell("DOWN_LEFT", "↙", 0, 2), new Cell("DOWN", "↓", 1, 2), new Cell("DOWN_RIGHT", "↘", 2, 2),
            new Cell("NORTH", "↑", 1, 0), new Cell("SOUTH", "↓", 1, 2),
            new Cell("WEST", "←", 0, 1), new Cell("EAST", "→", 2, 1),
            new Cell("NORTH_WEST", "↖", 0, 0), new Cell("NORTH_EAST", "↗", 2, 0),
            new Cell("SOUTH_WEST", "↙", 0, 2), new Cell("SOUTH_EAST", "↘", 2, 2),
            new Cell("CENTER", "⊙", 1, 1));

    /**
     * A direction as a pad of arrows.
     *
     * <p>A dropdown of eight names is a list to read; a pad of eight arrows is a shape to point at, and the
     * shape is what the value means. It is the one enum editor where the layout carries the semantics — up
     * and to the left <em>is</em> up-left — so nothing has to be read at all.
     */
    public static Node direction(ValueContext ctx) {
        Direction current = ctx.value(Direction.class).orElse(null);
        ToggleGroup group = new ToggleGroup();

        GridPane pad = new GridPane();
        pad.setHgap(2);
        pad.setVgap(2);
        Map<Direction, int[]> cells = padCells();
        cells.forEach((constant, square) -> pad.add(
                toggle(ctx, group, constant, arrowOf(constant), constant.name(), current, "direction-pad-key"),
                square[0], square[1]));

        VBox box = new VBox(4, pad);
        box.getStyleClass().add("direction-pad");
        showUnread(ctx, Direction.class, box, group);

        // A direction with no square still has to be reachable, so it goes in a row underneath as a named
        // button rather than quietly disappearing from the editor.
        HBox spare = new HBox(2);
        for (Direction constant : Direction.values()) {
            if (cells.containsKey(constant)) continue;
            spare.getChildren().add(
                    toggle(ctx, group, constant, constant.name(), constant.name(), current, "direction-pad-key"));
        }
        if (!spare.getChildren().isEmpty()) box.getChildren().add(spare);
        return box;
    }

    /**
     * Where each constant lands, as {@code {column, row}}: one button per square. The two spellings share
     * squares, and a Direction holding both would otherwise stack two buttons on one cell, the second painting
     * over the first — so the first cell per square wins.
     */
    static Map<Direction, int[]> padCells() {
        Map<Direction, int[]> out = new EnumMap<>(Direction.class);
        Set<String> taken = new HashSet<>();
        for (Cell cell : CELLS) {
            Direction constant = constantNamed(cell.name());
            if (constant == null || out.containsKey(constant)) continue;
            if (!taken.add(cell.column() + "," + cell.row())) continue;
            out.put(constant, new int[]{cell.column(), cell.row()});
        }
        return out;
    }

    private static String arrowOf(Direction constant) {
        return CELLS.stream().filter(c -> c.name().equals(constant.name())).map(Cell::arrow)
                .findFirst().orElse(constant.name());
    }

    private static Direction constantNamed(String name) {
        for (Direction constant : Direction.values()) {
            if (constant.name().equals(name)) return constant;
        }
        return null;
    }

    // --- mouse button ----------------------------------------------------------------------------------

    /**
     * The mouse buttons on a drawn mouse ({@link MouseButtons}): click the part, or click the strip under it
     * with the button you mean — a right click there is the answer, never a menu.
     *
     * <p><b>What this names is what the button does, never where it sits.</b> A mouse with two thumb
     * buttons, a left-handed mouse, a mouse the vendor's driver has remapped — in all of them the OS
     * reports the button the user configured, so a bot that says Back keeps working on a mouse whose back
     * button is somewhere else, and nothing has to ask which layout the machine running the bot has.
     */
    public static Node mouseButton(ValueContext ctx) {
        MouseButton current = ctx.value(MouseButton.class).orElse(null);
        ToggleGroup group = new ToggleGroup();
        Map<MouseButton, ToggleButton> buttons = new EnumMap<>(MouseButton.class);

        Pane drawing = new Pane();
        drawing.setMinSize(120, 160);
        drawing.setPrefSize(120, 160);
        // The body under the buttons: a plain outline, drawn first so the buttons sit on it.
        Region body = new Region();
        body.setStyle("-fx-shape: \"M0 40 Q0 0 50 0 Q100 0 100 40 L100 110 Q100 150 50 150 Q0 150 0 110 Z\";"
                + " -fx-border-color: -bm-divider; -fx-border-width: 1;");
        // A Pane autosizes its children to their pref size, so the size is set as one, not by resize().
        body.setPrefSize(100, 150);
        body.relocate(10, 6);
        drawing.getChildren().add(body);
        for (MouseButtons.Part part : MouseButtons.parts()) {
            ToggleButton button = toggle(ctx, group, part.button(), "", part.label(), current, "mouse-diagram-key");
            button.setStyle("-fx-shape: \"" + part.shape() + "\"; -fx-padding: 0;");
            button.setMinSize(part.w(), part.h());
            button.setPrefSize(part.w(), part.h());
            button.setMaxSize(part.w(), part.h());
            button.relocate(part.x(), part.y());
            drawing.getChildren().add(button);
            buttons.put(part.button(), button);
        }

        Label capture = new Label("Click here with the button you mean");
        capture.getStyleClass().add("dialog-hint-text");
        capture.setStyle("-fx-border-color: -bm-divider; -fx-border-style: dashed; -fx-padding: 6;");
        capture.setOnMousePressed(e -> MouseButtons.of(e.getButton()).ifPresent(chosen -> {
            e.consume();
            ctx.set(chosen);
            ToggleButton button = buttons.get(chosen);
            if (button != null) button.setSelected(true);
        }));
        capture.setOnContextMenuRequested(Event::consume);   // a right click is an answer here

        VBox box = new VBox(6, drawing, capture);
        box.getStyleClass().add("mouse-diagram");
        showUnread(ctx, MouseButton.class, box, group);

        // A button this drawing has no part for is still reachable, for the reason the direction pad's
        // spare row exists: an editor that cannot express a value the type has is worse than an ugly one.
        // It joins `buttons` too, so pressing it on the strip above selects it (it wrote and showed nothing).
        HBox spare = new HBox(2);
        for (MouseButton constant : MouseButton.values()) {
            if (buttons.containsKey(constant)) continue;
            ToggleButton button =
                    toggle(ctx, group, constant, constant.name(), constant.name(), current, "mouse-diagram-key");
            spare.getChildren().add(button);
            buttons.put(constant, button);
        }
        if (!spare.getChildren().isEmpty()) box.getChildren().add(spare);
        return box;
    }

    /**
     * An enum drawn as a shape selects nothing for a value it cannot read — a variable, a call — so the value
     * is shown as written under it until something is picked. It said nothing at all until 2026-09-28, and an
     * empty pad reads as "not set".
     */
    private static void showUnread(ValueContext ctx, Class<?> type, VBox box, ToggleGroup group) {
        String unread = unreadSource(ctx, type);
        if (unread == null) return;
        Label written = Styles.on(new Label(unread), Styles.CAPTION);
        written.setTooltip(new Tooltip("Not a value this editor can read. A pick replaces it."));
        box.getChildren().add(written);
        group.selectedToggleProperty().addListener((o, was, is) -> {
            if (is != null) box.getChildren().remove(written);
        });
    }

    /** The source to show beside a shape that selects nothing, or {@code null} when it selects the value. */
    static String unreadSource(ValueContext ctx, Class<?> type) {
        return ctx.value(type).isEmpty() && !Slots.isEmpty(ctx) ? Slots.raw(ctx) : null;
    }

    // --- key and combination ---------------------------------------------------------------------------

    /** The key pill: the cap, else the source as written, else an invitation. */
    static String keyPill(ValueContext ctx) {
        return ctx.value(Key.class).map(Key::label)
                .orElseGet(() -> Slots.sourceOr(ctx, "Choose a key…"));
    }

    /** The combination pill: {@code Ctrl+Shift+S}, else the source as written, else an invitation. */
    static String comboPill(ValueContext ctx) {
        return ctx.value(Combo.class).map(Combo::toString)
                .orElseGet(() -> Slots.sourceOr(ctx, "Choose keys…"));
    }

    /**
     * The chord the popup opens on: the value's, or nothing — never a guess at an expression the host could not
     * read, nor at a combo the keyboard cannot hold (one that repeats a key), which OK would otherwise rewrite
     * with a key dropped. Since 2026-09-27 any other combo is held as written, in its own order.
     */
    static Chord chordOf(ValueContext ctx) {
        return ctx.value(Combo.class).map(InputEditors::chordOf).orElse(Chord.EMPTY);
    }

    /** {@code combo}'s keys as a chord, or nothing when one repeats; the hold is not the chord's. */
    private static Chord chordOf(Combo combo) {
        return Chord.of(combo).combo().filter(held -> held.keys().equals(combo.keys())).isPresent()
                ? Chord.of(combo) : Chord.EMPTY;
    }

    /**
     * A hold or a wait as typed: whole milliseconds, a trailing "ms" allowed, blank for none. {@code null} for
     * anything else, a negative number included, so the field can say it is wrong rather than write a guess.
     */
    static Duration holdOf(String text) {
        String t = text == null ? "" : text.strip();
        if (t.endsWith("ms")) t = t.substring(0, t.length() - 2).strip();
        if (t.isEmpty()) return Duration.ZERO;
        try {
            long ms = Long.parseLong(t);
            return ms < 0 ? null : Duration.ofMillis(ms);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The sequence pill: {@code Ctrl+A → 100 ms → Ctrl+C}, else the source as written, else an invitation. */
    static String sequencePill(ValueContext ctx) {
        return ctx.value(KeySequence.class).map(KeySequence::toString)
                .orElseGet(() -> Slots.sourceOr(ctx, "Choose key steps…"));
    }

    /** {@code items} with the one at {@code from} taken out and put back at {@code to}; unchanged for a bad index. */
    static <T> List<T> moved(List<T> items, int from, int to) {
        if (from < 0 || from >= items.size()) return items;
        List<T> next = new ArrayList<>(items);
        T item = next.remove(from);
        next.add(Math.clamp(to, 0, next.size()), item);
        return List.copyOf(next);
    }

    /**
     * A key, picked on a drawn keyboard ({@link KeyboardView}): click the cap, press the key, or search. The
     * choice is the answer, so the window closes on it. It was a type-to-search dropdown of constant names
     * until 2026-09-26; the search stays, and a name that matches no key still writes nothing.
     */
    public static Node key(ValueContext ctx) {
        Button[] pill = new Button[1];
        pill[0] = Pills.button(keyPill(ctx), () -> {
            Chord initial = ctx.value(Key.class).map(Chord::single).orElse(Chord.EMPTY);
            Stage[] stage = new Stage[1];
            KeyboardView view = new KeyboardView(false, initial, chord -> {
                Key key = chord.last();
                if (key == null) return;
                ctx.set(key);
                pill[0].setText(key.label());
                if (stage[0] != null) stage[0].close();
            });
            stage[0] = Modals.form(ctx, "Choose a key", view.node(), null);
        });
        return pill[0];
    }

    /**
     * A combination, picked on the same keyboard in chord mode: any keys, in the order clicked or pressed —
     * shown as chips, each removable, with Clear (2026-09-27; modifiers plus one key until then). Written on OK
     * in that order; a dialog left with nothing chosen writes nothing.
     */
    public static Node combo(ValueContext ctx) {
        Button[] pill = new Button[1];
        pill[0] = Pills.button(comboPill(ctx), () -> chooseCombo(ctx, ctx.value(Combo.class).orElse(null), combo -> {
            ctx.set(combo);
            pill[0].setText(combo.toString());
        }));
        return pill[0];
    }

    /**
     * The combination window: the keyboard in chord mode, and under it how long the keys are held (2026-09-27),
     * typed in milliseconds or set with a preset. OK hands {@code onChosen} the combo; nothing chosen, or a hold
     * that is not a number, hands it nothing.
     */
    private static void chooseCombo(ValueContext ctx, Combo initial, Consumer<Combo> onChosen) {
        Chord[] held = {initial == null ? Chord.EMPTY : chordOf(initial)};
        KeyboardView view = new KeyboardView(true, held[0], chord -> held[0] = chord);
        TextField hold = msField(initial == null ? Duration.ZERO : initial.hold());
        HBox presets = new HBox(4);
        for (long ms : new long[] {0, 50, 200, 1000}) {
            Button preset = new Button(ms + " ms");
            preset.setFocusTraversable(false);
            preset.setOnAction(e -> hold.setText(Long.toString(ms)));
            presets.getChildren().add(preset);
        }
        Label holdLabel = new Label("Hold");
        holdLabel.setTooltip(new Tooltip("How long every key stays down before they are released. 0 presses "
                + "and releases at once; a game that misses short presses needs 50 ms or more."));
        HBox holdRow = new HBox(8, holdLabel, hold, new Label("ms"), presets);
        holdRow.setAlignment(Pos.CENTER_LEFT);
        VBox body = new VBox(10, view.node(), holdRow);
        Modals.form(ctx, "Choose a key combination", body, () -> {
            Duration wait = holdOf(hold.getText());
            if (wait == null) return;
            held[0].combo().ifPresent(combo -> onChosen.accept(combo.held(wait)));
        });
    }

    /**
     * A whole-millisecond field that takes digits only, blank meaning none. It accepted anything and marked it
     * red until 2026-09-28, and OK closed the window regardless: a combination with a typo in its hold was
     * dropped whole, and a step's wait kept its old value without a word.
     */
    private static TextField msField(Duration initial) {
        TextField field = new TextField(Long.toString(initial.toMillis()));
        field.setPrefColumnCount(5);
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0,7}") ? change : null));
        return field;
    }

    /**
     * A key sequence (2026-09-27): a row per step — its combination as a pill that opens the combination
     * window, the wait after it in milliseconds, ✕ — dragged by ⠿ to change the order, and <i>Add step</i>.
     * Written on OK; a sequence left with no step writes nothing.
     */
    public static Node sequence(ValueContext ctx) {
        Button[] pill = new Button[1];
        pill[0] = Pills.button(sequencePill(ctx), () -> {
            List<KeySequence.Step>[] steps = sequenceSteps(ctx);
            VBox rows = new VBox(4);
            Runnable[] redraw = new Runnable[1];
            redraw[0] = () -> {
                rows.getChildren().clear();
                for (int i = 0; i < steps[0].size(); i++) rows.getChildren().add(stepRow(ctx, steps, i, redraw[0]));
                if (steps[0].isEmpty()) rows.getChildren().add(new Label("No steps yet"));
            };
            redraw[0].run();
            Button add = new Button("Add step");
            add.setOnAction(e -> chooseCombo(ctx, null, combo -> {
                List<KeySequence.Step> next = new ArrayList<>(steps[0]);
                next.add(KeySequence.step(combo, Duration.ZERO));
                steps[0] = List.copyOf(next);
                redraw[0].run();
            }));
            Label hint = new Label("Each combination is pressed, then the wait after it passes. Drag ⠿ to change "
                    + "the order.");
            hint.getStyleClass().add("dialog-hint-text");
            hint.setWrapText(true);
            VBox body = new VBox(8, rows, add, hint);
            body.setPrefWidth(460);
            Modals.form(ctx, "Choose a key sequence", body, () -> {
                if (steps[0].isEmpty()) return;
                KeySequence sequence = new KeySequence(steps[0]);
                ctx.set(sequence);
                pill[0].setText(sequence.toString());
            });
        });
        return pill[0];
    }

    @SuppressWarnings("unchecked")
    private static List<KeySequence.Step>[] sequenceSteps(ValueContext ctx) {
        return new List[] {ctx.value(KeySequence.class).map(KeySequence::steps).orElse(List.of())};
    }

    /** One step's row; every change replaces {@code steps[0]} and, where the row count changes, redraws. */
    private static Node stepRow(ValueContext ctx, List<KeySequence.Step>[] steps, int at, Runnable redraw) {
        KeySequence.Step step = steps[0].get(at);
        Button combo = new Button(step.combo().toString());
        combo.setOnAction(e -> chooseCombo(ctx, steps[0].get(at).combo(), chosen -> {
            steps[0] = replaced(steps[0], at, KeySequence.step(chosen, steps[0].get(at).after()));
            combo.setText(chosen.toString());
        }));
        TextField wait = msField(step.after());
        wait.textProperty().addListener((o, was, is) -> {
            Duration after = holdOf(is);
            if (after != null) steps[0] = replaced(steps[0], at, KeySequence.step(steps[0].get(at).combo(), after));
        });
        Button remove = new Button("✕");
        remove.getStyleClass().add("row-icon-button");
        remove.setTooltip(new Tooltip("Take this step out"));
        remove.setOnAction(e -> {
            List<KeySequence.Step> next = new ArrayList<>(steps[0]);
            next.remove(at);
            steps[0] = List.copyOf(next);
            redraw.run();
        });
        Label handle = new Label("⠿");
        handle.setTooltip(new Tooltip("Drag to change when this step runs"));
        HBox row = new HBox(8, handle, combo, new Label("then wait"), wait, new Label("ms"), remove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setOnDragDetected(e -> {
            Dragboard drag = row.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(Integer.toString(at));
            drag.setContent(content);
            e.consume();
        });
        row.setOnDragOver(e -> {
            if (e.getGestureSource() instanceof Node source && source.getParent() == row.getParent()) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        row.setOnDragDropped(e -> {
            boolean done = false;
            try {
                steps[0] = moved(steps[0], Integer.parseInt(e.getDragboard().getString()), at);
                done = true;
            } catch (RuntimeException ignored) {
                // Not one of these rows: nothing moves.
            }
            e.setDropCompleted(done);
            e.consume();
            if (done) redraw.run();
        });
        return row;
    }

    private static <T> List<T> replaced(List<T> items, int at, T item) {
        List<T> next = new ArrayList<>(items);
        next.set(at, item);
        return List.copyOf(next);
    }

    // --- shared ----------------------------------------------------------------------------------------

    private static <E extends Enum<E>> ToggleButton toggle(ValueContext ctx, ToggleGroup group, E constant,
                                                           String glyph, String tooltip, E current,
                                                           String styleClass) {
        ToggleButton button = new ToggleButton(glyph);
        button.setToggleGroup(group);
        button.setSelected(constant == current);
        button.setTooltip(new Tooltip(tooltip));
        button.getStyleClass().add(styleClass);
        if ("direction-pad-key".equals(styleClass)) button.setPrefSize(30, 30);
        // On the button rather than on the group: a group listener also fires for the de-selection half of
        // a switch, which would write the outgoing value a moment before the incoming one.
        button.setOnAction(e -> {
            Toggle chosen = group.getSelectedToggle();
            if (chosen == button) ctx.set(constant);
            else button.setSelected(true);       // a toggle group must not be emptied by clicking the pick
        });
        return button;
    }
}
