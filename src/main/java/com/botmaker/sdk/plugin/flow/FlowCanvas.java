package com.botmaker.sdk.plugin.flow;

import com.botmaker.sdk.api.flow.Flow;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.WritableImage;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The free-form Activity Flow canvas. Each activity is a draggable card with <em>one output port per
 * outcome</em>; a wire from an outcome port to another card's input port says "when this activity reports
 * that outcome, run that one next". An outcome port with no wire ends the run — that is the only "stop"
 * there is.
 *
 * <p><b>Mouse.</b> Left-drag on empty canvas rubber-bands a selection; left-drag on a card moves it, taking
 * the rest of the selection with it. Right-drag (or middle-drag) pans, which is why {@code ScrollPane}'s own
 * panning is off: it pans on <em>any</em> button, so leaving it on left the left button with two meanings and
 * no gesture free to select with. Ctrl+scroll zooms, a wire is drawn by dragging from a ▶ port, clicking a
 * wire removes it, and right-clicking a card offers "start here".
 *
 * <p>The flow is a graph, not a chain: an activity branches by wiring each outcome somewhere different,
 * branches meet again by wiring two outcomes into one card, and a wire leading back to an earlier activity is
 * how a bot repeats. {@link FlowRules} vetoes only what can't be drawn — two wires on one outcome. Cards the
 * run can't reach are marked as orphans ("won't run"); that is a warning, not a blocked action, because while
 * you are still wiring most cards are legitimately unconnected.
 *
 * <p>This class owns only topology and presentation. The activity data itself lives in the shared
 * {@link ActivityDraft}s, so edits made in the dialog's side panel show up on the cards immediately.
 *
 * <p><b>Its look is this plugin's too.</b> The {@code .flow-*} classes
 * every node here carries are defined in {@code flow.css} beside this class, over the {@code -bm-*} colour
 * tokens the host's theme publishes on the scene root — so the canvas follows the editor's theme without the
 * host having to know a flow exists.
 */
public final class FlowCanvas extends StackPane {

    private static final double CARD_WIDTH = 180;
    private static final double PORT_RADIUS = 6;
    private static final double GRID_STEP = 24;
    /** How much the canvas grows at a time when a card or wire passes its edge. */
    private static final double GROW_STEP = 500;
    private static final double MIN_ZOOM = 0.4;
    private static final double MAX_ZOOM = 2.0;
    private static final double ARROW_LENGTH = 11;

    /** "Won't run" on a card and on its minimap dot; "selected" on a card. Both styled in CSS. */
    private static final PseudoClass OFF = PseudoClass.getPseudoClass("off");
    private static final PseudoClass PICKED = PseudoClass.getPseudoClass("picked");

    /** The grid dot colour used before CSS has been applied — the light theme's, and never seen for long. */
    private static final Color GRID_FALLBACK = Color.web("#d8d8d8");

    private static final double CARD_HEIGHT_FALLBACK = 80;

    /** The minimap's longer side; the other follows the canvas's shape. */
    private static final double MINIMAP_MAX = 190;

    /**
     * The canvas's size. It starts at {@link FlowArrange#MIN_W}×{@link FlowArrange#MIN_H}, Auto-arrange sets it to
     * hold the arranged block, and a card dragged or loaded past its edge grows it.
     */
    private double canvasW = FlowArrange.MIN_W;
    private double canvasH = FlowArrange.MIN_H;

    private final Pane content = new Pane();
    private final Group wires = new Group();
    private final Scale zoom = new Scale(1, 1);
    private final ScrollPane scroller;
    private final Pane minimap = new Pane();
    private final Rectangle minimapViewport = new Rectangle();
    private final Rectangle rubberBand = new Rectangle();

    private final ObservableList<ActivityDraft> drafts = FXCollections.observableArrayList();
    private final ObservableList<Arrow> edges = FXCollections.observableArrayList();

    /** Every card by activity name. */
    private final Map<String, NodeCard> cards = new LinkedHashMap<>();

    /**
     * The cards currently selected. Multi-selection exists for moving a group; {@link #selectedProperty()} is
     * the single-selection view the side panel edits, and is null whenever this holds anything but one card.
     */
    private final ObservableList<ActivityDraft> selection = FXCollections.observableArrayList();
    private final ObjectProperty<ActivityDraft> selected = new SimpleObjectProperty<>();

    /** Where inline feedback ("one outcome can't lead to two places") goes — the dialog's status label. */
    private Consumer<String> onMessage = m -> {};
    /** Fired whenever the wiring changes, so the dialog can refresh its reachability summary. */
    private Runnable onChainChanged = () -> {};
    /** Fired on a double-click on empty canvas, with the point in unscaled canvas coordinates. */
    private Consumer<Point2D> onCanvasDoubleClick = p -> {};

    /**
     * The dot grid, and the one surface on this canvas CSS cannot paint: a pattern tile takes a {@link Color} and
     * has never heard of a stylesheet. So {@code gridInk} — zero-sized, unmanaged, never drawn — carries the
     * {@code .flow-grid-ink} class, and {@link #paintGrid()} reads the colour back off it. The grid is one
     * {@link #GRID_STEP}-sized tile repeated, so a canvas that grows costs no larger texture: a whole-canvas
     * {@code Canvas} took 24 MB at 3000×2000, and would take hundreds once the canvas grew (2026-10-12).
     */
    private final Region grid = new Region();
    private final Region gridInk = new Region();

    /**
     * Undo/redo for this canvas, over whole-canvas snapshots. Every mutation below goes through it, which is
     * what makes autosave and click-to-delete safe to have: nothing is lost, because everything can be taken
     * back. See {@link SnapshotHistory} for why snapshots rather than per-mutation inverses.
     */
    private final SnapshotHistory<FlowSnapshot> history = new SnapshotHistory<>(this::capture, this::restore);

    /** True while the history is putting a snapshot back: the mutations it performs must not record themselves. */
    private boolean restoring;

    /** Fired after any recorded mutation, undo or redo — the dialog saves on it. */
    private Runnable onFlowMutated = () -> {};

    private CubicCurve pendingArrow;
    private ActivityDraft pendingFrom;
    /** The wire a drag from a wired port is carrying, hidden from the canvas until the drop; else null. */
    private Arrow pickedUp;
    /** Where the pending wire's drag began, to tell a click on a port from a drag. */
    private Point2D pendingStart;
    /** How far a port drag must travel before its release wires anything. */
    private static final double CLICK_SLOP = 6;
    private String pendingOutcome;

    private Point2D bandOrigin;
    private Point2D panOrigin;
    private double panStartH;
    private double panStartV;

    /** True while a {@link #redrawSoon()} is queued. */
    private boolean redrawPending;

    /** The activity the run begins at; blank until one is chosen (then the first placed card is used). */
    private String start = "";

