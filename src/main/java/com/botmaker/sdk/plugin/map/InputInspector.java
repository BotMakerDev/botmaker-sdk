package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.State;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The input picked on 🗺 Screens' board, edited by its type under its row: what it does (its result, information only,
 * comes and goes, never, locked), a button's looks (each with its state, in order), whether the screen shows it more
 * than once and the copy each of its ways goes through, a hotkey's key, and ▶ Try with try_input's own settings. Each
 * change is one {@link ScreenEdits} write, as the tools make it, so ↶ undoes it.
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
                all.add(heading("Looks"));
                all.add(looks(constant, field, button));
                all.add(heading("Copies"));
                all.add(copies(constant, field, button));
            }
            case Hotkey hotkey -> {
                all.add(heading("Key"));
                all.add(key(constant, field, hotkey));
            }
            default -> {
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

    // --- a hotkey's key ---

    private Node key(String constant, String field, Hotkey hotkey) {
        ComboBox<Key> key = keyPick(hotkey.key());
        // The key last written from here: the editable combo commits on focus loss and on Enter both.
        Key[] written = {hotkey.key()};
        key.setOnAction(e -> {
            Key now = key.getValue();
            if (now == null || now == written[0]) return;
            Optional<String> taken = keyTaken(graph.screens().get(constant), field, now);
            if (taken.isPresent()) {
                panel.wrote(ScreenEdits.NOT_WRITTEN + taken.get() + " is pressed by " + now.label() + " already.");
                return;
            }
            written[0] = now;
            set(constant, field, ScreenEdits.withKey(hotkey, now));
        });
        return line(new Label("Pressed by"), key);
    }

    /** The field of {@code screen}, other than {@code field}, that is a hotkey of {@code key}; empty for none. */
    static Optional<String> keyTaken(MapScreen screen, String field, Key key) {
        if (screen == null) return Optional.empty();
        return screen.inputs().entrySet().stream()
                .filter(e -> !e.getKey().equals(field) && e.getValue() instanceof Hotkey h && h.key() == key)
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
        javafx.scene.control.Button go = action("▶ Try", MapGuide.TRY_HOW, () -> {
            // A number typed and not yet entered is the one tried.
            copy.commitValue();
            settle.commitValue();
            Trying how = new Trying(opens.getText().isBlank() ? null : opens.getText().strip(),
                    byCopy && copy.getValue() > 0 ? copy.getValue() : null, settle.getValue(), stay.isSelected());
            AgentContext context = panel.tryContext();
            panel.run("Trying " + row.label() + "…", constant, () -> ScreenDesk.tryInput(context, constant, input,
                    how));
        });
        go.getStyleClass().add("screen-panel-primary");
        VBox box = new VBox(6, line(new Label("Opens"), opens), new FlowPane(12, 6, line(new Label("Wait up to"),
                settle, new Label("ms")), stay));
        if (byCopy) box.getChildren().addFirst(line(new Label("Copy #"), copy));
        box.getChildren().add(go);
        return box;
    }

    // --- pure edits of a button's looks ---

    /** {@code looks} with the one at {@code from} put at {@code to}; unchanged for either out of range. */
    static List<Look> moved(List<Look> looks, int from, int to) {
        if (from < 0 || from >= looks.size() || to < 0 || to >= looks.size()) return looks;
        List<Look> now = new ArrayList<>(looks);
        now.add(to, now.remove(from));
        return List.copyOf(now);
    }

    /** {@code looks} without the one at {@code at}. */
    static List<Look> without(List<Look> looks, int at) {
        if (at < 0 || at >= looks.size()) return looks;
        List<Look> now = new ArrayList<>(looks);
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
