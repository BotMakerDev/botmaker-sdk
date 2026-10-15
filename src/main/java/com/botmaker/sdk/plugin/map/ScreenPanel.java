package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.ScreenDesk;
import com.botmaker.sdk.plugin.assist.ScreenEdits;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import com.botmaker.sdk.plugin.pictures.CaptureTemplates;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import com.botmaker.sdk.plugin.pictures.TemplateUses;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The side of 🗺 Screens: the selected screen's way from the first screen, its inputs and what each did, what the
 * notes say of it, and the actions on it, each by the assistant's own code ({@link ScreenDesk}): ▶ Try an input,
 * ✂ Cut its marker or more inputs, Go here. With no screen selected it says how to start or read the map.
 *
 * <p>Trying and going act on the game, and on the user's own desktop when the game is there: they pressed it.
 */
final class ScreenPanel {

    private static final double ROW_THUMB = 48;
    private static final double HEAD_THUMB = 60;
    /** The selected screen's whole-screen shot, the panel's width less its padding. */
    /** The selected screen's shot, wide enough to click its boxed inputs. */
    private static final double HEAD_SHOT = 400;

    private final StudioServices services;
    private final ScreenCanvas canvas;
    private final Supplier<Window> owner;
    private final Runnable changed;
    private final VBox content = new VBox(12);
    private final ScrollPane root = new ScrollPane(content);
    private final Label status = new Label();
    /** The one window a picture or a screen's shot is shown in, each show replacing the last. */
    private final PictureView viewer;
    /** The ⋯ menus that edit the map by hand. */
    private final ScreenHands hands;

    private ScreenGraph graph = ScreenGraph.of(Map.of());
    private String selected;
    /** What the panel last drew, so a poll that changed nothing redraws nothing. */
    private Object drawn;
    private boolean busy;

    /**
     * @param changed run once an action wrote the map, so the window reads it at once rather than at its next poll
     */
    ScreenPanel(StudioServices services, ScreenCanvas canvas, Supplier<Window> owner, Runnable changed) {
        this.services = services;
        this.canvas = canvas;
        this.owner = owner;
        this.changed = changed;
        viewer = new PictureView(services, owner);
        hands = new ScreenHands(services, () -> graph, said -> {
            drawn = null;
            say(said);
            changed.run();
        });
        content.getStyleClass().add("screen-panel");
        content.setPadding(new Insets(14));
        content.setFillWidth(true);
        root.setFitToWidth(true);
        root.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        root.getStyleClass().addAll("screen-map", "screen-panel-scroller");
        root.getStylesheets().add(ScreenCanvas.SHEET);
        root.setPrefWidth(440);
        root.setMinWidth(260);
        status.setWrapText(true);
        status.getStyleClass().add("screen-panel-status");
        draw();
    }

    Node node() {
        return root;
    }

    /** Shows {@code constant} of {@code graph}; null for none. Draws again only when something it shows changed. */
    void show(ScreenGraph graph, String constant) {
        this.graph = graph;
        this.selected = constant != null && graph.screens().containsKey(constant) ? constant : null;
        draw();
    }

    private void draw() {
        GameScreen at = selected == null ? null : graph.screens().get(selected);
        String notes = at == null || at.notes() == null ? "" : at.notes();
        Object now = selected == null ? List.of(graph.screens().values().stream().allMatch(GameScreen::isPopup), busy)
                : List.of(ScreenCards.card(graph, selected, ""), ScreenCards.rows(graph, selected),
                ScreenCards.path(graph, selected), notes, at.owes(), busy);
        if (now.equals(drawn)) return;
        drawn = now;
        content.getChildren().setAll(selected == null ? none() : screen(notes));
        content.getChildren().add(status);
    }

    private List<Node> none() {
        // Popups alone start no tree: the first screen is still to be cut.
        if (!graph.screens().values().stream().allMatch(GameScreen::isPopup)) {
            return List.of(heading("Screens"), text("Pick a screen to see its inputs, try them, cut more and go there. "
                    + "Solid arrows are how each screen was found; dashed ones are other ways in and ways back. "
                    + "Double-click a screen to fold what is below it."));
        }
        TextField first = new TextField("Home");
        first.setPromptText("the first screen's name, as the game shows it");
        Button cut = action("✂ Cut its marker", "Draw a box around a part only this screen shows",
                () -> cut(first.getText().strip(), true));
        return List.of(heading("No map yet"), text("The map is the bot's Screens class, one constant per screen. "
                + "Start it with the first screen's marker: a part only that screen shows. The assistant's "
                + "Discovery fills in the rest, or cut and try each input here."), first, cut);
    }

