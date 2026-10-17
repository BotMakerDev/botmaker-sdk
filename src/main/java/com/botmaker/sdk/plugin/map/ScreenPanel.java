package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.MapAudit;
import com.botmaker.sdk.plugin.assist.MapAudit.Audit;
import com.botmaker.sdk.plugin.assist.MapAudit.Spot;
import com.botmaker.sdk.plugin.assist.MapAudit.Where;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
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
    /** The selected screen's shot is the panel's width less its padding, and never narrower than this. */
    private static final double LEAST_SHOT = 200;
    private static final double PADDING = 14;

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
    /** The notes box, kept over redraws while it shows the same screen: what is being typed is not lost to a poll. */
    private TextArea notesEdit;
    private String notesOf;
    /** The notes as the map held them when the box was filled: a box still holding them takes newer ones. */
    private String notesRead;
    /** Each screen's shot as last checked ({@link MapAudit}), by constant: made off the FX thread, kept on it. */
    private final Map<String, Audit> audits = new HashMap<>();

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
        content.setPadding(new Insets(PADDING));
        content.setFillWidth(true);
        root.setFitToWidth(true);
        root.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // Always there: a scrollbar coming and going with the shot's height would change the width the shot is drawn
        // at, and so its height, again and again.
        root.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        root.getStyleClass().addAll("screen-map", "screen-panel-scroller");
        root.getStylesheets().add(ScreenCanvas.SHEET);
        root.setPrefWidth(440);
        root.setMinWidth(260);
        status.setWrapText(true);
        status.getStyleClass().add("screen-panel-status");
        // The panel made wider or narrower: its shot is drawn again at the new width.
        root.viewportBoundsProperty().addListener((o, was, is) -> {
            if (was == null || Math.abs(was.getWidth() - is.getWidth()) >= 8) draw();
        });
        draw();
    }

    /** The project this panel was of is closed: nothing on it acts any more. */
    void closed() {
        root.setDisable(true);
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
        MapScreen at = selected == null ? null : graph.screens().get(selected);
        String notes = at == null || at.notes() == null ? "" : at.notes();
        Map<String, FrameCut> cuts = selected == null || services.pluginValues() == null ? Map.of()
                : TemplateUses.cuts(services.pluginValues());
        double width = shotWidth();
        // Everything the panel shows: the screen itself (its back, looks, notes and debts), its card and rows, where
        // its inputs were cut, when its shot was taken and how wide it is drawn.
        Object now = selected == null ? List.of(graph.screens().values().stream().allMatch(MapScreen::isPopup), busy)
                : List.of(at, card(selected), ScreenCards.rows(graph, selected), ScreenCards.path(graph, selected),
                cutsOf(at, cuts), canvas.shotTime(selected), pictureTimes(), width, busy,
                Optional.ofNullable(audits.get(selected)));
        if (now.equals(drawn)) return;
        drawn = now;
        content.getChildren().setAll(selected == null ? none() : screen(notes, cuts, width));
        content.getChildren().add(status);
        if (at != null) audit(selected, at, cuts);
    }

    /**
     * Checks {@code constant}'s shot off the FX thread, and draws again once it differs from the one drawn: a shot
     * matched against every picture the screen names takes a second, too long to hold the window for.
     */
    private void audit(String constant, MapScreen screen, Map<String, FrameCut> cuts) {
        Path resources = services.resourcesDir();
        double confidence = MapAudit.confidence(services);
        Thread.ofVirtual().name("screen-audit").start(() -> {
            Optional<Audit> made;
            try {
                made = MapAudit.of(resources, confidence, cuts, constant, screen);
            } catch (RuntimeException unmatched) {
                made = Optional.empty();
            }
            Optional<Audit> checked = made;
            Platform.runLater(() -> {
                if (checked.equals(Optional.ofNullable(audits.get(constant)))) return;
                if (checked.isPresent()) audits.put(constant, checked.get());
                else audits.remove(constant);
                if (constant.equals(selected)) draw();
            });
        });
    }

    /** {@code constant}'s card, the stage its notes say it reached on it. */
    private Card card(String constant) {
        return ScreenCards.card(graph, constant, ScreenDesk.stages(graph).getOrDefault(constant, ""));
    }

    /** Where each picture {@code screen} uses was cut: what its shot's boxes are drawn from. */
    private Map<String, FrameCut> cutsOf(MapScreen screen, Map<String, FrameCut> cuts) {
        if (screen == null) return Map.of();
        Map<String, FrameCut> used = new TreeMap<>();
        for (String id : graph.pictures(selected)) {
            FrameCut cut = cuts.get(id);
            if (cut != null) used.put(id, cut);
        }
        return used;
    }

    /** When each picture the selected screen uses was written: a recut draws its thumbs again. */
    private List<Long> pictureTimes() {
        return graph.pictures(selected).stream().map(canvas::pictureTime).toList();
    }

    /** How wide the selected screen's shot is drawn: the panel's width less its padding. */
    private double shotWidth() {
        double seen = root.getViewportBounds() == null ? 0 : root.getViewportBounds().getWidth();
        return Math.max(LEAST_SHOT, Math.floor((seen > 0 ? seen : root.getPrefWidth()) - 2 * PADDING));
    }

    private List<Node> none() {
        // Popups alone start no tree: the first screen is still to be cut.
        if (!graph.screens().values().stream().allMatch(MapScreen::isPopup)) {
            return List.of(heading("Screens"), text("Pick a screen to see its inputs, try them, cut more and go there. "
                    + "Solid arrows are how each screen was found; dashed ones are other ways in and ways back. "
                    + "Double-click a screen to fold what is below it."));
        }
        TextField first = new TextField("Home");
        first.setPromptText("the first screen's name, as the game shows it");
        Button cut = action("✂ Cut its marker", MapGuide.CUT_MARKER,
                () -> cut(first.getText().strip(), true));
        return List.of(heading("No map yet"), text("The map is the bot's Screens class, one constant per screen. "
                + "Start it with the first screen's marker: a part only that screen shows. The assistant's "
                + "Discovery fills in the rest, or cut and try each input here."), first, cut);
    }

    private List<Node> screen(String notes, Map<String, FrameCut> cuts, double shotWidth) {
        Card card = card(selected);
        MapScreen screen = graph.screens().get(selected);
        String shown = selected;

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

        FlowPane pills = ScreenCanvas.pills(card);

        List<Row> rows = ScreenCards.rows(graph, shown);
        Optional<Row> next = rows.stream().filter(r -> r.state() == ScreenCards.RowState.UNTRIED).findFirst();
        Button tryNext = action("▶ Try next", next.map(r -> "Try " + r.label() + " as the assistant's try_input does")
                .orElse("Nothing on it is left to try"), () -> next.ifPresent(r -> tryRow(shown, r)));
        tryNext.setDisable(busy || next.isEmpty());
        tryNext.getStyleClass().add("screen-panel-primary");
        Button cutInputs = action("✂ Cut inputs", MapGuide.CUT_INPUTS, () -> cut(shown, false));
        Button cutMarker = action(screen.hasMarker() ? "✂ Add marker" : "✂ Cut marker",
                MapGuide.CUT_MARKER, () -> cut(shown, true));
        Button go = action("🧭 Go here", MapGuide.GO_HERE, () -> goHere(shown));
        go.setDisable(busy || card.popup());
        cutInputs.setDisable(busy);
        cutMarker.setDisable(busy);
        // No hand edit while a try or a walk writes the map: the two would race for Screens.java.
        Node screenMenu = hands.screenMenu(shown, this::retake);
        screenMenu.setDisable(busy);
        FlowPane actions = new FlowPane(8, 8, tryNext, go, cutInputs, cutMarker, screenMenu);

        VBox inputs = new VBox(6);
        if (rows.isEmpty()) inputs.getChildren().add(text("No input cut yet."));
        for (Row row : rows) inputs.getChildren().add(row(shown, row));

        VBox markers = new VBox(6);
        if (!screen.hasMarker()) markers.getChildren().add(text("None yet: ✂ Cut marker."));
        screen.markers().forEach(m -> markers.getChildren().add(markerRow(shown, m.id(), "shows", busy)));
        screen.absent().forEach(m -> markers.getChildren().add(markerRow(shown, m.id(), "never shows", busy)));

        VBox all = new VBox(12, head, pills, actions, heading("Markers"), markers, heading("Inputs"), inputs);
        canvas.shotWithInputs(shown, shotWidth, rows, ScreenCards.strips(graph, shown, rows), cuts, audits.get(shown),
                        row -> pick(shown, row),
                        () -> showScreen(shown))
                .ifPresent(shot -> all.getChildren().addFirst(shot));
        all.getChildren().add(text("Way back: " + (screen.back() == null ? "the default one, a close button, then "
                + "Escape" : screen.back().label()) + "."));
        if (!screen.owes().isEmpty()) {
            all.getChildren().addAll(heading("Owes"), text("• " + String.join("\n• ", screen.owes())));
        }
        // The notes live on the screen's constant: edited here, written as the assistant's write_notes writes them.
        TextArea edit = notesBox(shown, notes);
        Button save = action("Save notes", "Keep these notes on " + graph.shown(shown) + ", in the bot's Screens",
                () -> {
                    notesRead = edit.getText();
                    say(ScreenEdits.setNotes(services, shown, edit.getText()));
                    changed.run();
                });
        all.getChildren().addAll(heading("Notes"), edit, save);
        return List.of(all);
    }

    /**
     * The notes box for {@code constant}: the one shown already while it is the same screen's, so a poll that draws
     * the panel again keeps what is being typed; it takes {@code notes} only while it still holds what was read.
     */
    private TextArea notesBox(String constant, String notes) {
        if (notesEdit == null || !constant.equals(notesOf)) {
            notesEdit = new TextArea(notes);
            notesEdit.setWrapText(true);
            notesEdit.setPrefRowCount(6);
            notesEdit.setPromptText("What this screen spends and earns, its timers, badges and quirks");
            notesEdit.getStyleClass().add("screen-panel-notes");
            notesOf = constant;
        } else if (notesEdit.getText().equals(notesRead)) {
            notesEdit.setText(notes);
        }
        notesRead = notes;
        return notesEdit;
    }

    private Node row(String constant, Row row) {
        Label label = new Label(row.label());
        label.getStyleClass().add("screen-row-label");
        label.setMinWidth(0);
        VBox words = new VBox(1, label);
        String about = String.join(" · ", Stream.of(row.kind(), row.traits(),
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
        // What the shot says of its picture: not on it, or away from its place.
        Optional<Spot> spot = Optional.ofNullable(audits.get(constant)).flatMap(a -> a.of(row.picture()));
        Optional<Label> onShot = spot.filter(s -> s.where() != Where.AT_PLACE).map(s -> {
            boolean gone = s.where() == Where.MISSING;
            Label said = pill(gone ? "not on shot" : "off its place", gone && s.sure() ? "screen-pill-owes"
                    : "screen-pill-untried");
            Tooltip.install(said, new Tooltip(gone ? "Its picture is not on " + graph.shown(constant) + "'s shot"
                    + (s.sure() ? "." : "; it comes and goes, or is another look.")
                    : "Its picture sits elsewhere on the shot than its place: placed again while the game shows "
                    + graph.shown(constant) + ", it gets a copy of its own there."));
            return said;
        });
        // A button with several looks steps through them; any look's thumb shows the input's picture.
        Node thumb = LookToggle.of(row.looks(), picture -> {
            Node one = canvas.thumb(picture, ROW_THUMB, "screen-row-thumb");
            if (picture != null) {
                Tooltip.install(one, new Tooltip("Show " + picture + " at its own size"));
                one.setOnMouseClicked(e -> showPicture(constant, row, null));
            }
            return one;
        }, result, row.result());
        boolean untried = row.state() == ScreenCards.RowState.UNTRIED || row.state() == ScreenCards.RowState.UNSEEN;
        HBox line = new HBox(8, thumb, words, result);
        onShot.ifPresent(said -> line.getChildren().add(said));
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("screen-row");
        if (untried) {
            Button play = action("▶", "Try " + row.label(), () -> tryRow(constant, row));
            play.setDisable(busy);
            line.getChildren().add(play);
        }
        Node menu = hands.rowMenu(constant, row);
        menu.setDisable(busy);
        line.getChildren().add(menu);
        return line;
    }

    /**
     * One picture of what tells {@code constant} apart: a marker it {@code shows}, or one it never shows; ✕ takes it
     * off, refused for its last marker or when another screen's shot then shows the rest.
     */
    private Node markerRow(String constant, String picture, String shows, boolean busy) {
        Node thumb = canvas.thumb(picture, ROW_THUMB, "screen-row-thumb");
        Label label = new Label(picture);
        label.getStyleClass().add("screen-row-label");
        label.setMinWidth(0);
        HBox.setHgrow(label, Priority.ALWAYS);
        Label kind = pill(shows, "screen-pill-plain");
        // Off the FX thread: what is left is matched over every other screen's shot.
        Button off = action("✕", "Remove " + picture + " from what tells " + graph.shown(constant) + " apart",
                () -> run("Taking " + picture + " off…", constant,
                        () -> ScreenEdits.removeMarker(services, constant, picture)));
        off.setDisable(busy);
        HBox line = new HBox(8, thumb, label, kind, off);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("screen-row");
        return line;
    }

    // --- actions ---

    /**
     * A boxed input of {@code constant} clicked on the panel's shot: the screen it opens, selected on the map; else
     * its picture.
     */
    private void pick(String constant, ScreenCards.Row row) {
        Optional<String> opens = graph.opens(constant, row.field());
        if (opens.isPresent()) canvas.select(opens.get());
        else showPicture(constant, row, null);
    }

    /**
     * A boxed input of {@code constant} clicked in the big view: the screen it opens, selected and shown big in its
     * place, its ← going back to {@code constant}; else its picture. {@code back} is the big view's own ←.
     */
    private void pickBig(String constant, ScreenCards.Row row, PictureView.Back back) {
        Optional<String> opens = graph.opens(constant, row.field());
        if (opens.isEmpty()) {
            showPicture(constant, row, back);
            return;
        }
        canvas.select(opens.get());
        if (!bigShot(opens.get(), new PictureView.Back(graph.shown(constant), () -> bigShot(constant, back)))) {
            say(graph.shown(opens.get()) + " has no shot yet: ⋯ Screen ▸ take it again while the game shows it.");
        }
    }

    private void showPicture(String constant, ScreenCards.Row row, PictureView.Back from) {
        boolean untried = row.state() == ScreenCards.RowState.UNTRIED || row.state() == ScreenCards.RowState.UNSEEN;
        PictureView.Back back = new PictureView.Back(graph.shown(constant), () -> bigShot(constant, from));
        viewer.showPicture(row, back, untried && !busy ? () -> tryRow(constant, row) : null, () -> {
            drawn = null;
            canvas.forgetPictures();
            changed.run();
        });
    }

    /** The right-click menu of {@code constant}'s card: its shot shown big, then the ⋯ Screen menu's edits. */
    ContextMenu menuFor(String constant) {
        ContextMenu menu = new ContextMenu(hands.screenItems(constant, this::retake).toArray(MenuItem[]::new));
        // No hand edit while a try or a walk writes the map.
        menu.getItems().forEach(item -> item.setDisable(busy));
        MenuItem big = new MenuItem("🔍 Show the screen big");
        big.setOnAction(e -> showScreen(constant));
        menu.getItems().addAll(0, List.of(big, new SeparatorMenuItem()));
        return menu;
    }

    /** The right-click menu of {@code link}: its picture, and when its way is open. */
    ContextMenu linkMenu(ScreenCards.Link link) {
        ContextMenu menu = hands.linkMenu(link, () -> showLink(link));
        menu.getItems().stream().skip(1).forEach(item -> item.setDisable(busy));
        return menu;
    }

    /** A link clicked on the map: the screen it leaves picked, and the picture clicked on it shown. */
    void showLink(ScreenCards.Link link) {
        canvas.select(link.from());
        Optional<Row> row = link.field() == null ? Optional.empty() : ScreenCards.rows(graph, link.from()).stream()
                .filter(r -> r.field().equals(link.field())).findFirst();
        row.ifPresentOrElse(r -> showPicture(link.from(), r, null), () -> say(link.via() == null
                ? graph.shown(link.from()) + " has no way back of its own: a walk closes it by its close button, "
                + "else Escape."
                : link.label() + " is " + graph.shown(link.from()) + "'s way back, not one of its inputs: its "
                + "picture shows in 🖼 Manage Pictures."));
    }

    /** {@code constant}'s shot in the picture window, big, its inputs boxed: the wheel zooms it. */
    void showScreen(String constant) {
        if (!bigShot(constant, null)) {
            say(graph.shown(constant) + " has no shot yet: ⋯ Screen ▸ take it again while the game shows it.");
        }
    }

    /**
     * {@code constant}'s shot in the picture window, {@code back} its ← (null for none); false, and nothing shown,
     * when it has none.
     */
    private boolean bigShot(String constant, PictureView.Back back) {
        Map<String, FrameCut> cuts = services.pluginValues() == null ? Map.of()
                : TemplateUses.cuts(services.pluginValues());
        List<Row> rows = ScreenCards.rows(graph, constant);
        List<ScreenCards.Strip> strips = ScreenCards.strips(graph, constant, rows);
        return viewer.showScreen(graph.shown(constant), back, width -> canvas.shotWithInputs(constant, width, rows,
                strips, cuts, audits.get(constant), row -> pickBig(constant, row, back), null));
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

    private void tryRow(String constant, Row row) {
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

    private void goHere(String constant) {
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
            canvas.forgetPictures();
            // Off the FX thread: a marker's set is matched over every other screen's shot.
            run("Noting " + String.join(", ", order) + "…", screen, () -> String.join("\n", order.stream()
                    .map(n -> n + ": " + ScreenDesk.noteCut(services, screen, n, marker && n.equals(order.getFirst())))
                    .toList()));
        };
        CaptureTemplates.open(services, window, null, null, saved);
    }

    /** Whether a try or a walk runs now: the map is not edited, by hand or by ↶/↷, until it ends. */
    boolean busy() {
        return busy;
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