    public FlowCanvas() {
        content.setPrefSize(canvasW, canvasH);
        content.getStyleClass().add("flow-canvas-content");
        content.getTransforms().add(zoom);
        grid.setMouseTransparent(true);
        grid.setManaged(false);
        grid.resize(canvasW, canvasH);
        gridInk.getStyleClass().add("flow-grid-ink");
        gridInk.setManaged(false);
        gridInk.setMouseTransparent(true);
        gridInk.resize(0, 0);
        content.getChildren().addAll(grid, gridInk, wires, rubberBand());
        paintGrid();

        scroller = new ScrollPane(new Group(content));
        // Deliberately NOT pannable: ScrollPane pans on any button, which would steal the left drag from
        // rubber-band selection. Panning is done below, on the secondary/middle button only.
        scroller.setPannable(false);
        scroller.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            if (!e.isControlDown()) return;
            double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
            double next = Math.clamp(zoom.getX() * factor, MIN_ZOOM, MAX_ZOOM);
            zoom.setX(next);
            zoom.setY(next);
            drawMinimap();
            e.consume();
        });

        // The grid is painted rather than styled, so it has to be repainted by hand once CSS has something to
        // say — which is the moment this canvas joins a scene, and again if the look on that scene changes.
        //
        // The host used to tell us: Studio's own canvas held a BlockTheme listener. A plugin has no such
        // channel — the contract's Theme applies a look, it does not announce one — so what is watched here is
        // the scene root's own style classes, which is what the host stamps a theme onto. A theme switched
        // while this window is open therefore repaints the grid if the host restyles the root, and otherwise
        // costs a reopen; that is the accepted price of the window living outside the editor.
        sceneProperty().addListener((o, was, is) -> {
            if (is == null) return;
            is.getRoot().getStyleClass().addListener(
                    (javafx.collections.ListChangeListener<String>) c -> paintGrid());
            paintGrid();
        });

        history.setOnChanged(() -> onFlowMutated.run());

        content.setOnMousePressed(this::beginCanvasGesture);
        content.setOnMouseDragged(this::continueCanvasGesture);
        content.setOnMouseReleased(this::endCanvasGesture);

        getChildren().addAll(scroller, buildMinimap());
        scroller.hvalueProperty().addListener((o, was, is) -> drawMinimapViewport());
        scroller.vvalueProperty().addListener((o, was, is) -> drawMinimapViewport());
        scroller.viewportBoundsProperty().addListener((o, was, is) -> drawMinimapViewport());
    }

    /**
     * A faint dot grid — it makes dragging and alignment legible without any per-frame cost. Painted once per
     * theme rather than once ever, in whatever colour {@code .flow-grid-ink} resolves to now.
     */
    private void paintGrid() {
        int step = (int) GRID_STEP;
        WritableImage tile = new WritableImage(step, step);
        tile.getPixelWriter().setColor(0, 0, gridInk());
        grid.setBackground(new Background(new BackgroundFill(
                new ImagePattern(tile, 0, 0, step, step, false), null, null)));
    }

    /** Makes the canvas {@code width}×{@code height}, never below the minimum. */
    private void resizeCanvas(double width, double height) {
        double w = Math.max(FlowArrange.MIN_W, Math.ceil(width));
        double h = Math.max(FlowArrange.MIN_H, Math.ceil(height));
        if (w == canvasW && h == canvasH) return;
        canvasW = w;
        canvasH = h;
        content.setPrefSize(w, h);
        grid.resize(w, h);
    }

    /**
     * Grows the canvas so {@code box} has {@link FlowArrange#MARGIN} to spare right and below, in steps of
     * {@link #GROW_STEP} so a drag past the edge does not resize on every pixel.
     */
    private void growToHold(FlowArrange.Rect box) {
        if (box == null) return;
        double needW = box.maxX() + FlowArrange.MARGIN;
        double needH = box.maxY() + FlowArrange.MARGIN;
        if (needW <= canvasW && needH <= canvasH) return;
        resizeCanvas(Math.max(canvasW, Math.ceil(needW / GROW_STEP) * GROW_STEP),
                Math.max(canvasH, Math.ceil(needH / GROW_STEP) * GROW_STEP));
    }

    /** What the stylesheet says a grid dot is, or {@link #GRID_FALLBACK} before there is a stylesheet to ask. */
    private Color gridInk() {
        if (getScene() == null) return GRID_FALLBACK;
        gridInk.applyCss();
        Background background = gridInk.getBackground();
        if (background == null || background.getFills().isEmpty()) return GRID_FALLBACK;
        return background.getFills().getFirst().getFill() instanceof Color c ? c : GRID_FALLBACK;
    }

    private Node rubberBand() {
        rubberBand.getStyleClass().add("flow-band");
        rubberBand.getStrokeDashArray().addAll(4.0, 4.0);
        rubberBand.setVisible(false);
        rubberBand.setMouseTransparent(true);
        return rubberBand;
    }

    // --- contents ---

    public ObservableList<ActivityDraft> drafts() { return drafts; }

    public ObservableList<Arrow> edges() { return edges; }

    /** The single selected activity — null when nothing, or more than one, is selected. */
    public ObjectProperty<ActivityDraft> selectedProperty() { return selected; }

    /** Everything currently selected; a group drag moves all of it. */
    public ObservableList<ActivityDraft> selection() { return selection; }

    public void setOnMessage(Consumer<String> onMessage) { this.onMessage = onMessage; }

    public void setOnChainChanged(Runnable onChainChanged) { this.onChainChanged = onChainChanged; }

    /** Undo/redo for the flow: the dialog drives its arrows and, on every step, saves. */
    public SnapshotHistory<FlowSnapshot> history() { return history; }

    /** Called after every recorded mutation, undo and redo. */
    public void setOnFlowMutated(Runnable onFlowMutated) { this.onFlowMutated = onFlowMutated; }

    /**
     * Runs {@code change} as a single undoable step. For a caller outside the canvas that changes several
     * things at once — flipping a dozen switches together, say — taking that back should be one ↶, not twelve.
     */
    public void mutate(String label, Runnable change) { history.mutate(label, change); }

    /**
     * Drops the undo history and reports the change anyway — for an edit no snapshot describes.
     *
     * <p>A rename and an outcome edit both rewrite the names every stored snapshot's wires are expressed in,
     * so undoing across one would restore wires between activities that no longer answer to those names.
     * Forgetting is the honest answer: the arrows go grey, which says plainly that this edit is not one you
     * can take back, rather than offering an undo that quietly loses wiring.
     */
    private void invalidateHistory() {
        history.clear();
        onFlowMutated.run();   // clear() is silent by design, but the edit that caused it still has to save
    }

    /** Everything undo restores, right now. */
    private FlowSnapshot capture() {
        List<FlowSnapshot.CardState> states = new ArrayList<>(drafts.size());
        for (ActivityDraft d : drafts) {
            states.add(new FlowSnapshot.CardState(d, d.x(), d.y(), d.enabled()));
        }
        return new FlowSnapshot(states, edges, start);
    }

    /**
     * Puts a snapshot back. Cards the snapshot doesn't have are detached, ones it has that aren't here are
     * re-attached — so an undone delete brings back the same {@link ActivityDraft} object, still bound to
     * whatever was editing it.
     */
    private void restore(FlowSnapshot snapshot) {
        restoring = true;
        try {
            List<ActivityDraft> wanted = snapshot.drafts();
            for (ActivityDraft d : new ArrayList<>(drafts)) {
                if (!wanted.contains(d)) detach(d);
            }
            for (FlowSnapshot.CardState state : snapshot.cards()) {
                if (!drafts.contains(state.draft())) attach(state.draft());
                NodeCard card = cards.get(state.draft().name());
                if (card != null) placeAt(card, state.x(), state.y());
                state.draft().enabledProperty().set(state.enabled());
            }
            drafts.setAll(wanted);   // the canvas order is the order activities are written back in
            edges.setAll(snapshot.edges());
            start = snapshot.start();
            selection.removeIf(d -> !wanted.contains(d));
            syncSelection();
            onMessage.accept("");
            refresh();
        } finally {
            restoring = false;
        }
    }

    /** Where a double-click on empty canvas goes — the dialog opens its "new activity" prompt there. */
    public void setOnCanvasDoubleClick(Consumer<Point2D> onCanvasDoubleClick) {
        this.onCanvasDoubleClick = onCanvasDoubleClick;
    }

    /** Adds a card for {@code draft} at its stored position and selects it. */
    public void add(ActivityDraft draft) {
        history.mutate("add " + draft.name(), () -> {
            attach(draft);
            select(draft);
            refresh();
        });
    }

    /** Removes the activity and every wire touching it. */
    public void remove(ActivityDraft draft) {
        history.mutate("delete " + draft.name(), () -> {
            detach(draft);
            edges.removeIf(e -> e.from().equals(draft.name()) || e.to().equals(draft.name()));
            if (draft.name().equals(start)) start = "";
            refresh();
        });
    }

    /** Puts a card on the canvas. The topology around it — wires, start — is the caller's business. */
    private void attach(ActivityDraft draft) {
        drafts.add(draft);
        NodeCard card = new NodeCard(draft);
        cards.put(draft.name(), card);
        content.getChildren().add(card);
    }

    /** Takes a card off the canvas, leaving the draft object itself intact so an undo can bring it back. */
    private void detach(ActivityDraft draft) {
        drafts.remove(draft);
        NodeCard card = cards.remove(draft.name());
        if (card != null) content.getChildren().remove(card);
        selection.remove(draft);
        if (selected.get() == draft) select(null);
    }

    /** Selects exactly {@code draft} (or nothing, when null). */
    public void select(ActivityDraft draft) {
        selection.setAll(draft == null ? List.of() : List.of(draft));
        syncSelection();
    }

    private void selectAll(List<ActivityDraft> chosen) {
        selection.setAll(chosen);
        syncSelection();
    }

    /** Pushes the selection out to the cards and to the single-selection property the side panel binds to. */
    private void syncSelection() {
        selected.set(selection.size() == 1 ? selection.getFirst() : null);
        for (NodeCard c : cards.values()) c.setSelected(selection.contains(c.draft()));
    }

    /** A free spot for a new card: laid out in a column down the canvas, next to what is already placed. */
    public Point2D nextFreeSpot() {
        double x = 60;
        double y = 40 + drafts.size() * 110.0;
        double height = FlowArrange.MIN_H;
        if (y > height - 120) { // wrap into a new column rather than run off the bottom
            x += 260 * (int) (y / (height - 120));
            y = 40 + (y % (height - 120));
        }
        return new Point2D(x, y);
    }

    /** The activities a run can reach, exactly as the generator will walk them. */
    public List<String> chain() {
        return FlowRules.reachable(placedNames(), edges, start);
    }

    /** Placed activities the flow never reaches — they won't run. */
    public List<String> orphans() {
        return FlowRules.orphans(placedNames(), edges, start);
    }

    /**
     * The {@code activity.outcome} ports with no wire leaving them. Each one ends the run, which is
     * legitimate — the dialog reports them as information, not as a problem.
     */
    public List<String> unwiredOutcomes() {
        List<String> unwired = new ArrayList<>();
        for (String name : chain()) {
            ActivityDraft draft = cards.get(name).draft();
            for (String outcome : draft.allOutcomes()) {
                boolean wired = edges.stream()
                        .anyMatch(e -> e.from().equals(name) && e.outcome().equals(outcome));
                if (!wired) unwired.add(name + "." + outcome);
            }
        }
        return unwired;
    }

    /**
     * The activities the flow continues past but whose {@code DISABLED} port is unwired — so switching one of
     * them off stops the run where the drawing says it should carry on.
     *
     * <p>Only nodes with at least one outgoing wire qualify. A leaf ends the run whether it is switched on or
     * off, so its unwired {@code DISABLED} says nothing; reporting it would flood every existing project,
     * none of which has a {@code DISABLED} wire at all.
     */
    public List<String> unwiredWhenDisabled() {
        List<String> stopping = new ArrayList<>();
        for (String name : chain()) {
            boolean continues = false;
            boolean disabledArrowd = false;
            for (Arrow e : edges) {
                if (!e.from().equals(name)) continue;
                if (e.isDisabled()) disabledArrowd = true;
                else continues = true;
            }
            if (continues && !disabledArrowd) stopping.add(name);
        }
        return stopping;
    }

    /** The activity the run begins at, resolved against what is placed (blank when nothing is). */
    public String resolvedStart() {
        List<String> placed = placedNames();
        if (placed.contains(start)) return start;
        return placed.isEmpty() ? "" : placed.getFirst();
    }

    /** The stored start, which may be blank ("no explicit choice — use the first card"). */
    public String start() {
        return start;
    }

    /**
     * Sets the starting activity as loaded from disk — and moves it if what was saved is switched off, which
     * an older project can be: the start was stored without any regard for the enable flag until now.
     */
    public void setStart(String newStart) {
        this.start = newStart == null ? "" : newStart;
        String runnable = firstEnabledOtherThan(null);
        if (isOff(resolvedStart()) && !runnable.isEmpty()) start = runnable;
        refresh();
    }

    /** True when {@code activity} is placed and switched off — a card the run cannot begin at. */
    private boolean isOff(String activity) {
        for (ActivityDraft d : drafts) {
            if (d.name().equals(activity)) return !d.enabled();
        }
        return false;
    }

    /** The first switched-on activity that isn't {@code excluded}, or blank when there is none. */
    private String firstEnabledOtherThan(ActivityDraft excluded) {
        for (ActivityDraft d : drafts) {
            if (d != excluded && d.enabled()) return d.name();
        }
        return "";
    }

    private List<String> placedNames() {
        List<String> names = new ArrayList<>(drafts.size());
        for (ActivityDraft d : drafts) names.add(d.name());
        return names;
    }

    /**
     * Shows every card and every wire: the zoom that fits them all into the viewport, never above 1:1, centred on
     * them — the way out of "I panned into empty canvas and lost everything". A flow too big to fit at
     * {@link #MIN_ZOOM} centres on the start card instead and says so. Until 2026-10-12 this reset the zoom to 1:1
     * and framed the cards only, so a large flow was cut off and a loop's wire could sit outside the view.
     *
     * <p>A {@code ScrollPane}'s h/v value is a fraction of the <em>scrollable extent</em> (content minus
     * viewport), not of the content, and the content is scaled by the zoom. Getting either wrong lands the
     * view somewhere plausible-looking but never actually centred, which is what this used to do.
     */
    public void recenter() {
        // The viewport is only known after a layout pass; without this the first recenter divides by zero.
        Platform.runLater(() -> {
            Bounds view = scroller.getViewportBounds();
            if (cards.isEmpty() || view.getWidth() <= 0) {
                setZoom(1);
                scroller.setHvalue(0);
                scroller.setVvalue(0);
                drawMinimap();
                return;
            }
            List<FlowArrange.Card> now = layoutCards();
            FlowArrange.Rect box = FlowArrange.extent(now, FlowArrange.routes(now, edges));
            double fit = FlowArrange.fitZoom(box, view.getWidth(), view.getHeight());
            setZoom(Math.max(MIN_ZOOM, fit));
            Point2D centre = new Point2D(box.centre().x(), box.centre().y());
            NodeCard first = cards.get(resolvedStart());
            if (fit < MIN_ZOOM && first != null) {
                centre = new Point2D(first.getLayoutX() + widthOf(first) / 2, first.getLayoutY() + heightOf(first) / 2);
                onMessage.accept("Too big to fit: use the minimap to move around.");
            }
            scroller.setHvalue(scrollFraction(centre.getX() * zoom.getX(), view.getWidth(), canvasW * zoom.getX()));
            scroller.setVvalue(scrollFraction(centre.getY() * zoom.getY(), view.getHeight(), canvasH * zoom.getY()));
            drawMinimap();
        });
    }

    private void setZoom(double factor) {
        zoom.setX(factor);
        zoom.setY(factor);
    }

    /** The h/v value that centres {@code center} in a viewport of {@code viewLength} over {@code contentLength}. */
    private static double scrollFraction(double center, double viewLength, double contentLength) {
        double scrollable = contentLength - viewLength;
        if (scrollable <= 0) return 0;   // everything fits: there is nothing to scroll
        return Math.clamp((center - viewLength / 2) / scrollable, 0, 1);
    }

    /**
     * Every card as {@link FlowArrange} sees it — where it is, its size, and where each outcome port sits — in the
     * drafts' order, the one a blank start falls back on; the {@code cards} map reorders on a rename.
     */
    private List<FlowArrange.Card> layoutCards() {
        List<FlowArrange.Card> out = new ArrayList<>(drafts.size());
        for (ActivityDraft draft : drafts) {
            NodeCard card = cards.get(draft.name());
            if (card != null) out.add(card.asLayout());
        }
        return out;
    }

    private static double heightOf(NodeCard card) {
        double height = Math.max(card.getHeight(), card.prefHeight(-1));
        return height > 0 ? height : CARD_HEIGHT_FALLBACK;
    }

    private static double widthOf(NodeCard card) {
        double width = Math.max(card.getWidth(), card.prefWidth(-1));
        return width > 0 ? width : CARD_WIDTH;
    }

    // --- auto-arrange ---

    /**
     * Lays the cards out in layers, left to right, with anything unreachable on a grid below, on a canvas sized to
     * hold them, then shows them all ({@link #recenter()}). {@link FlowArrange} decides where each card goes; it
     * reads only the cards' sizes and the wiring, so a second click moves nothing.
     */
    public void autoArrange() {
        history.mutate("auto-arrange", this::arrangeNow);
    }

    private void arrangeNow() {
        FlowArrange.Layout layout = FlowArrange.arrange(layoutCards(), edges, start);
        resizeCanvas(layout.width(), layout.height());
        layout.at().forEach((name, at) -> {
            NodeCard card = cards.get(name);
            if (card != null) placeAt(card, at.x(), at.y());
        });
        refresh();
        recenter();
    }

    /** Moves a card, never closer to the canvas's top or left edge than a wire passing it needs. */
    private static void placeAt(NodeCard card, double x, double y) {
        card.setLayoutX(Math.max(FlowArrange.CLEAR, x));
        card.setLayoutY(Math.max(FlowArrange.CLEAR, y));
        card.draft().moveTo(card.getLayoutX(), card.getLayoutY());
    }

    /** Redraws the wires and re-marks orphan and start cards. Call after any topology or naming change. */
    public void refresh() {
        redrawArrows();
        List<String> orphans = orphans();
        String startNow = resolvedStart();
        for (Map.Entry<String, NodeCard> entry : cards.entrySet()) {
            entry.getValue().setOrphan(orphans.contains(entry.getKey()));
            entry.getValue().setStart(entry.getKey().equals(startNow));
        }
        drawMinimap();
        onChainChanged.run();
    }

    // --- canvas gestures: rubber-band select (left) and pan (right/middle) ---

    private void beginCanvasGesture(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY) {
            if (e.getTarget() != content) return;   // a card handles its own press
            if (e.getClickCount() == 2) {
                // The first click of the pair already ran this method and its band was cleared on release, so
                // there is nothing to undo — just don't start a second one on top of the dialog.
                onCanvasDoubleClick.accept(new Point2D(e.getX(), e.getY()));
                e.consume();
                return;
            }
            select(null);
            bandOrigin = new Point2D(e.getX(), e.getY());
            rubberBand.setX(bandOrigin.getX());
            rubberBand.setY(bandOrigin.getY());
            rubberBand.setWidth(0);
            rubberBand.setHeight(0);
            rubberBand.setVisible(true);
        } else {
            panOrigin = new Point2D(e.getSceneX(), e.getSceneY());
            panStartH = scroller.getHvalue();
            panStartV = scroller.getVvalue();
        }
        e.consume();
    }

    private void continueCanvasGesture(MouseEvent e) {
        if (bandOrigin != null) {
            rubberBand.setX(Math.min(bandOrigin.getX(), e.getX()));
            rubberBand.setY(Math.min(bandOrigin.getY(), e.getY()));
            rubberBand.setWidth(Math.abs(e.getX() - bandOrigin.getX()));
            rubberBand.setHeight(Math.abs(e.getY() - bandOrigin.getY()));
        } else if (panOrigin != null) {
            pan(e);
        }
        e.consume();
    }

    /** Drags the view: a scene-space delta divided by the scrollable extent is the h/v change it represents. */
    private void pan(MouseEvent e) {
        Bounds view = scroller.getViewportBounds();
        double scrollableX = canvasW * zoom.getX() - view.getWidth();
        double scrollableY = canvasH * zoom.getY() - view.getHeight();
        if (scrollableX > 0) {
            scroller.setHvalue(Math.clamp(
                    panStartH - (e.getSceneX() - panOrigin.getX()) / scrollableX, 0, 1));
        }
        if (scrollableY > 0) {
            scroller.setVvalue(Math.clamp(
                    panStartV - (e.getSceneY() - panOrigin.getY()) / scrollableY, 0, 1));
        }
    }

    private void endCanvasGesture(MouseEvent e) {
        if (bandOrigin != null) {
            Bounds band = rubberBand.getBoundsInParent();
            List<ActivityDraft> caught = new ArrayList<>();
            for (NodeCard card : cards.values()) {
                if (band.intersects(card.getBoundsInParent())) caught.add(card.draft());
            }
            selectAll(caught);
            rubberBand.setVisible(false);
            bandOrigin = null;
        }
        panOrigin = null;
        e.consume();
    }

    // --- minimap ---

    /**
     * A thumbnail of the whole canvas in the corner, with the current viewport outlined. The canvas is far
     * bigger than any window, so without it there is no way to tell where you are or that a card exists off
     * screen — the same problem {@link #recenter()} rescues you from, but continuously.
     */
    private Node buildMinimap() {
        sizeMinimap();
        minimap.getStyleClass().add("flow-minimap");
        minimapViewport.getStyleClass().add("flow-minimap-viewport");
        minimap.setOnMousePressed(this::jumpFromMinimap);
        minimap.setOnMouseDragged(this::jumpFromMinimap);
        StackPane.setAlignment(minimap, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(minimap, new Insets(0, 16, 16, 0));
        return minimap;
    }

    /** Canvas pixels per minimap pixel: the canvas's longer side fills {@link #MINIMAP_MAX}. */
    private double minimapScale() {
        return MINIMAP_MAX / Math.max(canvasW, canvasH);
    }

    private void sizeMinimap() {
        double width = canvasW * minimapScale();
        double height = canvasH * minimapScale();
        minimap.setPrefSize(width, height);
        minimap.setMaxSize(width, height);
    }

    private void jumpFromMinimap(MouseEvent e) {
        Bounds view = scroller.getViewportBounds();
        double centerX = e.getX() / minimapScale() * zoom.getX();
        double centerY = e.getY() / minimapScale() * zoom.getY();
        scroller.setHvalue(scrollFraction(centerX, view.getWidth(), canvasW * zoom.getX()));
        scroller.setVvalue(scrollFraction(centerY, view.getHeight(), canvasH * zoom.getY()));
        e.consume();
    }

    private void drawMinimap() {
        sizeMinimap();
        minimap.getChildren().clear();
        double scale = minimapScale();
        for (NodeCard card : cards.values()) {
            Rectangle dot = new Rectangle(
                    card.getLayoutX() * scale,
                    card.getLayoutY() * scale,
                    Math.max(3, widthOf(card) * scale),
                    Math.max(2, heightOf(card) * scale));
            dot.getStyleClass().add("flow-minimap-card");
            dot.pseudoClassStateChanged(OFF, !card.draft().enabled());
            minimap.getChildren().add(dot);
        }
        minimap.getChildren().add(minimapViewport);
        drawMinimapViewport();
    }

    private void drawMinimapViewport() {
        Bounds view = scroller.getViewportBounds();
        if (view.getWidth() <= 0) return;
        double contentW = canvasW * zoom.getX();
        double contentH = canvasH * zoom.getY();
        double mapW = canvasW * minimapScale();
        double mapH = canvasH * minimapScale();
        double x = scroller.getHvalue() * Math.max(0, contentW - view.getWidth()) / contentW;
        double y = scroller.getVvalue() * Math.max(0, contentH - view.getHeight()) / contentH;
        minimapViewport.setX(x * mapW);
        minimapViewport.setY(y * mapH);
        minimapViewport.setWidth(Math.min(mapW, view.getWidth() / contentW * mapW));
        minimapViewport.setHeight(Math.min(mapH, view.getHeight() / contentH * mapH));
    }

    // --- wiring ---

    /**
     * Arrows {@code from —outcome→ to}. A port that already has a wire has it moved, in one undo step; dropping
     * it back on the card it already led to changes nothing.
     */
    private void tryConnect(ActivityDraft from, String outcome, String to) {
        Optional<Arrow> old = FlowRules.held(edges, from.name(), outcome);
        if (old.isPresent() && old.get().to().equals(to)) {
            redrawArrows();
            return;
        }
        String label = old.isPresent() ? "move the wire from " + from.name() + " to " + to
                : "wire " + from.name() + " to " + to;
        history.mutate(label, () -> {
            List<Arrow> next = FlowRules.rewired(edges, from.name(), outcome, to);
            edges.clear();
            edges.addAll(next);
            onMessage.accept(old.isPresent() ? "Arrow moved: " + from.name() + " — "
                    + old.get().outcome() + " → " + to + ". ↶ puts it back." : "");
            refresh();
        });
    }

    /**
     * Draws every wire along the path {@link FlowArrange#routes} gives it from where the cards are now — clear of
     * the cards between its ends, a wire back or onto its own card around them — and grows the canvas to hold it.
     */
    private void redrawArrows() {
        wires.getChildren().clear();
        List<Arrow> shown = new ArrayList<>(edges);
        shown.remove(pickedUp); // on the pointer while a drag moves it; a stale wire has no card and no route
        List<FlowArrange.Card> now = layoutCards();
        List<FlowArrange.Route> routes = FlowArrange.routes(now, shown);
        for (FlowArrange.Route route : routes) wires.getChildren().add(buildArrow(route));
        growToHold(FlowArrange.extent(now, routes));
    }

    /** Redraws the wires once, after the current pulse's layout — however many cards resize in it. */
    private void redrawSoon() {
        if (redrawPending) return;
        redrawPending = true;
        Platform.runLater(() -> {
            redrawPending = false;
            redrawArrows();
        });
    }

    private Node buildArrow(FlowArrange.Route route) {
        Arrow edge = route.arrow();
        Path curve = path(route);
        curve.getStyleClass().add("flow-wire");

        // A wire is symmetrical, so without a head you can't tell "A then B" from "B then A" — the direction
        // is the entire meaning of the wire. The head is aimed along the path's last direction rather than the
        // straight start→end line, so it stays flush with the path however the cards sit.
        FlowArrange.Pt from = route.headFrom();
        FlowArrange.Pt end = route.end();
        Polygon head = arrowHead(from.x(), from.y(), end.x(), end.y());

        // The hitbox: the same path again, invisible and six times as wide, behind the visible one. Picking
        // on a 2.5px stroke meant hunting for the pixel — and a wire you cannot hit is a wire you cannot
        // remove, which is what made a mis-drawn flow feel permanent.
        Path hit = path(route);
        hit.getStyleClass().add("flow-wire-hit");

        Group wire = new Group(hit, curve, head);
        wire.getStyleClass().add("flow-wire-group");
        Tooltip.install(wire, new Tooltip(edge.from() + " — " + edge.outcome() + " → " + edge.to()
                + "  (click to remove it)"));
        // One click, gone. This was a two-step select-then-Delete for a while, because a click used to remove
        // the wire outright and brushing one while reaching for a card silently unwired the flow with nothing
        // to undo it. There is an undo now, so the reason for the second step is gone, and what is left is a
        // gesture that made removing a mis-drawn wire feel like an incantation.
        wire.setOnMouseClicked(e -> {
            removeEdge(edge);
            e.consume();
        });
        wire.setOnContextMenuRequested(e -> {
            MenuItem remove = new MenuItem("Remove this wire");
            remove.setOnAction(a -> removeEdge(edge));
            new ContextMenu(remove).show(wire, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        return wire;
    }

    private void removeEdge(Arrow edge) {
        history.mutate("remove the wire from " + edge.from(), () -> {
            edges.remove(edge);
            onMessage.accept("Arrow removed: " + edge.from() + " — " + edge.outcome() + " → " + edge.to()
                    + ". ↶ puts it back.");
            refresh();
        });
    }

    /** A filled triangle at {@code (tipX, tipY)}, pointing away from {@code (fromX, fromY)}. */
    private static Polygon arrowHead(double fromX, double fromY, double tipX, double tipY) {
        double angle = Math.atan2(tipY - fromY, tipX - fromX);
        double spread = Math.toRadians(22);
        Polygon head = new Polygon(
                tipX, tipY,
                tipX - ARROW_LENGTH * Math.cos(angle - spread), tipY - ARROW_LENGTH * Math.sin(angle - spread),
                tipX - ARROW_LENGTH * Math.cos(angle + spread), tipY - ARROW_LENGTH * Math.sin(angle + spread));
        head.getStyleClass().add("flow-arrow");
        return head;
    }

    /** {@code route} as a JavaFX path: its lines and curves, in order. */
    private static Path path(FlowArrange.Route route) {
        Path path = new Path(new MoveTo(route.start().x(), route.start().y()));
        for (FlowArrange.Segment segment : route.segments()) {
            path.getElements().add(switch (segment) {
                case FlowArrange.Line line -> new LineTo(line.to().x(), line.to().y());
                case FlowArrange.Cubic c -> new CubicCurveTo(c.c1().x(), c.c1().y(), c.c2().x(), c.c2().y(),
                        c.to().x(), c.to().y());
            });
        }
        return path;
    }

    private static CubicCurve styledCurve() {
        CubicCurve curve = new CubicCurve();
        curve.getStyleClass().add("flow-wire");
        return curve;
    }

    private void startPendingArrow(ActivityDraft from, String outcome, Point2D at) {
        pendingFrom = from;
        pendingOutcome = outcome;
        // A port that has a wire lends it to the pointer: hidden while dragged, moved on a drop onto a card,
        // back where it was on a drop anywhere else. It used to refuse the drop with "remove that wire first".
        pickedUp = FlowRules.held(edges, from.name(), outcome).orElse(null);
        if (pickedUp != null) redrawArrows();
        pendingStart = at;
        pendingArrow = styledCurve();
        pendingArrow.getStrokeDashArray().addAll(6.0, 4.0);
        pendingArrow.setMouseTransparent(true);
        movePendingArrow(at, at);
        wires.getChildren().add(pendingArrow);
    }

    private void movePendingArrow(Point2D start, Point2D end) {
        pendingArrow.setStartX(start.getX());
        pendingArrow.setStartY(start.getY());
        pendingArrow.setEndX(end.getX());
        pendingArrow.setEndY(end.getY());
        pendingArrow.setControlX1(start.getX() + 40);
        pendingArrow.setControlY1(start.getY());
        pendingArrow.setControlX2(end.getX() - 40);
        pendingArrow.setControlY2(end.getY());
    }

    private void finishPendingArrow(Point2D at) {
        wires.getChildren().remove(pendingArrow);
        pendingArrow = null;
        String target = cardIdAt(at);
        ActivityDraft from = pendingFrom;
        String outcome = pendingOutcome;
        pendingFrom = null;
        pendingOutcome = null;
        boolean hadArrow = pickedUp != null;
        pickedUp = null;
        // A press and release on the port is a click, not a drag: it lands on the port's own card, and wired a
        // retry (or, since wires move, re-routed an existing one onto its own card) with no drag at all.
        boolean dragged = pendingStart == null || pendingStart.distance(at) >= CLICK_SLOP;
        pendingStart = null;
        if (dragged && target != null && from != null) tryConnect(from, outcome, target);
        else if (hadArrow) redrawArrows();
    }

    /** The activity name of the card containing the given content-space point, or null. */
    private String cardIdAt(Point2D at) {
        for (Map.Entry<String, NodeCard> entry : cards.entrySet()) {
            if (entry.getValue().getBoundsInParent().contains(at)) return entry.getKey();
        }
        return null;
    }

    // --- cards ---

    /**
     * One activity's card: a title, its enable and go-home toggles, and a labelled output port per outcome.
     * Dragging the body moves the card — and everything else selected — writing each new position straight
     * back into its draft; dragging an outcome's ▶ port draws a wire for that outcome.
     */
    private final class NodeCard extends HBox {

        private final ActivityDraft draft;
        private final Label orphanNote = new Label("not wired — won't run");
        private final Label startBadge = new Label("▶ start");
        private final Label offBadge = new Label("off");
        /** The badge row — see the header comment in the constructor for why it isn't part of the header. */
        private final HBox badges = new HBox(6, offBadge, startBadge);
        private final VBox body = new VBox(2);
        private final VBox ports = new VBox(4);
        private final Map<String, Circle> outPorts = new LinkedHashMap<>();

        private boolean selectedNow;
        private Point2D dragAnchor;
        private FlowSnapshot dragBefore;
        private Map<ActivityDraft, Point2D> dragStartPositions = Map.of();

        NodeCard(ActivityDraft draft) {
            super(6);
            setAlignment(Pos.CENTER);
            this.draft = draft;
            setLayoutX(draft.x());
            setLayoutY(draft.y());
            // A card's size isn't known until it has been laid out, and the ports hang off its edges — so
            // re-draw the wires once the real bounds arrive (and again whenever the card resizes).
            // Once per pulse, not once per card: every redraw routes every wire around every card.
            layoutBoundsProperty().addListener((o, was, is) -> redrawSoon());

            // The card is a fixed 180px wide and the title is the only part of the header with no natural
            // size, so it is the part that gets squeezed — and it was squeezed to nothing the moment a badge
            // appeared beside it, which is to say the start card, alone among all of them, didn't say which
            // activity it was. Growing and ellipsizing makes it the part that *keeps* the room instead.
            Label title = new Label();
            title.textProperty().bind(draft.nameProperty());
            title.getStyleClass().add("flow-card-title");
            title.setMaxWidth(Double.MAX_VALUE);
            title.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
            title.tooltipProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> new Tooltip(draft.name()), draft.nameProperty()));
            HBox.setHgrow(title, Priority.ALWAYS);

            // Two toggles that mean unrelated things, so two *different* controls. They used to be adjacent
            // bare CheckBoxes — one captionless, one captioned "⌂" — which read as a pair of related ticks and
            // gave no way to tell at a glance which was which. A captioned tick ("On") for the state the whole
            // card is about, and a pressed/unpressed button for the pre-step.
            CheckBox enabled = new CheckBox("On");
            enabled.getStyleClass().add("flow-toggle-run");
            enabled.selectedProperty().bindBidirectional(draft.enabledProperty());
            enabled.setTooltip(new Tooltip("Run this activity (the flow still passes through it when off)"));

            ToggleButton goHome = new ToggleButton("⌂");
            goHome.getStyleClass().add("flow-toggle-home");
            goHome.selectedProperty().bindBidirectional(draft.goHomeProperty());
            goHome.setTooltip(new Tooltip("Go back to the home screen before running this activity"));

            startBadge.getStyleClass().add("flow-start-badge");
            startBadge.setManaged(false);
            startBadge.setVisible(false);

            // Said in words, not only in colour: the dimmed-and-dashed card is the ambient cue, this is the
            // one a user can read without knowing the convention — and the one that survives any theme.
            offBadge.getStyleClass().add("flow-off-badge");
            offBadge.setManaged(false);
            offBadge.setVisible(false);

            HBox header = new HBox(8, title, enabled, goHome);
            header.setAlignment(Pos.CENTER_LEFT);

            // The badges get a row of their own rather than a place in the header. They come and go, and a
            // control that appears and disappears in the middle of a fixed-width row takes its space from
            // whatever is next to it — here, the name. On their own line they cost a few pixels of height,
            // which the card has, instead of the width, which it hasn't.
            badges.setAlignment(Pos.CENTER_LEFT);
            badges.setManaged(false);
            badges.setVisible(false);

            orphanNote.getStyleClass().add("flow-orphan-note");
            orphanNote.setManaged(false);
            orphanNote.setVisible(false);

            body.getStyleClass().add("flow-card");
            body.setPrefWidth(CARD_WIDTH);
            body.setMinWidth(CARD_WIDTH);
            body.getChildren().addAll(header, badges, orphanNote);
            body.setPadding(new Insets(8, 10, 8, 10));

            ports.setAlignment(Pos.CENTER_LEFT);
            rebuildPorts();
            // Adding an outcome in the side panel has to put a port on the card straight away — otherwise
            // there is nothing to drag the new wire from until the dialog is reopened.
            draft.outcomes().addListener((javafx.collections.ListChangeListener<String>) c -> {
                rebuildPorts();
                dropArrowsForRemovedOutcomes();
                refresh();
                if (!restoring) invalidateHistory();
            });

            // The input port is decoration only — a wire's endpoint is computed from the card's geometry
            // (inPortCenter), so nothing needs to hold on to the circle.
            getChildren().addAll(port("flow-port-in"), body, ports);
            setSelected(false);
            // Cards greyed out when disabled: the flow still passes through, the activity just doesn't run.
            draft.enabledProperty().addListener((o, was, is) -> {
                restyle();
                drawMinimap();
                if (restoring) return;
                // The tick is bound straight to the property, so by the time this runs the change has already
                // happened — the "before" is this state with the flag put back, which is exactly what
                // withEnabled builds. Whatever the start move below does then lands in the same undo step.
                FlowSnapshot before = capture().withEnabled(draft, was);
                if (!is) startAwayFrom(draft);
                refresh();
                history.commit((is ? "switch on " : "switch off ") + draft.name(), before);
            });
            draft.nameProperty().addListener((o, was, is) -> renamed(was, is));

            body.setOnMousePressed(this::beginDrag);
            body.setOnMouseDragged(this::continueDrag);
            body.setOnMouseReleased(this::endDrag);
            body.setOnContextMenuRequested(e -> cardMenu().show(body, e.getScreenX(), e.getScreenY()));
        }

        ActivityDraft draft() {
            return draft;
        }

        private ContextMenu cardMenu() {
            MenuItem startHere = new MenuItem("Start the flow here");
            startHere.setOnAction(e -> setStartTo(draft.name()));
            return new ContextMenu(startHere);
        }

        /**
         * One labelled port per outcome, in the order the enum will declare them, with the {@code DISABLED}
         * port last — see {@link ActivityDraft#flowPorts()}.
         */
        private void rebuildPorts() {
            ports.getChildren().clear();
            outPorts.clear();
            for (String outcome : draft.flowPorts()) {
                Circle circle = port("flow-port-out");
                // A modifier, not a replacement: the base class carries the stroke that punches the dot out
                // of the card's edge, and DISABLED only recolours the fill.
                if (Arrow.DISABLED.equals(outcome)) {
                    circle.getStyleClass().add("flow-port-out-disabled");
                }
                installPortHandlers(circle, outcome);
                outPorts.put(outcome, circle);
                Label label = new Label(outcome);
                label.getStyleClass().add("flow-port-label");
                HBox row = new HBox(3, label, circle);
                row.setAlignment(Pos.CENTER_RIGHT);
                ports.getChildren().add(row);
            }
        }

        /** Drops any wire whose outcome the activity no longer declares — its port has just disappeared. */
        private void dropArrowsForRemovedOutcomes() {
            // flowPorts(), not allOutcomes(): DISABLED is a port with no declared outcome behind it, so
            // pruning against the enum list would delete every DISABLED wire the moment it was drawn.
            Set<String> live = new LinkedHashSet<>(draft.flowPorts());
            edges.removeIf(e -> e.from().equals(draft.name()) && !live.contains(e.outcome()));
        }

        /** This card for {@link FlowArrange}: its place, its size, and each outcome port's height on it. */
        FlowArrange.Card asLayout() {
            Map<String, Double> portY = new LinkedHashMap<>();
            if (getHeight() > 0) {   // before the first layout pass the ports have no place yet: the middle stands in
                for (String outcome : outPorts.keySet()) portY.put(outcome, outPortCenter(outcome).getY() - getLayoutY());
            }
            return new FlowArrange.Card(draft.name(), getLayoutX(), getLayoutY(), widthOf(this), heightOf(this), portY);
        }

        Point2D outPortCenter(String outcome) {
            Circle circle = outPorts.get(outcome);
            if (circle == null) return new Point2D(getLayoutX() + getWidth(), getLayoutY() + getHeight() / 2);
            Bounds bounds = circle.getBoundsInParent();
            Bounds row = circle.getParent().getBoundsInParent();
            return new Point2D(getLayoutX() + ports.getLayoutX() + row.getMinX() + bounds.getWidth() / 2
                    + bounds.getMinX(),
                    getLayoutY() + ports.getLayoutY() + row.getMinY() + bounds.getMinY()
                            + bounds.getHeight() / 2);
        }

        private void renamed(String oldName, String newName) {
            cards.remove(oldName);
            cards.put(newName, this);
            if (oldName.equals(start)) start = newName;
            List<Arrow> rewired = new ArrayList<>(edges.size());
            for (Arrow e : edges) {
                rewired.add(new Arrow(e.from().equals(oldName) ? newName : e.from(),
                        e.to().equals(oldName) ? newName : e.to(), e.outcome()));
            }
            edges.setAll(rewired);
            refresh();
            if (!restoring) invalidateHistory();
        }

        private void beginDrag(MouseEvent e) {
            if (e.getButton() != MouseButton.PRIMARY) return;   // let a right-drag bubble up and pan
            // Dragging an unselected card selects it first, so a drag never moves something you can't see.
            if (!selection.contains(draft)) select(draft);
            // A drag is one undo step, so the snapshot is taken here and committed on release — a per-frame
            // step for every pixel of the drag would be unusable, and the intermediate positions are not
            // states anybody wants back.
            dragBefore = history.mark();
            dragAnchor = new Point2D(e.getSceneX(), e.getSceneY());
            Map<ActivityDraft, Point2D> starts = new HashMap<>();
            for (ActivityDraft d : selection) starts.put(d, new Point2D(d.x(), d.y()));
            dragStartPositions = starts;
            e.consume();
        }

        private void continueDrag(MouseEvent e) {
            if (dragAnchor == null) return;
            double dx = (e.getSceneX() - dragAnchor.getX()) / zoom.getX();
            double dy = (e.getSceneY() - dragAnchor.getY()) / zoom.getY();
            for (Map.Entry<ActivityDraft, Point2D> entry : dragStartPositions.entrySet()) {
                NodeCard card = cards.get(entry.getKey().name());
                if (card == null) continue;
                placeAt(card, entry.getValue().getX() + dx, entry.getValue().getY() + dy);
            }
            redrawArrows();
            drawMinimap();
            e.consume();
        }

        /** Ends a drag and records it — as one step, and only when the card actually ended up somewhere else. */
        private void endDrag(MouseEvent e) {
            if (dragAnchor == null) return;
            dragAnchor = null;
            FlowSnapshot before = dragBefore;
            dragBefore = null;
            if (before != null) {
                history.commit(dragStartPositions.size() > 1 ? "move the cards" : "move " + draft.name(), before);
            }
            e.consume();
        }

        /** What one output port promises, in the user's terms rather than the enum's. */
        private String portTooltip(String outcome) {
            if (Arrow.DISABLED.equals(outcome)) {
                return "Drag to the activity that runs next when " + draft.name() + " is switched off. "
                        + "Leave it unwired and the run stops there.";
            }
            return "Drag to the activity that runs next when " + draft.name() + " reports " + outcome;
        }

        private void installPortHandlers(Circle circle, String outcome) {
            Tooltip.install(circle, new Tooltip(portTooltip(outcome)));
            circle.setOnMousePressed(e -> {
                startPendingArrow(draft, outcome, outPortCenter(outcome));
                e.consume();
            });
            circle.setOnMouseDragged(e -> {
                if (pendingArrow != null) movePendingArrow(outPortCenter(outcome), toContent(e));
                e.consume();
            });
            circle.setOnMouseReleased(e -> {
                if (pendingArrow != null) finishPendingArrow(toContent(e));
                e.consume();
            });
        }

        private Point2D toContent(MouseEvent e) {
            return content.sceneToLocal(e.getSceneX(), e.getSceneY());
        }

        void setSelected(boolean isSelected) {
            this.selectedNow = isSelected;
            restyle();
        }

        void setOrphan(boolean orphan) {
            orphanNote.setManaged(orphan);
            orphanNote.setVisible(orphan);
        }

        void setStart(boolean isStart) {
            startBadge.setManaged(isStart);
            startBadge.setVisible(isStart);
            showBadgeRow();
        }

        /** The badge row exists only while it has something in it; an empty one would cost a blank line. */
        private void showBadgeRow() {
            boolean any = startBadge.isVisible() || offBadge.isVisible();
            badges.setManaged(any);
            badges.setVisible(any);
        }

        /**
         * The card's two states, as pseudo-classes rather than an inline style. Inline was the reason a dark
         * Studio drew a white card: {@code setStyle} beats the stylesheet in every theme, so there was no
         * theme override that could have reached it.
         */
        void restyle() {
            body.pseudoClassStateChanged(PICKED, selectedNow);
            body.pseudoClassStateChanged(OFF, !draft.enabled());
            offBadge.setManaged(!draft.enabled());
            offBadge.setVisible(!draft.enabled());
            showBadgeRow();
        }
    }

    /**
     * Makes {@code activity} the card the run begins at — <em>switching it on</em> if it wasn't.
     *
     * <p>A run that begins at a switched-off activity begins by doing nothing, which is not a state anybody
     * asks for on purpose: choosing "start here" says you want this to run. So the two go together, as one
     * undoable step. The other half of the same invariant is in the card's enable listener — switching the
     * start activity off moves the start rather than leaving it pointing at a card that won't run.
     */
    private void setStartTo(String activity) {
        history.mutate("start at " + activity, () -> {
            start = activity;
            ActivityDraft draft = draftNamed(activity);
            boolean wasOff = draft != null && !draft.enabled();
            if (wasOff) draft.enabledProperty().set(true);
            onMessage.accept("The flow now starts at '" + activity + "'"
                    + (wasOff ? ", and it has been switched on." : "."));
            refresh();
        });
    }

    /**
     * Moves the start off {@code draft} when it has just been switched off, since a run cannot begin at an
     * activity that doesn't run. It goes to the first other switched-on card; when there is none, the start
     * is cleared and the canvas says so — every card off is a flow that does nothing, and silence there reads
     * as a bug in the bot rather than as the switch the user just flipped.
     */
    private void startAwayFrom(ActivityDraft draft) {
        if (!draft.name().equals(resolvedStart())) return;
        String next = firstEnabledOtherThan(draft);
        start = next;
        onMessage.accept(next.isEmpty()
                ? "Every activity is switched off, so nothing will run."
                : "'" + draft.name() + "' is off, so the flow now starts at '" + next + "'.");
    }

    private ActivityDraft draftNamed(String activity) {
        for (ActivityDraft d : drafts) {
            if (d.name().equals(activity)) return d;
        }
        return null;
    }

    private static Circle port(String styleClass) {
        Circle c = new Circle(PORT_RADIUS);
        c.getStyleClass().add(styleClass);
        return c;
    }
}
