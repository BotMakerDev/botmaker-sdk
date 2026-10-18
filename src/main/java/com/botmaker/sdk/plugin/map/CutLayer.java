package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.plugin.pictures.PictureCuts;
import com.botmaker.sdk.plugin.screen.CutShape;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cuts drawn on a frozen frame on the board itself (the user's call, 2026-10-18): no window over the game, and the
 * pixels are the frame's, never grabbed again. A box or an oval is dragged, a free shape clicked corner by corner (a
 * double-click or Enter closes it, Backspace drops a corner, Esc the shape); with ▨ on, a drag inside a cut is the part
 * of it matching skips. Every cut is kept in the frame's pixels, so the board zooms under it.
 */
final class CutLayer {

    /** The fewest corners a free shape closes with. */
    private static final int CORNERS = 3;
    /** The smallest cut, in the board's pixels: a click is no cut. */
    private static final double LEAST = 3;

    /** A cut in the frame's pixels: its box, a free shape's corners (else none), and the part skipped (null: none). */
    record Drawn(Rect box, CutShape shape, List<Point> points, Rect ignore) {

        Drawn {
            points = List.copyOf(points);
        }

        Drawn ignoring(Rect part) {
            return new Drawn(box, shape, points, part);
        }
    }

    private final ImageView view;
    private final Pane marks = new Pane();
    private final StackPane root;
    private final Runnable changed;
    private final List<Drawn> cuts = new ArrayList<>();
    /** A free shape's corners clicked so far, in the frame's pixels. */
    private final List<Point> corners = new ArrayList<>();
    private final Rectangle band = new Rectangle();
    private final Ellipse oval = new Ellipse();
    private final Polyline line = new Polyline();
    private int frameWidth;
    private int frameHeight;
    private boolean on;
    private CutShape shape = CutShape.RECT;
    private boolean ignoring;
    private double[] origin;

