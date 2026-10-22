package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.PluginValues.MethodShape;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.geometry.Anchor;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.text.Text;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.ocr.OcrEngine;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.BoardCuts;
import com.botmaker.sdk.plugin.assist.BoardCuts.As;
import com.botmaker.sdk.plugin.assist.ScreenEdits;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.map.CutLayer.Drawn;
import com.botmaker.sdk.plugin.pictures.PictureCuts;
import com.botmaker.sdk.plugin.pictures.TagPicker;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import javafx.application.Platform;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * What each cut drawn on the board is, asked once for all of them: its name and kind, and what that kind takes (a
 * popup, when it shows, the button it is a look of, the strip it joins, the number it shows…), with the screen they are
 * on and the tags they are filed under. Saved through {@link BoardCuts}, as the tools save them.
 */
final class CutForm {

    /**
     * What a cut is for, the form's first choice: the kinds split by it (2026-10-22: one list of fifteen kinds was the
     * by-hand flow's hardest step).
     */
    enum Intent {
        TELLS("Tells the screen"),
        ACTS("Acts"),
        READS("Reads or fixes");

        private final String displayName;

        Intent(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }

        /** Its kinds, in the form's order. */
        List<Kind> kinds() {
            return Arrays.stream(Kind.values()).filter(k -> k.intent == this).toList();
        }
    }

    /** What a cut is, as the form offers it, grouped by what it is for. */
    enum Kind {
        MARKER("Its marker", Intent.TELLS, "marker"),
        MORE_MARKER("One more marker", Intent.TELLS, "marker"),
        ABSENT("A picture it never shows", Intent.TELLS, "absent"),
        TEXT_MARKER("Words that tell it", Intent.TELLS, "words"),
        BUTTON("A button", Intent.ACTS, "button"),
        BACK("Its way back", Intent.ACTS, "back"),
        STRIP("A button of a strip", Intent.ACTS, "member"),
        TREE("A node of a tree", Intent.ACTS, "node"),
        TEXT_BUTTON("A button known by its words", Intent.ACTS, "words_button"),
        SPOT("A spot beside a picture, or its grid", Intent.ACTS, "spot"),
        LOOK("Another look of a button", Intent.READS, "look"),
        SCREEN_LOOK("A look of the screen", Intent.READS, "screen_look"),
        ZONE("An area (Zones)", Intent.READS, "zone"),
        DIGITS("Digits of a number", Intent.READS, "number"),
        RECUT("A new image for a picture", Intent.READS, "recut");

        private final String displayName;
        private final Intent intent;
        private final String slug;

        Kind(String displayName, Intent intent, String slug) {
            this.displayName = displayName;
            this.intent = intent;
            this.slug = slug;
        }

        String displayName() {
            return displayName;
        }

        Intent intent() {
            return intent;
        }

        /** The kind in a suggested name: {@code home_button_3}. */
        String slug() {
            return slug;
        }

        /** Whether its name is a new picture's or constant's: a recut keeps the picture's. */
        boolean named() {
            return this != RECUT;
        }
    }

    /** What the form hands back: the screen (null: none), the tags, and each cut as saved. */
    record Answer(String screen, List<String> tags, List<BoardCuts.Cut> cuts) {
    }