    private List<Node> screen(String notes) {
        Card card = ScreenCards.card(graph, selected, "");
        GameScreen screen = graph.screens().get(selected);

        Label name = new Label(card.name());
        name.getStyleClass().add("screen-panel-name");
        name.setWrapText(true);
        Label path = new Label(card.popup() ? "a popup, opened by the game itself"
                : String.join(" › ", ScreenCards.path(graph, selected)));
        path.getStyleClass().add("screen-panel-path");
        path.setWrapText(true);
        Label constant = new Label("Screens." + selected);
        constant.getStyleClass().add("screen-panel-path");
        VBox titles = new VBox(3, name, path, constant);
        titles.setMinWidth(0);
        HBox.setHgrow(titles, Priority.ALWAYS);
        HBox head = new HBox(12, canvas.thumb(card.marker(), HEAD_THUMB, "screen-card-thumb"), titles);
        head.setAlignment(Pos.CENTER_LEFT);

        FlowPane pills = new FlowPane(4, 4, pill(card.said(), "screen-pill-" + card.state().name()
                .toLowerCase(Locale.ROOT)));
        if (!card.shape().isEmpty()) pills.getChildren().add(pill(card.shape(), "screen-pill-plain"));
        if (card.conditional()) {
            pills.getChildren().add(pill(card.presence() == ScreenGraph.Presence.LOCKED ? "locked" : "sometimes",
                    "screen-pill-plain"));
        }

        List<Row> rows = ScreenCards.rows(graph, selected);
        Optional<Row> next = rows.stream().filter(r -> r.state() == ScreenCards.RowState.UNTRIED).findFirst();
        Button tryNext = action("▶ Try next", next.map(r -> "Try " + r.label() + " as the assistant's try_input does")
                .orElse("Nothing on it is left to try"), () -> next.ifPresent(r -> tryRow(r)));
        tryNext.setDisable(busy || next.isEmpty());
        tryNext.getStyleClass().add("screen-panel-primary");
        Button cutInputs = action("✂ Cut inputs", "Draw boxes around more of its buttons: each is noted untried",
                () -> cut(selected, false));
        Button cutMarker = action(screen.marker() == null ? "✂ Cut marker" : "✂ Recut marker",
                "Draw a box around a part only this screen shows", () -> cut(selected, true));
        Button go = action("🧭 Go here", "Walk the game here by the map, as Navigate.to does in a run",
                this::goHere);
        go.setDisable(busy || card.popup());
        String shownNow = selected;
        FlowPane actions = new FlowPane(8, 8, tryNext, go, cutInputs, cutMarker,
                hands.screenMenu(shownNow, this::retake));

        VBox inputs = new VBox(6);
        if (rows.isEmpty()) inputs.getChildren().add(text("No input cut yet."));
        for (Row row : rows) inputs.getChildren().add(row(row));

        VBox all = new VBox(12, head, pills, actions, heading("Inputs"), inputs);
        Map<String, FrameCut> cuts = services.pluginValues() == null ? Map.of()
                : TemplateUses.cuts(services.pluginValues());
        String shown = selected;
        canvas.shotWithInputs(shown, HEAD_SHOT, rows, cuts, this::pick, () -> showScreen(shown))
                .ifPresent(shot -> all.getChildren().addFirst(shot));
        if (screen.back() != null) all.getChildren().add(text("Way back: " + screen.back().label() + "."));
        if (!screen.owes().isEmpty()) {
            all.getChildren().addAll(heading("Owes"), text("• " + String.join("\n• ", screen.owes())));
        }
        // The notes live on the screen's constant: edited here, written as the assistant's write_notes writes them.
        TextArea edit = new TextArea(notes);
        edit.setWrapText(true);
        edit.setPrefRowCount(6);
        edit.setPromptText("What this screen spends and earns, its timers, badges and quirks");
        edit.getStyleClass().add("screen-panel-notes");
        String noted = selected;
        Button save = action("Save notes", "Keep these notes on " + graph.shown(noted) + ", in the bot's Screens",
                () -> {
                    say(ScreenEdits.setNotes(services, noted, edit.getText()));
                    changed.run();
                });
        all.getChildren().addAll(heading("Notes"), edit, save);
        return List.of(all);
    }

