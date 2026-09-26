package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.interaction.Combo;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.MouseButton;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            new Cell("WEST", "←", 0, 1), new Cell("EAST", "→", 2, 1));

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
        // One button per square. The two spellings share squares, and a Direction holding both would
        // otherwise stack two buttons on one cell, the second painting over the first.
        Set<String> taken = new HashSet<>();
        Set<String> placed = new HashSet<>();
        for (Cell cell : CELLS) {
            Direction constant = constantNamed(cell.name());
            if (constant == null) continue;
            if (!taken.add(cell.column() + "," + cell.row())) continue;
            placed.add(cell.name());
            pad.add(toggle(ctx, group, constant, cell.arrow(), constant.name(), current, "direction-pad-key"),
                    cell.column(), cell.row());
        }

        VBox box = new VBox(4, pad);
        box.getStyleClass().add("direction-pad");

        // A direction with no square still has to be reachable, so it goes in a row underneath as a named
        // button rather than quietly disappearing from the editor.
        HBox spare = new HBox(2);
        for (Direction constant : Direction.values()) {
            if (placed.contains(constant.name())) continue;
            spare.getChildren().add(
                    toggle(ctx, group, constant, constant.name(), constant.name(), current, "direction-pad-key"));
        }
        if (!spare.getChildren().isEmpty()) box.getChildren().add(spare);
        return box;
    }

    private static Direction constantNamed(String name) {
        for (Direction constant : Direction.values()) {
            if (constant.name().equals(name)) return constant;
        }
        return null;
    }

    // --- mouse button ----------------------------------------------------------------------------------

    private static final List<String[]> TOP = List.of(
            new String[]{"LEFT", "Left"}, new String[]{"MIDDLE", "Wheel"}, new String[]{"RIGHT", "Right"});
    private static final List<String[]> SIDE = List.of(
            new String[]{"BACK", "Back"}, new String[]{"FORWARD", "Forward"});

    /**
     * The mouse buttons as a labelled diagram: the two main buttons and the wheel across the top, the side
     * buttons under them.
     *
     * <p><b>What this names is what the button does, never where it sits.</b> A mouse with two thumb
     * buttons, a left-handed mouse, a mouse the vendor's driver has remapped — in all of them the OS
     * reports the button the user configured, so a bot that says Back keeps working on a mouse whose back
     * button is somewhere else, and nothing has to ask which layout the machine running the bot has.
     */
    public static Node mouseButton(ValueContext ctx) {
        MouseButton current = ctx.value(MouseButton.class).orElse(null);
        ToggleGroup group = new ToggleGroup();

        HBox top = new HBox(2);
        HBox side = new HBox(2);
        Set<String> placed = new HashSet<>();
        for (String[] entry : TOP) addButton(ctx, group, top, entry, current, placed);
        for (String[] entry : SIDE) addButton(ctx, group, side, entry, current, placed);

        VBox box = new VBox(4, top);
        box.getStyleClass().add("mouse-diagram");
        if (!side.getChildren().isEmpty()) {
            Label hint = new Label("side buttons");
            hint.getStyleClass().add("dialog-hint-text");
            box.getChildren().addAll(side, hint);
        }

        // A button this diagram has no place for is still reachable, for the reason the direction pad's
        // spare row exists: an editor that cannot express a value the type has is worse than an ugly one.
        HBox spare = new HBox(2);
        for (MouseButton constant : MouseButton.values()) {
            if (placed.contains(constant.name())) continue;
            spare.getChildren().add(
                    toggle(ctx, group, constant, constant.name(), constant.name(), current, "mouse-diagram-key"));
        }
        if (!spare.getChildren().isEmpty()) box.getChildren().add(spare);
        return box;
    }

    private static void addButton(ValueContext ctx, ToggleGroup group, HBox into, String[] entry,
                                  MouseButton current, Set<String> placed) {
        for (MouseButton constant : MouseButton.values()) {
            if (!constant.name().equals(entry[0])) continue;
            placed.add(constant.name());
            into.getChildren().add(
                    toggle(ctx, group, constant, entry[1], constant.name(), current, "mouse-diagram-key"));
        }
    }

    // --- key and combination ---------------------------------------------------------------------------

    /** The key pill: the cap, else the source as written, else an invitation. */
    static String keyPill(ValueContext ctx) {
        return ctx.value(Key.class).map(Key::label)
                .orElseGet(() -> Slots.raw(ctx).isBlank() ? "Choose a key…" : Slots.raw(ctx));
    }

    /** The combination pill: {@code Ctrl+Shift+S}, else the source as written, else an invitation. */
    static String comboPill(ValueContext ctx) {
        return ctx.value(Combo.class).map(Combo::toString)
                .orElseGet(() -> Slots.raw(ctx).isBlank() ? "Choose keys…" : Slots.raw(ctx));
    }

    /** The chord the popup opens on: the value's, or nothing — never a guess at an expression the host could not read. */
    static Chord chordOf(ValueContext ctx) {
        return ctx.value(Combo.class).map(Chord::of).orElse(Chord.EMPTY);
    }

    /**
     * A key, picked on a drawn keyboard ({@link KeyboardView}): click the cap, press the key, or search. The
     * choice is the answer, so the window closes on it. It was a type-to-search dropdown of constant names
     * until 2026-09-26; the search stays, and a name that matches no key still writes nothing.
     */
    public static Node key(ValueContext ctx) {
        Button[] pill = new Button[1];
        pill[0] = Pills.button(keyPill(ctx), () -> {
            Chord initial = ctx.value(Key.class).map(k -> new Chord(Set.of(), k)).orElse(Chord.EMPTY);
            Stage[] stage = new Stage[1];
            KeyboardView view = new KeyboardView(false, initial, chord -> {
                if (chord.main() == null) return;
                ctx.set(chord.main());
                pill[0].setText(chord.main().label());
                if (stage[0] != null) stage[0].close();
            });
            stage[0] = Modals.form(ctx, "Choose a key", view.node(), null);
        });
        return pill[0];
    }

    /**
     * A combination, picked on the same keyboard in chord mode: Ctrl, Alt, Shift and Meta toggle and one other
     * key is kept, or the whole chord is pressed at once. Written on OK, modifiers first; a dialog left with
     * nothing chosen writes nothing.
     */
    public static Node combo(ValueContext ctx) {
        Button[] pill = new Button[1];
        pill[0] = Pills.button(comboPill(ctx), () -> {
            Chord[] held = {chordOf(ctx)};
            KeyboardView view = new KeyboardView(true, held[0], chord -> held[0] = chord);
            Modals.form(ctx, "Choose a key combination", view.node(), () -> held[0].combo().ifPresent(combo -> {
                ctx.set(combo);
                pill[0].setText(combo.toString());
            }));
        });
        return pill[0];
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