    /**
     * The one thread the form reads cuts' words on: OCR keeps an engine per thread (tessdata loaded, never freed), so
     * a thread per read would leave one behind each time.
     */
    private static final ExecutorService WORDS = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("cut-words").daemon().factory());

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
    /** The bot's pictures' names: a recut's choices, and names a suggestion takes none of. */
    private final List<String> pictures;
    /** The bot's zones' constants, read once: a words' zone or a readout is saved as one. */
    private final List<String> zones;

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
        Path resources = services.resourcesDir();
        pictures = resources == null ? List.of() : TemplateLibrary.list(resources).stream()
                .filter(f -> !TemplateLibrary.isUnmodifiedDefaultTemplate(f)).map(TemplateLibrary::baseName)
                .sorted().toList();
        zones = ScreenEdits.zones(services);
        GridPane grid = new GridPane(8, 8);
        for (int i = 0; i < cuts.size(); i++) {
            Line line = new Line(i + 1, frame, cuts.get(i), i == 0 ? first : Kind.BUTTON);
            lines.add(line);
            grid.addRow(i, line.thumb, line.name, line.intent, line.kind, line.details);
        }
        screen.getEditor().textProperty().addListener((o, was, is) -> lines.forEach(Line::fill));
        HBox top = new HBox(8, new Label("On the screen"), screen, new Label("Tags"), tags);
        top.setAlignment(Pos.CENTER_LEFT);
        wrong.setWrapText(true);
        // The form is the toolkit's, without the map's sheet: the theme's own error colour.
        wrong.setStyle("-fx-text-fill: -bm-severity-error;");
        wrong.managedProperty().bind(wrong.visibleProperty());
        wrong.setVisible(false);
        body = new VBox(10, wrong, top, grid, hint("Each cut is named from the words on it, else as screen, kind and "
                + "number: change any name, lowercase with underscores (collect_button). Say first what a cut is for: "
                + "it tells the screen, it acts, or it reads or fixes. The first marker replaces the screen's; a picture is watched still a second before it is saved when the game still "
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
                case TEXT_MARKER, TEXT_BUTTON -> {
                    if (on() == null) return Optional.of(at + "words are a screen's: pick it.");
                    if (line.reads.getText().isBlank()) {
                        return Optional.of(at + "give the words it shows, as the game writes them.");
                    }
                    // Saving the words' zone over a zone of that name would move what other words read.
                    if (ScreenEdits.zones(services).contains(FlowNames.constantFor(name))) {
                        return Optional.of(at + "Zones has " + FlowNames.constantFor(name) + " already: give the "
                                + "words' zone another name.");
                    }
                }
                case SPOT -> {
                    if (on() == null) return Optional.of(at + "a spot is a screen's: pick it.");
                    if (line.beside.getValue() == null) {
                        return Optional.of(at + "pick the picture it sits beside: one that always shows near it.");
                    }
                }
                case DIGITS -> {
                    if (line.shows.getText().chars().noneMatch(Character::isDigit)) {
                        return Optional.of(at + "give the number it shows, as the game writes it.");
                    }
                    if (!line.readout.isSelected()) break;
                    if (on() == null) return Optional.of(at + "a readout is a screen's: pick it.");
                    // Its box is saved as a zone of its name too: over one of that name, it would move what that reads.
                    if (ScreenEdits.zones(services).contains(FlowNames.constantFor(name))) {
                        return Optional.of(at + "Zones has " + FlowNames.constantFor(name) + " already: give the "
                                + "number another name.");
                    }
                }
                case ZONE -> {
                    if (line.readout.isSelected() && on() == null) {
                        return Optional.of(at + "a readout is a screen's: pick it.");
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

    /** The names a suggestion for {@code line} must not take: the bot's pictures and zones, and the other cuts'. */
    private Set<String> taken(Line line) {
        Set<String> taken = new HashSet<>(pictures);
        zones.forEach(zone -> taken.add(zone.toLowerCase(Locale.ROOT)));
        lines.stream().filter(l -> l != line).forEach(l -> taken.add(TemplateLibrary.sanitizeName(l.name.getText())));
        return taken;
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
        private final ComboBox<Intent> intent = new ComboBox<>(FXCollections.observableArrayList(Intent.values()));
        private final ComboBox<Kind> kind = new ComboBox<>();
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
        private final TextField reads = new TextField();
        private final ComboBox<String> picture = new ComboBox<>();
        /** The picture a spot sits beside: the screen's markers and buttons' pictures. */
        private final ComboBox<String> beside = new ComboBox<>();
        private final Spinner<Integer> rows = new Spinner<>(1, 30, 1);
        private final Spinner<Integer> columns = new Spinner<>(1, 30, 1);
        /** A zone or a number read on the screen as a readout of the cut's name, and whether it names the subject. */
        private final CheckBox readout = new CheckBox("read on this screen");
        private final CheckBox subject = new CheckBox("names which one it shows");
        /** The cut as drawn, read by OCR once; null when it is off the frame. */
        private final BufferedImage image;
        /** The words OCR read on it, its first line; null until read, or when nothing was. */
        private String words;
        /** The name last suggested: the name field still holding it takes the next suggestion. */
        private String suggested = "";

        Line(int index, BufferedImage frame, Drawn drawn, Kind first) {
            this.index = index;
            this.drawn = drawn;
            Optional<PictureCuts.Piece> piece = PictureCuts.cut(frame, drawn.box(), drawn.shape(), drawn.points())
                    .map(p -> drawn.ignore() == null ? p : PictureCuts.masked(p, drawn.ignore()));
            image = piece.map(PictureCuts.Piece::image).orElse(null);
            piece.ifPresent(p -> thumb.setImage(ScreenCapture.toFxImage(PictureCuts.checkered(p.image()))));
            thumb.setPreserveRatio(true);
            thumb.setFitWidth(Math.min(72, drawn.box().width()));
            thumb.setFitHeight(48);
            name.setPromptText(index + ": its name");
            intent.setButtonCell(cell(Intent::displayName));
            intent.setCellFactory(list -> cell(Intent::displayName));
            intent.setValue(first.intent());
            kind.getItems().setAll(first.intent().kinds());
            kind.setButtonCell(kindCell());
            kind.setCellFactory(list -> kindCell());
            kind.setValue(first);
            // What it is for first, then which kind of that: another intent offers its own kinds, its first picked.
            intent.valueProperty().addListener((o, was, is) -> {
                if (is == null || is == was) return;
                kind.getItems().setAll(is.kinds());
                if (!is.kinds().contains(kind.getValue())) kind.setValue(is.kinds().getFirst());
            });
            kind.valueProperty().addListener((o, was, is) -> {
                if (is != null) fill();
            });
            state.setValue(State.DISABLED);
            look.setPromptText("the look's name: Halloween");
            anchor.setValue(Anchor.CENTER);
            shows.setPromptText("the number it shows: 12,500");
            reads.setPromptText("the words it shows: Settings");
            reads.setTooltip(new Tooltip(MapGuide.TEXT_MARKER));
            notches.setPrefWidth(70);
            List<String> methods = services.pluginValues() == null ? List.of()
                    : services.pluginValues().methods(MethodShape.BOOLEAN);
            when.getItems().setAll(Stream.concat(Stream.of(ALWAYS), methods.stream()).toList());
            when.setValue(ALWAYS);
            gesture.setButtonCell(cell(Gesture::displayName));
            gesture.setCellFactory(list -> cell(Gesture::displayName));
            gesture.setValue(Gesture.CLICK);
            gesture.setTooltip(new Tooltip(MapGuide.GESTURE));
            picture.getItems().setAll(pictures);
            picture.setPromptText("the picture");
            beside.setPromptText("its anchor");
            beside.setTooltip(new Tooltip(MapGuide.SPOT));
            rows.setPrefWidth(64);
            columns.setPrefWidth(64);
            readout.setTooltip(new Tooltip(MapGuide.READOUT));
            subject.disableProperty().bind(readout.selectedProperty().not());
            details.setAlignment(Pos.CENTER_LEFT);
            fill();
            readWords();
        }

        Kind kind() {
            return kind.getValue();
        }

        /** A button, or a member of a strip or tree, made a member of a new one: {@code as} a strip or a tree. */
        void join(Kind as) {
            if (kind() != Kind.BUTTON && kind() != Kind.STRIP && kind() != Kind.TREE) return;
            intent.setValue(as.intent());
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
            beside.getItems().setAll(on == null ? List.of() : Stream.concat(on.markers().stream(),
                            on.inputs().values().stream().flatMap(i -> i instanceof Button b && !b.isBeside()
                                    ? Stream.of(b.picture()) : Stream.empty()))
                    .filter(p -> p != null && p.place() != null).map(ImageTemplate::id).distinct().toList());
            if (!beside.getItems().contains(beside.getValue())) beside.setValue(null);
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
                case ZONE -> List.of(new Label("stays with"), anchor, readout, subject);
                case TEXT_MARKER, TEXT_BUTTON -> List.of(new Label("reads"), reads);
                case SPOT -> List.of(new Label("beside"), beside, new Label("cells"), rows, new Label("×"), columns);
                case DIGITS -> List.of(shows, readout);
                case RECUT -> List.of(picture);
                case ABSENT, BACK -> List.of();
            });
            showWords();
            suggest();
        }

        /** A name suggested ({@link CutNames}) while the field is empty or still holds the one suggested last. */
        private void suggest() {
            if (!kind().named()) return;
            String now = name.getText();
            if (!now.isBlank() && !now.equals(suggested)) return;
            suggested = CutNames.suggest(words, screenTyped(), kind(), taken(this));
            name.setText(suggested);
        }

        /** The words read put in {@code reads}, for words that tell the screen or press, unless typed there first. */
        private void showWords() {
            if (words != null && (kind() == Kind.TEXT_MARKER || kind() == Kind.TEXT_BUTTON)
                    && reads.getText().isBlank()) {
                reads.setText(words);
            }
        }

        /**
         * The words the cut shows, read once by OCR off the FX thread, as the run reads them: the first line, its name
         * suggested from, and put in {@code reads}. Nothing is put when OCR cannot read it.
         */
        private void readWords() {
            if (image == null) return;
            WORDS.execute(() -> {
                String said;
                try {
                    said = OcrEngine.text(image, Text.DEFAULT_OPTIONS);
                } catch (RuntimeException | LinkageError e) {
                    return;
                }
                String first = said.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
                if (first.isEmpty()) return;
                Platform.runLater(() -> {
                    words = first;
                    showWords();
                    suggest();
                });
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
                case ZONE -> new BoardCuts.Place(anchor.getValue(), readout.isSelected(),
                        readout.isSelected() && subject.isSelected());
                case TEXT_MARKER -> new BoardCuts.Reads(reads.getText().strip(), false);
                case TEXT_BUTTON -> new BoardCuts.Reads(reads.getText().strip(), true);
                case SPOT -> new BoardCuts.Spot(beside.getValue(), rows.getValue(), columns.getValue());
                case DIGITS -> new BoardCuts.Digits(shows.getText(), readout.isSelected());
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