    private Node row(Row row) {
        Label label = new Label(row.label());
        label.getStyleClass().add("screen-row-label");
        label.setMinWidth(0);
        VBox words = new VBox(1, label);
        String about = String.join(" · ", Stream.of(row.kind(),
                row.look().isEmpty() ? "" : "look " + row.look(), row.when().isEmpty() ? "" : "while " + row.when())
                .filter(s -> !s.isEmpty()).toList());
        if (!about.isEmpty()) {
            Label kind = new Label(about);
            kind.getStyleClass().add("screen-row-kind");
            words.getChildren().add(kind);
        }
        words.setMinWidth(0);
        HBox.setHgrow(words, Priority.ALWAYS);
        Label result = pill(row.result(), "screen-pill-" + switch (row.state()) {
            case TRIED -> "finished";
            case UNTRIED, UNSEEN -> "untried";
            case NEVER, LOCKED -> "plain";
        });
        result.setMaxWidth(130);
        // A button with several looks steps through them; any look's thumb shows the input's picture.
        Node thumb = LookToggle.of(row.looks(), picture -> {
            Node one = canvas.thumb(picture, ROW_THUMB, "screen-row-thumb");
            if (picture != null) {
                Tooltip.install(one, new Tooltip("Show " + picture + " at its own size"));
                one.setOnMouseClicked(e -> showPicture(row));
            }
            return one;
        }, result, row.result());
        boolean untried = row.state() == ScreenCards.RowState.UNTRIED || row.state() == ScreenCards.RowState.UNSEEN;
        HBox line = new HBox(8, thumb, words, result);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("screen-row");
        if (untried) {
            Button play = action("▶", "Try " + row.label(), () -> tryRow(row));
            play.setDisable(busy);
            line.getChildren().add(play);
        }
        line.getChildren().add(hands.rowMenu(selected, row));
        return line;
    }

    // --- actions ---

    /** A boxed input clicked on a shot: the screen it opens, selected on the map; else its picture. */
    private void pick(ScreenCards.Row row) {
        Optional<String> opens = selected == null ? Optional.empty() : graph.opens(selected, row.input());
        if (opens.isPresent()) canvas.select(opens.get());
        else showPicture(row);
    }

    private void showPicture(ScreenCards.Row row) {
        boolean untried = row.state() == ScreenCards.RowState.UNTRIED || row.state() == ScreenCards.RowState.UNSEEN;
        viewer.showPicture(row, untried && !busy ? () -> tryRow(row) : null, () -> {
            drawn = null;
            canvas.forgetPictures();
            changed.run();
        });
    }

    /** The right-click menu of {@code constant}'s card: its shot shown big, then the ⋯ Screen menu's edits. */
    ContextMenu menuFor(String constant) {
        ContextMenu menu = new ContextMenu(hands.screenItems(constant, this::retake).toArray(MenuItem[]::new));
        MenuItem big = new MenuItem("🔍 Show the screen big");
        big.setOnAction(e -> showScreen(constant));
        menu.getItems().addAll(0, List.of(big, new SeparatorMenuItem()));
        return menu;
    }

    /** The right-click menu of {@code link}: its picture, and when its way is open. */
    ContextMenu linkMenu(ScreenCards.Link link) {
        return hands.linkMenu(link, () -> showLink(link));
    }

    /** A link clicked on the map: the screen it leaves picked, and the picture clicked on it shown. */
    void showLink(ScreenCards.Link link) {
        canvas.select(link.from());
        ScreenCards.rows(graph, link.from()).stream().filter(r -> r.input().sameAs(link.via())).findFirst()
                .ifPresentOrElse(this::showPicture, () -> say(link.via().label() + " is " + graph.shown(link.from())
                        + "'s way back, not one of its inputs: its picture shows in 🖼 Manage Pictures."));
    }

