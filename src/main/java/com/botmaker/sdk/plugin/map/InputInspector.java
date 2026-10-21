package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.ScreenDesk;
import com.botmaker.sdk.plugin.assist.ScreenDesk.Trying;
import com.botmaker.sdk.plugin.assist.ScreenEdits;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The input picked on 🗺 Screens' board, edited by its type under its row: what it does (its result, information only,
 * comes and goes, never, locked), a button's looks (each with its state, in order), whether the screen shows it more
 * than once and the copy each of its ways goes through, the strip or tree a button is in, a hotkey's key, a strip's
 * members and scroll, a tree's nodes (in order, what unlocks each, where each sits, dragged in its drawing) and scroll,
 * and ▶ Try with try_input's own settings; and the screen panel's ＋ Strip… and ＋ Tree…. Each change is one
 * {@link ScreenEdits} write, as the tools make it, so ↶ undoes it.
 */
final class InputInspector {

    /** What the inspector acts through: the panel's own. */
    interface Panel {
        /** An edit answered: said, and the map read again. */
        void wrote(String said);

        /** {@code work} run off the FX thread while {@code doing} is said, on {@code constant}'s card. */
        void run(String doing, String constant, Supplier<String> work);

        /** A picture's thumb. */
        Node thumb(String picture);

        /** A picture's thumb {@code side} pixels big. */
        Node thumb(String picture, double side);

        /** A picture's size as cut, in the game's pixels; empty for one not cut. */
        Size pictureSize(String picture);

        /** How wide the panel draws what it shows. */
        double width();

        /** {@code row}, an input of {@code constant}, picked instead. */
        void pick(String constant, Row row);

        /** What ▶ Try acts through: the project's capture, made on the FX thread. */
        AgentContext tryContext();
    }

    /** What an input did when tried, as the inspector sets it; opening a screen is said by the ways it opens. */
    enum Result {
        UNTRIED("Untried"),
        NOTHING("Tried, opens nothing"),
        LEAVES("Leaves for a screen reached otherwise"),
        OPENS("Opens a screen");

        private final String displayName;

        Result(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }

        /** What {@code input}, of a screen whose ways say whether it {@code opens} one, did. */
        static Result of(Input input, boolean opens) {
            return opens ? OPENS : input.isTried() ? NOTHING : input.isLeaving() ? LEAVES : UNTRIED;
        }
    }

    /** How a strip is scrolled, as its editor picks it; none takes it off. */
    enum ScrollKind {
        WHEEL("The wheel"),
        DRAG("A drag"),
        NONE("None: not a strip");

        private final String displayName;

        ScrollKind(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }

        /** How {@code scroll} goes; none for a scroll that moves nothing. */
        static ScrollKind of(Scroll scroll) {
            return scroll == null || !scroll.moves() ? NONE : scroll.drag() == null ? WHEEL : DRAG;
        }
    }

    /** How a tree is scrolled, as its editor picks it; none when it fits on the screen, still a tree. */
    enum TreeScroll {
        DRAG("A drag"),
        WHEEL("The wheel"),
        NONE("None: it fits on the screen");

        private final String displayName;

        TreeScroll(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }

        /** How {@code tree} goes. */
        static TreeScroll of(Tree tree) {
            return tree.isDragged() ? DRAG : tree.notches() != 0 ? WHEEL : NONE;
        }
    }

    /** The picks a way through a repeated button may take: {@link Pick.Mode#UNTIL} is a body's, never a way's. */
    static final List<Pick.Mode> WAY_PICKS = List.of(Pick.Mode.FIRST, Pick.Mode.NTH, Pick.Mode.EACH,
            Pick.Mode.UNTIL_NONE);

    private final StudioServices services;
    private final ScreenGraph graph;
    private final ScreenHands hands;
    private final boolean busy;
    private final Panel panel;

    InputInspector(StudioServices services, ScreenGraph graph, ScreenHands hands, boolean busy, Panel panel) {
        this.services = services;
        this.graph = graph;
        this.hands = hands;
        this.busy = busy;
        this.panel = panel;
    }

    /** The editors of {@code row}, an input of {@code constant}, under its row. */
    List<Node> of(String constant, Row row) {
        String field = row.field();
        Input input = graph.input(constant, field).orElse(row.input());
        List<Node> all = new ArrayList<>();
        all.add(does(constant, field, input));
        switch (input) {
            case Button button -> {
                all.add(heading("Pressed by"));
                all.add(gesture(constant, field, button));
                all.add(heading("Looks"));
                all.add(looks(constant, field, button));
                all.add(heading("Copies"));
                all.add(copies(constant, field, button));
                all.add(heading("Strip or tree"));
                all.add(inGroup(constant, button));
            }
            case Hotkey hotkey -> {
                all.add(heading("Keys"));
                all.add(key(constant, field, hotkey));
            }
            case Strip strip -> {
                all.add(heading("Members"));
                all.add(members(constant, field, strip));
                all.add(heading("Scroll"));
                all.add(scroll(constant, field, strip));
            }
            case Tree tree -> {
                all.add(heading("Nodes"));
                all.add(nodes(constant, field, tree));
                all.add(heading("Scroll"));
                all.add(treeScroll(constant, field, tree));
            }
        }
        all.add(heading("Try"));
        all.add(trying(constant, row, input));
        all.forEach(node -> node.setDisable(busy));
        return all;
    }

    // --- what it does ---

