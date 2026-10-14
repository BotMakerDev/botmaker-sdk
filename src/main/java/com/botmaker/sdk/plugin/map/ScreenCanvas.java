package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.toolkit.graph.GraphCanvas;
import com.botmaker.plugin.toolkit.graph.GraphCanvas.Arrow;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Gaps;
import com.botmaker.plugin.toolkit.graph.LayeredLayout;
import com.botmaker.plugin.toolkit.graph.TreeLayout;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.internal.screens.ScreenGraph.Presence;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Link;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.util.Duration;

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
import java.util.Set;

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
    private final Set<String> folded = new HashSet<>();
    private final Deque<String> trail = new ArrayDeque<>();
    private List<Card> cards = List.of();
    private List<Link> links = List.of();
    private String root;
    private String trying;

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

    /** Shows {@code cards} joined by {@code links}, the tree grown from {@code root} (null: no tree). */
    void show(List<Card> cards, List<Link> links, String root) {
        this.cards = List.copyOf(cards);
        this.links = List.copyOf(links);
        this.root = root;
        folded.retainAll(cards.stream().map(Card::constant).toList());
        redraw();
    }

    private void redraw() {
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
        VBox face = new VBox(bar, body);
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
            return Files.isRegularFile(file) ? new Image(file.toUri().toString(), 96, 96, true, true, true) : null;
        });
    }

    /** Pictures are read once per window: reads them again and draws the cards with them, after a recut. */
    void forgetPictures() {
        thumbs.clear();
        redraw();
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
