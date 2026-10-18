package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.plugin.pictures.PictureCuts;
import com.botmaker.sdk.plugin.screen.CutShape;
import javafx.css.PseudoClass;
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
import javafx.scene.shape.Circle;
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
 * click on its first corner, a double-click or Enter closes it, Backspace drops a corner, Esc the shape); with ▨ on, a
 * drag inside a cut is the part of it matching skips. A cut drawn is picked by a click on it (on its number, for a free
 * shape), then dragged to move it, its corners dragged to resize a box or an oval, and Delete takes it off. Every cut
 * is kept in the frame's pixels, so the board zooms under it.
 */
final class CutLayer {

    /** The fewest corners a free shape closes with. */
    private static final int CORNERS = 3;
    /** The smallest cut, in the board's pixels: a click is no cut. */
    private static final double LEAST = 3;
    /** How near, in the board's pixels, a click on a free shape's first corner closes it. */
    private static final double CLOSE_NEAR = 8;
    /** A picked cut's corner handle, in the board's pixels. */
    private static final double HANDLE = 8;
    /** A cut's number, in the board's pixels from its box's top left: a click on it picks the cut. */
    private static final double BADGE_WIDTH = 18;
    private static final double BADGE_HEIGHT = 16;
    private static final PseudoClass PICKED = PseudoClass.getPseudoClass("picked");

    /** A cut in the frame's pixels: its box, a free shape's corners (else none), and the part skipped (null: none). */
    record Drawn(Rect box, CutShape shape, List<Point> points, Rect ignore) {

        Drawn {
            points = List.copyOf(points);
        }

        Drawn ignoring(Rect part) {
            return new Drawn(box, shape, points, part);
        }
    }

    /** A picked box's or oval's corner, dragged to resize it. */
    enum Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

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
    /** The cut picked, by its place in {@link #cuts}; -1 for none. */
    private int picked = -1;
    /** The picked cut as the press found it, while it is moved or resized; null otherwise. */
    private Drawn grabbed;
    /** The handle being dragged; null while the picked cut is moved whole. */
    private Handle handle;
    /** Where the move began, in the frame's pixels. */
    private Point grabbedAt;