    private Node does(String constant, String field, Input input) {
        boolean opens = graph.opens(constant, field).isPresent();
        ComboBox<Result> result = new ComboBox<>(FXCollections.observableArrayList(Result.values()));
        result.setConverter(named(Result::displayName));
        result.setValue(Result.of(input, opens));
        result.setOnAction(e -> {
            Result now = result.getValue();
            if (now == null || now == Result.of(input, opens)) return;
            switch (now) {
                case UNTRIED -> set(constant, field, ScreenEdits.untried(input));
                case NOTHING -> set(constant, field, ScreenEdits.untried(input).tried());
                case LEAVES -> set(constant, field, ScreenEdits.untried(input).leaves());
                // A screen it opens is a new one, or one of the map: the same choice ⋯ ▸ Result offers. Until it is
                // written the combo says what the input did; the panel draws again once it is.
                case OPENS -> {
                    result.setValue(Result.of(input, opens));
                    hands.newScreen(constant, field);
                }
            }
        });
        Label resultSaid = new Label("Result");
        HBox resultLine = line(resultSaid, result);

        CheckBox info = check("Information only", "It only tells: help, rules, a leaderboard. Tried last",
                input.isInfo(), on -> set(constant, field, ScreenEdits.info(input, on)));
        CheckBox sometimes = check("Comes and goes", "The screen shows it only at times", input.comesAndGoes(),
                on -> set(constant, field, ScreenEdits.sometimes(input, on)));

        TextField why = new TextField(input.never() == null ? "" : input.never());
        why.setPromptText("why: it spends gems, real money");
        HBox.setHgrow(why, Priority.ALWAYS);
        CheckBox never = new CheckBox("Never, because");
        never.setSelected(input.never() != null);
        never.setTooltip(new Tooltip("A run never presses it, and Discovery never tries it"));
        Runnable writeNever = () -> {
            if (!never.isSelected()) {
                if (input.never() != null) set(constant, field, ScreenEdits.untried(input));
                return;
            }
            if (why.getText().isBlank()) {
                never.setSelected(false);
                panel.wrote("Say why it is never pressed, then press Enter.");
                return;
            }
            if (!why.getText().strip().equals(input.never())) {
                set(constant, field, ScreenEdits.untried(input).never(why.getText().strip()));
            }
        };
        never.setOnAction(e -> writeNever.run());
        why.setOnAction(e -> {
            never.setSelected(true);
            writeNever.run();
        });

        ComboBox<String> proof = new ComboBox<>(FXCollections.observableArrayList(ScreenEdits.pictures(services)));
        proof.setEditable(true);
        proof.setPromptText("the picture proving it (a padlock)");
        proof.setValue(input.lock() == null ? null : input.lock().id());
        HBox.setHgrow(proof, Priority.ALWAYS);
        CheckBox locked = new CheckBox("Locked, shown by");
        locked.setSelected(input.lock() != null);
        locked.setTooltip(new Tooltip("Locked on this account: a run never presses it, and Discovery never tries it"));
        // The lock last written from here: an editable combo commits on focus loss too, and a click on the box after
        // typing would write it twice against the input as it was drawn.
        String[] lock = {input.lock() == null ? null : input.lock().id()};
        Runnable writeLock = () -> {
            String picture = proof.getValue() == null ? "" : proof.getValue().strip();
            if (!locked.isSelected()) {
                if (lock[0] != null) set(constant, field, ScreenEdits.untried(input));
                lock[0] = null;
                return;
            }
            if (picture.isEmpty()) {
                locked.setSelected(false);
                panel.wrote("Pick the picture proving it locked.");
                return;
            }
            if (!picture.equals(lock[0])) {
                lock[0] = picture;
                set(constant, field, ScreenEdits.untried(input).locked(ScreenEdits.picture(services, picture)));
            }
        };
        locked.setOnAction(e -> writeLock.run());
        // A picture picked writes only while the box is ticked; ticking it writes the picture shown.
        proof.setOnAction(e -> {
            if (locked.isSelected()) writeLock.run();
        });
        return new VBox(6, resultLine, new FlowPane(12, 6, info, sometimes), line(never, why), line(locked, proof));
    }

    // --- a button's looks ---

    private Node looks(String constant, String field, Button button) {
        VBox box = new VBox(6);
        if (button.picture() != null) {
            Label usual = new Label(button.picture().id());
            usual.getStyleClass().add("screen-row-label");
            HBox.setHgrow(usual, Priority.ALWAYS);
            box.getChildren().add(line(panel.thumb(button.picture().id()), usual, pill("usual")));
        }
        List<Look> looks = button.looks();
        for (int i = 0; i < looks.size(); i++) {
            int at = i;
            Look look = looks.get(i);
            Label name = new Label(look.picture().id());
            name.getStyleClass().add("screen-row-label");
            name.setMinWidth(0);
            HBox.setHgrow(name, Priority.ALWAYS);
            ComboBox<State> state = new ComboBox<>(FXCollections.observableArrayList(State.values()));
            state.setConverter(named(State::displayName));
            state.setValue(look.state());
            state.setOnAction(e -> {
                if (state.getValue() != null && state.getValue() != look.state()) {
                    set(constant, field, button.looks(restated(looks, at, state.getValue())));
                }
            });
            javafx.scene.control.Button up = action("↑", "Before the look above it",
                    () -> set(constant, field, button.looks(moved(looks, at, at - 1))));
            up.setDisable(at == 0);
            javafx.scene.control.Button down = action("↓", "After the look below it",
                    () -> set(constant, field, button.looks(moved(looks, at, at + 1))));
            down.setDisable(at == looks.size() - 1);
            javafx.scene.control.Button off = action("✕", "No longer a look of " + button.label(),
                    () -> set(constant, field, button.looks(without(looks, at))));
            box.getChildren().add(line(panel.thumb(look.picture().id()), name, state, up, down, off));
        }
        javafx.scene.control.Button add = action("＋ Look from a picture…", "Another look of " + button.label()
                + ", and what it means: greyed out, with a badge, picked", () -> askLook(constant, field, button));
        CheckBox cycles = check("Cycles", MapGuide.CYCLES, button.cycles(),
                on -> set(constant, field, ScreenEdits.cycles(button, on)));
        cycles.setDisable(looks.isEmpty() && !button.cycles());
        box.getChildren().add(new FlowPane(12, 6, add, cycles));
        return box;
    }

    private void askLook(String constant, String field, Button button) {
        ComboBox<String> picture = new ComboBox<>(FXCollections.observableArrayList(ScreenEdits.pictures(services)));
        picture.setEditable(true);
        ComboBox<State> state = new ComboBox<>(FXCollections.observableArrayList(State.values()));
        state.setConverter(named(State::displayName));
        state.setValue(State.DISABLED);
        Modals.form(services, "Another look of " + button.label(), new VBox(8, new Label("The picture of it as it "
                + "looks then"), picture, new Label("What that look means"), state), () -> {
            String id = picture.getValue() == null ? "" : picture.getValue().strip();
            if (id.isEmpty()) {
                panel.wrote("Pick the picture of the look.");
                return;
            }
            if (button.picture() != null && id.equals(button.picture().id())) {
                panel.wrote(id + " is its usual look already.");
                return;
            }
            set(constant, field, button.withLook(Look.of(ScreenEdits.picture(services, id), state.getValue())));
        });
    }

    // --- a button's copies ---

    private Node copies(String constant, String field, Button button) {
        List<ScreenGraph.Lead> leads = graph.leads(constant, field);
        CheckBox repeated = new CheckBox("Repeated");
        repeated.setSelected(button.repeats());
        repeated.setTooltip(new Tooltip(MapGuide.REPEATED));
        repeated.setOnAction(e -> {
            // A way through a copy other than the first would name a copy the button no longer has.
            if (!repeated.isSelected() && leads.stream().anyMatch(lead -> lead.pick() != null)) {
                repeated.setSelected(true);
                panel.wrote(ScreenEdits.NOT_WRITTEN + "a way it opens goes through a copy of its own: set each "
                        + "one's Copy… to the first first.");
                return;
            }
            set(constant, field, button.repeats(repeated.isSelected()));
        });
        VBox box = new VBox(6, repeated);
        if (!button.repeats()) return box;
        if (leads.isEmpty()) box.getChildren().add(text("It leads nowhere yet: its ways name their copy once it does."));
        for (ScreenGraph.Lead lead : leads) {
            Label said = new Label("→ " + graph.shown(lead.to()) + " through "
                    + (lead.pick() == null ? Pick.first() : lead.pick()).label());
            said.getStyleClass().add("screen-row-label");
            said.setMinWidth(0);
            said.setWrapText(true);
            HBox.setHgrow(said, Priority.ALWAYS);
            box.getChildren().add(line(said, action("Copy…", MapGuide.WAY_COPY,
                    () -> askCopy(constant, field, lead))));
        }
        return box;
    }

