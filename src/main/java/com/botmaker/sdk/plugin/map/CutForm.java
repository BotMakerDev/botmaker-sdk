package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.PluginValues.MethodShape;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.geometry.Anchor;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.BoardCuts;
import com.botmaker.sdk.plugin.assist.BoardCuts.As;
import com.botmaker.sdk.plugin.map.CutLayer.Drawn;
import com.botmaker.sdk.plugin.pictures.PictureCuts;
import com.botmaker.sdk.plugin.pictures.TagPicker;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * What each cut drawn on the board is, asked once for all of them: its name and kind, and what that kind takes (a
 * popup, when it shows, the button it is a look of, the strip it joins, the number it shows…), with the screen they are
 * on and the tags they are filed under. Saved through {@link BoardCuts}, as the tools save them.
 */
final class CutForm {

    /** What a cut is, as the form offers it. */
    enum Kind {
        MARKER("Its marker"),
        MORE_MARKER("One more marker"),
        ABSENT("A picture it never shows"),
        BUTTON("A button"),
        BACK("Its way back"),
        LOOK("Another look of a button"),
        SCREEN_LOOK("A look of the screen"),
        STRIP("A button of a strip"),
        TREE("A node of a tree"),
        ZONE("An area (Zones)"),
        DIGITS("Digits of a number"),
        RECUT("A new image for a picture");

        private final String displayName;

        Kind(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }

        /** Whether its name is a new picture's or constant's: a recut keeps the picture's. */
        boolean named() {
            return this != RECUT;
        }
    }

    /** What the form hands back: the screen (null: none), the tags, and each cut as saved. */
    record Answer(String screen, List<String> tags, List<BoardCuts.Cut> cuts) {
    }

    /** No method: always. */
    private static final String ALWAYS = "Always";
    /** A strip or tree not on the screen yet. */
    private static final String NEW = "New";

    private final StudioServices services;
    private final ScreenGraph graph;
    private final ComboBox<String> screen = new ComboBox<>();
    private final TagPicker tags;
    private final List<Line> lines = new ArrayList<>();
    private final VBox body;
    private final Consumer<Answer> save;
    private final Consumer<String> say;
    /** Why OK did not save, at the top of the form opened again. */
    private final Label wrong = new Label();

    /**
     * @param shown  the screen the frame shows, or the one the cut was asked for; null: none
     * @param first  what the first cut is
     * @param save   the answer, on OK with every cut named
     * @param say    why the form was opened again
     */
    private CutForm(StudioServices services, ScreenGraph graph, BufferedImage frame, List<Drawn> cuts, String shown,
                    Kind first, Consumer<Answer> save, Consumer<String> say) {
        this.services = services;
        this.graph = graph;
        this.save = save;
        this.say = say;
        tags = new TagPicker(services);
        screen.setEditable(true);
        screen.getItems().setAll(graph.screens().keySet().stream().sorted().toList());
        screen.setConverter(new StringConverter<>() {
            @Override
            public String toString(String constant) {
                return constant == null ? "" : constant;
            }

            @Override
            public String fromString(String typed) {
                return typed == null || typed.isBlank() ? null : typed.strip();
            }
        });
        screen.setPromptText("none: pictures on no screen");
        screen.setValue(shown);
        GridPane grid = new GridPane(8, 8);
        for (int i = 0; i < cuts.size(); i++) {
            Line line = new Line(i + 1, frame, cuts.get(i), i == 0 ? first : Kind.BUTTON);
            lines.add(line);
            grid.addRow(i, line.thumb, line.name, line.kind, line.details);
        }
        screen.getEditor().textProperty().addListener((o, was, is) -> lines.forEach(Line::fill));
        HBox top = new HBox(8, new Label("On the screen"), screen, new Label("Tags"), tags);
        top.setAlignment(Pos.CENTER_LEFT);
        wrong.setWrapText(true);
        // The form is the toolkit's, without the map's sheet: the theme's own error colour.
        wrong.setStyle("-fx-text-fill: -bm-severity-error;");
        wrong.managedProperty().bind(wrong.visibleProperty());
        wrong.setVisible(false);
        body = new VBox(10, wrong, top, grid, hint("A name is lowercase with underscores: collect_button. The first marker "
                + "replaces the screen's; a picture is watched still a second before it is saved when the game still "
                + "shows the frame. The cuts marked a new strip make one strip, in order, turning by the first one's "
                + "notches; those marked a new tree, one tree."));
        if (cuts.size() > 1) {
            // Written in full: api.screens.Button is this file's Button.
            javafx.scene.control.Button strip = new javafx.scene.control.Button("Buttons → one new strip");
            strip.setOnAction(e -> lines.forEach(l -> l.join(Kind.STRIP)));
            javafx.scene.control.Button tree = new javafx.scene.control.Button("Buttons → one new tree");
            tree.setOnAction(e -> lines.forEach(l -> l.join(Kind.TREE)));
            HBox join = new HBox(8, strip, tree);
            join.setAlignment(Pos.CENTER_LEFT);
            body.getChildren().add(3, join);
        }
    }

