package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.toolkit.graph.GraphCanvas;
import com.botmaker.plugin.toolkit.graph.GraphCanvas.Arrow;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Gaps;
import com.botmaker.plugin.toolkit.graph.LayeredLayout;
import com.botmaker.plugin.toolkit.graph.TreeLayout;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.internal.screens.ScreenGraph.Presence;
import com.botmaker.sdk.plugin.assist.ScreenShots;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Link;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The map drawn: one card per screen on the toolkit's {@link GraphCanvas}, laid out as a tree from the first screen,
 * left to right. Each entrance is a solid arrow carrying its input's picture; another way in and a way back are
 * dashed, and can be hidden. A double-click folds a screen's subtree away and back. Popups and screens the tree does
 * not reach sit in a grid below it.
 *
 * <p>What a run reaches is lit ({@link #setHere}) with a short trail of the screens before it; the screen being tried
 * pulses ({@link #setTrying}).
 */
final class ScreenCanvas {

    static final String SHEET = Objects.requireNonNull(ScreenCanvas.class.getResource("screens.css")).toExternalForm();

    private static final double THUMB = 52;
    /** A card's whole-screen shot: the card's width less its border. */
    private static final double CARD_SHOT = 246;
    /** A shot drawn narrower than this is loaded at twice its width; a wider one whole. */
    private static final double CARD_LOAD_LIMIT = 600;
    private static final double EDGE_THUMB = 22;
    private static final double RING = 15;
    private static final int TRAIL = 4;
    private static final Gaps GAPS = new Gaps(90, 28);

    private static final PseudoClass HERE = PseudoClass.getPseudoClass("here");
    private static final PseudoClass WALKED = PseudoClass.getPseudoClass("walked");
    private static final PseudoClass TRYING = PseudoClass.getPseudoClass("trying");

    private final Path resources;
    private final GraphCanvas<Card, Link> graph;
    private final Map<String, StackPane> views = new HashMap<>();
    private final Map<String, FadeTransition> pulses = new HashMap<>();
    private final Map<String, Image> thumbs = new HashMap<>();
    /** When each card's shot was written, as last drawn: a newer one draws the cards again. */
    private final Map<String, Long> shotTimes = new HashMap<>();
    private final Set<String> folded = new HashSet<>();
    private final Deque<String> trail = new ArrayDeque<>();
    private List<Card> cards = List.of();
    private List<Link> links = List.of();
    private String root;
    private String trying;
    /** Each card's right-click menu, by constant; null: none (the overlay editor's narrow pane). */
    private Function<String, ContextMenu> menus;
    /** Pictures read before a card is drawn, not after: a drawing made off screen is taken at once. */
    private boolean loadNow;

    /** A canvas drawing the pictures in {@code resources}, the project's resources folder (null for none). */
    ScreenCanvas(Path resources) {
        this.resources = resources;
        graph = new GraphCanvas<>(Card::constant, this::view, this::arrow, (cards, links) -> root == null
                ? LayeredLayout.arrange(cards, links, List.of())
                : TreeLayout.arrange(cards, links, root, Direction.RIGHT, GAPS, Set.copyOf(folded)));
        graph.getStylesheets().add(SHEET);
        graph.getStyleClass().add("screen-map");
        graph.setOnOpen(card -> {
            if (!folded.remove(card.constant())) folded.add(card.constant());
            redraw();
        });
    }

    GraphCanvas<Card, Link> graph() {
        return graph;
    }

    /** Reads each picture before its card is drawn, for a drawing taken at once ({@link MapDrawing}). */
    void loadNow() {
        loadNow = true;
    }

    /** Gives each card the right-click menu {@code menus} makes for its constant. */
    void setMenus(Function<String, ContextMenu> menus) {
        this.menus = menus;
    }

    /** Shows {@code cards} joined by {@code links}, the tree grown from {@code root} (null: no tree). */
    void show(List<Card> cards, List<Link> links, String root) {
        this.cards = List.copyOf(cards);
        this.links = List.copyOf(links);
        this.root = root;
        folded.retainAll(cards.stream().map(Card::constant).toList());
        redraw();
    }

    void redraw() {
        views.clear();
        pulses.values().forEach(Animation::stop);
        pulses.clear();
        graph.show(cards, links);
        light();
    }

    /** Lights {@code constant} as the screen a run is on, the ones before it as its trail; null clears it all. */
    void setHere(String constant) {
        if (constant == null) {
            trail.clear();
        } else if (!constant.equals(trail.peekFirst())) {
            trail.remove(constant);
            trail.addFirst(constant);
            while (trail.size() > TRAIL) trail.removeLast();
        }
        light();
    }

    /** Pulses {@code constant} while it is tried; null stops it. */
    void setTrying(String constant) {
        trying = constant;
        light();
    }

    private void light() {
        views.forEach(this::light);
    }

    /** Lights one card as the run and the tries say. */
    private void light(String constant, StackPane view) {
        String here = trail.peekFirst();
        view.pseudoClassStateChanged(HERE, constant.equals(here));
        view.pseudoClassStateChanged(WALKED, !constant.equals(here) && trail.contains(constant));
        boolean pulsing = constant.equals(trying);
        view.pseudoClassStateChanged(TRYING, pulsing);
        FadeTransition pulse = pulses.get(constant);
        if (pulsing) {
            pulse.play();
        } else {
            pulse.stop();
            pulse.getNode().setOpacity(0);
        }
    }

    // --- one card ---

    private Node view(Card card) {
        Label name = new Label(card.name());
        name.getStyleClass().add("screen-card-name");
        name.setMinWidth(0);

        FlowPane pills = new FlowPane(4, 4);
        pills.getChildren().add(pill(card.said(), "screen-pill-" + card.state().name().toLowerCase(Locale.ROOT)));
        if (!card.shape().isEmpty()) pills.getChildren().add(pill(card.shape(), "screen-pill-plain"));
        if (card.popup()) pills.getChildren().add(pill("popup", "screen-pill-plain"));
        if (card.presence() == Presence.SOMETIMES) {
            pills.getChildren().add(pill("sometimes", "screen-pill-plain"));
        }
        if (!card.stage().isEmpty()) pills.getChildren().add(pill(card.stage(), "screen-pill-stage"));

        VBox middle = new VBox(5, name, pills);
        middle.setMinWidth(0);
        HBox.setHgrow(middle, Priority.ALWAYS);

        HBox body = new HBox(10, thumb(card.marker(), THUMB, "screen-card-thumb"), middle, ring(card));
        body.setAlignment(Pos.CENTER_LEFT);
        body.setPadding(new Insets(8, 10, 9, 8));

        Region bar = new Region();
        bar.getStyleClass().add("screen-card-bar");
        bar.setMinHeight(4);
        VBox face = new VBox(bar);
        shot(card.constant(), CARD_SHOT).ifPresent(face.getChildren()::add);
        face.getChildren().add(body);
        face.getStyleClass().add("screen-card");
        face.pseudoClassStateChanged(PseudoClass.getPseudoClass(card.state().name().toLowerCase(Locale.ROOT)), true);
        face.pseudoClassStateChanged(PseudoClass.getPseudoClass("conditional"), card.conditional());
        face.pseudoClassStateChanged(PseudoClass.getPseudoClass("popup"), card.popup());
        face.setPrefWidth(250);
        face.setMaxWidth(250);
        // A right click that did not drag (a right drag pans the map): the card picked, then its menu.
        face.setOnMouseReleased(e -> {
            if (e.getButton() != MouseButton.SECONDARY || !e.isStillSincePress() || menus == null) return;
            graph.selectedProperty().set(card);
            ContextMenu menu = menus.apply(card.constant());
            if (menu != null) menu.show(face, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        Region halo = new Region();
        halo.getStyleClass().add("screen-card-halo");
        halo.setOpacity(0);
        halo.setMouseTransparent(true);
        FadeTransition pulse = new FadeTransition(Duration.millis(800), halo);
        pulse.setFromValue(0.15);
        pulse.setToValue(0.85);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);

        HBox badges = new HBox(4);
        badges.setPickOnBounds(false);
        badges.setAlignment(Pos.TOP_RIGHT);
        badges.setTranslateX(8);
        badges.setTranslateY(-9);
        if (card.chores() > 0) badges.getChildren().add(badge("✓" + card.chores(), card.chores() + " chore"
                + (card.chores() == 1 ? "" : "s")));
        if (card.presence() == Presence.LOCKED) {
            badges.getChildren().add(badge("🔒", "Every way in is locked on this account"));
        }
        if (folded.contains(card.constant())) badges.getChildren().add(badge("⊕", "Folded: double-click to unfold"));

        StackPane view = new StackPane(halo, face, badges);
        view.getStyleClass().add("screen-card-view");
        StackPane.setAlignment(badges, Pos.TOP_RIGHT);
        StackPane.setMargin(halo, new Insets(-5));
        Tooltip.install(view, new Tooltip(card.name() + " — Screens." + card.constant() + "\n" + card.tried() + " of "
                + card.inputs() + " inputs tried. Double-click to fold what is below it."));
        // The canvas builds a card again whenever it lays out (a theme switch too), so the old one's pulse is
        // stopped and the new one lit at once.
        FadeTransition was = pulses.put(card.constant(), pulse);
        if (was != null) was.stop();
        views.put(card.constant(), view);
        light(card.constant(), view);
        return view;
    }

    private static Label pill(String text, String style) {
        Label pill = new Label(text);
        pill.getStyleClass().addAll("screen-pill", style);
        return pill;
    }

    private static Label badge(String text, String tip) {
        Label badge = new Label(text);
        badge.getStyleClass().add("screen-card-badge");
        badge.setTooltip(new Tooltip(tip));
        return badge;
    }

    /** Inputs tried out of all, as a ring with the count in it. */
    private static Node ring(Card card) {
        double side = RING * 2 + 6;
        Arc track = new Arc(side / 2, side / 2, RING, RING, 90, 360);
        track.setType(ArcType.OPEN);
        track.getStyleClass().add("screen-ring-track");
        double share = card.inputs() == 0 ? 0 : (double) card.tried() / card.inputs();
        Arc done = new Arc(side / 2, side / 2, RING, RING, 90, -360 * share);
        done.setType(ArcType.OPEN);
        done.getStyleClass().add("screen-ring-done");
        // In a plain pane, which leaves a shape where it is: a stack pane would centre a short arc by its bounds.
        Pane arcs = new Pane(track, done);
        arcs.setMinSize(side, side);
        arcs.setPrefSize(side, side);
        arcs.setMaxSize(side, side);
        Label count = new Label(card.tried() + "/" + card.inputs());
        count.getStyleClass().add("screen-ring-count");
        return new StackPane(arcs, count);
    }

    /** The picture {@code id} at {@code size}, or a question mark where there is none yet. */
    Node thumb(String id, double size, String style) {
        StackPane box = new StackPane();
        box.getStyleClass().add(style);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        Image image = id == null ? null : image(id);
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setPreserveRatio(true);
            view.setFitWidth(size - 4);
            view.setFitHeight(size - 4);
            view.setSmooth(true);
            box.getChildren().add(view);
        } else {
            Label none = new Label(id == null ? "?" : "…");
            none.getStyleClass().add("screen-thumb-none");
            box.getChildren().add(none);
        }
        return box;
    }

    private Image image(String id) {
        if (resources == null) return null;
        return thumbs.computeIfAbsent(id, name -> {
            Path file = TemplateLibrary.fileForName(resources, name);
            return Files.isRegularFile(file) ? new Image(file.toUri().toString(), 96, 96, true, true, !loadNow) : null;
        });
    }

    /** Pictures are read once per window: reads them again and draws the cards with them, after a recut. */
    void forgetPictures() {
        thumbs.clear();
        redraw();
    }

    /**
     * {@code constant}'s whole-screen shot ({@link ScreenShots}) at {@code width}, in a box of the game's shape so a
     * card measures the same before and after it loads; empty while the screen has none.
     */
    Optional<Node> shot(String constant, double width) {
        if (resources == null) return Optional.empty();
        Path file = ScreenShots.file(resources, constant);
        long time = modified(file);
        if (time == 0) return Optional.empty();
        shotTimes.put(constant, time);
        // A card loads the game's frame at twice its width, crisp and small; the viewer loads it whole.
        double loaded = width < CARD_LOAD_LIMIT ? width * 2 : 0;
        Image image = thumbs.computeIfAbsent("screen:" + constant + "@" + time + ":" + loaded,
                key -> new Image(file.toUri().toString(), loaded, 0, true, true));
        double height = Math.round(width * 9 / 16);
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setSmooth(true);
        StackPane box = new StackPane(view);
        box.getStyleClass().add("screen-card-shot");
        box.setMinSize(width, height);
        box.setPrefSize(width, height);
        box.setMaxSize(width, height);
        return Optional.of(box);
    }

    /**
     * {@link #shot} with each of {@code rows} boxed where its picture was cut ({@code cuts}, by picture), each box
     * handing its row to {@code onPick} when clicked; {@code onShot} runs on a click elsewhere on the shot (null: none).
     */
    Optional<Node> shotWithInputs(String constant, double width, List<Row> rows, Map<String, FrameCut> cuts,
                                  Consumer<Row> onPick, Runnable onShot) {
        int shotWidth = resources == null ? 0 : ScreenShots.width(ScreenShots.file(resources, constant));
        return shot(constant, width).map(shot -> {
            double height = Math.round(width * 9 / 16);
            Pane boxes = new Pane();
            boxes.setPrefSize(width, height);
            boxes.setMaxSize(width, height);
            for (Row row : rows) {
                FrameCut cut = row.picture() == null ? null : cuts.get(row.picture());
                if (cut == null || cut.place() == null) continue;
                // A picture that names no frame was cut on the game's frame, which the shot is, pixel for pixel.
                int frame = cut.frame() != null && cut.frame().width() > 0 ? cut.frame().width() : shotWidth;
                if (frame <= 0) continue;
                double scale = width / frame;
                Rect place = cut.place();
                Rectangle box = new Rectangle(place.x() * scale, place.y() * scale,
                        Math.max(4, place.width() * scale), Math.max(4, place.height() * scale));
                box.getStyleClass().addAll("screen-shot-box", "screen-shot-box-" + row.state().name()
                        .toLowerCase(Locale.ROOT));
                Tooltip.install(box, new Tooltip(row.label() + ": " + row.result()));
                box.setOnMouseClicked(e -> {
                    onPick.accept(row);
                    e.consume();
                });
                boxes.getChildren().add(box);
            }
            StackPane layered = new StackPane(shot, boxes);
            layered.setMaxSize(width, height);
            if (onShot != null) {
                layered.setOnMouseClicked(e -> onShot.run());
                layered.getStyleClass().add("screen-shot-open");
            }
            return layered;
        });
    }

    /** Selects {@code constant}'s card and brings it into view. */
    void select(String constant) {
        cards.stream().filter(c -> c.constant().equals(constant)).findFirst().ifPresent(card -> {
            graph.selectedProperty().set(card);
            graph.centreOn(constant);
        });
    }

    /** Whether a shot came or changed since the cards were drawn: try_input or which_screen saved one meanwhile. */
    boolean shotsChanged() {
        return resources != null && cards.stream().anyMatch(c -> modified(ScreenShots.file(resources, c.constant()))
                != shotTimes.getOrDefault(c.constant(), 0L));
    }

    private static long modified(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
        } catch (IOException gone) {
            return 0;
        }
    }

    // --- one edge ---

    private Arrow arrow(Link link) {
        Arrow arrow = link.entrance() ? Arrow.solid(link.from(), link.to()) : Arrow.dashed(link.from(), link.to());
        return link.entrance() ? arrow.withLabel(chip(link.via())) : arrow;
    }

    /** What an entrance does, on its arrow: the picture clicked or scrolled, or the key pressed. */
    private Node chip(Input via) {
        Node face = via.picture() != null ? thumb(via.picture().id(), EDGE_THUMB, "screen-edge-thumb")
                : new Label(via.key() == null ? "?" : via.key().label());
        HBox chip = new HBox(face);
        chip.getStyleClass().add("screen-edge-chip");
        Tooltip.install(chip, new Tooltip(via.label()));
        return chip;
    }
}