    /** @param changed run when a cut is drawn, moved, picked, undone or cleared, and as a free shape's corners change */
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
        // A filter: the board's scroller around it would take Enter and the arrows first.
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::keyed);
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
        picked = -1;
        ignoring = false;
        setOn(false);
    }

    /** Starts or stops drawing; the cuts drawn stay until {@link #clear}. */
    void setOn(boolean on) {
        this.on = on;
        marks.setMouseTransparent(!on);
        marks.setCursor(on ? javafx.scene.Cursor.CROSSHAIR : null);
        origin = null;
        grabbed = null;
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
        changed.run();
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

    /** How many corners of a free shape are clicked, not closed yet. */
    int corners() {
        return corners.size();
    }

    /** Whether a cut is picked, to move, resize or take off. */
    boolean hasPicked() {
        return picked >= 0;
    }

    void undo() {
        if (cuts.isEmpty()) return;
        cuts.removeLast();
        picked = -1;
        redraw();
        changed.run();
    }

    void clear() {
        cuts.clear();
        corners.clear();
        picked = -1;
        redraw();
        changed.run();
    }

    /**
     * Keeps only the cuts at {@code places} of {@code saved}, the list a save was given: the ones it refused, to fix.
     * Cuts drawn since the save began stay too.
     */
    void keepOnly(List<Drawn> saved, List<Integer> places) {
        List<Drawn> kept = places.stream().filter(i -> i >= 0 && i < saved.size()).map(saved::get).toList();
        cuts.removeIf(cut -> saved.contains(cut) && !kept.contains(cut));
        picked = -1;
        redraw();
        changed.run();
    }

    /** Takes the picked cut off. */
    void removePicked() {
        if (picked < 0 || picked >= cuts.size()) return;
        cuts.remove(picked);
        picked = -1;
        redraw();
        changed.run();
    }

    /** Closes the free shape clicked so far into a cut; one too small is kept drawing. */
    void closeShape() {
        if (corners.size() < CORNERS) return;
        Optional<Rect> around = PictureCuts.bounds(corners);
        if (around.isEmpty() || around.get().width() < LEAST || around.get().height() < LEAST) return;
        cuts.add(new Drawn(around.get(), CutShape.POLYGON, corners, null));
        corners.clear();
        picked = -1;
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
            outline.pseudoClassStateChanged(PICKED, i == picked);
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
        if (picked >= 0 && picked < cuts.size() && cuts.get(picked).shape() != CutShape.POLYGON) {
            for (Handle h : Handle.values()) {
                double[] at = handleAt(cuts.get(picked).box(), h, scale);
                Rectangle grip = new Rectangle(at[0] - HANDLE / 2, at[1] - HANDLE / 2, HANDLE, HANDLE);
                grip.getStyleClass().add("screen-cut-handle");
                marks.getChildren().add(grip);
            }
        }
        if (!corners.isEmpty()) {
            Point first = corners.getFirst();
            Circle closer = new Circle(first.x() * scale, first.y() * scale, CLOSE_NEAR / 1.5);
            closer.getStyleClass().add("screen-cut-first");
            marks.getChildren().add(closer);
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
        if (!ignoring && corners.isEmpty() && picked >= 0 && picked < cuts.size()) {
            Drawn cut = cuts.get(picked);
            Optional<Handle> grip = cut.shape() == CutShape.POLYGON ? Optional.empty()
                    : handleHit(cut.box(), e.getX(), e.getY(), scale());
            Point at = toFrame(e.getX(), e.getY());
            if (grip.isPresent() || contains(cut.box(), at)) {
                grabbed = cut;
                handle = grip.orElse(null);
                grabbedAt = at;
                return;
            }
        }
        if (shape == CutShape.POLYGON && !ignoring) return;
        origin = new double[] {e.getX(), e.getY()};
        dragged(e);
    }

    private void dragged(MouseEvent e) {
        if (!on) return;
        if (grabbed != null) {
            e.consume();
            Point at = toFrame(e.getX(), e.getY());
            cuts.set(picked, handle == null
                    ? moved(grabbed, at.x() - grabbedAt.x(), at.y() - grabbedAt.y(), frameWidth, frameHeight)
                    : resized(grabbed, handle, at, frameWidth, frameHeight));
            redraw();
            return;
        }
        if (origin == null) return;
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
        if (!on) return;
        if (grabbed != null) {
            e.consume();
            boolean changedIt = !grabbed.equals(cuts.get(picked));
            grabbed = null;
            handle = null;
            if (changedIt) changed.run();
            return;
        }
        if (origin == null) return;
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
            picked = -1;
        }
        redraw();
        changed.run();
    }

    private void clicked(MouseEvent e) {
        if (!on || ignoring || e.getButton() != MouseButton.PRIMARY || !e.isStillSincePress()) return;
        e.consume();
        Point at = toFrame(e.getX(), e.getY());
        double scale = scale();
        if (corners.isEmpty()) {
            // A cut's number picks it whatever the shape; inside a box or an oval does too while not drawing a free one.
            int hit = badgeHit(cuts, e.getX(), e.getY(), scale);
            if (hit < 0 && shape != CutShape.POLYGON) hit = hit(cuts, at);
            if (hit >= 0 || shape != CutShape.POLYGON) {
                if (hit != picked) {
                    picked = hit;
                    redraw();
                    changed.run();
                }
                return;
            }
        }
        if (shape != CutShape.POLYGON) return;
        // The first click of a double-click already added this corner.
        if (e.getClickCount() >= 2 || closes(corners, at, CLOSE_NEAR / scale)) {
            closeShape();
            return;
        }
        corners.add(at);
        picked = -1;
        redraw();
        drawCorners(e.getX(), e.getY());
        changed.run();
    }

    private void keyed(KeyEvent e) {
        if (!on) return;
        KeyCode code = e.getCode();
        if (code == KeyCode.ENTER && !corners.isEmpty()) {
            closeShape();
        } else if (code == KeyCode.BACK_SPACE && !corners.isEmpty()) {
            corners.removeLast();
            changed.run();
        } else if (code == KeyCode.ESCAPE && !corners.isEmpty()) {
            corners.clear();
            changed.run();
        } else if ((code == KeyCode.DELETE || code == KeyCode.BACK_SPACE) && picked >= 0) {
            removePicked();
        } else if (code == KeyCode.ESCAPE && picked >= 0) {
            picked = -1;
            changed.run();
        } else {
            return;
        }
        e.consume();
        redraw();
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

    /** Where {@code h} of {@code box} is on the board at {@code scale}. */
    private static double[] handleAt(Rect box, Handle h, double scale) {
        boolean left = h == Handle.TOP_LEFT || h == Handle.BOTTOM_LEFT;
        boolean top = h == Handle.TOP_LEFT || h == Handle.TOP_RIGHT;
        return new double[] {(left ? box.x() : box.x() + box.width()) * scale,
                (top ? box.y() : box.y() + box.height()) * scale};
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

    static boolean contains(Rect box, Point p) {
        return p.x() >= box.x() && p.x() < box.x() + box.width() && p.y() >= box.y() && p.y() < box.y() + box.height();
    }

    /** The last of {@code cuts} whose box holds {@code p}, the one drawn on top; -1 for none. */
    static int hit(List<Drawn> cuts, Point p) {
        for (int i = cuts.size() - 1; i >= 0; i--) {
            if (contains(cuts.get(i).box(), p)) return i;
        }
        return -1;
    }

    /** The last of {@code cuts} whose number, drawn at {@code scale}, is under ({@code x}, {@code y}); -1 for none. */
    static int badgeHit(List<Drawn> cuts, double x, double y, double scale) {
        for (int i = cuts.size() - 1; i >= 0; i--) {
            Rect b = cuts.get(i).box();
            double left = b.x() * scale;
            double top = b.y() * scale;
            if (x >= left && x < left + BADGE_WIDTH && y >= top && y < top + BADGE_HEIGHT) return i;
        }
        return -1;
    }

    /** The handle of {@code box}, drawn at {@code scale}, under ({@code x}, {@code y}); empty for none. */
    static Optional<Handle> handleHit(Rect box, double x, double y, double scale) {
        for (Handle h : Handle.values()) {
            double[] at = handleAt(box, h, scale);
            if (Math.abs(x - at[0]) <= HANDLE && Math.abs(y - at[1]) <= HANDLE) return Optional.of(h);
        }
        return Optional.empty();
    }

    /** Whether a click at {@code at} closes the free shape of {@code corners}: on its first corner, within {@code near}. */
    static boolean closes(List<Point> corners, Point at, double near) {
        if (corners.size() < CORNERS) return false;
        Point first = corners.getFirst();
        return Math.hypot(at.x() - first.x(), at.y() - first.y()) <= near;
    }

    /** {@code cut} moved by ({@code dx}, {@code dy}), its corners and skipped part with it, kept on the frame. */
    static Drawn moved(Drawn cut, int dx, int dy, int width, int height) {
        Rect b = cut.box();
        int x = Math.clamp(b.x() + dx, 0, Math.max(0, width - b.width()));
        int y = Math.clamp(b.y() + dy, 0, Math.max(0, height - b.height()));
        int mx = x - b.x();
        int my = y - b.y();
        return new Drawn(new Rect(x, y, b.width(), b.height()), cut.shape(),
                cut.points().stream().map(p -> new Point(p.x() + mx, p.y() + my)).toList(),
                cut.ignore() == null ? null : new Rect(cut.ignore().x() + mx, cut.ignore().y() + my,
                        cut.ignore().width(), cut.ignore().height()));
    }

    /**
     * {@code cut}, a box or an oval, with {@code h} dragged to {@code to} and the opposite corner kept, on the frame
     * and at least 2 pixels each way; its skipped part keeps what still lies in it. A free shape is not resized.
     */
    static Drawn resized(Drawn cut, Handle h, Point to, int width, int height) {
        if (cut.shape() == CutShape.POLYGON) return cut;
        Rect b = cut.box();
        boolean left = h == Handle.TOP_LEFT || h == Handle.BOTTOM_LEFT;
        boolean top = h == Handle.TOP_LEFT || h == Handle.TOP_RIGHT;
        int fixedX = left ? b.x() + b.width() : b.x();
        int fixedY = top ? b.y() + b.height() : b.y();
        int x = Math.clamp(to.x(), 0, width);
        int y = Math.clamp(to.y(), 0, height);
        int x0 = Math.min(fixedX, x);
        int y0 = Math.min(fixedY, y);
        int w = Math.max(2, Math.abs(fixedX - x));
        int ht = Math.max(2, Math.abs(fixedY - y));
        Rect box = new Rect(Math.min(x0, width - w), Math.min(y0, height - ht), w, ht);
        Rect ignore = cut.ignore() == null ? null : inside(cut.ignore(), box).orElse(null);
        return new Drawn(box, cut.shape(), List.of(), ignore);
    }
}
