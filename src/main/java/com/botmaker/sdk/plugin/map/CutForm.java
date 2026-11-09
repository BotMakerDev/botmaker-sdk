package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.PluginValues.MethodShape;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.ButtonState;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.text.Text;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.ocr.OcrEngine;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.BoardCuts;
import com.botmaker.sdk.plugin.assist.BoardCuts.As;
import com.botmaker.sdk.plugin.assist.BotStates;
import com.botmaker.sdk.plugin.map.CutLayer.Drawn;
import com.botmaker.sdk.plugin.pictures.FrameWatch;
import com.botmaker.sdk.plugin.pictures.PictureCuts;
import com.botmaker.sdk.plugin.pictures.TagPicker;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.pictures.TemplateUses;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
        ANCHOR("A picture to measure an area from", Intent.READS, "icon"),
        READOUT("Words or a number read beside a picture", Intent.READS, "readout"),
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
     * The cut at {@code index} watched by ◉ Watch: what it kept; {@code longer}, which watches it again from the frames
     * given (blocking: off the FX thread); and {@code grew}, told what a longer watch kept, on the FX thread.
     */
    record Watching(int index, FrameWatch.Watched watched,
                    Function<List<BufferedImage>, BoardCuts.Watch> longer, Consumer<FrameWatch.Watched> grew) {
    }

    /**
     * The one thread the form reads cuts' words on: OCR keeps an engine per thread (tessdata loaded, never freed), so
     * a thread per read would leave one behind each time.
     */
    private static final ExecutorService WORDS = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("cut-words").daemon().factory());

    /** No method: always. */
    private static final String ALWAYS = "Always";
    /** Why an area's cut is refused without its anchor. */
    private static final String BESIDE = "pick the picture beside it, shown on this frame, or cut one as \"A picture "
            + "to measure an area from\": it is read where it sits from that picture, at any size.";
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
    /** The size of one frame of each bot's picture asked about, read once: a cut that size may join it. */
    private final Map<String, Optional<Size>> frameSizes = new HashMap<>();

    /**
     * @param shown    the screen the frame shows, or the one the cut was asked for; null: none
     * @param first    what the first cut is
     * @param watching the cut ◉ Watch watched, its frames shown to tick; null: none
     * @param save     the answer, on OK with every cut named
     * @param say      why the form was opened again
     */
    private CutForm(StudioServices services, ScreenGraph graph, BufferedImage frame, List<Drawn> cuts, String shown,
                    Kind first, Watching watching, Consumer<Answer> save, Consumer<String> say) {
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
        GridPane grid = new GridPane(8, 8);
        int row = 0;
        for (int i = 0; i < cuts.size(); i++) {
            Line line = new Line(i + 1, frame, cuts.get(i), i == 0 ? first : Kind.BUTTON);
            lines.add(line);
            grid.addRow(row++, line.thumb, new VBox(4, line.name, line.asFrame), line.intent, line.kind, line.details);
            if (watching != null && watching.index() == i) {
                // Its frames under it, across the row: the picture's poses, each ticked to keep.
                line.watched(watching);
                grid.add(line.frames, 1, row++, 4, 1);
            }
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
                    Kind first, Watching watching, Consumer<Answer> save, Consumer<String> say) {
        new CutForm(services, graph, frame, cuts, shown, first, watching, save, say).open();
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
            // Frames of one picture share its name: several may join it in one save.
            if (line.addsFrame()) continue;
            if (line.kind().named() && name.isEmpty()) return Optional.of(at + "give it a name.");
            if (line.kind().named() && names.contains(name)) return Optional.of(at + "another cut has that name.");
            names.add(name);
            if (line.watched != null && !BoardCuts.picture(line.as())) {
                return Optional.of(at + "it was watched for its frames, so it is a picture: a marker, a button or a "
                        + "look, not " + line.kind().displayName().toLowerCase(Locale.ROOT) + ".");
            }
            if (line.watched != null && line.ticked().isEmpty()) return Optional.of(at + "tick one of its frames at least.");
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
                    Optional<String> anchor = anchorWrong(line);
                    if (anchor.isPresent()) return Optional.of(at + anchor.get());
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
                    Optional<String> anchor = anchorWrong(line);
                    if (anchor.isPresent()) return Optional.of(at + anchor.get());
                }
                case READOUT -> {
                    if (on() == null) return Optional.of(at + "a readout is a screen's: pick it.");
                    Optional<String> anchor = anchorWrong(line);
                    if (anchor.isPresent()) return Optional.of(at + anchor.get());
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

    /** The names a suggestion for {@code line} must not take: the bot's pictures, and the other cuts'. */
    private Set<String> taken(Line line) {
        Set<String> taken = new HashSet<>(pictures);
        lines.stream().filter(l -> l != line).forEach(l -> taken.add(TemplateLibrary.sanitizeName(l.name.getText())));
        return taken;
    }

    /**
     * The pictures {@code line}'s area may be measured from besides the bot's: the names of this form's other cuts
     * saved as new pictures, which the save writes before the area.
     */
    private List<String> cutPictures(Line line) {
        return lines.stream().filter(l -> l != line && !l.addsFrame() && BoardCuts.picture(l.as()))
                .map(l -> TemplateLibrary.sanitizeName(l.name.getText()))
                .filter(n -> !n.isEmpty() && !pictures.contains(n)).distinct().toList();
    }

    /** Why {@code line}'s area has no anchor the save will have: none is named, or neither the bot nor a cut makes it. */
    private Optional<String> anchorWrong(Line line) {
        Optional<String> anchor = line.areaAnchor();
        if (anchor.isEmpty()) return Optional.of(BESIDE);
        if (pictures.contains(anchor.get()) || cutPictures(line).contains(anchor.get())) return Optional.empty();
        return Optional.of("the bot has no picture " + anchor.get() + " and no cut here makes one: " + BESIDE);
    }

    /** The size of one frame of the bot's picture {@code name}; empty when it has none, or it cannot be read. */
    private Optional<Size> frameSize(String name) {
        Path resources = services.resourcesDir();
        if (resources == null || !pictures.contains(name)) return Optional.empty();
        return frameSizes.computeIfAbsent(name, n -> {
            try {
                BufferedImage strip = ImageIO.read(TemplateLibrary.fileForName(resources, n).toFile());
                // Counted as add_frame counts them, so the size offered is the one it takes.
                int count = services.pluginValues() == null ? 1
                        : Math.max(1, TemplateUses.frames(services.pluginValues(), n));
                return strip == null ? Optional.empty() : Optional.of(new Size(strip.getWidth() / count,
                        strip.getHeight()));
            } catch (IOException | RuntimeException unreadable) {
                return Optional.empty();
            }
        });
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
        private final ComboBox<ButtonState> state = new ComboBox<>(FXCollections.observableArrayList(
                BotStates.offered(services.pluginValues()).stream().filter(s -> s != State.NORMAL).toList()));
        private final TextField look = new TextField();
        private final CheckBox lookMarker = new CheckBox("tells the look apart");
        private final ComboBox<String> group = new ComboBox<>();
        private final Spinner<Integer> notches = new Spinner<>(-20, 20, -3);
        /**
         * The picture an area is measured from (2026-11-09): words, a readout or a number is read where it sits beside
         * it, in its widths and heights, so it follows the picture at any size.
         */
        private final ComboBox<String> areaAnchor = new ComboBox<>();
        private final TextField shows = new TextField();
        private final TextField reads = new TextField();
        private final ComboBox<String> picture = new ComboBox<>();
        /** The picture a spot sits beside: the screen's markers and buttons' pictures. */
        private final ComboBox<String> beside = new ComboBox<>();
        private final Spinner<Integer> rows = new Spinner<>(1, 30, 1);
        private final Spinner<Integer> columns = new Spinner<>(1, 30, 1);
        /** A number read on the screen as a readout of the cut's name, and whether a readout names the subject. */
        private final CheckBox readout = new CheckBox("read on this screen");
        private final CheckBox subject = new CheckBox("names which one it shows");
        /** The cut as drawn, read by OCR once; null when it is off the frame. */
        private final BufferedImage image;
        /** The words OCR read on it, its first line; null until read, or when nothing was. */
        private String words;
        /** The name last suggested: the name field still holding it takes the next suggestion. */
        private String suggested = "";
        /** Offered when the name is a picture the bot has, one of whose frames is the cut's size. */
        private final CheckBox asFrame = new CheckBox();
        /** The frames ◉ Watch kept, each with its tick, the watch's line and ◉ Watch longer; empty when not watched. */
        private final VBox frames = new VBox(6);
        /** What ◉ Watch kept of this cut; null: not watched. */
        private FrameWatch.Watched watched;
        private final List<CheckBox> ticks = new ArrayList<>();

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
            InputInspector.typedStates(state);
            state.setValue(State.DISABLED);
            look.setPromptText("the look's name: Halloween");
            areaAnchor.setEditable(true);
            areaAnchor.getItems().setAll(pictures);
            areaAnchor.setPromptText("the picture beside it");
            // The pictures cut in this form first, by the names they have when the list opens: the anchor is most
            // often the icon cut beside the readout in the same go.
            areaAnchor.setOnShowing(e -> {
                String had = areaAnchor.getEditor().getText();
                String value = areaAnchor.getValue();
                areaAnchor.getItems().setAll(Stream.concat(cutPictures(this).stream(), pictures.stream()).toList());
                areaAnchor.setValue(value);
                areaAnchor.getEditor().setText(had);
            });
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
            details.setAlignment(Pos.CENTER_LEFT);
            asFrame.managedProperty().bind(asFrame.visibleProperty());
            asFrame.setVisible(false);
            asFrame.setTooltip(new Tooltip(MapGuide.CUT_AS_FRAME));
            name.textProperty().addListener((o, was, is) -> offerAsFrame());
            asFrame.selectedProperty().addListener((o, was, is) -> {
                intent.setDisable(is);
                kind.setDisable(is);
                details.setDisable(is);
            });
            fill();
            readWords();
        }

        Kind kind() {
            return kind.getValue();
        }

        /** Whether it is saved as one more frame of the picture its name names. */
        boolean addsFrame() {
            return asFrame.isVisible() && asFrame.isSelected();
        }

        /** "Add as a frame of slime" offered while the name is a picture the bot has whose frames are the cut's size. */
        private void offerAsFrame() {
            String typed = TemplateLibrary.sanitizeName(name.getText());
            boolean fits = watched == null && frameSize(typed)
                    .filter(s -> s.width() == drawn.box().width() && s.height() == drawn.box().height()).isPresent();
            asFrame.setText("Add as a frame of " + typed);
            asFrame.setVisible(fits);
            if (!fits) asFrame.setSelected(false);
        }

        /** Shows {@code watching}'s frames under the line, each ticked, its line and ◉ Watch longer. */
        void watched(Watching watching) {
            showFrames(watching.watched(), List.of());
            javafx.scene.control.Button longer = new javafx.scene.control.Button("◉ Watch longer");
            longer.setTooltip(new Tooltip(MapGuide.CUT_WATCH_LONGER));
            Label saying = new Label();
            saying.setWrapText(true);
            longer.setOnAction(e -> {
                List<BufferedImage> kept = watched.frames();
                longer.setDisable(true);
                // The ticks held while it watches: what they say when it ends is kept.
                ticks.forEach(t -> t.setDisable(true));
                saying.setText("Watching… the game, as it plays now");
                Thread.ofPlatform().daemon().name("cut-watch").start(() -> {
                    BoardCuts.Watch again = watching.longer().apply(kept);
                    Platform.runLater(() -> {
                        longer.setDisable(false);
                        ticks.forEach(t -> t.setDisable(false));
                        if (again.refusal() != null) {
                            saying.setText(again.refusal());
                            return;
                        }
                        saying.setText("");
                        showFrames(again.watched(), ticks.stream().map(CheckBox::isSelected).toList());
                        frames.getChildren().add(new HBox(8, longer, saying));
                        watching.grew().accept(again.watched());
                    });
                });
            });
            frames.getChildren().add(new HBox(8, longer, saying));
        }

        /**
         * {@code seen}'s frames as thumbnails, each with its tick: the first ones as {@code wasTicked} says, the rest
         * ticked; then what the watch said.
         */
        private void showFrames(FrameWatch.Watched seen, List<Boolean> wasTicked) {
            watched = seen;
            ticks.clear();
            HBox thumbs = new HBox(8);
            for (int k = 0; k < seen.frames().size(); k++) {
                ImageView view = new ImageView(ScreenCapture.toFxImage(PictureCuts.checkered(seen.frames().get(k))));
                view.setPreserveRatio(true);
                view.setFitWidth(Math.min(72, drawn.box().width()));
                view.setFitHeight(48);
                CheckBox tick = new CheckBox("frame " + (k + 1));
                tick.setSelected(k >= wasTicked.size() || wasTicked.get(k));
                ticks.add(tick);
                VBox one = new VBox(2, view, tick);
                one.setAlignment(Pos.CENTER);
                thumbs.getChildren().add(one);
            }
            int n = seen.frames().size();
            String how = seen.ending() == FrameWatch.Ending.LOOP_CLOSED
                    ? seen.loop().map(l -> ", loop " + l.toMillis() + " ms").orElse(n == 1 ? ": it held still" : "")
                    : "; " + seen.ending().displayName();
            List<Node> said = new ArrayList<>(List.of(thumbs,
                    hint(n + (n == 1 ? " frame" : " frames") + " kept of " + seen.grabs() + " grabs" + how
                            + ". Untick a frame to drop it; one ticked saves a still picture.")));
            if (seen.slow()) {
                Label slow = hint("The game gave under " + FrameWatch.SLOW_FEED + " grabs a second: a quick pose may "
                        + "be missed. ◉ Watch longer, or cut it later and add it as a frame.");
                slow.setStyle("-fx-text-fill: -bm-severity-warning-text;");
                said.add(slow);
            }
            frames.getChildren().setAll(said);
            offerAsFrame();
        }

        /** The frames kept and ticked, in order. */
        List<BufferedImage> ticked() {
            List<BufferedImage> kept = new ArrayList<>();
            for (int k = 0; k < ticks.size(); k++) if (ticks.get(k).isSelected()) kept.add(watched.frames().get(k));
            return kept;
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
                case READOUT -> List.of(new Label("beside"), areaAnchor, subject);
                case TEXT_MARKER, TEXT_BUTTON -> List.of(new Label("reads"), reads, new Label("beside"), areaAnchor);
                case SPOT -> List.of(new Label("beside"), beside, new Label("cells"), rows, new Label("×"), columns);
                case DIGITS -> List.of(shows, readout, new Label("beside"), areaAnchor);
                case RECUT -> List.of(picture);
                case ABSENT, BACK, ANCHOR -> List.of();
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
            String named = kind().named() ? name.getText().strip() : picture.getValue();
            if (addsFrame()) {
                String of = TemplateLibrary.sanitizeName(name.getText());
                return new BoardCuts.Cut(of, drawn.box(), drawn.shape(), drawn.points(), drawn.ignore(),
                        new BoardCuts.FrameOf(of));
            }
            As as = as();
            return new BoardCuts.Cut(named, drawn.box(), drawn.shape(), drawn.points(), drawn.ignore(), as,
                    watched == null || !BoardCuts.picture(as) ? null : watched.keeping(ticked()));
        }

        /** What the form says the cut is. */
        As as() {
            String method = ALWAYS.equals(when.getValue()) ? null : when.getValue();
            String field = NEW.equals(group.getValue()) ? null : group.getValue();
            return switch (kind()) {
                case MARKER -> new BoardCuts.Marker(popup.isSelected());
                case MORE_MARKER -> new BoardCuts.MoreMarker(popup.isSelected());
                case ABSENT -> new BoardCuts.Absent();
                case BUTTON -> new BoardCuts.Press(sometimes.isSelected(), method, gesture.getValue());
                case BACK -> new BoardCuts.Back();
                case LOOK -> new BoardCuts.LookOf(input.getValue(), state.getValue());
                case SCREEN_LOOK -> new BoardCuts.ScreenLook(look.getText(), lookMarker.isSelected(), method);
                case STRIP -> new BoardCuts.Member(field, false, notches.getValue());
                case TREE -> new BoardCuts.Member(field, true, 0);
                case ANCHOR -> new BoardCuts.Anchor();
                case READOUT -> new BoardCuts.Place(areaAnchor().orElse(null), subject.isSelected());
                case TEXT_MARKER -> new BoardCuts.Reads(reads.getText().strip(), false, areaAnchor().orElse(null));
                case TEXT_BUTTON -> new BoardCuts.Reads(reads.getText().strip(), true, areaAnchor().orElse(null));
                case SPOT -> new BoardCuts.Spot(beside.getValue(), rows.getValue(), columns.getValue());
                case DIGITS -> new BoardCuts.Digits(shows.getText(), readout.isSelected(), areaAnchor().orElse(null));
                case RECUT -> new BoardCuts.Recut(picture.getValue());
            };
        }

        /**
         * The picture an area is measured from, as picked or typed: a picture the bot has as it is named, else as a
         * cut's name is saved ({@link TemplateLibrary#sanitizeName}); empty when none is.
         */
        Optional<String> areaAnchor() {
            String typed = areaAnchor.getEditor().getText();
            String picked = typed == null || typed.isBlank() ? areaAnchor.getValue() : typed;
            if (picked == null || picked.isBlank()) return Optional.empty();
            String named = picked.strip();
            return Optional.of(pictures.contains(named) ? named : TemplateLibrary.sanitizeName(named));
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