    private void askCopy(String constant, String field, ScreenGraph.Lead lead) {
        Pick now = lead.pick() == null ? Pick.first() : lead.pick();
        if (!WAY_PICKS.contains(now.mode())) {
            panel.wrote("That way goes " + now.label() + ", a pick 🗺 Screens does not edit: change it in Screens.java.");
            return;
        }
        ComboBox<Pick.Mode> mode = new ComboBox<>(FXCollections.observableArrayList(WAY_PICKS));
        mode.setConverter(named(InputInspector::modeName));
        mode.setValue(now.mode());
        Spinner<Integer> nth = new Spinner<>(1, 99, Math.max(1, now.nth()));
        nth.setEditable(true);
        nth.disableProperty().bind(mode.valueProperty().isNotEqualTo(Pick.Mode.NTH));
        ComboBox<Order> order = new ComboBox<>(FXCollections.observableArrayList(Order.values()));
        order.setConverter(named(InputInspector::orderName));
        order.setValue(now.order());
        order.disableProperty().bind(mode.valueProperty().isEqualTo(Pick.Mode.UNTIL_NONE));
        Modals.form(services, "The copy it goes through", new VBox(8, new Label("Which copy of " + field + " opens "
                + graph.shown(lead.to())), mode, line(new Label("#"), nth), new Label("Counted from"), order), () -> {
            nth.commitValue();
            Pick pick = wayPick(mode.getValue(), nth.getValue(), order.getValue());
            panel.wrote(ScreenEdits.setWayPick(services, lead.to(), constant, field, pick));
        });
    }

    /** The pick a way takes, as its factory writes it: a field the mode leaves out is its default. */
    static Pick wayPick(Pick.Mode mode, int nth, Order order) {
        return switch (mode == null ? Pick.Mode.FIRST : mode) {
            case NTH -> Pick.nth(nth, order);
            case EACH -> Pick.each(order);
            case UNTIL_NONE -> Pick.untilNone();
            case FIRST, UNTIL -> Pick.first(order);
        };
    }

    // --- how a button is pressed ---

    private Node gesture(String constant, String field, Button button) {
        ComboBox<Gesture> gesture = new ComboBox<>(FXCollections.observableArrayList(Arrays.stream(Gesture.values())
                .filter(g -> g != Gesture.UNKNOWN).toList()));
        gesture.setConverter(named(Gesture::displayName));
        gesture.setValue(button.gesture());
        gesture.setTooltip(new Tooltip(MapGuide.GESTURE));
        Spinner<Integer> ms = new Spinner<>(50, 60_000, (int) button.holdTime().toMillis(), 50);
        ms.setEditable(true);
        ms.setPrefWidth(90);
        Label unit = new Label("ms");
        boolean holds = button.gesture() == Gesture.HOLD;
        ms.setVisible(holds);
        ms.setManaged(holds);
        unit.setVisible(holds);
        unit.setManaged(holds);
        gesture.setOnAction(e -> {
            if (gesture.getValue() != null && gesture.getValue() != button.gesture()) {
                panel.wrote(ScreenEdits.setGesture(services, constant, field, gesture.getValue(), null));
            }
        });
        // The spinner commits on Enter, an arrow and focus loss alike: written once its time changed.
        ms.valueProperty().addListener((o, was, is) -> {
            if (is != null && is != button.holdTime().toMillis()) {
                panel.wrote(ScreenEdits.setGesture(services, constant, field, Gesture.HOLD, Duration.ofMillis(is)));
            }
        });
        return line(gesture, ms, unit);
    }

    // --- a hotkey's keys ---

    private Node key(String constant, String field, Hotkey hotkey) {
        List<Key> keys = hotkey.combo() == null ? List.of() : hotkey.combo().keys();
        Key main = keys.reversed().stream().filter(k -> !MODIFIERS.contains(k)).findFirst()
                .orElse(keys.isEmpty() ? null : keys.getLast());
        ComboBox<Key> key = keyPick(main);
        List<CheckBox> with = MODIFIERS.stream().map(m -> {
            CheckBox tick = new CheckBox(m.label());
            tick.setSelected(keys.contains(m) && m != main);
            return tick;
        }).toList();
        Spinner<Integer> hold = new Spinner<>(0, 60_000,
                hotkey.combo() == null ? 0 : (int) hotkey.combo().hold().toMillis(), 50);
        hold.setEditable(true);
        hold.setPrefWidth(90);
        hold.setTooltip(new Tooltip(MapGuide.KEY_HOLD));
        // The keys last written from here: the editable combo commits on focus loss and on Enter both.
        Combo[] written = {hotkey.combo()};
        Runnable write = () -> {
            Key now = key.getValue();
            if (now == null || hold.getValue() == null) return;
            Combo combo = new Combo(retyped(keys, now, MODIFIERS.stream()
                    .filter(m -> with.get(MODIFIERS.indexOf(m)).isSelected()).toList()),
                    Duration.ofMillis(hold.getValue()));
            if (combo.equals(written[0])) return;
            Optional<String> taken = keyTaken(graph.screens().get(constant), field, combo);
            if (taken.isPresent()) {
                panel.wrote(ScreenEdits.NOT_WRITTEN + taken.get() + " is pressed by " + combo.caps() + " already.");
                return;
            }
            written[0] = combo;
            set(constant, field, hotkey.combo(combo));
        };
        key.setOnAction(e -> write.run());
        with.forEach(tick -> tick.setOnAction(e -> write.run()));
        hold.valueProperty().addListener((o, was, is) -> write.run());
        List<Node> row = new ArrayList<>(with);
        row.add(key);
        return new VBox(6, line(row.toArray(Node[]::new)), line(new Label("held"), hold, new Label("ms")));
    }

    /** The keys a hotkey's ticks add to its key, in the order they go down. */
    private static final List<Key> MODIFIERS = List.of(Key.CTRL, Key.SHIFT, Key.ALT, Key.META);

    /**
     * {@code keys} as the editor changed them, in their own order: its last key that is no modifier made {@code main}
     * (added last when it has none), each modifier kept only while {@code ticked}, and a modifier newly ticked put
     * first. A modifier that is {@code main} itself stays as its key.
     */
    static List<Key> retyped(List<Key> keys, Key main, List<Key> ticked) {
        int last = -1;
        for (int i = 0; i < keys.size(); i++) if (!MODIFIERS.contains(keys.get(i))) last = i;
        List<Key> now = new ArrayList<>();
        ticked.stream().filter(m -> m != main && !keys.contains(m)).forEach(now::add);
        boolean placed = false;
        for (int i = 0; i < keys.size(); i++) {
            Key k = i == last ? main : keys.get(i);
            if (i == last) placed = true;
            else if (MODIFIERS.contains(k) && (k == main || !ticked.contains(k))) continue;
            if (!now.contains(k)) now.add(k);
        }
        if (!placed && !now.contains(main)) now.add(main);
        return now;
    }