    /** Asks what {@code cuts}, drawn on {@code frame}, are. On the FX thread. */
    static void ask(StudioServices services, ScreenGraph graph, BufferedImage frame, List<Drawn> cuts, String shown,
                    Kind first, Consumer<Answer> save, Consumer<String> say) {
        new CutForm(services, graph, frame, cuts, shown, first, save, say).open();
    }

    private void open() {
        Modals.form(services, lines.size() == 1 ? "Save the cut" : "Save " + lines.size() + " cuts", body,
                this::commit);
    }

    private void commit() {
        Optional<String> problem = problem();
        if (problem.isPresent()) {
            // Said in the form opened again, beside what to fix, and on the board.
            wrong.setText(problem.get());
            wrong.setVisible(true);
            say.accept(problem.get());
            open();
            return;
        }
        save.accept(new Answer(screenTyped(), tags.selected(), lines.stream().map(Line::cut).toList()));
    }

    /** Why the cuts cannot be saved as the form says, before anything is: a refusal after it would lose them. */
    private Optional<String> problem() {
        List<String> names = new ArrayList<>();
        for (Line line : lines) {
            String at = "Cut " + line.index + ": ";
            String name = TemplateLibrary.sanitizeName(line.name.getText());
            if (line.kind().named() && name.isEmpty()) return Optional.of(at + "give it a name.");
            if (line.kind().named() && names.contains(name)) return Optional.of(at + "another cut has that name.");
            names.add(name);
            switch (line.kind()) {
                case RECUT -> {
                    if (line.picture.getValue() == null) return Optional.of(at + "pick the picture it is a new image of.");
                }
                case LOOK -> {
                    if (line.input.getValue() == null) return Optional.of(at + "pick the button it is a look of.");
                }
                case STRIP, TREE -> {
                    if (on() == null) return Optional.of(at + "a strip's or a tree's button is a screen's: pick it.");
                    if (line.kind() == Kind.STRIP && NEW.equals(line.group.getValue()) && line.notches.getValue() == 0) {
                        return Optional.of(at + "a new strip turns by some notches of the wheel.");
                    }
                }
                case DIGITS -> {
                    if (line.shows.getText().chars().noneMatch(Character::isDigit)) {
                        return Optional.of(at + "give the number it shows, as the game writes it.");
                    }
                }
                default -> {
                }
            }
        }
        return Optional.empty();
    }

    /** The screen as typed or picked: a name typed and not yet taken by the box counts. */
    private String screenTyped() {
        String typed = screen.getEditor().getText();
        return typed == null || typed.isBlank() ? null : typed.strip();
    }

    private MapScreen on() {
        String typed = screenTyped();
        return typed == null ? null : graph.screens().get(typed);
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(560);
        label.getStyleClass().add("screen-panel-path");
        return label;
    }

    /** One cut's row: its picture, its name, its kind and what the kind takes. */
    private final class Line {

        private final int index;
        private final Drawn drawn;
        private final ImageView thumb = new ImageView();
        private final TextField name = new TextField();
        private final ComboBox<Kind> kind = new ComboBox<>(FXCollections.observableArrayList(Kind.values()));
        private final HBox details = new HBox(6);
        private final CheckBox popup = new CheckBox("a popup the game opens itself");
        private final CheckBox sometimes = new CheckBox("shows only at times");
        private final ComboBox<String> when = new ComboBox<>();
        private final ComboBox<Gesture> gesture = new ComboBox<>(FXCollections.observableArrayList(
                Arrays.stream(Gesture.values()).filter(g -> g != Gesture.UNKNOWN).toList()));
        private final ComboBox<String> input = new ComboBox<>();
        private final ComboBox<State> state = new ComboBox<>(FXCollections.observableArrayList(
                Arrays.stream(State.values()).filter(s -> s != State.NORMAL).toList()));
        private final TextField look = new TextField();
        private final CheckBox lookMarker = new CheckBox("tells the look apart");
        private final ComboBox<String> group = new ComboBox<>();
        private final Spinner<Integer> notches = new Spinner<>(-20, 20, -3);
        private final ComboBox<Anchor> anchor = new ComboBox<>(FXCollections.observableArrayList(Anchor.values()));
        private final TextField shows = new TextField();
        private final ComboBox<String> picture = new ComboBox<>();

