package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.plugin.assist.ElementRows;
import com.botmaker.sdk.plugin.assist.ElementRows.Row;
import com.botmaker.shared.emulator.tree.UiTree;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;

import java.awt.Point;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * The frozen frame's elements, drawn over it (spec § The picker, step 3): the pointer outlines the smallest node under
 * it, a click picks the row of the nearest tappable node from there up, and the row picked and the rows ticked are
 * outlined. Off while cutting: a drag then draws a cut.
 */
final class ElementLayer {

    private final ImageView view;
    private final Pane marks = new Pane();
    private final Rectangle hover = new Rectangle();
    private final Consumer<Row> picked;
    private UiTree tree;
    private List<Row> rows = List.of();
    private int frameWidth;
    private Row pick;
    private Collection<Row> ticked = List.of();

    /** @param picked a row picked by a click on the frame */
    ElementLayer(ImageView view, Consumer<Row> picked) {
        this.view = view;
        this.picked = picked;
        hover.getStyleClass().add("screen-element-hover");
        hover.setMouseTransparent(true);
        hover.setManaged(false);
        hover.setVisible(false);
        marks.setMouseTransparent(true);
        marks.setPickOnBounds(true);
        marks.addEventHandler(MouseEvent.MOUSE_MOVED, this::moved);
        marks.addEventHandler(MouseEvent.MOUSE_EXITED, e -> hover.setVisible(false));
        marks.addEventHandler(MouseEvent.MOUSE_CLICKED, this::clicked);
    }

    Node node() {
        return marks;
    }

    /** {@code rows} of {@code tree}, read off a frame {@code width} wide; a null tree: nothing to pick. */
    void show(UiTree tree, List<Row> rows, int width) {
        this.tree = tree;
        this.rows = rows;
        frameWidth = width;
        pick = null;
        ticked = List.of();
        setOn(tree != null);
    }

    /** Nothing to pick: a new frame, or none. */
    void clear() {
        show(null, List.of(), 0);
    }

    /** Whether the pointer and a click pick: off while cutting, or with no tree. */
    void setOn(boolean on) {
        marks.setMouseTransparent(!on || tree == null);
        hover.setVisible(false);
        redraw();
    }

    /** {@code pick} and {@code ticked} outlined. */
    void outline(Row pick, Collection<Row> ticked) {
        this.pick = pick;
        this.ticked = List.copyOf(ticked);
        redraw();
    }

    /** Drawn again at the frame's width as shown. */
    void redraw() {
        double width = view.getFitWidth();
        double height = view.getImage() == null ? 0 : width * view.getImage().getHeight() / view.getImage().getWidth();
        marks.setMinSize(width, height);
        marks.setPrefSize(width, height);
        marks.setMaxSize(width, height);
        marks.getChildren().setAll(hover);
        if (tree == null || frameWidth == 0) return;
        for (Row row : ticked) if (row != pick) marks.getChildren().add(drawn(row, "screen-element-ticked"));
        if (pick != null) marks.getChildren().add(drawn(pick, "screen-element-picked"));
    }

    private Rectangle drawn(Row row, String style) {
        Rectangle box = box(row.node().bounds());
        box.getStyleClass().add(style);
        box.setMouseTransparent(true);
        box.setManaged(false);
        return box;
    }

    private Rectangle box(java.awt.Rectangle bounds) {
        double scale = scale();
        return new Rectangle(bounds.x * scale, bounds.y * scale, bounds.width * scale, bounds.height * scale);
    }

    private void moved(MouseEvent e) {
        if (tree == null) return;
        ElementRows.under(tree, toFrame(e)).ifPresentOrElse(bounds -> {
            Rectangle at = box(bounds);
            hover.setX(at.getX());
            hover.setY(at.getY());
            hover.setWidth(at.getWidth());
            hover.setHeight(at.getHeight());
            hover.setVisible(true);
        }, () -> hover.setVisible(false));
    }

    private void clicked(MouseEvent e) {
        if (tree == null) return;
        e.consume();
        ElementRows.at(rows, tree, toFrame(e)).ifPresent(picked);
    }

    private Point toFrame(MouseEvent e) {
        double scale = scale();
        return new Point((int) (e.getX() / scale), (int) (e.getY() / scale));
    }

    private double scale() {
        return frameWidth == 0 ? 1 : view.getFitWidth() / frameWidth;
    }
}