    /** @param changed run when a cut is drawn, undone or cleared */
    CutLayer(ImageView view, Runnable changed) {
        this.view = view;
        this.changed = changed;
        for (Shape s : List.of(band, oval, line)) {
            s.getStyleClass().add("screen-cut-band");
            s.setMouseTransparent(true);
            s.setManaged(false);
        }
        root = new StackPane(view, marks);
        root.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        root.setFocusTraversable(true);
        marks.setMouseTransparent(true);
        marks.addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
        marks.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::dragged);
        marks.addEventHandler(MouseEvent.MOUSE_RELEASED, this::released);
        marks.addEventHandler(MouseEvent.MOUSE_CLICKED, this::clicked);
        marks.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            if (on && !corners.isEmpty()) drawCorners(e.getX(), e.getY());
        });
        root.addEventHandler(KeyEvent.KEY_PRESSED, this::keyed);
    }

    Node node() {
        return root;
    }

    /** A new frame, {@code width × height}: nothing cut on it, not cutting. */
    void reset(int width, int height) {
        frameWidth = width;
        frameHeight = height;
        cuts.clear();
        corners.clear();
        ignoring = false;
        setOn(false);
    }

    /** Starts or stops drawing; the cuts drawn stay until {@link #clear}. */
    void setOn(boolean on) {
        this.on = on;
        marks.setMouseTransparent(!on);
        marks.setCursor(on ? javafx.scene.Cursor.CROSSHAIR : null);
        origin = null;
        corners.clear();
        if (on) root.requestFocus();
        redraw();
    }

    boolean isOn() {
        return on;
    }

    void setShape(CutShape shape) {
        this.shape = shape;
        corners.clear();
        redraw();
    }

    CutShape shape() {
        return shape;
    }

    /** With it on, a drag inside a cut is the part of it matching skips. */
    void setIgnoring(boolean ignoring) {
        this.ignoring = ignoring;
        corners.clear();
        redraw();
    }

    boolean ignoring() {
        return ignoring;
    }

    List<Drawn> cuts() {
        return List.copyOf(cuts);
    }

    void undo() {
        if (cuts.isEmpty()) return;
        cuts.removeLast();
        redraw();
        changed.run();
    }

    void clear() {
        cuts.clear();
        corners.clear();
        redraw();
        changed.run();
    }

    /** Draws every cut again at the frame's width on the board now. */
    void redraw() {
        marks.getChildren().clear();
        marks.setPrefSize(view.getFitWidth(), frameWidth == 0 ? 0 : view.getFitWidth() * frameHeight / frameWidth);
        if (frameWidth == 0) return;
        double scale = scale();
        for (int i = 0; i < cuts.size(); i++) {
            Drawn cut = cuts.get(i);
            Shape outline = outline(cut, scale);
            outline.getStyleClass().add("screen-cut");
            marks.getChildren().add(outline);
            if (cut.ignore() != null) {
                Rectangle skipped = scaled(cut.ignore(), scale);
                skipped.getStyleClass().add("screen-cut-ignore");
                marks.getChildren().add(skipped);
            }
            Label badge = new Label(String.valueOf(i + 1));
            badge.getStyleClass().add("screen-cut-badge");
            badge.relocate(cut.box().x() * scale + 2, cut.box().y() * scale + 2);
            marks.getChildren().add(badge);
        }
        // Drawn only: a press on a cut is a press on the frame under it.
        marks.getChildren().forEach(n -> n.setMouseTransparent(true));
        marks.getChildren().addAll(band, oval, line);
        band.setVisible(false);
        oval.setVisible(false);
        line.setVisible(false);
        if (!corners.isEmpty()) drawCorners(Double.NaN, Double.NaN);
    }

    // --- drawing ---

    private void pressed(MouseEvent e) {
        if (!on || e.getButton() != MouseButton.PRIMARY) return;
        root.requestFocus();
        e.consume();
        if (shape == CutShape.POLYGON && !ignoring) return;
        origin = new double[] {e.getX(), e.getY()};
        dragged(e);
    }

    private void dragged(MouseEvent e) {
        if (!on || origin == null) return;
        e.consume();
        double w = Math.abs(e.getX() - origin[0]);
        double h = Math.abs(e.getY() - origin[1]);
        // Shift: a square box, a circle.
        if (e.isShiftDown()) w = h = Math.min(w, h);
        double x = e.getX() >= origin[0] ? origin[0] : origin[0] - w;
        double y = e.getY() >= origin[1] ? origin[1] : origin[1] - h;
        if (shape == CutShape.ELLIPSE && !ignoring) {
            oval.setCenterX(x + w / 2);
            oval.setCenterY(y + h / 2);
            oval.setRadiusX(w / 2);
            oval.setRadiusY(h / 2);
            oval.setVisible(true);
        } else {
            band.setX(x);
            band.setY(y);
            band.setWidth(w);
            band.setHeight(h);
            band.setVisible(true);
        }
    }

    private void released(MouseEvent e) {
        if (!on || origin == null) return;
        e.consume();
        boolean asOval = shape == CutShape.ELLIPSE && !ignoring;
        double x = asOval ? oval.getCenterX() - oval.getRadiusX() : band.getX();
        double y = asOval ? oval.getCenterY() - oval.getRadiusY() : band.getY();
        double w = asOval ? 2 * oval.getRadiusX() : band.getWidth();
        double h = asOval ? 2 * oval.getRadiusY() : band.getHeight();
        origin = null;
        band.setVisible(false);
        oval.setVisible(false);
        if (w < LEAST || h < LEAST) return;
        Rect drawn = toFrame(x, y, w, h, scale(), frameWidth, frameHeight);
        if (ignoring) {
            // The part goes to the last cut holding its middle.
            for (int i = cuts.size() - 1; i >= 0; i--) {
                Optional<Rect> part = inside(drawn, cuts.get(i).box());
                if (part.isPresent() && holds(cuts.get(i).box(), drawn)) {
                    cuts.set(i, cuts.get(i).ignoring(part.get()));
                    break;
                }
            }
        } else {
            cuts.add(new Drawn(drawn, shape, List.of(), null));
        }
        redraw();
        changed.run();
    }

    private void clicked(MouseEvent e) {
        if (!on || shape != CutShape.POLYGON || ignoring || e.getButton() != MouseButton.PRIMARY) return;
        e.consume();
        // The first click of a double-click already added this corner.
        if (e.getClickCount() >= 2) {
            closeShape();
            return;
        }
        corners.add(toFrame(e.getX(), e.getY()));
        drawCorners(e.getX(), e.getY());
    }

    private void keyed(KeyEvent e) {
        if (!on) return;
        if (e.getCode() == KeyCode.ENTER) closeShape();
        else if (e.getCode() == KeyCode.BACK_SPACE && !corners.isEmpty()) corners.removeLast();
        else if (e.getCode() == KeyCode.ESCAPE && !corners.isEmpty()) corners.clear();
        else return;
        e.consume();
        redraw();
    }

    /** Closes the free shape clicked so far into a cut; one too small is kept drawing. */
    private void closeShape() {
        if (corners.size() < CORNERS) return;
        Optional<Rect> around = PictureCuts.bounds(corners);
        if (around.isEmpty() || around.get().width() < LEAST || around.get().height() < LEAST) return;
        cuts.add(new Drawn(around.get(), CutShape.POLYGON, corners, null));
        corners.clear();
        redraw();
        changed.run();
    }

    /** The corners clicked so far, and a last line to the pointer at ({@code x}, {@code y}) when it is a number. */
    private void drawCorners(double x, double y) {
        double scale = scale();
        line.getPoints().clear();
        for (Point corner : corners) line.getPoints().addAll(corner.x() * scale, corner.y() * scale);
        if (!Double.isNaN(x)) line.getPoints().addAll(x, y);
        line.setVisible(!corners.isEmpty());
    }

    /** The frame's pixel under a point of the board. */
    private Point toFrame(double x, double y) {
        double scale = scale();
        return new Point(Math.clamp((long) Math.floor(x / scale), 0, frameWidth - 1),
                Math.clamp((long) Math.floor(y / scale), 0, frameHeight - 1));
    }

    /** The board's pixels per frame pixel. */
    private double scale() {
        return frameWidth == 0 ? 1 : view.getFitWidth() / frameWidth;
    }

    private static Shape outline(Drawn cut, double scale) {
        Rect b = cut.box();
        return switch (cut.shape()) {
            case RECT -> scaled(b, scale);
            case ELLIPSE -> new Ellipse((b.x() + b.width() / 2.0) * scale, (b.y() + b.height() / 2.0) * scale,
                    b.width() / 2.0 * scale, b.height() / 2.0 * scale);
            case POLYGON -> {
                Polygon shape = new Polygon();
                for (Point p : cut.points()) shape.getPoints().addAll(p.x() * scale, p.y() * scale);
                yield shape;
            }
        };
    }

    private static Rectangle scaled(Rect r, double scale) {
        return new Rectangle(r.x() * scale, r.y() * scale, r.width() * scale, r.height() * scale);
    }

    // --- plain values ---

    /** A box drawn on the board at {@code scale}, in the frame's pixels, kept on the {@code width × height} frame. */
    static Rect toFrame(double x, double y, double w, double h, double scale, int width, int height) {
        int left = (int) Math.clamp(Math.round(x / scale), 0, width - 1);
        int top = (int) Math.clamp(Math.round(y / scale), 0, height - 1);
        int right = (int) Math.clamp(Math.round((x + w) / scale), left + 1, width);
        int bottom = (int) Math.clamp(Math.round((y + h) / scale), top + 1, height);
        return new Rect(left, top, right - left, bottom - top);
    }

    /** What of {@code part} lies in {@code box}; empty when nothing does. */
    static Optional<Rect> inside(Rect part, Rect box) {
        int left = Math.max(part.x(), box.x());
        int top = Math.max(part.y(), box.y());
        int right = Math.min(part.x() + part.width(), box.x() + box.width());
        int bottom = Math.min(part.y() + part.height(), box.y() + box.height());
        return right <= left || bottom <= top ? Optional.empty() : Optional.of(new Rect(left, top, right - left,
                bottom - top));
    }

    /** Whether {@code box} holds the middle of {@code part}. */
    static boolean holds(Rect box, Rect part) {
        double x = part.x() + part.width() / 2.0;
        double y = part.y() + part.height() / 2.0;
        return x >= box.x() && x < box.x() + box.width() && y >= box.y() && y < box.y() + box.height();
    }
}
