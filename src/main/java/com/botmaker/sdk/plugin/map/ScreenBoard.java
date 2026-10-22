package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.plugin.assist.ScreenEdits;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleFunction;

/**
 * The 🗺 Screens window's Screen tab: the picked screen's shot as big as the tab, each input boxed where it is, the
 * picked one drawn picked (the user's call, 2026-10-18). A box clicked picks its input, which the side panel then
 * shows. The wheel zooms, a drag moves it; Fit draws it as wide as the tab again.
 *
 * <p>A cover ({@link #cover}) is drawn in the shot's place: the game live, which takes the wheel and the drag itself,
 * or a frame frozen off it, which zooms as the shot does.
 */
final class ScreenBoard {

    private static final double PADDING = 12;
    private static final double LEAST = 0.25;
    private static final double MOST = 6;
    private static final double STEP = 1.2;
    /** A mouse wheel's notch, in scroll pixels. */
    private static final double NOTCH = 40;
    private static final PseudoClass REFUSED = PseudoClass.getPseudoClass("refused");

    private final StackPane holder = new StackPane();
    private final ScrollPane scroller = new ScrollPane(holder);
    private final Label title = new Label();
    private final HBox bar;
    private final HBox line = new HBox(8);
    /** What the last action answered, under the line: the panel's own status may be scrolled out of view. */
    private final Label news = new Label();
    private final HBox newsRow;
    private final BorderPane root = new BorderPane(scroller);
    /** The shot at a width, its inputs boxed; null while no screen is picked. */
    private DoubleFunction<Optional<Node>> shotAt;
    /** What is drawn in the shot's place at a width; null for the shot. */
    private DoubleFunction<Node> cover;
    /** Whether {@link #cover} is the game live, which takes the wheel and the drag. */
    private boolean live;
    /** Whether a drag draws on the cover, a cut, rather than moving it. */
    private boolean held;
    private String constant;
    /** What the board shows, so a poll that changed nothing draws nothing. */
    private Object drawn;
    /** How much wider than the tab the shot is drawn: 1 fits it. */
    private double zoom = 1;
    private double drawnWidth;