    /** {@code constant}'s shot in the picture window, big, its inputs boxed: Ctrl+scroll or the wheel zooms it. */
    void showScreen(String constant) {
        Map<String, FrameCut> cuts = services.pluginValues() == null ? Map.of()
                : TemplateUses.cuts(services.pluginValues());
        List<Row> rows = ScreenCards.rows(graph, constant);
        if (!viewer.showScreen(graph.shown(constant), width -> canvas.shotWithInputs(constant, width, rows, cuts,
                this::pick, null))) {
            say(graph.shown(constant) + " has no shot yet: ⋯ Screen ▸ take it again while the game shows it.");
        }
    }

    /** Takes {@code constant}'s shot again from the game, off the FX thread. */
    private void retake(String constant) {
        run("Taking the shot of " + graph.shown(constant) + "…", constant, () -> ScreenEdits.retakeShot(
                new PanelContext(services, CaptureValue.current(services)), constant));
    }

    /** Closes the picture window; what the map's window does when it closes. */
    void close() {
        viewer.close();
    }

    private void tryRow(Row row) {
        String constant = selected;
        if (!ScreenDesk.asksWhatItOpens(graph, constant, row.input())) {
            tryRow(row, constant, null);
            return;
        }
        // A hotkey, or a picture whose name names no new screen: the name the screen it opens shows, asked first.
        TextField opens = new TextField();
        Modals.form(services, "Try " + row.label(), new VBox(8, new Label("The name the screen it opens shows, if "
                + "it opens one; blank when it opens none"), opens), () -> tryRow(row, constant,
                opens.getText().isBlank() ? null : opens.getText().strip()));
    }

    private void tryRow(Row row, String constant, String opens) {
        PanelContext context = new PanelContext(services, CaptureValue.current(services));
        run("Trying " + row.label() + "…", constant, () -> ScreenDesk.tryInput(context, constant, row.input(),
                opens));
    }

    private void goHere() {
        String constant = selected;
        PanelContext context = new PanelContext(services, CaptureValue.current(services));
        run("Going to " + graph.shown(constant) + "…", constant, () -> ScreenDesk.goTo(context, constant));
    }

    /** Runs {@code work} off the FX thread, the card pulsing, and says what it answered. */
    private void run(String doing, String constant, Supplier<String> work) {
        if (busy) return;
        setBusy(true, doing);
        canvas.setTrying(constant);
        Thread.ofVirtual().name("screen-map-action").start(() -> {
            String[] said = {"It stopped without an answer."};
            try {
                said[0] = work.get();
            } catch (RuntimeException failed) {
                said[0] = "It failed: " + Objects.requireNonNullElse(failed.getMessage(), failed.toString());
            } finally {
                // Whatever ended it, an Error too, the panel is free again.
                Platform.runLater(() -> {
                    canvas.setTrying(null);
                    setBusy(false, said[0]);
                    changed.run();
                });
            }
        });
    }

    /** Cuts pictures over the game with 🎯's tool, each then noted on {@code screen}: its marker, or inputs. */
    private void cut(String screen, boolean marker) {
        if (screen == null || screen.isBlank()) {
            say("Name the screen first.");
            return;
        }
        Window window = owner.get();
        Consumer<List<String>> saved = names -> {
            if (window != null) window.requestFocus();
            if (names.isEmpty()) return;
            // With the marker asked for, the last picture cut is it, noted first (it may start the map), and any
            // before it are inputs, never orphans.
            List<String> order = new ArrayList<>(names);
            if (marker) order.addFirst(order.removeLast());
            List<String> noted = order.stream().map(n -> n + ": " + ScreenDesk.noteCut(services, screen, n,
                    marker && n.equals(order.getFirst()))).toList();
            say(String.join("\n", noted));
            drawn = null;
            canvas.forgetPictures();
            changed.run();
        };
        CaptureTemplates.open(services, window, null, null, saved);
    }

    private void setBusy(boolean busy, String said) {
        this.busy = busy;
        drawn = null;
        draw();
        say(said);
    }

    private void say(String said) {
        status.setText(said == null ? "" : said);
    }

    // --- pieces ---

    private static Label heading(String text) {
        Label heading = new Label(text);
        heading.getStyleClass().add("screen-panel-heading");
        return heading;
    }

    private static Label text(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static Label pill(String text, String style) {
        Label pill = new Label(text);
        pill.getStyleClass().addAll("screen-pill", style);
        return pill;
    }

    private static Button action(String text, String tip, Runnable onPress) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tip));
        button.setOnAction(e -> onPress.run());
        return button;
    }
}