    /** The field of {@code screen}, other than {@code field}, that is a hotkey of {@code keys}; empty for none. */
    static Optional<String> keyTaken(MapScreen screen, String field, Combo keys) {
        if (screen == null) return Optional.empty();
        return screen.inputs().entrySet().stream()
                .filter(e -> !e.getKey().equals(field) && e.getValue() instanceof Hotkey h && h.presses(keys))
                .map(Map.Entry::getKey).findFirst();
    }

    /** Every key, typed by its label or name ({@code Q}, {@code page up}) or picked from the list. */
    static ComboBox<Key> keyPick(Key initial) {
        ComboBox<Key> key = new ComboBox<>(FXCollections.observableArrayList(Arrays.asList(Key.values())));
        key.setEditable(true);
        key.setVisibleRowCount(16);
        key.setPromptText("a key: Q, Escape, F5");
        key.setConverter(new StringConverter<>() {
            @Override
            public String toString(Key value) {
                return value == null ? "" : value.label();
            }

            @Override
            public Key fromString(String typed) {
                return typed == null ? null : Key.named(typed.strip()).orElse(null);
            }
        });
        key.setValue(initial);
        return key;
    }

    // --- the strip or tree a button is in ---

    private Node inGroup(String constant, Button button) {
        MapScreen screen = graph.screens().get(constant);
        Optional<String> held = screen == null ? Optional.empty() : screen.stripFieldOf(button);
        if (held.isPresent()) {
            Strip strip = screen.strips().get(held.get());
            Label said = text("In " + held.get() + ", " + (indexOf(strip.members(), button.picture()) + 1) + " of "
                    + strip.members().size());
            HBox.setHgrow(said, Priority.ALWAYS);
            return line(said, action("Out of the strip", "Looked for at its place again", () -> setMembers(constant,
                    held.get(), strip, strip.members().stream().filter(m -> !m.id().equals(button.picture().id()))
                            .toList())));
        }
        Optional<Map.Entry<String, Tree>> tree = screen == null || button.picture() == null ? Optional.empty()
                : screen.trees().entrySet().stream().filter(e -> e.getValue().has(button.picture())).findFirst();
        if (tree.isPresent()) {
            String field = tree.get().getKey();
            Tree in = tree.get().getValue();
            Label said = text("Node " + (indexOf(in.members(), button.picture()) + 1) + " of " + in.members().size()
                    + " in " + field);
            HBox.setHgrow(said, Priority.ALWAYS);
            return line(said, action("Out of the tree", "Looked for at its place again; its lines and offsets go",
                    () -> setNodes(constant, field, in, in.nodes().stream().filter(n -> n.picture() == null
                            || !n.picture().id().equals(button.picture().id())).toList())));
        }
        if (button.picture() == null) return text("It has no picture to look for along a strip or in a tree.");
        return new FlowPane(8, 6, action("Strip…", MapGuide.IN_STRIP, () -> askStrip(constant, screen,
                button.picture())), action("Tree…", MapGuide.IN_TREE, () -> askTree(constant, screen,
                button.picture())));
    }

    private void askTree(String constant, MapScreen screen, ImageTemplate picture) {
        List<String> fields = new ArrayList<>(screen == null ? List.of() : screen.trees().keySet());
        String fresh = "A new tree";
        fields.add(fresh);
        ComboBox<String> which = new ComboBox<>(FXCollections.observableArrayList(fields));
        which.setValue(fields.getFirst());
        ComboBox<TreeScroll> kind = new ComboBox<>(FXCollections.observableArrayList(TreeScroll.values()));
        kind.setConverter(named(TreeScroll::displayName));
        kind.setValue(TreeScroll.DRAG);
        Spinner<Integer> notches = new Spinner<>(-20, 20, -5);
        notches.setEditable(true);
        notches.setPrefWidth(80);
        HBox wheel = line(new Label("Notches"), notches);
        shownFor(wheel, kind, TreeScroll.WHEEL);
        VBox made = new VBox(8, line(new Label("A new one is scrolled by"), kind), wheel);
        made.disableProperty().bind(which.valueProperty().isNotEqualTo(fresh));
        Modals.form(services, "The tree " + picture.id() + " is in", new VBox(8, new Label("Make it a node of"),
                which, made), () -> {
            notches.commitValue();
            Tree had = fresh.equals(which.getValue()) || screen == null ? null : screen.trees().get(which.getValue());
            if (had != null) {
                setNodes(constant, which.getValue(), had, Stream.concat(had.nodes().stream(),
                        Stream.of(com.botmaker.sdk.api.screens.Node.of(picture))).toList());
            } else if (kind.getValue() == TreeScroll.WHEEL && notches.getValue() == 0) {
                panel.wrote(ScreenEdits.NOT_WRITTEN + "a tree the wheel scrolls turns by some notches: give them.");
            } else {
                tree(constant, null, scrolled(Tree.of(List.of(com.botmaker.sdk.api.screens.Node.of(picture))),
                        kind.getValue(), notches.getValue()));
            }
        });
    }

    private void askStrip(String constant, MapScreen screen, ImageTemplate picture) {
        List<String> fields = new ArrayList<>(screen == null ? List.of() : screen.strips().keySet());
        String fresh = "A new strip";
        fields.add(fresh);
        ComboBox<String> which = new ComboBox<>(FXCollections.observableArrayList(fields));
        which.setValue(fields.getFirst());
        Spinner<Integer> notches = new Spinner<>(-20, 20, -5);
        notches.setEditable(true);
        notches.disableProperty().bind(which.valueProperty().isNotEqualTo(fresh));
        Modals.form(services, "The strip " + picture.id() + " is in", new VBox(8, new Label("Put it last in"), which,
                line(new Label("A new one turns the wheel"), notches, new Label("notches"))), () -> {
            notches.commitValue();
            Strip had = fresh.equals(which.getValue()) || screen == null ? null : screen.strips().get(which.getValue());
            if (had != null) {
                setMembers(constant, which.getValue(), had, Stream.concat(had.members().stream(), Stream.of(picture))
                        .toList());
            } else if (notches.getValue() == 0) {
                panel.wrote(ScreenEdits.NOT_WRITTEN + "a new strip turns by some notches of the wheel: give them.");
            } else {
                strip(constant, null, Strip.of(Scroll.wheel(notches.getValue()), List.of(picture)));
            }
        });
    }

    /**
     * ＋ Strip… on the screen panel: a new strip of {@code constant} of the buttons ticked, in the screen's order, and
     * how it scrolls.
     */
    void askNewStrip(String constant) {
        List<ImageTemplate> free = joinable(graph.screens().get(constant));
        if (noneFree(constant, free)) return;
        List<CheckBox> ticks = ticks(free);
        ScrollPick pick = ScrollPick.of(Scroll.wheel(-5), List.of(ScrollKind.WHEEL, ScrollKind.DRAG));
        Modals.form(services, "A strip of " + graph.shown(constant), new VBox(8, new Label("Its buttons, in order"),
                new VBox(4, ticks.toArray(Node[]::new)), new Label("Scrolled by"), pick.node()), () -> {
            List<ImageTemplate> members = ticked(free, ticks);
            Optional<Scroll> scroll = pick.scroll();
            if (members.isEmpty()) {
                panel.wrote(ScreenEdits.NOT_WRITTEN + "tick the buttons the strip holds.");
            } else if (scroll.isEmpty()) {
                panel.wrote(ScreenEdits.NOT_WRITTEN + "a new strip turns by some notches of the wheel: give them.");
            } else {
                strip(constant, null, Strip.of(scroll.get(), members));
            }
        });
    }