    ScreenBoard() {
        holder.setPadding(new Insets(PADDING));
        holder.setAlignment(Pos.TOP_CENTER);
        scroller.setPannable(true);
        // Always there: a scrollbar coming and going with the shot's height would change its width, and so its height.
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        // At least as wide as the tab, so a fitted shot is centred, and wider once zoomed in, so it scrolls.
        holder.minWidthProperty().bind(Bindings.createDoubleBinding(() -> scroller.getViewportBounds() == null ? 0
                : scroller.getViewportBounds().getWidth(), scroller.viewportBoundsProperty()));
        scroller.getStyleClass().add("screen-panel-scroller");
        scroller.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.getDeltaY() == 0 || live || shotAt == null && cover == null) return;
            // The wheel over a strip drawn on the shot scrolls the strip.
            for (Node at = e.getTarget() instanceof Node n ? n : null; at != null && at != scroller;
                 at = at.getParent()) {
                if (at instanceof ScrollPane) return;
            }
            e.consume();
            zoom = Math.clamp(zoom * Math.pow(STEP, e.getDeltaY() / NOTCH), LEAST, MOST);
            draw(true);
        });
        // The tab made wider or narrower: the shot is drawn again to fit it.
        scroller.viewportBoundsProperty().addListener((o, was, is) -> {
            if (was == null || Math.abs(was.getWidth() - is.getWidth()) >= 8) draw(true);
        });
        title.getStyleClass().add("screen-panel-name");
        title.setMinWidth(0);
        HBox.setHgrow(title, Priority.ALWAYS);
        Button fit = new Button("Fit");
        fit.setTooltip(new Tooltip("Draw the shot as wide as the tab"));
        fit.setOnAction(e -> {
            zoom = 1;
            draw(true);
        });
        Label hint = new Label("Click a box to pick its input · the wheel zooms · a drag moves it");
        hint.getStyleClass().add("screen-panel-path");
        hint.setMinWidth(0);
        bar = new HBox(10, title, hint, fit);
        bar.setAlignment(Pos.CENTER_LEFT);
        line.setAlignment(Pos.CENTER_LEFT);
        line.managedProperty().bind(line.visibleProperty());
        line.visibleProperty().bind(Bindings.isNotEmpty(line.getChildren()));
        news.setWrapText(true);
        news.setMinHeight(Region.USE_PREF_SIZE);
        news.getStyleClass().add("screen-board-news");
        HBox.setHgrow(news, Priority.ALWAYS);
        Button dismiss = new Button("✕");
        dismiss.setTooltip(new Tooltip("Hide this message"));
        dismiss.setOnAction(e -> say(null));
        newsRow = new HBox(8, news, dismiss);
        newsRow.setAlignment(Pos.CENTER_LEFT);
        newsRow.managedProperty().bind(newsRow.visibleProperty());
        newsRow.setVisible(false);
        VBox top = new VBox(6, bar, line, newsRow);
        top.setPadding(new Insets(6, 10, 6, 10));
        top.getStyleClass().add("screen-map-bar");
        root.setTop(top);
        root.getStyleClass().add("screen-map");
        root.getStylesheets().add(ScreenCanvas.SHEET);
        show(null, null, null, null);
    }

    Node node() {
        return root;
    }

    /** Puts {@code controls} on the bar, before Fit. */
    void controls(Node... controls) {
        bar.getChildren().addAll(bar.getChildren().size() - 1, List.of(controls));
    }

    /** What the line under the bar says and offers; nothing hides it. */
    void line(Node... said) {
        line.getChildren().setAll(said);
    }

    /** What the last action answered, kept until the next one or ✕; null or blank hides it. */
    void say(String said) {
        boolean shown = said != null && !said.isBlank();
        news.setText(shown ? said.strip() : "");
        news.pseudoClassStateChanged(REFUSED, shown && refused(said));
        newsRow.setVisible(shown);
    }

    /** Whether {@code said} is a refusal, as the edits and the tools word one. */
    static boolean refused(String said) {
        String s = said.strip();
        return s.contains(ScreenEdits.NOT_WRITTEN) || s.startsWith("It failed") || s.contains("Nothing was saved");
    }

    /**
     * Draws {@code drawn} at the board's width in the shot's place, {@code live} when it is the game itself, which
     * takes the wheel and the drag; null draws the shot again. A live cover is drawn fitted.
     */
    void cover(DoubleFunction<Node> drawn, boolean live) {
        if (drawn != cover || live != this.live) zoom = 1;
        cover = drawn;
        this.live = live;
        scroller.setPannable(!live && !held);
        draw(false);
    }

    /** {@code held}: a drag on the cover draws a cut there, and moves nothing; the wheel still zooms. */
    void holdStill(boolean held) {
        this.held = held;
        scroller.setPannable(!live && !held);
    }

    /**
     * Shows {@code constant}, called {@code name}, drawn by {@code shotAt}; {@code key} is everything the drawing is
     * made of, so a call with the same key draws nothing. Another screen is drawn fitted. Null {@code constant}: none.
     */
    void show(String constant, String name, Object key, DoubleFunction<Optional<Node>> shotAt) {
        if (!Objects.equals(constant, this.constant) && cover == null) zoom = 1;
        this.constant = constant;
        this.shotAt = shotAt;
        title.setText(constant == null ? "No screen picked" : name);
        if (Objects.equals(key, drawn) && (constant != null || !holder.getChildren().isEmpty())) return;
        drawn = key;
        if (cover == null) draw(false);
    }

    /** The shot shown with the input of field {@code picked} picked on it (null: none), not drawn again. */
    void repick(String picked) {
        if (cover == null) holder.getChildren().forEach(drawn -> ScreenCanvas.repick(drawn, picked));
    }

    /** Draws the shot again; {@code resized} only when its width changed. */
    private void draw(boolean resized) {
        double width = width();
        if (resized && Math.abs(width - drawnWidth) < 1) return;
        drawnWidth = width;
        if (cover != null) {
            holder.getChildren().setAll(cover.apply(width));
            return;
        }
        if (shotAt == null) {
            holder.getChildren().setAll(said("Pick a screen in the outline or on the graph to see it big here, or "
                    + "● Live to see the game."));
            return;
        }
        holder.getChildren().setAll(shotAt.apply(width).orElseGet(() -> said("It has no shot yet: ● Live, then ❄ "
                + "Freeze while the game shows it, or ⋯ Screen ▸ Retake its shot.")));
    }

    /** How wide the shot is drawn: the tab's width less its padding, at the zoom. */
    private double width() {
        double seen = scroller.getViewportBounds() == null ? 0 : scroller.getViewportBounds().getWidth();
        return Math.max(160, Math.floor(((seen > 0 ? seen : 800) - 2 * PADDING) * zoom));
    }

    private static Label said(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }
}