        Line(int index, BufferedImage frame, Drawn drawn, Kind first) {
            this.index = index;
            this.drawn = drawn;
            PictureCuts.cut(frame, drawn.box(), drawn.shape(), drawn.points())
                    .map(p -> drawn.ignore() == null ? p : PictureCuts.masked(p, drawn.ignore()))
                    .ifPresent(p -> thumb.setImage(ScreenCapture.toFxImage(PictureCuts.checkered(p.image()))));
            thumb.setPreserveRatio(true);
            thumb.setFitWidth(Math.min(72, drawn.box().width()));
            thumb.setFitHeight(48);
            name.setPromptText(index + ": its name");
            kind.setButtonCell(kindCell());
            kind.setCellFactory(list -> kindCell());
            kind.setValue(first);
            kind.valueProperty().addListener((o, was, is) -> fill());
            state.setValue(State.DISABLED);
            look.setPromptText("the look's name: Halloween");
            anchor.setValue(Anchor.CENTER);
            shows.setPromptText("the number it shows: 12,500");
            notches.setPrefWidth(70);
            List<String> methods = services.pluginValues() == null ? List.of()
                    : services.pluginValues().methods(MethodShape.BOOLEAN);
            when.getItems().setAll(Stream.concat(Stream.of(ALWAYS), methods.stream()).toList());
            when.setValue(ALWAYS);
            gesture.setButtonCell(cell(Gesture::displayName));
            gesture.setCellFactory(list -> cell(Gesture::displayName));
            gesture.setValue(Gesture.CLICK);
            gesture.setTooltip(new Tooltip(MapGuide.GESTURE));
            Path resources = services.resourcesDir();
            picture.getItems().setAll(resources == null ? List.of() : TemplateLibrary.list(resources).stream()
                    .filter(f -> !TemplateLibrary.isUnmodifiedDefaultTemplate(f)).map(TemplateLibrary::baseName)
                    .sorted().toList());
            picture.setPromptText("the picture");
            details.setAlignment(Pos.CENTER_LEFT);
            fill();
        }

        Kind kind() {
            return kind.getValue();
        }

        /** A button, or a member of a strip or tree, made a member of a new one: {@code as} a strip or a tree. */
        void join(Kind as) {
            if (kind() != Kind.BUTTON && kind() != Kind.STRIP && kind() != Kind.TREE) return;
            kind.setValue(as);
            group.setValue(NEW);
        }

        /** The kind's own controls, its choices read off the screen picked. */
        void fill() {
            MapScreen on = on();
            input.getItems().setAll(on == null ? List.of() : on.inputs().values().stream()
                    .flatMap(i -> i instanceof Button b ? Stream.of(b.picture().id()) : Stream.empty()).toList());
            // A button of the screen picked before is no choice on this one.
            if (!input.getItems().contains(input.getValue())) {
                input.setValue(input.getItems().isEmpty() ? null : input.getItems().getFirst());
            }
            Map<String, ?> groups = on == null ? Map.of() : kind() == Kind.TREE ? on.trees() : on.strips();
            String had = group.getValue();
            group.getItems().setAll(Stream.concat(groups.keySet().stream(), Stream.of(NEW)).toList());
            group.setValue(had != null && group.getItems().contains(had) ? had : group.getItems().getFirst());
            name.setDisable(!kind().named());
            details.getChildren().setAll(switch (kind()) {
                case MARKER, MORE_MARKER -> List.of(popup);
                case BUTTON -> List.of(gesture, sometimes, new Label("there while"), when);
                case LOOK -> List.of(new Label("of"), input, new Label("meaning"), state);
                case SCREEN_LOOK -> List.of(look, lookMarker, new Label("shows while"), when);
                case STRIP -> List.of(new Label("strip"), group, new Label("new: notches"), notches);
                case TREE -> List.of(new Label("tree"), group);
                case ZONE -> List.of(new Label("stays with"), anchor);
                case DIGITS -> List.of(shows);
                case RECUT -> List.of(picture);
                case ABSENT, BACK -> List.of();
            });
        }

        BoardCuts.Cut cut() {
            String method = ALWAYS.equals(when.getValue()) ? null : when.getValue();
            String field = NEW.equals(group.getValue()) ? null : group.getValue();
            As as = switch (kind()) {
                case MARKER -> new BoardCuts.Marker(popup.isSelected());
                case MORE_MARKER -> new BoardCuts.MoreMarker(popup.isSelected());
                case ABSENT -> new BoardCuts.Absent();
                case BUTTON -> new BoardCuts.Press(sometimes.isSelected(), method, gesture.getValue());
                case BACK -> new BoardCuts.Back();
                case LOOK -> new BoardCuts.LookOf(input.getValue(), state.getValue());
                case SCREEN_LOOK -> new BoardCuts.ScreenLook(look.getText(), lookMarker.isSelected(), method);
                case STRIP -> new BoardCuts.Member(field, false, notches.getValue());
                case TREE -> new BoardCuts.Member(field, true, 0);
                case ZONE -> new BoardCuts.Place(anchor.getValue());
                case DIGITS -> new BoardCuts.Digits(shows.getText());
                case RECUT -> new BoardCuts.Recut(picture.getValue());
            };
            String named = kind().named() ? name.getText().strip() : picture.getValue();
            return new BoardCuts.Cut(named, drawn.box(), drawn.shape(), drawn.points(), drawn.ignore(), as);
        }

        private static ListCell<Kind> kindCell() {
            return cell(Kind::displayName);
        }
    }

    private static <T> ListCell<T> cell(Function<T, String> text) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : text.apply(item));
            }
        };
    }
}