    /** ＋ Tree… on the screen panel: a new tree of {@code constant} of the buttons ticked, and how it scrolls. */
    void askNewTree(String constant) {
        List<ImageTemplate> free = joinable(graph.screens().get(constant));
        if (noneFree(constant, free)) return;
        List<CheckBox> ticks = ticks(free);
        ComboBox<TreeScroll> kind = new ComboBox<>(FXCollections.observableArrayList(TreeScroll.values()));
        kind.setConverter(named(TreeScroll::displayName));
        kind.setValue(TreeScroll.DRAG);
        Spinner<Integer> notches = new Spinner<>(-20, 20, -5);
        notches.setEditable(true);
        notches.setPrefWidth(80);
        HBox wheel = line(new Label("Notches"), notches);
        shownFor(wheel, kind, TreeScroll.WHEEL);
        Modals.form(services, "A tree of " + graph.shown(constant), new VBox(8, new Label("Its nodes, in order"),
                new VBox(4, ticks.toArray(Node[]::new)), line(new Label("Scrolled by"), kind), wheel), () -> {
            notches.commitValue();
            List<ImageTemplate> nodes = ticked(free, ticks);
            if (nodes.isEmpty()) {
                panel.wrote(ScreenEdits.NOT_WRITTEN + "tick the buttons that are the tree's nodes.");
            } else if (kind.getValue() == TreeScroll.WHEEL && notches.getValue() == 0) {
                panel.wrote(ScreenEdits.NOT_WRITTEN + "a tree the wheel scrolls turns by some notches: give them.");
            } else {
                tree(constant, null, scrolled(Tree.of(nodes.stream().map(com.botmaker.sdk.api.screens.Node::of)
                        .toList()), kind.getValue(), notches.getValue()));
            }
        });
    }

    /** Whether {@code free}, the buttons of {@code constant} a new strip or tree may take, is empty: said so. */
    private boolean noneFree(String constant, List<ImageTemplate> free) {
        if (!free.isEmpty()) return false;
        panel.wrote(ScreenEdits.NOT_WRITTEN + "every button of " + graph.shown(constant) + " with a picture is in a "
                + "strip or a tree already: ✂ Cut inputs first.");
        return true;
    }

    /** A tick for each of {@code free}, none ticked. */
    private static List<CheckBox> ticks(List<ImageTemplate> free) {
        return free.stream().map(p -> new CheckBox(p.id())).toList();
    }

    /** Those of {@code free} whose tick is on, in order. */
    private static List<ImageTemplate> ticked(List<ImageTemplate> free, List<CheckBox> ticks) {
        List<ImageTemplate> on = new ArrayList<>();
        for (int i = 0; i < free.size(); i++) if (ticks.get(i).isSelected()) on.add(free.get(i));
        return on;
    }

    // --- a strip's members ---

    private Node members(String constant, String field, Strip strip) {
        VBox box = new VBox(6);
        List<ImageTemplate> members = strip.members();
        for (int i = 0; i < members.size(); i++) {
            int at = i;
            ImageTemplate member = members.get(i);
            Label name = new Label(member.id());
            name.getStyleClass().add("screen-row-label");
            name.setMinWidth(0);
            HBox.setHgrow(name, Priority.ALWAYS);
            javafx.scene.control.Button up = action("↑", "Before the button above it",
                    () -> setMembers(constant, field, strip, moved(members, at, at - 1)));
            up.setDisable(at == 0);
            javafx.scene.control.Button down = action("↓", "After the button below it",
                    () -> setMembers(constant, field, strip, moved(members, at, at + 1)));
            down.setDisable(at == members.size() - 1);
            javafx.scene.control.Button off = action("✕", "Out of the strip: looked for at its place again",
                    () -> setMembers(constant, field, strip, without(members, at)));
            box.getChildren().add(line(panel.thumb(member.id()), name, up, down, off));
        }
        List<ImageTemplate> free = joinable(graph.screens().get(constant));
        javafx.scene.control.Button add = action("＋ Member…", "A button of the screen in no strip or tree, last; ✂ "
                + "Cut on the board makes a new one as a strip's button", () -> askButton("Another button of " + field,
                "The button, last in the strip", free, m -> setMembers(constant, field, strip,
                        Stream.concat(strip.members().stream(), Stream.of(m)).toList())));
        add.setDisable(free.isEmpty());
        box.getChildren().add(add);
        return box;
    }

    /** Asks which of {@code free}, said {@code what}, and hands it to {@code picked}. */
    private void askButton(String title, String what, List<ImageTemplate> free, Consumer<ImageTemplate> picked) {
        ComboBox<String> picture = new ComboBox<>(FXCollections.observableArrayList(free.stream()
                .map(ImageTemplate::id).toList()));
        picture.setValue(free.getFirst().id());
        Modals.form(services, title, new VBox(8, new Label(what), picture), () -> free.stream()
                .filter(m -> m.id().equals(picture.getValue())).findFirst().ifPresent(picked));
    }

    /** {@code strip} given {@code members}; its last one gone takes it off, once the user says so. */
    private void setMembers(String constant, String field, Strip strip, List<ImageTemplate> members) {
        if (!members.isEmpty()) {
            strip(constant, field, strip.members(members));
            return;
        }
        hands.confirm("Take the strip off", "That is the last button of " + field + ": the strip is taken off, its "
                + "button looked for at its place.", () -> strip(constant, field, strip.members(List.of())));
    }

    /** The buttons of {@code screen} a strip may take: each with a picture, in no strip or tree yet. */
    static List<ImageTemplate> joinable(MapScreen screen) {
        if (screen == null) return List.of();
        return screen.inputs().values().stream()
                .flatMap(i -> i instanceof Button b && b.picture() != null && screen.stripOf(b).isEmpty()
                        && screen.treeOf(b).isEmpty() ? Stream.of(b.picture()) : Stream.empty())
                .toList();
    }

    // --- a strip's scroll ---

