package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.toolkit.graph.GraphCanvas;
import com.botmaker.plugin.toolkit.graph.GraphCanvas.Arrow;
import com.botmaker.plugin.toolkit.graph.GraphLayout;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Gaps;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import com.botmaker.plugin.toolkit.graph.LayeredLayout;
import com.botmaker.plugin.toolkit.graph.TreeLayout;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.internal.screens.ScreenGraph.Presence;
import com.botmaker.sdk.plugin.assist.MapAudit.Audit;
import com.botmaker.sdk.plugin.assist.MapAudit.Spot;
import com.botmaker.sdk.plugin.assist.MapAudit.Where;
import com.botmaker.sdk.plugin.assist.ScreenShots;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Link;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import com.botmaker.sdk.plugin.map.ScreenCards.Strip;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
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
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    private static final String SHOT_KEY = "screen:";
    private static final String PICTURE_KEY = "picture:";

    private static final PseudoClass HERE = PseudoClass.getPseudoClass("here");
    private static final PseudoClass WALKED = PseudoClass.getPseudoClass("walked");
    private static final PseudoClass TRYING = PseudoClass.getPseudoClass("trying");

    /** The most pictures and shots kept read: a long session's retakes and recuts drop the oldest. */
    private static final int MOST_IMAGES = 400;

    private final Path resources;
    private final GraphCanvas<Card, Link> graph;
    private final Map<String, FadeTransition> pulses = new HashMap<>();
    /** Each picture and shot read, by its file's name and time: a recut or a retake is a new key. */
    private final Map<String, Image> thumbs = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MOST_IMAGES;
        }
    };
    /** When each card's shot and each picture drawn was written, as the cards last drew them: a newer one redraws. */
    private final Map<String, Long> drawnTimes = new HashMap<>();
    private final Set<String> folded = new HashSet<>();
    private final Deque<String> trail = new ArrayDeque<>();
    private List<Card> cards = List.of();
    private List<Link> links = List.of();
    private String root;
    private String trying;
    /** Pictures read before a card is drawn, not after: a drawing made off screen is taken at once. */
    private boolean loadNow;
    /** The tree's size laid out each way, at the last layout: a resize asks again which fits the window. */
    private Map<Direction, Shape> shapes = Map.of();
    private Direction direction = Direction.RIGHT;
    /** Where the last layout put each card, before any was moved by hand. */
    private Placement laid = new Placement(Map.of(), Set.of(), 0);
    private Consumer<Map<String, Point>> onMoved = moved -> {};
    /** How much bigger the other way must show the map before the tree turns. */
    private static final double TURN = 1.15;

    /** How big a whole layout is. */
    record Shape(double width, double height) {}

    /** A canvas drawing the pictures in {@code resources}, the project's resources folder (null for none). */
    ScreenCanvas(Path resources) {
        this.resources = resources;
        graph = new GraphCanvas<>(Card::constant, this::view, this::arrow, this::arrange);
        graph.getStylesheets().add(SHEET);
        graph.getStyleClass().add("screen-map");
        graph.setOnOpen(card -> {
            // A screen with nothing below it has nothing to fold.
            if (!folded.remove(card.constant()) && !below(card.constant()).isEmpty()) folded.add(card.constant());
            redraw();
        });
        // A card picked while folded away (by Find, a run, an input opening it): the folds above it open.
        graph.selectedProperty().addListener((o, was, is) -> {
            // Only a new pick: a redraw handing back the same screen's new card leaves a fold the user just made.
            boolean picked = is != null && (was == null || !was.constant().equals(is.constant()));
            if (picked && reveal(is.constant())) {
                Platform.runLater(() -> {
                    redraw();
                    graph.centreOn(is.constant());
                });
            }
        });
        // A window made wide or tall: the tree is laid out the other way when that way now fits it better. Not while
        // cards are moved by hand: they stay where they were put.
        graph.viewportProperty().addListener((o, was, is) -> {
            if (!shapes.isEmpty() && pins().isEmpty() && turns(shapes, direction, is.getWidth(), is.getHeight())) {
                redraw();
            }
        });
        graph.setOnMoved(moved -> onMoved.accept(corners(moved)));
    }

    /**
     * Gives each card move to {@code onMoved}, as the corners to keep: the first move pins every card shown where it
     * is, so the map comes back as it was left whatever shape the window has then; a later one only the moved cards.
     */
    void setOnMoved(Consumer<Map<String, Point>> onMoved) {
        this.onMoved = onMoved;
    }

    private Map<String, Point> corners(Map<String, Pt> moved) {
        Map<String, Pt> kept = new LinkedHashMap<>();
        if (pins().isEmpty()) {
            laid.at().forEach((id, at) -> {
                if (!laid.hidden().contains(id)) kept.put(id, at);
            });
        }
        kept.putAll(moved);
        Map<String, Point> corners = new LinkedHashMap<>();
        kept.forEach((id, at) -> corners.put(id, new Point((int) Math.round(at.x()), (int) Math.round(at.y()))));
        return corners;
    }

    /** The corners the cards were moved to, by constant. */
    private Map<String, Pt> pins() {
        Map<String, Pt> pins = new LinkedHashMap<>();
        for (Card card : cards) if (card.at() != null) pins.put(card.constant(), new Pt(card.at().x(), card.at().y()));
        return pins;
    }

    /**
     * The tree laid out rightward and downward, keeping whichever shows bigger in the window (the user's call,
     * 2026-10-15: Home's twenty screens made a column a wide window showed small); a map with no tree, layered.
     */
    private Placement arrange(List<GraphLayout.Card> cards, List<GraphLayout.Link> links) {
        if (root == null) {
            shapes = Map.of();
            direction = Direction.RIGHT;
            graph.setDirection(direction);
            laid = LayeredLayout.arrange(cards, links, List.of());
            return laid;
        }
        Map<Direction, Placement> each = new EnumMap<>(Direction.class);
        Map<Direction, Shape> sized = new EnumMap<>(Direction.class);
        for (Direction way : Direction.values()) {
            Placement placed = TreeLayout.arrange(cards, links, root, way, GAPS, Set.copyOf(folded));
            each.put(way, placed);
            sized.put(way, shape(placed, cards));
        }
        boolean first = shapes.isEmpty();
        shapes = Map.copyOf(sized);
        Bounds view = graph.viewportProperty().get();
        double w = view == null ? 0 : view.getWidth(), h = view == null ? 0 : view.getHeight();
        if (first || pins().isEmpty() && turns(shapes, direction, w, h)) direction = better(shapes, w, h);
        graph.setDirection(direction);
        laid = each.get(direction);
        return laid;
    }

    /** How big {@code placed} is: the box round its shown cards. */
    static Shape shape(Placement placed, List<GraphLayout.Card> cards) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (GraphLayout.Card card : cards) {
            Pt at = placed.at().get(card.id());
            if (at == null || placed.hidden().contains(card.id())) continue;
            minX = Math.min(minX, at.x());
            minY = Math.min(minY, at.y());
            maxX = Math.max(maxX, at.x() + card.width());
            maxY = Math.max(maxY, at.y() + card.height());
        }
        return minX > maxX ? new Shape(0, 0) : new Shape(maxX - minX, maxY - minY);
    }

    /**
     * The way whose layout a {@code width} × {@code height} window shows at the bigger zoom; rightward on a tie, and
     * for a window not measured yet, as the map's window opens wider than tall.
     */
    static Direction better(Map<Direction, Shape> shapes, double width, double height) {
        Direction best = Direction.RIGHT;
        double bestZoom = -1;
        for (Direction way : Direction.values()) {
            double zoom = zoom(shapes, way, width, height);
            if (zoom > bestZoom + 1e-9) {
                best = way;
                bestZoom = zoom;
            }
        }
        return best;
    }

    /**
     * Whether the window now shows the other way's layout clearly bigger than {@code current}'s: by {@link #TURN} at
     * least, so a window near the tie, or one whose scrollbars come and go with the layout, does not flip it back.
     */
    static boolean turns(Map<Direction, Shape> shapes, Direction current, double width, double height) {
        Direction best = better(shapes, width, height);
        return best != current && zoom(shapes, best, width, height) > TURN * zoom(shapes, current, width, height);
    }

    /** The zoom a {@code width} × {@code height} window shows {@code way}'s whole layout at; -1 for no layout. */
    private static double zoom(Map<Direction, Shape> shapes, Direction way, double width, double height) {
        Shape shape = shapes.get(way);
        if (shape == null) return -1;
        double w = width > 0 ? width : 16;
        double h = height > 0 ? height : 9;
        return Math.min(w / Math.max(1, shape.width()), h / Math.max(1, shape.height()));
    }

    GraphCanvas<Card, Link> graph() {
        return graph;
    }

    /** Reads each picture before its card is drawn, for a drawing taken at once ({@link MapDrawing}). */
    void loadNow() {
        loadNow = true;
    }

    /** Gives each card the right-click menu {@code cardMenus} makes for its constant, each link {@code linkMenus}'. */
    void setMenus(Function<String, ContextMenu> cardMenus, Function<Link, ContextMenu> linkMenus) {
        graph.setMenus(card -> cardMenus.apply(card.constant()), linkMenus);
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
        pulses.values().forEach(Animation::stop);
        pulses.clear();
        drawnTimes.clear();
        // Before the cards, so they are laid out once with their corners.
        graph.setPinned(pins());
        graph.show(cards, links);
        light();
    }

    /** The screens the tree opens from {@code constant}: its entrances' ends. */
    private List<String> below(String constant) {
        return links.stream().filter(l -> l.entrance() && l.from().equals(constant)).map(Link::to).toList();
    }

    /** Opens every fold above {@code constant} on the tree; whether one was folded. */
    private boolean reveal(String constant) {
        boolean opened = false;
        Set<String> seen = new HashSet<>();
        for (String at = constant; at != null && seen.add(at); ) {
            String now = at;
            at = links.stream().filter(l -> l.entrance() && l.to().equals(now)).map(Link::from).findFirst()
                    .orElse(null);
            if (at != null) opened |= folded.remove(at);
        }
        return opened;
    }

    /** Brings {@code constant}'s card into view, opening the folds above it: a run reached it. */
    void centreOn(String constant) {
        if (reveal(constant)) redraw();
        graph.centreOn(constant);
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
        for (Card card : cards) {
            Node view = graph.cardOf(card.constant());
            if (view != null && pulses.containsKey(card.constant())) light(card.constant(), view);
        }
    }

    /** Lights one card as the run and the tries say. */
    private void light(String constant, Node view) {
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

        VBox middle = new VBox(5, name, pills(card));
        middle.setMinWidth(0);
        HBox.setHgrow(middle, Priority.ALWAYS);

        HBox body = new HBox(10, thumb(card.marker(), THUMB, "screen-card-thumb"), middle, ring(card));
        body.setAlignment(Pos.CENTER_LEFT);
        body.setPadding(new Insets(8, 10, 9, 8));

        Region bar = new Region();
        bar.getStyleClass().add("screen-card-bar");
        bar.setMinHeight(4);
        VBox face = new VBox(bar);
        // What the card draws is what a newer file is checked against: the panel's own shots leave it be.
        if (resources != null) drawnTimes.put(SHOT_KEY + card.constant(), shotTime(card.constant()));
        shot(card.constant(), CARD_SHOT).ifPresent(face.getChildren()::add);
        face.getChildren().add(body);
        face.getStyleClass().add("screen-card");
        face.pseudoClassStateChanged(PseudoClass.getPseudoClass(card.state().name().toLowerCase(Locale.ROOT)), true);
        face.pseudoClassStateChanged(PseudoClass.getPseudoClass("conditional"), card.conditional());
        face.pseudoClassStateChanged(PseudoClass.getPseudoClass("popup"), card.popup());
        face.setPrefWidth(250);
        face.setMaxWidth(250);

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
        if (card.presence() == Presence.LOCKED) {
            badges.getChildren().add(badge("🔒", MapGuide.LOCKED));
        }
        if (folded.contains(card.constant())) badges.getChildren().add(badge("⊕", MapGuide.FOLDED));

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
        light(card.constant(), view);
        return view;
    }

    /** {@code card}'s pills ({@link ScreenCards#pills}), as the card and the side panel show them. */
    static FlowPane pills(Card card) {
        FlowPane pills = new FlowPane(4, 4);
        for (ScreenCards.Pill one : ScreenCards.pills(card)) {
            Label pill = new Label(one.text());
            pill.getStyleClass().addAll("screen-pill", one.style());
            pills.getChildren().add(pill);
        }
        return pills;
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
        Path file = TemplateLibrary.fileForName(resources, id);
        long time = modified(file);
        // Kept for a picture not there yet too: cut later, it is drawn then.
        drawnTimes.put(PICTURE_KEY + id, time);
        if (time == 0) return null;
        // Keyed by the file's time: a picture recut by the assistant's crop_pictures reads afresh, not from the cache.
        return thumbs.computeIfAbsent(id + "@" + time, key -> new Image(file.toUri().toString(), 96, 96, true, true,
                !loadNow));
    }

    /** Draws the cards again with their pictures read afresh, after a recut here. */
    void forgetPictures() {
        thumbs.clear();
        redraw();
    }

    /** The picture {@code id}'s size as cut, in the game's pixels; empty when it is not cut. */
    Size pictureSize(String id) {
        return resources == null ? new Size() : ScreenShots.size(TemplateLibrary.fileForName(resources, id));
    }

    /** When the picture {@code id} was written, 0 for none: a recut changes it. */
    long pictureTime(String id) {
        return resources == null ? 0 : modified(TemplateLibrary.fileForName(resources, id));
    }

    /** When {@code constant}'s shot was written, 0 for none: a retake changes it. */
    long shotTime(String constant) {
        return resources == null ? 0 : modified(ScreenShots.file(resources, constant));
    }

    /**
     * {@code constant}'s whole-screen shot ({@link ScreenShots}) at {@code width}, in a box of the shot's own shape so
     * a card measures the same before and after it loads; empty while the screen has none.
     */
    Optional<Node> shot(String constant, double width) {
        if (resources == null) return Optional.empty();
        // A frame takes ~50 ms to read: read with the window shown, not before (1.7 s for Firestone's 36, 2026-10-16),
        // the card sized meanwhile by the file's header.
        Size size = ScreenShots.size(ScreenShots.file(resources, constant));
        return image(constant, width, !loadNow).map(image -> shot(image, width, size.width() > 0 && size.height() > 0
                ? Math.round(width * size.height() / size.width()) : heightAt(image, width)));
    }

    private static Node shot(Image image, double width, double height) {
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
        return box;
    }

    /**
     * {@code constant}'s shot read for drawing at {@code width}, read on a thread of its own ({@code background}) or at
     * once; empty while it has none.
     */
    private Optional<Image> image(String constant, double width, boolean background) {
        if (resources == null) return Optional.empty();
        Path file = ScreenShots.file(resources, constant);
        long time = modified(file);
        if (time == 0) return Optional.empty();
        // A card loads the game's frame at twice its width, crisp and small; the viewer loads it whole.
        double loaded = width < CARD_LOAD_LIMIT ? width * 2 : 0;
        // A whole frame is megabytes: read for the viewer each time, never kept.
        if (loaded == 0) return Optional.of(new Image(file.toUri().toString(), 0, 0, true, true));
        // Keyed by how it reads too: the panel's boxes need a shot read whole, not a card's still loading.
        return Optional.of(thumbs.computeIfAbsent(SHOT_KEY + constant + "@" + time + ":" + loaded
                        + (background ? "~" : ""),
                key -> new Image(file.toUri().toString(), loaded, 0, true, true, background)));
    }

    /** How tall {@code image} draws at {@code width}: its own shape; a game's usual 16:9 until it has read. */
    private static double heightAt(Image image, double width) {
        boolean read = image.getWidth() > 0 && image.getHeight() > 0;
        return Math.round(width * (read ? image.getHeight() / image.getWidth() : 9.0 / 16));
    }

    /** A box on a drawn shot, in the drawing's pixels. */
    record Box(double x, double y, double width, double height) {}

    /**
     * Where {@code place}, cut on a {@code frameWidth} × {@code frameHeight} frame, is on a shot drawn
     * {@code width} × {@code height}: the frame fitted into the shot whole and centred, as a frame of another shape
     * (the game's window resized since the cut) would show on it. At least 4 pixels a side, to be clicked.
     */
    static Box boxOn(Rect place, double frameWidth, double frameHeight, double width, double height) {
        double scale = Math.min(width / frameWidth, height / frameHeight);
        double left = (width - frameWidth * scale) / 2;
        double top = (height - frameHeight * scale) / 2;
        return new Box(left + place.x() * scale, top + place.y() * scale, Math.max(4, place.width() * scale),
                Math.max(4, place.height() * scale));
    }

    /**
     * {@link #shot} with each of {@code rows} boxed where its picture is on the shot ({@code audit}), else, before the
     * audit is made or for a picture it has not checked, where it was cut ({@code cuts}, by picture); each box hands its
     * row to {@code onPick} when clicked. A box away from its picture's place is dashed; a picture not on the shot is
     * listed under it. A member of one of {@code strips} is drawn in its strip instead, every member of the list in
     * it, over the place the list sits. {@code onShot} runs on a click elsewhere on the shot (null: none). The input
     * whose field is {@code picked} is drawn picked (null: none).
     */
    Optional<Node> shotWithInputs(String constant, double width, List<Row> rows, List<Strip> strips,
                                  Map<String, FrameCut> cuts, Audit audit, String picked, Consumer<Row> onPick,
                                  Runnable onShot) {
        int shotWidth = resources == null ? 0 : ScreenShots.size(ScreenShots.file(resources, constant)).width();
        // One read: the shot and its boxes are of the same file, rewritten meanwhile or not.
        return image(constant, width, false).map(image -> {
            double height = heightAt(image, width);
            Node shot = shot(image, width, height);
            // The shot is the game's frame: its height is its width in the shot's own shape.
            double shotHeight = shotWidth * height / width;
            Pane boxes = new Pane();
            boxes.setPrefSize(width, height);
            boxes.setMaxSize(width, height);
            FlowPane missing = new FlowPane(6, 4);
            // A shot whose size is unread has no place for a strip: its members are boxed one by one instead.
            List<Strip> drawn = shotWidth > 0 && shotHeight > 0 ? strips : List.of();
            for (Row row : rows) {
                if (row.picture() == null || drawn.stream().anyMatch(s -> s.has(row))) continue;
                Optional<Spot> spot = audit == null ? Optional.empty() : audit.of(row.picture());
                if (spot.isPresent() && spot.get().where() == Where.MISSING) {
                    Label gone = new Label(row.label());
                    gone.getStyleClass().addAll("screen-shot-missing", "screen-shot-open");
                    Tooltip.install(gone, new Tooltip(row.label() + ": its picture is not on this shot"
                            + (spot.get().area() != null ? "; it is scrolled out of its list."
                            : spot.get().sure() ? "." : "; it comes and goes, or is another look.")));
                    gone.setOnMouseClicked(e -> onPick.accept(row));
                    missing.getChildren().add(gone);
                    continue;
                }
                Box at;
                if (spot.isPresent() && spot.get().at() != null && shotWidth > 0 && shotHeight > 0) {
                    // Found on the shot, in the shot's own pixels.
                    at = boxOn(spot.get().at(), shotWidth, shotHeight, width, height);
                } else {
                    FrameCut cut = cuts.get(row.picture());
                    if (cut == null || cut.place() == null) continue;
                    // A picture that names no frame was cut on the game's frame, which the shot is, pixel for pixel.
                    boolean framed = cut.frame() != null && cut.frame().width() > 0 && cut.frame().height() > 0;
                    double frameWidth = framed ? cut.frame().width() : shotWidth;
                    double frameHeight = framed ? cut.frame().height() : shotHeight;
                    if (frameWidth <= 0 || frameHeight <= 0) continue;
                    at = boxOn(cut.place(), frameWidth, frameHeight, width, height);
                }
                Rectangle box = new Rectangle(at.x(), at.y(), at.width(), at.height());
                box.getStyleClass().addAll("screen-shot-box", "screen-shot-box-" + row.state().name()
                        .toLowerCase(Locale.ROOT));
                if (row.field().equals(picked)) box.getStyleClass().add("screen-shot-box-picked");
                String tip = row.label() + ": " + row.result();
                if (spot.isPresent() && spot.get().where() == Where.ELSEWHERE) {
                    box.getStyleClass().add("screen-shot-box-away");
                    tip += "\nIt sits " + (spot.get().place() == null ? "here"
                            : spot.get().dx() + "," + spot.get().dy() + " px from its place") + ": its picture's place "
                            + "is elsewhere.";
                }
                Tooltip.install(box, new Tooltip(tip));
                box.setOnMouseClicked(e -> {
                    onPick.accept(row);
                    e.consume();
                });
                boxes.getChildren().add(box);
            }
            for (Strip strip : drawn) {
                boxes.getChildren().add(strip(strip, boxOn(strip.place(), shotWidth, shotHeight, width, height),
                        picked, onPick));
            }
            StackPane layered = new StackPane(shot, boxes);
            layered.setMaxSize(width, height);
            if (onShot != null) {
                layered.setOnMouseClicked(e -> onShot.run());
                layered.getStyleClass().add("screen-shot-open");
            }
            if (missing.getChildren().isEmpty()) return layered;
            Label title = new Label("Not on this shot:");
            title.getStyleClass().add("screen-shot-missing-title");
            missing.getChildren().addFirst(title);
            missing.setMaxWidth(width);
            VBox shotAndMissing = new VBox(4, layered, missing);
            shotAndMissing.setMaxWidth(width);
            return shotAndMissing;
        });
    }

    /**
     * {@code strip} drawn {@code at} its place on a shot: each member's picture, the list's own order, as wide as the
     * strip (tall, across), the wheel scrolling it; each picture hands its row to {@code onPick} when clicked, the one
     * of field {@code picked} drawn picked.
     */
    private Node strip(Strip strip, Box at, String picked, Consumer<Row> onPick) {
        double size = Math.max(16, (strip.down() ? at.width() : at.height()) - 4);
        Pane line = strip.down() ? new VBox(2) : new HBox(2);
        for (Row row : strip.members()) {
            Node picture = thumb(row.picture(), size, "screen-strip-member");
            picture.getStyleClass().add("screen-strip-member-" + row.state().name().toLowerCase(Locale.ROOT));
            if (row.field().equals(picked)) picture.getStyleClass().add("screen-strip-member-picked");
            // Not on the screen at every time: drawn faint, as the game shows it only at times.
            if (row.input().comesAndGoes()) picture.setOpacity(0.45);
            Tooltip.install(picture, new Tooltip(row.label() + ": " + row.result()));
            picture.setOnMouseClicked(e -> {
                onPick.accept(row);
                e.consume();
            });
            line.getChildren().add(picture);
        }
        ScrollPane scroll = new ScrollPane(line);
        scroll.getStyleClass().add("screen-strip");
        scroll.setFitToWidth(strip.down());
        scroll.setFitToHeight(!strip.down());
        scroll.setHbarPolicy(strip.down() ? ScrollPane.ScrollBarPolicy.NEVER : ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(strip.down() ? ScrollPane.ScrollBarPolicy.AS_NEEDED : ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefSize(at.width(), at.height());
        scroll.setMaxSize(at.width(), at.height());
        scroll.relocate(at.x(), at.y());
        // An across strip scrolls by the wheel too, which turns it down.
        if (!strip.down()) {
            scroll.addEventFilter(ScrollEvent.SCROLL, e -> {
                if (e.getDeltaX() != 0 || line.getWidth() <= at.width()) return;
                scroll.setHvalue(scroll.getHvalue() - e.getDeltaY() / (line.getWidth() - at.width()));
                e.consume();
            });
        }
        // The wheel over the strip scrolls it, not the view the shot is in.
        scroll.addEventHandler(ScrollEvent.SCROLL, ScrollEvent::consume);
        Tooltip.install(scroll, new Tooltip(strip.members().size() + " in this list: the wheel scrolls it."));
        return scroll;
    }

    /** Selects {@code constant}'s card and brings it into view. */
    void select(String constant) {
        cards.stream().filter(c -> c.constant().equals(constant)).findFirst().ifPresent(card -> {
            graph.selectedProperty().set(card);
            graph.centreOn(constant);
        });
    }

    /**
     * Whether a shot or a picture the cards draw came or changed since they were drawn: try_input, which_screen or a
     * retake saved a shot meanwhile, or crop_pictures recut a marker.
     */
    boolean filesChanged() {
        if (resources == null) return false;
        if (cards.stream().anyMatch(c -> shotTime(c.constant()) != drawnTimes.getOrDefault(SHOT_KEY + c.constant(),
                0L))) {
            return true;
        }
        return drawnTimes.entrySet().stream().filter(e -> e.getKey().startsWith(PICTURE_KEY)).anyMatch(e ->
                modified(TemplateLibrary.fileForName(resources, e.getKey().substring(PICTURE_KEY.length())))
                        != e.getValue());
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
        return link.entrance() || !link.when().isEmpty() || !link.look().isEmpty() ? arrow.withLabel(chip(link))
                : arrow;
    }

    /**
     * What an entrance does, on its arrow: the picture clicked or scrolled, or the key pressed; and, on any arrow,
     * the look its button must have and the method a way is open only while.
     */
    private Node chip(Link link) {
        Input via = link.via();
        HBox chip = new HBox(4);
        if (link.entrance() && via != null) {
            String picture = ScreenCards.picture(via);
            chip.getChildren().add(picture != null ? thumb(picture, EDGE_THUMB, "screen-edge-thumb")
                    : new Label(via.label()));
        }
        List<String> needs = new ArrayList<>();
        if (!link.look().isEmpty()) needs.add(link.look());
        if (!link.when().isEmpty()) needs.add("while " + link.when());
        if (!needs.isEmpty()) {
            Label when = new Label(String.join(", ", needs));
            when.getStyleClass().add("screen-edge-when");
            chip.getChildren().add(when);
        }
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("screen-edge-chip");
        Tooltip.install(chip, new Tooltip(link.label() + (link.look().isEmpty() ? "" : ", while it looks "
                + link.look()) + (link.when().isEmpty() ? "" : ", only while " + link.when())
                + "\nClick to show its picture; right-click to change when it is open."));
        return chip;
    }
}
