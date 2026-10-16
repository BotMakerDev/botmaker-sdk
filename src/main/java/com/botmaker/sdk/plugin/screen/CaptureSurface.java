package com.botmaker.sdk.plugin.screen;

import com.botmaker.plugin.toolkit.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The transparent, always-on-top rubber-band surface shown <em>only while drawing</em> a template region
 * over the live window (it is created, shown, and disposed per capture session by
 * {@code pictures.CaptureTemplates} — the persistent mini-toolbar never covers the window, which keeps the
 * window clickable between captures).
 *
 * <p>Two modes:
 * <ul>
 *   <li>{@link Mode#SINGLE} — the first rubber-band fires {@code onRegion} once (the caller then hides the
 *       surface, crops, and names it).</li>
 *   <li>{@link Mode#MANY} — each rubber-band is kept on screen with an index badge and recorded; a
 *       {@code Done (N)} / {@code Cancel} bar ends the pass, firing {@code onDone} with all recorded regions.</li>
 * </ul>
 * Escape cancels the surface in either mode ({@code onCancel}); it does not tear down the whole tool. A free shape
 * ({@link CutShape#POLYGON}) is clicked corner by corner instead of dragged: a double-click or Enter closes it,
 * Backspace drops the last corner, and Escape drops the shape being drawn before it cancels the surface.
 */
public final class CaptureSurface {

    /** How the surface collects regions — one-shot ({@link #SINGLE}) or accumulate-then-done ({@link #MANY}). */
    public enum Mode { SINGLE, MANY }

    /**
     * A drawn selection in the surface's own (overlay-logical) pixels, together with the surface size it was
     * drawn on ({@code paneW}/{@code paneH}) so it can be scaled onto the captured physical image later. The
     * {@code shape} decides whether the crop is rectangular, an ellipse inscribed in that box, or the polygon through
     * {@code points}, whose bounding box the box is; {@code points} is empty for the other two.
     */
    public record Region(double x, double y, double w, double h, double paneW, double paneH, CutShape shape,
                         List<Point2D> points) {

        public Region {
            points = List.copyOf(points);
        }
    }

    /** The fewest corners a free shape closes with. */
    private static final int CORNERS = 3;

    private final Stage stage;
    private final Pane pane;
    private final Mode mode;
    private final CutShape shape;
    private final Consumer<Region> onRegion;
    private final Consumer<List<Region>> onDone;
    private final Runnable onCancel;

    /** The live rubber-band — a {@link Rectangle}, an {@link Ellipse} or a {@link Polyline} depending on {@link #shape}. */
    private final Shape rubberBand;
    /** The current band's bounding box {x, y, w, h} in overlay pixels, so a release is shape-agnostic. */
    private final double[] box = new double[4];
    /** A free shape's corners clicked so far, in overlay pixels. */
    private final List<Point2D> corners = new ArrayList<>();
    private final List<Region> regions = new ArrayList<>();
    /** Each recorded region's outline and badge, in step with {@link #regions}, so the last can be undone. */
    private final List<List<javafx.scene.Node>> marks = new ArrayList<>();
    private Button doneButton;
    private boolean finished;

    private CaptureSurface(EditorFrame frame, Mode mode, CutShape shape,
                           Consumer<Region> onRegion, Consumer<List<Region>> onDone, Runnable onCancel) {
        Rectangle2D bounds = frame.placement();
        // The frame isn't on the desktop behind us (an emulator's pixels arrive over ADB), so draw it —
        // otherwise the user rubber-bands over their own desktop and crops something else entirely.
        BufferedImage backdrop = frame.onScreen() ? null : frame.image();
        this.mode = mode;
        this.shape = shape;
        this.onRegion = onRegion;
        this.onDone = onDone;
        this.onCancel = onCancel;

        rubberBand = switch (shape) {
            case RECT -> new Rectangle();
            case ELLIPSE -> new Ellipse();
            case POLYGON -> new Polyline();
        };
        styleBand(rubberBand, Color.color(0.3, 0.6, 1.0, 0.25), 1.5);
        rubberBand.setVisible(false);
        rubberBand.setMouseTransparent(true);

        pane = new Pane(rubberBand);
        // A faint tint gives the transparent surface a pickable body (so drags register) and signals that
        // capture mode is active, while keeping the live window clearly visible underneath.
        pane.setStyle("-fx-background-color: rgba(20,110,220,0.06);");
        if (backdrop != null) {
            ImageView view = new ImageView(ScreenCapture.toFxImage(backdrop));
            view.setFitWidth(bounds.getWidth());
            view.setFitHeight(bounds.getHeight());
            view.setMouseTransparent(true);
            pane.getChildren().add(0, view);
        }
        pane.getChildren().add(buildControlBar());
        if (shape == CutShape.POLYGON) installCornerHandlers();
        else installDrawHandlers();

        stage = new Stage(StageStyle.TRANSPARENT);
        // Deliberately ownerless: an owned stage minimizes with the Studio window; the capture surface must
        // stay put so the user can capture while Studio is out of the way.
        stage.setAlwaysOnTop(true);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());

        // Opted out of the host's theming: this surface is a translucent tint over the live game, and the
        // shell's chrome is the one thing it must not acquire.
        pane.getStyleClass().add(Styles.UNTHEMED);
        Scene scene = new Scene(pane, bounds.getWidth(), bounds.getHeight(), Color.TRANSPARENT);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                if (corners.isEmpty()) cancel();
                else dropCorners();
            } else if (e.getCode() == KeyCode.ENTER && shape == CutShape.POLYGON) closeShape();
            else if (e.getCode() == KeyCode.BACK_SPACE && !corners.isEmpty()) {
                corners.removeLast();
                drawCorners(null);
            } else if (mode == Mode.MANY && e.isShortcutDown() && e.getCode() == KeyCode.Z) removeLast();
        });
        stage.setScene(scene);
    }

    /**
     * Opens a one-shot surface over {@code frame}: the first drawn region fires {@code onRegion}; Esc/Cancel
     * fires {@code onCancel}.
     *
     * <p>Over an on-screen frame the surface is transparent and the live window shows through; over one that
     * is not on screen (an emulator, whose pixels arrive over ADB) it paints the frame, so what the user draws
     * over is what the crop is taken from.
     */
    public static CaptureSurface single(EditorFrame frame, CutShape shape, Consumer<Region> onRegion,
                                        Runnable onCancel) {
        CaptureSurface s = new CaptureSurface(frame, Mode.SINGLE, shape, onRegion, null, onCancel);
        s.stage.show();
        OverlayStage.promoteAboveFullscreen(s.stage);
        return s;
    }

    /**
     * Opens a multi-region surface over {@code frame}: draw several, then Done fires {@code onDone}; Ctrl+Z
     * removes the last one drawn; Esc/Cancel fires {@code onCancel}.
     */
    public static CaptureSurface many(EditorFrame frame, CutShape shape, Consumer<List<Region>> onDone,
                                      Runnable onCancel) {
        CaptureSurface s = new CaptureSurface(frame, Mode.MANY, shape, null, onDone, onCancel);
        s.stage.show();
        OverlayStage.promoteAboveFullscreen(s.stage);
        return s;
    }

    /** Hides the surface without disposing it — used before an off-thread window re-capture. */
    public void hide() {
        stage.hide();
    }

    /** Disposes the surface stage. */
    public void close() {
        stage.close();
    }

    private HBox buildControlBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.setStyle("-fx-background-color: rgba(0,0,0,0.72); -fx-background-radius: 6;");
        bar.setLayoutX(12);
        bar.setLayoutY(12);

        String click = "Click each corner, double-click or Enter to close · Backspace removes a corner";
        String draw = mode == Mode.SINGLE ? "Draw a region over the window" : "Draw regions over the window, then Done";
        Label hint = new Label((shape == CutShape.POLYGON ? click + (mode == Mode.MANY ? ", then Done" : "") : draw)
                + (mode == Mode.MANY ? " · Ctrl+Z removes the last" : "") + " · Esc to cancel");
        hint.setTextFill(Color.WHITE);

        Button cancel = new Button("✕ Cancel");
        cancel.setOnAction(e -> cancel());

        if (mode == Mode.MANY) {
            doneButton = new Button("✓ Done (0)");
            doneButton.setOnAction(e -> finish());
            bar.getChildren().addAll(doneButton, cancel, hint);
        } else {
            bar.getChildren().addAll(cancel, hint);
        }
        return bar;
    }

    private void installDrawHandlers() {
        final double[] origin = new double[2];
        pane.setOnMousePressed(e -> {
            if (finished || inControlBar(e.getX(), e.getY())) return;
            origin[0] = e.getX();
            origin[1] = e.getY();
            updateBand(e.getX(), e.getY(), 0, 0);
            rubberBand.setVisible(true);
            rubberBand.toFront();
        });
        pane.setOnMouseDragged(e -> {
            if (finished || !rubberBand.isVisible()) return;
            // Shift constrains to a square box, which for the ellipse shape means a perfect circle.
            double w = Math.abs(e.getX() - origin[0]);
            double h = Math.abs(e.getY() - origin[1]);
            if (e.isShiftDown()) { double s = Math.min(w, h); w = s; h = s; }
            double x = e.getX() >= origin[0] ? origin[0] : origin[0] - w;
            double y = e.getY() >= origin[1] ? origin[1] : origin[1] - h;
            updateBand(x, y, w, h);
        });
        pane.setOnMouseReleased(e -> {
            if (finished || !rubberBand.isVisible()) return;
            double x = box[0], y = box[1], w = box[2], h = box[3];
            rubberBand.setVisible(false);
            if (w < 3 || h < 3) return;
            take(new Region(x, y, w, h, pane.getWidth(), pane.getHeight(), shape, List.of()));
        });
    }

    /**
     * A free shape's clicks: each adds a corner, the second click of a double-click closes the shape, and the line to
     * the pointer shows where the next corner goes.
     */
    private void installCornerHandlers() {
        pane.setOnMouseClicked(e -> {
            if (finished || e.getButton() != MouseButton.PRIMARY || inControlBar(e.getX(), e.getY())) return;
            // Keys go to the surface, not to the Cancel or Done button last pressed.
            pane.requestFocus();
            // The first click of the pair already added this corner. A third, right after a close, starts the next shape.
            if (e.getClickCount() == 2) closeShape();
            else {
                corners.add(new Point2D(e.getX(), e.getY()));
                drawCorners(null);
            }
        });
        pane.setOnMouseMoved(e -> {
            if (!finished && !corners.isEmpty()) drawCorners(new Point2D(e.getX(), e.getY()));
        });
    }

    /** The corners clicked so far as the band, with a last segment to {@code pointer} when it is given. */
    private void drawCorners(Point2D pointer) {
        Polyline line = (Polyline) rubberBand;
        line.getPoints().clear();
        for (Point2D corner : corners) line.getPoints().addAll(corner.getX(), corner.getY());
        if (pointer != null) line.getPoints().addAll(pointer.getX(), pointer.getY());
        rubberBand.setVisible(!corners.isEmpty());
        rubberBand.toFront();
    }

    /** Closes the free shape drawn so far into a region; one with too few corners or no size is kept drawing. */
    private void closeShape() {
        if (finished || corners.size() < CORNERS) return;
        double left = corners.stream().mapToDouble(Point2D::getX).min().orElseThrow();
        double top = corners.stream().mapToDouble(Point2D::getY).min().orElseThrow();
        double right = corners.stream().mapToDouble(Point2D::getX).max().orElseThrow();
        double bottom = corners.stream().mapToDouble(Point2D::getY).max().orElseThrow();
        if (right - left < 3 || bottom - top < 3) return;
        Region region = new Region(left, top, right - left, bottom - top, pane.getWidth(), pane.getHeight(), shape,
                corners);
        dropCorners();
        take(region);
    }

    private void dropCorners() {
        corners.clear();
        drawCorners(null);
    }

    /** A region drawn: the one-shot's answer, or one more of many. */
    private void take(Region region) {
        if (mode == Mode.SINGLE) {
            finished = true;
            onRegion.accept(region);
        } else {
            addRegion(region);
        }
    }

    /** Common band/mark styling: translucent blue fill + a solid blue outline. */
    private static void styleBand(Shape node, Color fill, double strokeWidth) {
        node.setFill(fill);
        node.setStroke(Color.web("#2f80ed"));
        node.setStrokeWidth(strokeWidth);
    }

    /** Moves/sizes the live rubber-band to the bounding box {@code (x,y,w,h)}, whatever its {@link #shape}. */
    private void updateBand(double x, double y, double w, double h) {
        box[0] = x; box[1] = y; box[2] = w; box[3] = h;
        if (rubberBand instanceof Rectangle rect) {
            rect.setX(x); rect.setY(y); rect.setWidth(w); rect.setHeight(h);
        } else if (rubberBand instanceof Ellipse el) {
            el.setCenterX(x + w / 2); el.setCenterY(y + h / 2);
            el.setRadiusX(w / 2); el.setRadiusY(h / 2);
        }
    }

    /** A persistent outline of the region in this surface's shape (rectangle, ellipse or polygon), for MANY mode. */
    private Shape markFor(Region region) {
        return switch (shape) {
            case RECT -> new Rectangle(region.x(), region.y(), region.w(), region.h());
            case ELLIPSE -> new Ellipse(region.x() + region.w() / 2, region.y() + region.h() / 2,
                    region.w() / 2, region.h() / 2);
            case POLYGON -> {
                Polygon outline = new Polygon();
                for (Point2D corner : region.points()) outline.getPoints().addAll(corner.getX(), corner.getY());
                yield outline;
            }
        };
    }

    /** Removes the region drawn last, and its outline and badge (MANY mode, Ctrl+Z). */
    private void removeLast() {
        if (finished || regions.isEmpty()) return;
        regions.removeLast();
        pane.getChildren().removeAll(marks.removeLast());
        doneButton.setText("✓ Done (" + regions.size() + ")");
    }

    /** Records a region and marks it on the surface with a persistent outline + index badge (MANY mode). */
    private void addRegion(Region region) {
        regions.add(region);

        Shape mark = markFor(region);
        styleBand(mark, Color.color(0.3, 0.6, 1.0, 0.15), 1.5);
        mark.setMouseTransparent(true);

        Label badge = new Label(String.valueOf(regions.size()));
        badge.setTextFill(Color.WHITE);
        badge.setStyle("-fx-background-color: rgba(47,128,237,0.9); -fx-background-radius: 3; -fx-padding: 0 4 0 4;");
        badge.setMouseTransparent(true);
        badge.setLayoutX(region.x() + 2);
        badge.setLayoutY(region.y() + 2);

        pane.getChildren().addAll(mark, badge);
        marks.add(List.of(mark, badge));
        rubberBand.toFront();
        if (doneButton != null) doneButton.setText("✓ Done (" + regions.size() + ")");
    }

    private void finish() {
        if (finished) return;
        // A free shape still open is one more region when it can close, never dropped without a word.
        closeShape();
        finished = true;
        onDone.accept(List.copyOf(regions));
    }

    private void cancel() {
        if (finished) return;
        finished = true;
        onCancel.run();
    }

    /** True when ({@code x},{@code y}) falls within the control bar so a click there isn't a rubber-band. */
    private boolean inControlBar(double x, double y) {
        for (var node : pane.getChildren()) {
            if (node instanceof HBox bar) return bar.getBoundsInParent().contains(x, y);
        }
        return false;
    }
}