    /** A strip's scroll as its editors pick it: the way it goes, and the wheel's notches or the drag's way and length. */
    private record ScrollPick(Node node, ComboBox<ScrollKind> kind, Spinner<Integer> notches, ComboBox<Order> way,
                              Spinner<Integer> px, Spinner<Integer> ms) {

        /** The controls set to {@code now}, offering {@code kinds}. */
        static ScrollPick of(Scroll now, List<ScrollKind> kinds) {
            ComboBox<ScrollKind> kind = new ComboBox<>(FXCollections.observableArrayList(kinds));
            kind.setConverter(named(ScrollKind::displayName));
            kind.setValue(ScrollKind.of(now));
            // Each spinner's range holds the value written: one out of it would be clamped, and written so on Set.
            int turns = now.drag() == null && now.notches() != 0 ? now.notches() : -5;
            Spinner<Integer> notches = new Spinner<>(Math.min(-20, turns), Math.max(20, turns), turns);
            notches.setEditable(true);
            notches.setPrefWidth(80);
            ComboBox<Order> way = new ComboBox<>(FXCollections.observableArrayList(Arrays.stream(Order.values())
                    .filter(o -> o != Order.CENTER).toList()));
            way.setConverter(named(InputInspector::orderName));
            way.setValue(now.drag() == null ? Order.NORTH : now.drag());
            int far = now.drag() == null ? 300 : Math.max(10, now.px());
            Spinner<Integer> px = new Spinner<>(10, Math.max(4000, far), far, 10);
            px.setEditable(true);
            px.setPrefWidth(90);
            int took = now.drag() == null ? 500 : (int) Math.min(Integer.MAX_VALUE, now.time().toMillis());
            Spinner<Integer> ms = new Spinner<>(0, Math.max(5000, took), took, 100);
            ms.setEditable(true);
            ms.setPrefWidth(90);
            HBox wheel = line(new Label("Notches"), notches, new Label("negative: down or toward you"));
            HBox drag = line(new Label("Toward"), way, px, new Label("px over"), ms, new Label("ms"));
            shownFor(wheel, kind, ScrollKind.WHEEL);
            shownFor(drag, kind, ScrollKind.DRAG);
            return new ScrollPick(new VBox(6, line(new Label("By"), kind), wheel, drag), kind, notches, way, px, ms);
        }

        /** The scroll picked; none for the wheel turned by no notch. */
        Optional<Scroll> scroll() {
            notches.commitValue();
            px.commitValue();
            ms.commitValue();
            if (kind.getValue() == ScrollKind.WHEEL && notches.getValue() == 0) return Optional.empty();
            return Optional.of(scrollOf(kind.getValue(), notches.getValue(), way.getValue(), px.getValue(),
                    ms.getValue()));
        }
    }

    private Node scroll(String constant, String field, Strip strip) {
        Scroll now = strip.scroll() == null ? Scroll.wheel(0) : strip.scroll();
        ScrollPick pick = ScrollPick.of(now, List.of(ScrollKind.values()));
        Label measured = text(now.step() == 0 ? "Not measured yet: ▶ Scroll and measure, below."
                : "Measured: its buttons move " + now.step() + " px a scroll and hold still within "
                + now.settle().toMillis() + " ms.");
        javafx.scene.control.Button set = action("Set scroll", MapGuide.STRIP_SCROLL, () -> {
            Optional<Scroll> picked = pick.scroll();
            if (picked.isEmpty()) {
                panel.wrote(ScreenEdits.NOT_WRITTEN + "the wheel turns by some notches: give them.");
                return;
            }
            Scroll made = picked.get();
            if (made.moves()) {
                strip(constant, field, strip.scroll(made));
            } else {
                hands.confirm("Take the strip off", field + " is no longer scrolled: the strip is taken off, its "
                        + "buttons looked for at their places.", () -> strip(constant, field, strip.scroll(made)));
            }
        });
        return new VBox(6, pick.node(), measured, set);
    }

    /** The scroll a strip's editor sets: none is a wheel of no notches, which takes the strip off. */
    static Scroll scrollOf(ScrollKind kind, int notches, Order way, int px, int ms) {
        return switch (kind == null ? ScrollKind.NONE : kind) {
            case WHEEL -> Scroll.wheel(notches);
            case DRAG -> Scroll.drag(way == null || way == Order.CENTER ? Order.NORTH : way, px,
                    Duration.ofMillis(ms));
            case NONE -> Scroll.wheel(0);
        };
    }

    private static <T> void shownFor(Node node, ComboBox<T> kind, T shown) {
        node.visibleProperty().bind(kind.valueProperty().isEqualTo(shown));
        node.managedProperty().bind(node.visibleProperty());
    }

