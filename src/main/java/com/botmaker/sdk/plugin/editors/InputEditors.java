package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.MouseButton;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The three input enums a bot's own API names — a {@link Direction}, a {@link Key}, a {@link MouseButton} —
 * drawn as the shapes they are rather than as three dropdowns.
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

    // --- key -------------------------------------------------------------------------------------------

    /**
     * A key as a type-to-search box over every constant the SDK has.
     *
     * <p>A pad is out of the question — there are well over a hundred keys — and a plain dropdown of them
     * is unusable, so this filters as the user types.
     *
     * <p>The filter keeps the current selection visible whatever the needle is: an item filtered out from
     * under the selection clears it, and a cleared selection blanks the field. The popup is shown only
     * while the user is actually narrowing, because showing it on the programmatic text change that follows
     * a pick reopens the list the pick just closed.
     *
     * <p><b>A name that matches no constant writes nothing.</b> Half a key is not a key, and the old
     * version normalising {@code "esca"} through the value registry is how a typo became a stored value.
     */
    public static Node key(ValueContext ctx) {
        Key current = ctx.value(Key.class).orElse(null);
        List<String> names = names(Key.values());

        ObservableList<String> all = FXCollections.observableArrayList(names);
        FilteredList<String> shown = new FilteredList<>(all, key -> true);

        ComboBox<String> box = new ComboBox<>();
        box.setItems(shown);
        box.setEditable(true);
        box.setVisibleRowCount(12);
        box.setPromptText("Type to search…");
        if (current != null) box.setValue(current.name());

        box.getEditor().textProperty().addListener((o, was, is) -> {
            String needle = is == null ? "" : is.trim().toUpperCase(Locale.ROOT);
            String chosen = box.getValue();
            shown.setPredicate(key -> needle.isEmpty()
                    || key.toUpperCase(Locale.ROOT).contains(needle)
                    || key.equals(chosen));
            if (!needle.isEmpty() && !needle.equalsIgnoreCase(chosen) && !box.isShowing()) box.show();
        });
        // Committed on a pick and on the field losing focus, never per keystroke: a value written while
        // "ESC" is half-typed is a different key from the one being reached for.
        box.setOnAction(e -> commitKey(ctx, box));
        box.getEditor().focusedProperty().addListener((o, had, has) -> {
            if (!has) commitKey(ctx, box);
        });
        return box;
    }

    /** The constant the typed text names exactly (case-insensitively), else the one last picked, else none. */
    private static void commitKey(ValueContext ctx, ComboBox<String> box) {
        String typed = box.getEditor().getText();
        for (Key constant : Key.values()) {
            if (constant.name().equalsIgnoreCase(typed == null ? "" : typed.trim())) {
                ctx.set(constant);
                return;
            }
        }
        String chosen = box.getValue();
        for (Key constant : Key.values()) {
            if (constant.name().equals(chosen)) {
                ctx.set(constant);
                return;
            }
        }
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

    private static <E extends Enum<E>> List<String> names(E[] constants) {
        return java.util.Arrays.stream(constants).map(Enum::name).toList();
    }
}