    private static int indexOf(List<ImageTemplate> members, ImageTemplate picture) {
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i) != null && members.get(i).id().equals(picture.id())) return i;
        }
        return -1;
    }

    // --- a tree's nodes ---

    private Node nodes(String constant, String field, Tree tree) {
        VBox box = new VBox(6);
        Node drawing = TreeView.drawing(tree, panel::pictureSize, panel.width(), panel::thumb,
                id -> pickNode(constant, field, id), (id, drop) -> dropped(constant, field, tree, id, drop));
        Tooltip.install(drawing, new Tooltip(MapGuide.TREE_NODES));
        box.getChildren().add(drawing);
        List<com.botmaker.sdk.api.screens.Node> nodes = tree.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            int at = i;
            com.botmaker.sdk.api.screens.Node node = nodes.get(i);
            if (node.picture() == null) continue;
            Label name = new Label(node.picture().id());
            name.getStyleClass().add("screen-row-label");
            name.setMinWidth(0);
            HBox.setHgrow(name, Priority.ALWAYS);
            javafx.scene.control.Button up = action("↑", "Before the node above it",
                    () -> setNodes(constant, field, tree, moved(nodes, at, at - 1)));
            up.setDisable(at == 0);
            javafx.scene.control.Button down = action("↓", "After the node below it",
                    () -> setNodes(constant, field, tree, moved(nodes, at, at + 1)));
            down.setDisable(at == nodes.size() - 1);
            javafx.scene.control.Button off = action("✕", "Out of the tree: looked for at its place again, its lines "
                    + "and offsets gone", () -> setNodes(constant, field, tree, without(nodes, at)));
            javafx.scene.control.Button links = action("Unlocked by…", MapGuide.UNLOCKED_BY,
                    () -> askLinks(constant, tree, node));
            javafx.scene.control.Button place = action("Place…", MapGuide.PLACE_NODE,
                    () -> askPlace(constant, field, tree, node));
            links.setDisable(tree.members().size() < 2);
            place.setDisable(tree.members().size() < 2);
            Label said = text(nodeSaid(node));
            box.getChildren().addAll(line(panel.thumb(node.picture().id()), name, up, down, off),
                    new FlowPane(8, 6, said, links, place));
        }
        List<String> unplaced = tree.unplaced();
        if (!unplaced.isEmpty()) {
            box.getChildren().add(text("No offsets place " + String.join(", ", unplaced) + " yet: drag each beside "
                    + "a node it is linked to, or ▶ Scroll and measure with them in view."));
        }
        List<ImageTemplate> free = joinable(graph.screens().get(constant));
        javafx.scene.control.Button add = action("＋ Node…", "A button of the screen in no strip or tree, last; ✂ Cut "
                + "on the board makes a new one as a tree's node", () -> askButton("Another node of " + field,
                "The button, last in the tree", free, m -> setNodes(constant, field, tree, Stream.concat(
                        nodes.stream(), Stream.of(com.botmaker.sdk.api.screens.Node.of(m))).toList())));
        add.setDisable(free.isEmpty());
        box.getChildren().add(add);
        return box;
    }

    /** What {@code node} says of itself: what unlocks it, and where it is placed from. */
    static String nodeSaid(com.botmaker.sdk.api.screens.Node node) {
        List<String> after = node.after().stream().filter(p -> p != null).map(ImageTemplate::id).toList();
        List<String> beside = node.beside().stream().filter(b -> b.other() != null)
                .map(b -> "(" + b.across() + ", " + b.down() + ") from " + b.other().id()).toList();
        return (after.isEmpty() ? "No line leads to it" : "Unlocked by " + String.join(", ", after)) + "; "
                + (beside.isEmpty() ? "no offset written on it" : "placed " + String.join(", ", beside)) + ".";
    }

    /** The node {@code id} clicked in the drawing: its button picked, where its looks are edited. */
    private void pickNode(String constant, String field, String id) {
        ScreenCards.rows(graph, constant).stream()
                .filter(r -> r.input() instanceof Button && id.equals(r.picture())).findFirst()
                .ifPresentOrElse(r -> panel.pick(constant, r), () -> panel.wrote(id + " is in " + field + " but is no "
                        + "button of " + graph.shown(constant) + ": cut it on the board first."));
    }

    /** The node {@code id} dragged in the drawing, written where it snapped; false when it snapped nowhere. */
    private boolean dropped(String constant, String field, Tree tree, String id, Optional<TreeView.Drop> drop) {
        Optional<ImageTemplate> node = memberOf(tree, id);
        Optional<ImageTemplate> other = drop.flatMap(d -> memberOf(tree, d.other()));
        if (node.isEmpty() || other.isEmpty()) {
            panel.wrote(ScreenEdits.NOT_WRITTEN + id + " is linked to no node placed yet: Unlocked by… links it, "
                    + "then drag it beside that node.");
            return false;
        }
        panel.wrote(ScreenEdits.placeNode(services, constant, field, node.get(),
                Beside.of(other.get(), drop.get().across(), drop.get().down())));
        return true;
    }

    private static Optional<ImageTemplate> memberOf(Tree tree, String id) {
        return tree.members().stream().filter(p -> p != null && p.id().equals(id)).findFirst();
    }

    private void askLinks(String constant, Tree tree, com.botmaker.sdk.api.screens.Node node) {
        String id = node.picture().id();
        VBox ticks = new VBox(4);
        List<CheckBox> boxes = new ArrayList<>();
        for (ImageTemplate other : tree.members()) {
            if (other == null || other.id().equals(id)) continue;
            CheckBox tick = new CheckBox(other.id());
            tick.setSelected(node.after().stream().anyMatch(p -> p != null && p.id().equals(other.id())));
            boxes.add(tick);
        }
        ticks.getChildren().addAll(boxes);
        Modals.form(services, "What unlocks " + id, new VBox(8, new Label("The nodes whose line leads to it"), ticks),
                () -> panel.wrote(ScreenEdits.link(services, constant, graph.screens().get(constant), id,
                        boxes.stream().filter(CheckBox::isSelected).map(CheckBox::getText).toList())));
    }

    private void askPlace(String constant, String field, Tree tree, com.botmaker.sdk.api.screens.Node node) {
        String id = node.picture().id();
        List<String> others = tree.members().stream().filter(p -> p != null && !p.id().equals(id))
                .map(ImageTemplate::id).toList();
        // The node it is placed from already first, then one that unlocks it, then any.
        String first = node.beside().stream().filter(b -> b.other() != null).map(b -> b.other().id()).findFirst()
                .or(() -> node.after().stream().filter(p -> p != null).map(ImageTemplate::id).findFirst())
                .orElse(others.getFirst());
        ComboBox<String> from = new ComboBox<>(FXCollections.observableArrayList(others));
        from.setValue(first);
        Optional<Beside> had = besideOf(node, first);
        double wide = had.map(Beside::across).orElse(1.0);
        double tall = had.map(Beside::down).orElse(0.0);
        // Each range holds every offset written on the node: one out of it would be clamped, and written so.
        double most = Math.max(20, node.beside().stream()
                .mapToDouble(b -> Math.max(Math.abs(b.across()), Math.abs(b.down()))).max().orElse(0));
        Spinner<Double> across = new Spinner<>(-most, most, wide, TreeView.STEP);
        Spinner<Double> down = new Spinner<>(-most, most, tall, TreeView.STEP);
        across.setEditable(true);
        down.setEditable(true);
        across.setPrefWidth(90);
        down.setPrefWidth(90);
        // A node it has no offset from starts one width right of it, as a fresh form does.
        from.setOnAction(e -> {
            Optional<Beside> now = besideOf(node, from.getValue());
            across.getValueFactory().setValue(now.map(Beside::across).orElse(1.0));
            down.getValueFactory().setValue(now.map(Beside::down).orElse(0.0));
        });
        CheckBox none = new CheckBox("No offset between them");
        across.disableProperty().bind(none.selectedProperty());
        down.disableProperty().bind(none.selectedProperty());
        Modals.form(services, "Where " + id + " sits", new VBox(8, new Label("From"), from,
                line(new Label("Its centre"), across, new Label("of its widths right")),
                line(new Label("and"), down, new Label("of its heights below")),
                text("Negative: left or above."), none), () -> {
            across.commitValue();
            down.commitValue();
            Optional<ImageTemplate> other = memberOf(tree, from.getValue());
            if (other.isEmpty()) return;
            panel.wrote(none.isSelected() ? ScreenEdits.unplaceNode(services, constant, field, id, other.get().id())
                    : ScreenEdits.placeNode(services, constant, field, node.picture(),
                    Beside.of(other.get(), across.getValue(), down.getValue())));
        });
    }

    private static Optional<Beside> besideOf(com.botmaker.sdk.api.screens.Node node, String other) {
        return node.beside().stream().filter(b -> b.other() != null && b.other().id().equals(other)).findFirst();
    }

    /** {@code tree} given {@code nodes}; its last one gone takes it off, once the user says so. */
    private void setNodes(String constant, String field, Tree tree, List<com.botmaker.sdk.api.screens.Node> nodes) {
        if (!nodes.isEmpty()) {
            tree(constant, field, tree.nodes(nodes));
            return;
        }
        hands.confirm("Take the tree off", "That is the last node of " + field + ": the tree is taken off, its "
                + "button looked for at its place.", () -> tree(constant, field, tree.nodes(List.of())));
    }

    // --- a tree's scroll ---

    private Node treeScroll(String constant, String field, Tree tree) {
        ComboBox<TreeScroll> kind = new ComboBox<>(FXCollections.observableArrayList(TreeScroll.values()));
        kind.setConverter(named(TreeScroll::displayName));
        kind.setValue(TreeScroll.of(tree));
        int turns = tree.notches() != 0 ? tree.notches() : -5;
        Spinner<Integer> notches = new Spinner<>(Math.min(-20, turns), Math.max(20, turns), turns);
        notches.setEditable(true);
        notches.setPrefWidth(80);
        HBox wheel = line(new Label("Notches"), notches, new Label("how far: a walk turns it the way a node lies"));
        shownFor(wheel, kind, TreeScroll.WHEEL);
        javafx.scene.control.Button set = action("Set scroll", MapGuide.TREE_SCROLL, () -> {
            notches.commitValue();
            if (kind.getValue() == TreeScroll.WHEEL && notches.getValue() == 0) {
                panel.wrote(ScreenEdits.NOT_WRITTEN + "the wheel turns by some notches: give them.");
                return;
            }
            tree(constant, field, scrolled(tree, kind.getValue(), notches.getValue()));
        });
        return new VBox(6, line(new Label("By"), kind), wheel, set);
    }

    /** {@code tree} scrolled as its editor sets it: {@code notches} only by the wheel. */
    static Tree scrolled(Tree tree, TreeScroll kind, int notches) {
        TreeScroll by = kind == null ? TreeScroll.NONE : kind;
        return new Tree(tree.nodes(), by == TreeScroll.DRAG, by == TreeScroll.WHEEL ? notches : 0, tree.isTried(),
                tree.isLeaving(), tree.isInfo(), tree.never(), tree.comesAndGoes(), tree.lock(), tree.when());
    }

    // --- ▶ Try ---

    private Node trying(String constant, Row row, Input input) {
        // Only a button has copies: a strip or a tree is tried by a member, a hotkey by its key.
        boolean byCopy = input instanceof Button;
        Spinner<Integer> copy = new Spinner<>(0, 99, 0);
        copy.setEditable(true);
        copy.setPrefWidth(80);
        copy.setTooltip(new Tooltip("Which copy is clicked, from 1 in reading order: the button is noted repeated. "
                + "0 clicks its best match"));
        TextField opens = new TextField();
        opens.setPromptText(input instanceof Hotkey ? "the name the screen it opens shows, if it opens one"
                : "the name the screen it opens shows; blank: the picture's name");
        HBox.setHgrow(opens, Priority.ALWAYS);
        Spinner<Integer> settle = new Spinner<>(0, ScreenDesk.LONGEST_SETTLE_MS, ScreenDesk.SETTLE_MS, 500);
        settle.setEditable(true);
        settle.setPrefWidth(100);
        settle.setTooltip(new Tooltip("The longest it is given to answer, in milliseconds"));
        CheckBox stay = new CheckBox("Stay here after");
        stay.setSelected(true);
        stay.setTooltip(new Tooltip("Off: once the screen is finished, the game is walked to the next screen to "
                + "explore, as Discovery goes"));
        // A strip or a tree is scrolled, which opens nothing, and measured; try_input turns a strip by the wheel only.
        boolean scrolled = input instanceof Strip || input instanceof Tree;
        boolean dragged = input instanceof Strip strip && strip.scroll() != null && strip.scroll().drag() != null;
        CheckBox back = new CheckBox("Toward its start (left or above)");
        back.setTooltip(new Tooltip("Off: the view moves toward its end, what lies right of or below it"));
        javafx.scene.control.Button go = action(scrolled ? "▶ Scroll and measure" : "▶ Try",
                input instanceof Tree ? MapGuide.MEASURE_TREE : scrolled ? MapGuide.MEASURE : MapGuide.TRY_HOW, () -> {
            // A number typed and not yet entered is the one tried.
            copy.commitValue();
            settle.commitValue();
            Trying how = new Trying(opens.getText().isBlank() ? null : opens.getText().strip(),
                    byCopy && copy.getValue() > 0 ? copy.getValue() : null, settle.getValue(), stay.isSelected());
            AgentContext context = panel.tryContext();
            boolean towardStart = back.isSelected();
            panel.run("Trying " + row.label() + "…", constant, () -> input instanceof Tree tree
                    ? ScreenDesk.measureTree(context, constant, tree, towardStart)
                    : ScreenDesk.tryInput(context, constant, input, how));
        });
        go.getStyleClass().add("screen-panel-primary");
        go.setDisable(dragged);
        // A tree's measure waits on its nodes holding still, and stays.
        if (input instanceof Tree) return new VBox(6, back, go);
        VBox box = new VBox(6, new FlowPane(12, 6, line(new Label("Wait up to"), settle, new Label("ms")), stay));
        if (!scrolled) box.getChildren().addFirst(line(new Label("Opens"), opens));
        if (byCopy) box.getChildren().addFirst(line(new Label("Copy #"), copy));
        box.getChildren().add(go);
        if (dragged) box.getChildren().add(text("try_input scrolls a strip by the wheel only: a dragged one is "
                + "scrolled by a run."));
        return box;
    }

    // --- pure edits of a button's looks and a strip's members ---

    /** {@code items} with the one at {@code from} put at {@code to}; unchanged for either out of range. */
    static <T> List<T> moved(List<T> items, int from, int to) {
        if (from < 0 || from >= items.size() || to < 0 || to >= items.size()) return items;
        List<T> now = new ArrayList<>(items);
        now.add(to, now.remove(from));
        return List.copyOf(now);
    }

    /** {@code items} without the one at {@code at}. */
    static <T> List<T> without(List<T> items, int at) {
        if (at < 0 || at >= items.size()) return items;
        List<T> now = new ArrayList<>(items);
        now.remove(at);
        return List.copyOf(now);
    }

    /** {@code looks} with the one at {@code at} meaning {@code state}. */
    static List<Look> restated(List<Look> looks, int at, State state) {
        if (at < 0 || at >= looks.size()) return looks;
        List<Look> now = new ArrayList<>(looks);
        now.set(at, Look.of(looks.get(at).picture(), state));
        return List.copyOf(now);
    }

    // --- pieces ---

    private void set(String constant, String field, Input now) {
        panel.wrote(ScreenEdits.setInput(services, constant, field, now));
    }

    private void strip(String constant, String field, Strip now) {
        panel.wrote(ScreenEdits.setStrip(services, constant, field, now));
    }

    private void tree(String constant, String field, Tree now) {
        panel.wrote(ScreenEdits.setTree(services, constant, field, now));
    }

    private static String modeName(Pick.Mode mode) {
        return switch (mode) {
            case FIRST -> "The first";
            case NTH -> "The nth";
            case EACH -> "Each, in turn";
            case UNTIL_NONE -> "The first, until none is left";
            case UNTIL -> "Until it shows a look";
        };
    }

    private static String orderName(Order order) {
        String words = order.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return order == Order.CENTER ? "the centre" : "the " + words;
    }

    private static <T> StringConverter<T> named(Function<T, String> name) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : name.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    private static CheckBox check(String text, String tip, boolean on, Consumer<Boolean> set) {
        CheckBox box = new CheckBox(text);
        box.setSelected(on);
        box.setTooltip(new Tooltip(tip));
        box.setOnAction(e -> set.accept(box.isSelected()));
        return box;
    }

    private static HBox line(Node... nodes) {
        HBox line = new HBox(8, nodes);
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    private static Label heading(String text) {
        Label heading = new Label(text);
        heading.getStyleClass().add("screen-panel-subheading");
        return heading;
    }

    private static Label text(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private static Label pill(String text) {
        Label pill = new Label(text);
        pill.getStyleClass().addAll("screen-pill", "screen-pill-plain");
        return pill;
    }

    private static javafx.scene.control.Button action(String text, String tip, Runnable onPress) {
        javafx.scene.control.Button button = new javafx.scene.control.Button(text);
        button.setTooltip(new Tooltip(tip));
        button.setOnAction(e -> onPress.run());
        return button;
    }
}
