package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.api.flow.Run;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Button;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Activity Flow editor — the single place activities are defined, ordered and switched on. Cards are
 * dropped on a canvas, wired into the order they should run, and ticked.
 *
 * <p>Three panes: the {@link FlowCanvas} in the middle, a side panel with the selected card's name, outcomes
 * and go-home/popup/runner ticks, and a top bar of canvas tools. Parameters themselves are <em>defined</em> in
 * the editor's own Parameters window, not here: this dialog is about the graph.
 *
 * <h2>Why this window is the plugin's</h2>
 *
 * <p>The host draws every window frame and a plugin supplies sections as data — which is why the Parameters
 * window and the Runner are Studio's. <b>A flow does not reduce to a row.</b> Its nodes, edges, ports and
 * outcomes are a vocabulary of this plugin's own, so either the contract learns what a branch is —
 * vocabulary, which the platform refuses — or the editor is the plugin's.
 *
 * <h2>What it writes</h2>
 *
 * <p><b>One flow at a time.</b> The bot has several flows, its {@code Flows} constants; the flow tree on the left
 * ({@link FlowTreeView}) picks the one drawn, and its menu adds, copies, renames and deletes them. A flow may run
 * another as one card, a sub-flow card ({@code ▸ DAILY}): opening it shows that flow one breadcrumb deeper, and its
 * ports are the outcomes that flow ends on, read as the canvas opens (2026-10-12). Activities are the bot's, not a flow's: a card's enum and
 * outcomes are the same in every flow running it, so a rename reaches them all and a flow dropping one leaves it
 * to the others. Its body, description and switches are each flow's own step, copied from another flow when the
 * card is added to this one.
 *
 * <p>No file. The graph is one expression — a {@code Flows} constant ({@link FlowValue#target}) — written through {@link com.botmaker.plugin.api.source.PluginValues}, so it lands
 * in the bot's own Java, in the open buffer as well as on disk, as one entry in the project's history. The
 * game's home and setup are {@code Sdk.run()}'s, written after the flow and only when they changed. The card
 * positions are this flow's entry in the {@link FlowLayout} {@code Sdk.flowLayout()} returns, written after the
 * flow and only when they moved, so a renamed card keeps its place.
 *
 * <p>Before the flow, the names in it: each activity is an enum nested in the bot's {@code Activities} class and
 * each of its outcomes a constant of that enum, renamed, added and removed by binding as the canvas changes
 * ({@link FlowConstants}), so a card renamed here renames the enum and every line of code naming it.
 *
 * <p>A flow whose expression the plugin cannot read — hand-written, or a call to something else — is
 * <b>shown empty and refused</b> rather than overwritten, which is the same rule every other value editor
 * follows.
 */
public final class ActivityFlowDialog {

    private final Window owner;
    private final StudioServices services;

    private final BorderPane root = new BorderPane();

    /** The canvas of the flow shown: a new one per flow picked, so undo never reaches into another flow. */
    private FlowCanvas canvas;

    /**
     * The flow this window edits — a {@code Flows} constant picked by name, {@link FlowValue#target} — read again
     * after each save, since the first save of a project with no flow writes the constant.
     */
    private FlowValue.Target target = FlowValue.Target.refused(null);

    /** Why the flow cannot be edited, or {@code null} when it can. Shown once, on the status line. */
    private String readOnlyReason;

    /**
     * The {@code @SdkValue(SdkValue.Id.FLOW_LAYOUT)} value, or empty when the project has no {@code flowLayout()}
     * or one somebody wrote by hand — the cards are then placed for the session and their positions are not kept.
     */
    private Optional<ValueContext> layoutValue = Optional.empty();

    /**
     * The whole layout last read or written, every flow's cards, so a save that moved no card leaves
     * {@code flowLayout()} alone and one that did keeps the other flows' entries.
     */
    private FlowLayout written = FlowLayout.NONE;

    /**
     * The activity labels the flow held when it was last read or written — what the bot's {@code Activities}
     * enums were kept in step with. An activity that leaves the flow has its enum removed on the next save
     * ({@link FlowConstants#forget}).
     */
    private Set<String> savedActivities = Set.of();

    /** The run's home, {@code Gamebot::goHome}: the canvas does not draw it, and a save writes it when it changed. */
    private Runnable home;

    /** The run's setup, {@code Gamebot::setup}: written like {@link #home}. */
    private Runnable setup;

    /** The run's popups method, {@code Gamebot::clearPopups}: written like {@link #home}. */
    private Runnable popups;

    /**
     * Canvas renames not yet saved, label at last save → label now; an outcome's keyed first by its activity's
     * label now. A save renames the enum or constant holding the first, and every use of it, before writing the
     * flow under the second; without this a rename would read as a removal plus an addition, and the code
     * naming the old one would stop matching the canvas. Renames clear the canvas's undo history, so nothing
     * here has to be taken back.
     */
    private final Map<String, String> activityRenames = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> outcomeRenames = new LinkedHashMap<>();

    private final Label statusLabel = new Label();

    /** The flow's step budget; edited in the no-selection panel alongside the globals. */
    private int maxSteps = Flow.Limits.DEFAULT.maxSteps();

    /** The flow's pause between activities, in ms; edited beside {@link #maxSteps}. */
    private int stepDelayMs = Flow.Limits.DEFAULT.stepDelayMs();

    /** Whether a newly added activity starts with its "go home first" tick on. */
    private boolean goHomeByDefault = true;

    /**
     * Set by {@link #loadCurrent()} when the saved flow carries no card positions at all, so the canvas is
     * arranged for the user on open instead of stacking every card in one column. Deliberately <em>not</em>
     * an unconditional auto-arrange: positions are persisted, and a layout someone placed by hand must
     * survive being looked at.
     */
    private boolean arrangeOnOpen;
    private final Label orderLabel = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final VBox sidePanel = new VBox(10);

    /**
     * Autosave, coalesced. Every edit on the canvas asks to be written, which is far too much work to do per
     * pixel of a card drag. So an edit restarts this timer instead, and the save happens once the user stops.
     */
    private final javafx.animation.PauseTransition autosaveDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));

    /** What autosave has to say for itself: "Saving…", "Saved", or "Not saved" when the write was refused. */
    private final Label savedLabel = new Label();

    /** There are edits not yet written. */
    private boolean dirty;
    /** A write is in flight; the next one waits for it rather than racing it. */
    private boolean saving;
    /** Close was pressed with edits outstanding — close as soon as they are safely on disk. */
    private boolean closeWhenSaved;

    private Button closeButton;
    private Stage stage;

    /**
     * The flows open on the way to the one shown, outermost first, its own last — {@code MAIN ▸ DAILY}: a sub-flow
     * card opened adds its flow, a breadcrumb clicked cuts back to it, a flow picked in the tree starts over.
     */
    private List<String> trail = List.of();

    /** The flow tree on the left, rebuilt each time a flow is shown; hidden by the top bar's ☰ Flows. */
    private final VBox treePane = new VBox(6);
    private boolean treeShown = true;

    public ActivityFlowDialog(StudioServices services, Window owner) {
        this.services = services;
        this.owner = owner;
    }

    /**
     * The 🔀 Activity Flow press.
     *
     * <p>It is this plugin's window rather than a host frame with sections, because a flow's nodes, edges,
     * ports and outcomes are vocabulary of this plugin's own that the contract must never learn. Single-instance
     * is not enforced: this window owns no port and no display, so a second one is a second view of the same
     * value.
     */
    public static void open(ActionContext context) {
        StudioServices services = context.services();
        new ActivityFlowDialog(services, Modals.owner(services)).show();
    }

    public void show() {
        root.setRight(buildSidePanel());
        root.setBottom(buildBottomBar());
        autosaveDelay.setOnFinished(e -> flush());
        showFlow(null);

        // The window's own ✕ is the same door as the Close button, and it must not be the one that loses the
        // last edit: both go through closeRequested, which flushes anything outstanding first.
        stage = Modals.window(services, owner, Modals.Frame.modeless("Activity Flow", 1040, 680, 760, 480), root);
        stage.setOnCloseRequest(e -> {
            if (dirty || saving || closeWhenSaved) {
                e.consume();
                closeRequested();
            }
        });
        FlowStyles.apply(stage.getScene());
        stage.show();
        layOut();
    }

    /**
     * Shows the flow {@code name} names — {@link FlowValue#defaultName} when null — on a fresh canvas: its own
     * cards, arrows and undo history. The caller has saved the flow it leaves.
     */
    private void showFlow(String name) {
        canvas = new FlowCanvas();
        activityRenames.clear();
        outcomeRenames.clear();
        readOnlyReason = null;
        error("");
        loadCurrent(name);
        if (trail.isEmpty() || !trail.getLast().equals(target.name())) trail = List.of(target.name());
        root.setTop(buildTopBar());
        root.setCenter(canvas);
        buildTreePane();
        root.setLeft(treeShown ? treePane : null);

        canvas.setOnMessage(this::error);
        canvas.setOnChainChanged(this::refreshOrderLabel);
        canvas.setOnCanvasDoubleClick(this::createActivityAt);
        canvas.setOnOpenSubflow(this::openSubflow);
        // Arrowd *after* loadCurrent, so seeding the canvas doesn't read as a dozen edits by the user; and the
        // history is cleared for the same reason — the flow as it was loaded is the state undo bottoms out at.
        canvas.setOnFlowMutated(this::markDirty);
        canvas.history().clear();
        canvas.selectedProperty().addListener((o, was, is) -> showInSidePanel(is));
        showInSidePanel(null);
        refreshOrderLabel();
        if (stage != null) layOut();
    }

    /**
     * Cards have real bounds only after the first layout pass; re-draw so the wires land on the ports — and
     * auto-arrange there too, since it stacks cards by their real heights and would otherwise lay the first-ever
     * open out against the fallback height.
     */
    private void layOut() {
        FlowCanvas shown = canvas;
        Platform.runLater(() -> {
            if (arrangeOnOpen) shown.autoArrange();
            else shown.refresh();
        });
    }

    /**
     * Saves the flow on the canvas, then shows the last of {@code path}, the breadcrumb leading to it. A save refused
     * leaves the canvas where it is: what it holds would be lost.
     */
    private void switchTo(List<String> path) {
        afterSave(() -> null, path.getLast(), path);
    }

    /** Opens the flow the sub-flow card running {@code inner} runs, one breadcrumb deeper. */
    private void openSubflow(String inner) {
        List<String> path = new ArrayList<>();
        for (String flow : trail) {
            if (flow.equals(inner)) break;
            path.add(flow);
        }
        path.add(inner);
        switchTo(path);
    }

    /**
     * Seeds the canvas from the stored flow {@code name}: a card per activity and per sub-flow card, at its stored
     * spot or a fresh one.
     */
    private void loadCurrent(String name) {
        Flow flow = readFlow(name);
        Board board = Board.read(services.pluginValues(), flow);
        FlowRefs refs = FlowRefs.of(services.pluginValues());
        home = target.run().home();
        setup = target.run().setup();
        popups = target.run().popups();
        FlowLayout layout = loadLayout();
        FlowLayout.Cards cards = layout.cardsOf(target.name());
        boolean anyPlaced = false;
        List<Arrow> arrows = new ArrayList<>(flow.edges().stream().map(Arrow::of)
                .filter(arrow -> !arrow.outcome().isBlank()).toList());
        for (Flow.Step step : flow.steps()) {
            // By the enum's name, a sub-flow card's by its label; a layout saved before 2026-10-10 keyed a card by its
            // label.
            FlowLayout.Spot placed = cards.spot(FlowNames.layoutKey(step.label()));
            if (placed == null) placed = cards.spot(step.label());
            anyPlaced |= placed != null;
            Point2D at = placed == null ? canvas.nextFreeSpot() : new Point2D(placed.x(), placed.y());
            String inner = FlowRefs.flowOf(step.node());
            FlowRefs.Exits exits = inner == null ? null : refs.exitsIn(flow, inner);
            ActivityDraft draft = ActivityDraft.of(step, exits == null ? board.exitsOf(step.label()) : exits.all(),
                    at.getX(), at.getY());
            if (exits != null) draft.setBrokenExits(exits.broken());
            canvas.add(draft);
            Arrow off = Arrow.off(step);
            if (off != null) arrows.add(off);
        }
        // Only when nothing at all was placed: one saved position is enough to mean someone laid this out.
        arrangeOnOpen = !anyPlaced && !flow.steps().isEmpty();
        canvas.edges().setAll(arrows);
        canvas.setStart(flow.start().label());
        maxSteps = flow.limits().maxSteps();
        stepDelayMs = flow.limits().stepDelayMs();
        goHomeByDefault = layout.goHomeByDefault();
        canvas.select(null);
        savedActivities = FlowEdits.labels(flow);
        canvas.refresh();
    }

    /**
     * Reads the stored flow, or says why it cannot be edited and opens empty.
     *
     * <p>Three states, one shape of answer. <b>No {@code Sdk.run()}</b> means the project has no {@code Sdk.java}
     * — the SDK was never added, or the file was deleted — and the window opens empty saying so. <b>An expression
     * the plugin did not write</b> means somebody wrote the run or the flow by hand, and the window opens empty,
     * refuses to save, and says which it is; overwriting it is exactly what this whole design exists to
     * avoid. Otherwise there is a flow, and it may perfectly well be {@link Flow#NONE} — a project with none yet,
     * whose first save writes {@code Flows.MAIN}.
     *
     * <p>The window still opens in every case, because closing it outright leaves the user no way to see
     * what the project has.
     */
    private Flow readFlow(String name) {
        target = FlowValue.target(services, name);
        if (target.problem() != null) {
            readOnly(target.problem());
            return Flow.NONE;
        }
        return target.flow();
    }

    /** Records why the flow cannot be written, and says it once. */
    private void readOnly(String reason) {
        readOnlyReason = reason;
        error(reason);
    }

    // --- top bar: canvas tools + add activity ---

    private Node buildTopBar() {
        Button addActivity = new Button("Add activity");
        addActivity.setTooltip(new javafx.scene.control.Tooltip(
                "Name it and declare its outcomes up front. Double-clicking empty canvas opens the same "
                        + "dialog, and drops the card where you clicked."));
        addActivity.setOnAction(e -> createActivityAt(canvas.nextFreeSpot()));

        Button recenter = new Button("⌖ Recenter");
        recenter.setTooltip(new javafx.scene.control.Tooltip("Zoom and scroll to show every card and wire"));
        recenter.setOnAction(e -> canvas.recenter());

        Button arrange = new Button("⇄ Auto-arrange");
        arrange.setTooltip(new javafx.scene.control.Tooltip(
                "Lay the cards out in layers, by how many steps they are from the start, with anything "
                        + "unreachable below, and show them all"));
        arrange.setOnAction(e -> canvas.autoArrange());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(8, treeToggle(), breadcrumb(),
                new Separator(javafx.geometry.Orientation.VERTICAL), undoButton(), redoButton(),
                new Separator(javafx.geometry.Orientation.VERTICAL), recenter, arrange,
                spacer, addSubflowMenu(), fromOtherFlows(), addActivity);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10));
        return bar;
    }

    /** Shows or hides the flow tree, giving its width to the canvas. */
    private Node treeToggle() {
        ToggleButton toggle = new ToggleButton("☰ Flows");
        toggle.setSelected(treeShown);
        toggle.setTooltip(new javafx.scene.control.Tooltip("Show or hide the bot's flows, each with its cards"));
        toggle.selectedProperty().addListener((o, was, is) -> {
            treeShown = is;
            root.setLeft(is ? treePane : null);
        });
        return toggle;
    }

    /**
     * {@code MAIN ▸ DAILY}: the flows open on the way to the one shown, each a link back to it but the last. Which
     * flow runs is the bot's flow parameter, set in the Runner or Project ▸ Parameters…
     */
    private Node breadcrumb() {
        HBox crumbs = new HBox(4);
        crumbs.setAlignment(Pos.CENTER_LEFT);
        crumbs.getStyleClass().add("flow-breadcrumb");
        for (int i = 0; i < trail.size(); i++) {
            String flow = trail.get(i);
            if (i > 0) crumbs.getChildren().add(new Label("▸"));
            if (i == trail.size() - 1) {
                Label here = new Label(flow);
                here.getStyleClass().add("flow-breadcrumb-here");
                here.setTooltip(new javafx.scene.control.Tooltip("The flow drawn here, Flows." + flow + ". Which flow "
                        + "runs is the bot's flow parameter, set in the Runner or Project ▸ Parameters…"));
                crumbs.getChildren().add(here);
            } else {
                List<String> back = List.copyOf(trail.subList(0, i + 1));
                Hyperlink link = new Hyperlink(flow);
                link.setOnAction(e -> switchTo(back));
                crumbs.getChildren().add(link);
            }
        }
        return crumbs;
    }

    /**
     * The flow tree ({@link FlowTreeView}): a click on a flow shows it, on a card shows its flow with the card
     * centred, on a sub-flow card opens the flow it runs. Its menu adds, copies, renames and deletes a flow; each
     * saves the flow shown first.
     */
    private void buildTreePane() {
        TreeView<FlowTreeView.Item> tree = FlowTreeView.of(FlowRefs.of(services.pluginValues()), null);
        // The flow shown, expanded and selected; a project with none lists the one its first save writes.
        TreeItem<FlowTreeView.Item> here = FlowTreeView.flowRow(tree, target.name());
        if (here == null) {
            here = new TreeItem<>(new FlowTreeView.Item(target.name(), null));
            tree.getRoot().getChildren().add(here);
        }
        here.setExpanded(true);
        tree.getSelectionModel().select(here);
        tree.setOnMouseClicked(e -> {
            TreeItem<FlowTreeView.Item> picked = tree.getSelectionModel().getSelectedItem();
            if (e.getButton() != MouseButton.PRIMARY || picked == null) return;
            FlowTreeView.Item item = picked.getValue();
            List<String> path = FlowTreeView.trail(picked);
            if (path.isEmpty()) return;
            String card = item.isFlow() || item.subflow() != null ? null : item.card();
            if (!path.getLast().equals(target.name())) switchTo(path);
            if (card != null) canvas.reveal(card);
        });
        tree.setContextMenu(new ContextMenu());
        tree.getContextMenu().setOnShowing(e -> {
            TreeItem<FlowTreeView.Item> picked = tree.getSelectionModel().getSelectedItem();
            String flow = picked == null || !picked.getValue().isFlow() ? null : picked.getValue().flow();
            tree.getContextMenu().getItems().setAll(flowMenuItems(flow));
        });
        VBox.setVgrow(tree, Priority.ALWAYS);

        Button create = new Button("+ New flow");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(e -> newFlow());
        treePane.getChildren().setAll(tree, create);
        treePane.setPadding(new Insets(10, 0, 10, 10));
        treePane.setPrefWidth(200);
        treePane.setMinWidth(160);
    }

    /**
     * New, and for the flow {@code flow} — null when no flow row is picked — duplicate, rename and delete. Nothing
     * to copy, rename or delete before the first save writes the constant.
     */
    private List<MenuItem> flowMenuItems(String flow) {
        MenuItem create = new MenuItem("New flow…");
        create.setOnAction(e -> newFlow());
        if (flow == null || !FlowValue.names(services).contains(flow)) return List.of(create);
        MenuItem duplicate = new MenuItem("Duplicate " + flow + "…");
        duplicate.setOnAction(e -> askName("Duplicate flow", "Name the copy of Flows." + flow, flow + "_COPY")
                .ifPresent(typed -> afterSave(() -> FlowValue.duplicate(services, flow, typed),
                        FlowNames.constantFor(typed))));
        MenuItem rename = new MenuItem("Rename " + flow + "…");
        rename.setOnAction(e -> askName("Rename flow", "Rename Flows." + flow + " and every use of it", flow)
                .ifPresent(typed -> afterSave(() -> FlowValue.rename(services, flow, typed),
                        flow.equals(target.name()) ? FlowNames.constantFor(typed) : target.name())));
        MenuItem delete = new MenuItem("Delete " + flow);
        delete.setOnAction(e -> deleteFlow(flow));
        return List.of(create, duplicate, rename, delete);
    }

    private void newFlow() {
        askName("New flow", "Name the new flow", "").ifPresent(typed ->
                afterSave(() -> FlowValue.create(services, typed, Flow.NONE), FlowNames.constantFor(typed)));
    }

    /**
     * Saves the flow shown, runs {@code change} — null when it landed, else why not — and shows {@code then}.
     * A refusal is said and the canvas stays, its picker back on it. A flow written by hand is never saved from
     * here, so its canvas edits have nothing to keep.
     */
    private void afterSave(java.util.function.Supplier<String> change, String then) {
        afterSave(change, then, then == null ? List.of() : List.of(then));
    }

    /** {@link #afterSave(java.util.function.Supplier, String)}, the breadcrumb then {@code path}. */
    private void afterSave(java.util.function.Supplier<String> change, String then, List<String> path) {
        autosaveDelay.stop();
        if (readOnlyReason == null && dirty) flush();
        String refused = readOnlyReason == null && (dirty || saving)
                ? "Flows." + target.name() + " isn't saved, so nothing else changed: " + statusLabel.getText()
                : change.get();
        if (refused != null) {
            error(refused);
            root.setTop(buildTopBar());
            buildTreePane();
            return;
        }
        dirty = false;
        trail = List.copyOf(path);
        showFlow(then);
    }

    /**
     * Deletes the flow {@code name} after asking; refused, before asking, while another flow runs it as a sub-flow
     * card. Shows the flow drawn before, or the default when that was the one deleted.
     */
    private void deleteFlow(String name) {
        List<String> users = FlowRefs.of(services.pluginValues()).usersOf(name);
        if (!users.isEmpty()) {
            error("Flows." + name + " runs as a sub-flow card in " + String.join(", ", users) + ": remove "
                    + (users.size() == 1 ? "that card" : "those cards") + " first.");
            return;
        }
        Alert confirm = services.theme().alert(Alert.AlertType.CONFIRMATION,
                "Its arrows and card places go with it. Its activities stay: they are the bot's, and other flows "
                        + "may run them. A flow the bot's code still names, as its flow parameter does, isn't deleted.",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(stage);
        confirm.setTitle("Delete flow");
        confirm.setHeaderText("Delete Flows." + name + "?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        afterSave(() -> FlowValue.remove(services, name), name.equals(target.name()) ? null : target.name());
    }

    /**
     * The flows a card of this one may run: each other flow that does not run this one, which would walk it
     * forever. Picking one declared below this flow moves its declaration above it first ({@link FlowValue#placeAbove}).
     */
    private Node addSubflowMenu() {
        MenuButton menu = new MenuButton("Add sub-flow");
        menu.setDisable(readOnlyReason != null);
        menu.setTooltip(new javafx.scene.control.Tooltip("Run another flow as one card: it runs to its end, and the "
                + "outcome it ends on leaves on that port. A flow that runs this one isn't offered."));
        menu.setOnShowing(e -> {
            menu.getItems().clear();
            Set<String> placed = placedNames();
            for (String flow : FlowRefs.of(services.pluginValues()).offerable(target.name())) {
                if (placed.contains(FlowNames.subflowLabel(flow))) continue;
                MenuItem item = new MenuItem(flow);
                item.setOnAction(a -> addSubflow(flow));
                menu.getItems().add(item);
            }
            if (menu.getItems().isEmpty()) {
                MenuItem none = new MenuItem("No other flow can run here: add one with + New flow");
                none.setDisable(true);
                menu.getItems().add(none);
            }
        });
        return menu;
    }

    /** Puts a card running {@code inner} on the canvas, its ports {@code inner}'s exits, once it is declared above. */
    private void addSubflow(String inner) {
        String refused = FlowValue.placeAbove(services, inner, target.name());
        if (refused != null) {
            error("Flows." + inner + " is declared below Flows." + target.name() + " and couldn't be moved above it, "
                    + "so a card running it would read nothing: " + refused);
            return;
        }
        // The move rewrote Flows.java: open this flow's constant again before the next save writes it.
        FlowValue.Target again = FlowValue.target(services, target.name());
        if (again.problem() == null) target = again;
        Point2D at = canvas.nextFreeSpot();
        List<String> exits = FlowRefs.of(services.pluginValues()).exits(inner);
        canvas.add(new ActivityDraft(FlowNames.subflowLabel(inner), "", true, exits, false, at.getX(),
                at.getY()));
        error("");
        buildTreePane();
    }

    /** A flow name typed in a prompt, or empty when cancelled. */
    private Optional<String> askName(String title, String header, String initial) {
        TextInputDialog prompt = new TextInputDialog(initial);
        services.theme().apply(prompt);
        prompt.initOwner(stage);
        prompt.setTitle(title);
        prompt.setHeaderText(header);
        prompt.setContentText("Name:");
        return prompt.showAndWait().map(String::strip).filter(typed -> !typed.isEmpty());
    }

    /**
     * The activities other flows run that this one does not, each added as a card with its body, outcomes and
     * switches as the first flow running it has them. With none, the menu says so.
     */
    private Node fromOtherFlows() {
        MenuButton menu = new MenuButton("Add from another flow");
        // A flow written by hand is never saved from here: a card added to it would only be dropped.
        menu.setDisable(readOnlyReason != null);
        menu.setTooltip(new javafx.scene.control.Tooltip("An activity is the bot's, not one flow's: run one another "
                + "flow already has here too, with the same code and outcomes."));
        menu.setOnShowing(e -> {
            menu.getItems().clear();
            Set<String> placed = placedNames();
            PluginValues values = services.pluginValues();
            for (String flowName : FlowValue.names(services)) {
                if (flowName.equals(target.name())) continue;
                for (Flow.Step step : FlowValue.FLOWS.read(values, PluginValues.Member.of(flowName))
                        .orElse(Flow.NONE).steps()) {
                    if (!(step.node() instanceof Activity) || placed.contains(step.label())) continue;
                    placed.add(step.label());
                    MenuItem item = new MenuItem(step.label() + "  (" + flowName + ")");
                    item.setOnAction(a -> addShared(step));
                    menu.getItems().add(item);
                }
            }
            if (menu.getItems().isEmpty()) {
                MenuItem none = new MenuItem("Every other flow's activities are already here");
                none.setDisable(true);
                menu.getItems().add(none);
            }
        });
        return menu;
    }

    /** Puts {@code step}, run by another flow, on this canvas: same enum, same body, its outcomes as declared. */
    private void addShared(Flow.Step step) {
        Point2D at = canvas.nextFreeSpot();
        List<String> outcomes = FlowConstants.outcomesOf(services.pluginValues(), step.label());
        ActivityDraft draft = ActivityDraft.of(new Flow.Step(step.activity(), step.body(), step.description(), true,
                step.goHome(), step.runnerSwitch(), Activity.NONE),
                outcomes.isEmpty() ? List.of(Arrow.NEXT) : outcomes, at.getX(), at.getY());
        canvas.add(draft);
        error("");
    }

    /**
     * The two arrows that make autosave bearable. With no Save button there is no "close without saving" to
     * retreat to, so every mutation has to be reversible in the editor itself; these are that. They are
     * flow-local by design — the code editor has its own undo, and one stack spanning both would let ↶ in a
     * dialog take back a block edit behind it.
     *
     * <p>Disabled when there is nothing to take back, and captioned with what that would be, so ↶ is never a
     * guess. The arrows go grey after a rename for a reason {@code FlowCanvas.invalidateHistory} explains.
     */
    private Button undoButton() {
        Button undo = new Button("↶");
        undo.disableProperty().bind(canvas.history().canUndoProperty().not());
        undo.tooltipProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                () -> new javafx.scene.control.Tooltip(labelled("Undo", canvas.history().undoLabelProperty().get())),
                canvas.history().undoLabelProperty()));
        undo.setOnAction(e -> canvas.history().undo());
        return undo;
    }

    private Button redoButton() {
        Button redo = new Button("↷");
        redo.disableProperty().bind(canvas.history().canRedoProperty().not());
        redo.tooltipProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                () -> new javafx.scene.control.Tooltip(labelled("Redo", canvas.history().redoLabelProperty().get())),
                canvas.history().redoLabelProperty()));
        redo.setOnAction(e -> canvas.history().redo());
        return redo;
    }

    private static String labelled(String verb, String step) {
        return step == null || step.isBlank() ? verb + " — nothing to " + verb.toLowerCase() : verb + " " + step;
    }

    /**
     * Opens the new-activity prompt and drops the resulting card at {@code at}. Both entry points come
     * through here: the "Add activity" button (at the next free spot) and a double-click on empty canvas
     * (under the cursor). The dialog itself owns the name and outcome validation.
     */
    private void createActivityAt(Point2D at) {
        Optional<ActivityDraft> made = new NewActivityDialog(stage, services, placedNames(), goHomeByDefault)
                .showAt(at.getX(), at.getY());
        if (made.isEmpty()) return;
        // A name another flow already runs is that activity: its enum's outcomes come first, so saving this
        // card does not drop the ones the other flow's arrows leave on, and its body is the one that flow runs.
        ActivityDraft draft = made.get();
        PluginValues values = services.pluginValues();
        FlowValue.stepElsewhere(values, draft.name(), target.name())
                .ifPresent(step -> draft.setBody(FlowValue.bodySource(step)));
        List<String> declared = FlowConstants.outcomesOf(values, draft.name());
        if (!declared.isEmpty()) {
            List<String> outcomes = new ArrayList<>(declared);
            for (String typed : draft.outcomes()) if (!outcomes.contains(typed)) outcomes.add(typed);
            draft.outcomes().setAll(outcomes);
        }
        canvas.add(draft);
        error("");
    }

    private Set<String> placedNames() {
        Set<String> names = new HashSet<>();
        for (ActivityDraft d : canvas.drafts()) names.add(d.name());
        return names;
    }

    // --- side panel: the selected activity's graph properties, or the project globals ---

    private Node buildSidePanel() {
        sidePanel.setPadding(new Insets(12));
        sidePanel.setPrefWidth(300);
        sidePanel.setMinWidth(300);
        ScrollPane scroll = new ScrollPane(sidePanel);
        scroll.setFitToWidth(true);
        scroll.setPrefWidth(320);
        return scroll;
    }

    /** Rebuilds the side panel for {@code draft} — the graph's properties, not its settings (see the class doc). */
    private void showInSidePanel(ActivityDraft draft) {
        sidePanel.getChildren().clear();

        if (draft == null) {
            sidePanel.getChildren().addAll(heading("Variables"), buildParametersNote());
            sidePanel.getChildren().addAll(new Separator(), buildHomeSection());
            sidePanel.getChildren().addAll(new Separator(), buildSetupSection());
            sidePanel.getChildren().addAll(new Separator(), buildPopupsSection());
            sidePanel.getChildren().addAll(new Separator(), buildFlowLimitsSection());
            return;
        }

        if (draft.isSubflow()) {
            showSubflowInSidePanel(draft);
            return;
        }

        TextField name = new TextField(draft.name());
        name.focusedProperty().addListener((o, was, is) -> {
            if (is) return;
            renameDraft(draft, FlowNames.activityLabel(name.getText()), name);
        });
        TextField description = descriptionField(draft);
        CheckBox goHome = tick("Go home first", draft.goHomeProperty(),
                FlowNames.GO_HOME_TIP + " Same tick as the ⌂ on the card.");
        CheckBox runnerSwitch = tick("Runner may switch this off", draft.runnerSwitchProperty(), "Offer this "
                + "activity to the person running the bot, who may switch it off (or back on) for their computer "
                + "without opening the project. A switched-off activity takes its 'disabled' arrow, as when you "
                + "untick it here.");

        // The methods returning this activity's enum, read as the list opens. A card whose enum is not written yet
        // has none, and nothing can be created for it until the flow's next save declares the enum.
        PluginValues values = services.pluginValues();
        String type = FlowNames.typeFor(draft.name());
        HBox body = MethodPicker.of(services, () -> FlowConstants.bodies(values, FlowNames.typeFor(draft.name())),
                draft.body(), (type == null ? "Activity" : type) + "::body",
                (className, method) -> FlowConstants.createMethod(values, FlowNames.typeFor(draft.name()), className,
                        method),
                () -> createBlocked(values, draft), reference -> commitBody(draft, reference));
        javafx.scene.control.Tooltip.install(body, new javafx.scene.control.Tooltip(
                "The method that does this activity's work: one returning its enum in " + FlowNames.ACTIVITIES
                        + ".java. Leave it at (none) and the card is a placeholder: the flow walks through it and "
                        + "it does nothing."));

        GridPane head = new GridPane();
        head.setHgap(8);
        head.setVgap(6);
        head.addRow(0, new Label("Name"), name);
        head.addRow(1, new Label("Runs"), body);
        head.addRow(2, new Label("Description"), description);
        head.add(goHome, 1, 3);
        head.add(runnerSwitch, 1, 4);
        GridPane.setHgrow(name, Priority.ALWAYS);
        GridPane.setHgrow(body, Priority.ALWAYS);
        GridPane.setHgrow(description, Priority.ALWAYS);

        Button delete = new Button("Delete activity");
        delete.setTooltip(new javafx.scene.control.Tooltip(
                "Removes it from the flow. The file you wrote its steps in is left alone."));
        delete.setOnAction(e -> deleteActivity(draft));

        sidePanel.getChildren().addAll(heading("Activity"), head, new Separator(),
                heading("Outcomes"), buildOutcomeEditor(draft), new Separator(),
                heading("Variables"), buildParametersNote(),
                new Separator(), delete);
    }

    /**
     * A sub-flow card's panel: the flow it runs, a way into it, its description and switches, and its exits — the
     * outcomes that flow ends on, read off it, so nothing here renames them.
     */
    private void showSubflowInSidePanel(ActivityDraft draft) {
        String inner = draft.subflow();
        Label runs = new Label("Runs Flows." + inner + " to its end, then follows the wire of the outcome it ended "
                + "on. Its own limits and switches hold while it runs.");
        runs.setWrapText(true);
        runs.getStyleClass().add("dialog-hint-text");
        Button open = new Button("Open " + inner);
        open.setOnAction(e -> openSubflow(inner));

        GridPane head = new GridPane();
        head.setHgap(8);
        head.setVgap(6);
        TextField description = descriptionField(draft);
        head.addRow(0, new Label("Description"), description);
        head.add(tick("Go home first", draft.goHomeProperty(), FlowNames.GO_HOME_TIP), 1, 1);
        head.add(tick("Runner may switch this off", draft.runnerSwitchProperty(), "Offer this sub-flow to the "
                + "person running the bot, who may switch it off for their computer: it then takes its 'disabled' "
                + "arrow."), 1, 2);
        GridPane.setHgrow(description, Priority.ALWAYS);

        VBox exits = new VBox(4);
        if (draft.allOutcomes().isEmpty()) {
            exits.getChildren().add(new Label(inner + " has no end yet: it has no card, or every outcome is wired."));
        }
        for (String exit : draft.allOutcomes()) {
            boolean broken = draft.brokenExits().contains(exit);
            Label row = new Label((broken ? "⚠ " : "• ") + exit + (broken ? " — no longer an end of " + inner : ""));
            if (broken) row.getStyleClass().add("flow-port-broken");
            exits.getChildren().add(row);
        }

        Button delete = new Button("Remove card");
        delete.setTooltip(new javafx.scene.control.Tooltip("Removes the card and its wires. Flows." + inner
                + " stays."));
        delete.setOnAction(e -> deleteActivity(draft));

        sidePanel.getChildren().addAll(heading("Sub-flow " + inner), runs, open, head, new Separator(),
                heading("Exits"), exits, new Separator(), delete);
    }

    /** The draft's description, saved as it is typed. */
    private TextField descriptionField(ActivityDraft draft) {
        TextField description = new TextField(draft.description());
        description.textProperty().addListener((o, was, is) -> {
            draft.descriptionProperty().set(is);
            markDirty();
        });
        return description;
    }

    /**
     * A tick bound to one of the draft's switches. The canvas records what it owns — position, wiring, the enable
     * switch. These live on the draft and nowhere else, so they ask for the save themselves. They are not undoable,
     * which is the honest answer for a text field and a tick you can simply set back.
     */
    private CheckBox tick(String caption, BooleanProperty value, String tip) {
        CheckBox box = new CheckBox(caption);
        box.selectedProperty().bindBidirectional(value);
        box.selectedProperty().addListener((o, was, is) -> markDirty());
        box.setTooltip(new javafx.scene.control.Tooltip(tip));
        return box;
    }

    /**
     * The outcome list for one activity: what it can report having happened, one card port each.
     *
     * <p>Deliberately says nothing about <em>where</em> an outcome goes — that is the canvas's job. Keeping
     * the two apart is the whole reason an activity's code never names another activity.
     */
    private Node buildOutcomeEditor(ActivityDraft draft) {
        VBox box = new VBox(6);

        Label explain = new Label(FlowNames.OUTCOMES_HINT);
        explain.setWrapText(true);
        explain.getStyleClass().add("dialog-hint-text");
        box.getChildren().add(explain);

        for (String outcome : List.copyOf(draft.outcomes())) {
            TextField field = new TextField(outcome);
            // What the row names now: a rename changes it without rebuilding the row, and the next commit or
            // ✕ has to act on the outcome as it is, not as it was drawn, or a second rename reads as a duplicate
            // of the first.
            String[] current = {outcome};
            field.focusedProperty().addListener((o, was, is) -> {
                if (is) return;
                current[0] = renameOutcome(draft, current[0], field.getText(), field);
            });
            // Enter has to commit here too: it is the obvious way to finish a rename, and without a consuming
            // handler it would reach the default button and close the dialog with the old name still set.
            field.setOnAction(e -> {
                current[0] = renameOutcome(draft, current[0], field.getText(), field);
                e.consume();
            });
            Button remove = new Button("✕");
            // The enum keeps one constant at least: the body has to return something.
            boolean last = draft.outcomes().size() <= 1;
            remove.setDisable(last);
            remove.setTooltip(new javafx.scene.control.Tooltip(last
                    ? "An activity keeps one outcome at least: its body has to return one. Add another first."
                    : "Remove this outcome from this activity. Any arrow leaving it is removed too."));
            remove.setOnAction(e -> {
                if (draft.outcomes().size() <= 1) return;
                draft.outcomes().remove(current[0]);
                showInSidePanel(draft);
            });
            HBox row = new HBox(6, field, remove);
            HBox.setHgrow(field, Priority.ALWAYS);
            row.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(row);
        }

        TextField newOutcome = new TextField();
        newOutcome.setPromptText("new outcome (e.g. bag full)");
        Button add = new Button("Add");
        Runnable addOutcome = () -> {
            String candidate = FlowNames.label(newOutcome.getText());
            String problem = FlowNames.outcomeProblem(draft.outcomes(), draft.name(), candidate, null);
            if (problem != null) { error(problem); return; }
            draft.outcomes().add(candidate);
            newOutcome.clear();
            error("");
            showInSidePanel(draft);
        };
        add.setOnAction(e -> addOutcome.run());
        newOutcome.setOnAction(e -> {
            addOutcome.run();
            e.consume();
        });
        HBox addRow = new HBox(6, newOutcome, add);
        HBox.setHgrow(newOutcome, Priority.ALWAYS);
        addRow.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(addRow);
        return box;
    }

    /**
     * Renames one of {@code draft}'s outcomes, carrying its arrow across, and answers the name the row now holds.
     * The outcome is a constant of that activity's enum alone, so no other card changes.
     */
    private String renameOutcome(ActivityDraft draft, String oldName, String typed, TextField field) {
        String candidate = FlowNames.label(typed);
        if (candidate.equals(oldName)) {
            field.setText(oldName);   // trimming may have changed the text without changing the outcome
            return oldName;
        }
        String problem = FlowNames.outcomeProblem(draft.outcomes(), draft.name(), candidate, oldName);
        if (problem != null) {
            error(problem);
            field.setText(oldName);
            return oldName;
        }
        int at = draft.outcomes().indexOf(oldName);
        if (at >= 0) {
            // The arrow first, then the outcome. The card drops every arrow whose port has gone the moment the
            // list changes, so renaming the outcome first would drop its arrow before it could be carried across.
            canvas.edges().setAll(rewiredOutcome(canvas.edges(), draft.name(), oldName, candidate));
            draft.outcomes().set(at, candidate);
        }
        renamed(outcomeRenames.computeIfAbsent(draft.name(), activity -> new LinkedHashMap<>()), oldName, candidate);
        field.setText(candidate);
        error("");
        canvas.refresh();
        return candidate;
    }

    /**
     * Records that the label {@code was} is now {@code now} in {@code renames}, keyed by the label at the last
     * save: two renames before a save are one, and a rename back to the saved label is none.
     */
    static void renamed(Map<String, String> renames, String was, String now) {
        String original = was;
        for (Map.Entry<String, String> pending : renames.entrySet()) {
            if (pending.getValue().equals(was)) {
                original = pending.getKey();
                break;
            }
        }
        if (original.equals(now)) renames.remove(original);
        else renames.put(original, now);
    }

    /** {@code edges} with {@code activity}'s wire from {@code oldName} leaving from {@code newName} instead. */
    static List<Arrow> rewiredOutcome(List<Arrow> edges, String activity, String oldName, String newName) {
        List<Arrow> rewired = new ArrayList<>(edges.size());
        for (Arrow e : edges) {
            boolean mine = e.from().equals(activity) && e.outcome().equals(oldName);
            rewired.add(mine ? new Arrow(e.from(), e.to(), newName) : e);
        }
        return rewired;
    }

    /**
     * Removes {@code draft} from the flow for good — the card and its wires.
     *
     * <p>This replaced "Archive activity", which promised a reversible retirement and could not deliver one.
     * A user who wants to stop an activity running without losing it turns its <em>enable flag</em> off —
     * that is what the flag is for, it survives everything, and it needs no second mechanism.
     *
     * <p>It asks first, because the wiring goes with it. What it does <b>not</b> touch is the user's own Java:
     * an activity's behaviour is the method its card names ({@code Collect::body}), and deleting somebody's
     * source because a card left a canvas is not this window's to do. ↶ brings the card back.
     */
    private void deleteActivity(ActivityDraft draft) {
        Alert confirm = services.theme().alert(Alert.AlertType.CONFIRMATION,
                "Its card and every wire into or out of it are removed; ↶ brings them back. "
                        + (draft.isSubflow() ? "Flows." + draft.subflow() + " stays as it is."
                        : "The method it runs is left exactly as it is.") + "\n\n"
                        + "To stop it running without losing it, turn its switch off instead.",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(stage);
        confirm.setTitle(draft.isSubflow() ? "Remove sub-flow card" : "Delete activity");
        confirm.setHeaderText("Delete “" + draft.name() + "”?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        canvas.remove(draft); // also drops any wires into or out of it
        error("");
    }

    /**
     * The bot's way home — {@code Sdk.run()}'s {@code .home(Gamebot::goHome)}, run at start-up, after anything
     * unexpected and before each activity whose ⌂ is ticked, whichever flow runs — picked among the bot's
     * {@code public static void} methods, and how a bot starts the flow at all ({@link #methodChoice}).
     *
     * <p>Where the run starts is the bot's own {@code main}, which this window does not write: the sentence says
     * which block to insert there. Studio offers it in {@code main} only, filled with the bot's values classes.
     */
    private Node buildHomeSection() {
        VBox box = new VBox(6);
        box.getChildren().add(heading("Home"));
        Label explain = new Label("The method that gets the game back to a known screen: run when the bot "
                + "starts, after anything unexpected, and before each activity whose ⌂ is ticked. One for the "
                + "whole bot, whichever flow runs.");
        explain.setWrapText(true);
        explain.getStyleClass().add("dialog-hint-text");
        box.getChildren().add(explain);

        box.getChildren().add(methodChoice("home", home, "Home::goHome", picked -> home = picked));

        Label start = new Label("To start the flow, insert Bot ▸ Run activity flow in your bot's main. It is "
                + "offered there only: Bot.runFlow(Sdk.class) walks the flow the "
                + "bot's flow parameter names.");
        start.setWrapText(true);
        start.getStyleClass().add("dialog-hint-text");
        box.getChildren().add(start);
        return box;
    }

    /**
     * The bot's setup — {@code Sdk.run()}'s {@code .setup(Gamebot::setup)}, run after the bot has started the game,
     * before home, when the bot's {@code doSetup} parameter is ticked — picked as the home is. What it checks is the
     * bot's own code: the game's settings, and the ones a user chose in the bot's parameters.
     */
    private Node buildSetupSection() {
        VBox box = new VBox(6);
        box.getChildren().add(heading("Setup"));
        Label explain = new Label("The method that makes the game ready for this bot, run once the bot has started "
                + "the game when the bot's doSetup parameter is ticked: set the game's settings to what the bot "
                + "needs, including those chosen in its parameters. Bot.stop(\"…\") stops the run and tells the user "
                + "what to change.");
        explain.setWrapText(true);
        explain.getStyleClass().add("dialog-hint-text");
        box.getChildren().add(explain);
        box.getChildren().add(methodChoice("setup", setup, "GameSetup::prepare",
                picked -> setup = picked));
        return box;
    }

    /**
     * The bot's popups method — {@code Sdk.run()}'s {@code .popups(Gamebot::clearPopups)}, run before every vision
     * call of every activity — picked as the home is.
     */
    private Node buildPopupsSection() {
        VBox box = new VBox(6);
        box.getChildren().add(heading("Popups"));
        Label explain = new Label("The method that clears a popup the game opened on its own (an offer, a mail, a "
                + "level-up): run before every look at the screen in every activity, so no activity has to expect "
                + "one. It looks for the popups' close buttons and clicks them.");
        explain.setWrapText(true);
        explain.getStyleClass().add("dialog-hint-text");
        box.getChildren().add(explain);
        box.getChildren().add(methodChoice("popups", popups, "Popups::clear", picked -> popups = picked));
        return box;
    }

    /**
     * A pick of the bot's {@code public static void} methods for the run's {@code .<link>(…)}, starting at
     * {@code current}. One written otherwise than as a method reference is not this window's to show or replace: it
     * says so and keeps it, rather than offering a list that would read "(none)" over it.
     */
    private Node methodChoice(String link, Runnable current, String example, Consumer<Runnable> picked) {
        String source = current == null ? "" : com.botmaker.sdk.plugin.types.FlowTypes.sourceOf(current);
        if (current != null && source.isBlank()) {
            Label kept = new Label("Sdk.run()'s ." + link + "(…) is written in code, not as a method reference, "
                    + "and is kept as it is. Change it there.");
            kept.setWrapText(true);
            return kept;
        }
        PluginValues values = services.pluginValues();
        return MethodPicker.of(services, () -> FlowConstants.homes(values), source, example,
                (className, method) -> FlowConstants.createMethod(values, null, className, method),
                () -> null, reference -> {
                    picked.accept(com.botmaker.sdk.plugin.types.FlowTypes.method(reference));
                    markDirty();
                });
    }

    /**
     * The two loop-safety controls, both about a flow that legitimately cycles.
     *
     * <p>The <b>step budget</b> is how many activities one run may hand off to before the generated driver
     * gives up: nothing structural says when a loop should stop, so this is what separates "farms all night"
     * from a cycle with no way out. It bounds transitions <em>between</em> activities; the SDK's watchdog
     * covers being stuck inside one.
     *
     * <p>The <b>pause between activities</b> is the other half: the budget eventually stops a runaway, but a
     * fast activity looping back to itself gives the <em>user</em> no moment to intervene, because the bot is
     * holding the mouse the whole time. A default second between activities is that moment.
     */
    private Node buildFlowLimitsSection() {
        VBox box = new VBox(6);
        box.getChildren().add(heading("Loop safety"));

        Label explain = new Label("A flow can loop on purpose. This is how many activities one run may go "
                + "through before the bot gives up and stops — it's what catches a loop with no exit.");
        explain.setWrapText(true);
        explain.getStyleClass().add("dialog-hint-text");

        TextField field = new TextField(String.valueOf(maxSteps));
        field.setPrefColumnCount(6);
        field.focusedProperty().addListener((o, was, is) -> {
            if (is) return;
            commitMaxSteps(field);
        });
        field.setOnAction(e -> {
            commitMaxSteps(field);
            e.consume();
        });

        HBox row = new HBox(8, new Label("Max steps per run"), field);
        row.setAlignment(Pos.CENTER_LEFT);

        Label delayExplain = new Label("How long the bot pauses between two activities. A fast activity that "
                + "loops back to itself otherwise never lets go of the mouse — this is your window to hit "
                + "Stop. Set 0 for no pause.");
        delayExplain.setWrapText(true);
        delayExplain.getStyleClass().add("dialog-hint-text");

        TextField delayField = new TextField(String.valueOf(stepDelayMs));
        delayField.setPrefColumnCount(6);
        delayField.focusedProperty().addListener((o, was, is) -> {
            if (is) return;
            commitStepDelay(delayField);
        });
        delayField.setOnAction(e -> {
            commitStepDelay(delayField);
            e.consume();
        });

        HBox delayRow = new HBox(8, new Label("Pause between activities (ms)"), delayField);
        delayRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox goHome = new CheckBox("New activities go home first");
        goHome.setSelected(goHomeByDefault);
        goHome.setTooltip(new javafx.scene.control.Tooltip(
                "Whether a newly added activity starts with its ⌂ tick on. Each activity can still be changed "
                        + "individually on its card."));
        goHome.selectedProperty().addListener((o, was, is) -> {
            goHomeByDefault = is;
            markDirty();
        });

        box.getChildren().addAll(explain, row, delayExplain, delayRow, goHome);
        return box;
    }

    private void commitStepDelay(TextField field) {
        try {
            int parsed = Integer.parseInt(field.getText().trim());
            // 0 is allowed here, unlike the step budget: "run flat out" is a real choice, it just forfeits the
            // gap that lets you stop a runaway loop.
            if (parsed < 0) throw new NumberFormatException();
            stepDelayMs = parsed;
            error("");
            markDirty();
        } catch (NumberFormatException bad) {
            error("The pause must be a whole number of milliseconds, 0 or more.");
            field.setText(String.valueOf(stepDelayMs));
        }
    }

    private void commitMaxSteps(TextField field) {
        try {
            int parsed = Integer.parseInt(field.getText().trim());
            if (parsed <= 0) throw new NumberFormatException();
            maxSteps = parsed;
            error("");
            markDirty();
        } catch (NumberFormatException bad) {
            // A zero or negative budget generates a driver that stops before running anything at all.
            error("The step limit must be a whole number above zero.");
            field.setText(String.valueOf(maxSteps));
        }
    }

    /**
     * Why no body can be written for {@code draft} yet — its name makes no enum, or the enum is not in
     * {@code Activities.java} until the flow's next save — or null when one can. Asked at the click, so a save in
     * between is seen.
     */
    private static String createBlocked(PluginValues values, ActivityDraft draft) {
        String type = FlowNames.typeFor(draft.name());
        if (type == null) return "'" + draft.name() + "' makes no enum name, so no method can return it.";
        if (FlowConstants.declared(values).containsKey(type)) return null;
        return FlowNames.ACTIVITIES + "." + type + " is not written yet: it is declared when the flow is next "
                + "saved. Try again once it is.";
    }

    /**
     * Links {@code draft} to the method {@code reference} picked or created, blank for none — a placeholder card.
     * The reference is one the host listed or wrote, so there is nothing left to check here.
     */
    private void commitBody(ActivityDraft draft, String reference) {
        if (reference.equals(draft.body())) return;
        draft.setBody(reference);
        error("");
        markDirty();
    }

    private void renameDraft(ActivityDraft draft, String candidate, TextField field) {
        field.setText(candidate);
        if (candidate.equals(draft.name())) return;
        // The new-activity dialog's rule, over the other cards: one rule for both ways of naming a card.
        Set<String> others = new HashSet<>();
        for (ActivityDraft d : canvas.drafts()) if (d != draft) others.add(d.name());
        String problem = FlowNames.activityNameProblem(candidate, others);
        // And the bot's: an enum another activity has, which another flow may run. Asked of the label the card had
        // at the last save, since the enum keeps it until the next one.
        String saved = activityRenames.entrySet().stream().filter(e -> e.getValue().equals(draft.name()))
                .map(Map.Entry::getKey).findFirst().orElse(draft.name());
        if (problem == null) problem = FlowConstants.renameProblem(services.pluginValues(), saved, candidate);
        if (problem != null) {
            error(problem + " Reverted.");
            field.setText(draft.name());
            return;
        }
        String was = draft.name();
        renamed(activityRenames, was, candidate);
        // An outcome's pending renames are keyed by its activity's label now.
        Map<String, String> outcomes = outcomeRenames.remove(was);
        if (outcomes != null) outcomeRenames.put(candidate, outcomes);
        draft.nameProperty().set(candidate); // the card re-labels and its arrows follow the new name
        error("");
    }

    /**
     * Where the values are, said rather than offered.
     *
     * <p>This panel used to print the variables filed under the selected activity, then became a button that
     * opened the Parameters window. The list went because it read as though the activity <em>owned</em> those
     * values, when a category is a filing label and nothing else. <b>The button went with the move out of the
     * host</b>: the Parameters window is Studio's, drawn from whatever rows every plugin hands over, and a
     * plugin has no handle on one of the editor's own windows — the same cost the capture and picture tools
     * paid when they left, and the same answer, which is to say where to go.
     */
    private Node buildParametersNote() {
        Label what = new Label("Values belong to the project, not to a card: a variable filed under an "
                + "activity is still readable from all the others. Add, retype and share them in "
                + "Project ▸ Parameters…, which sees every plugin's at once.");
        what.setWrapText(true);
        what.getStyleClass().add("dialog-hint-text");
        return new VBox(6, what);
    }

    // --- bottom bar: run-order preview + what autosave is doing ---

    private Node buildBottomBar() {
        progress.setVisible(false);
        progress.setPrefSize(20, 20);
        orderLabel.getStyleClass().add("dialog-hint-text");
        statusLabel.getStyleClass().add("dialog-error-text");
        savedLabel.getStyleClass().add("dialog-hint-text");

        // No Save button. Everything on this canvas is a gesture — drag a card, draw a wire, flip a switch —
        // and a gesture that needs confirming afterwards is a gesture you can lose by closing the window. So
        // the flow saves itself, ↶ is what takes a change back, and Close only ever closes.
        closeButton = new Button("Close");
        closeButton.setDefaultButton(true);
        closeButton.setOnAction(e -> closeRequested());

        // No "Create Sdk.java" button: the host writes the holder itself on every bind the project lacks one
        // (Studio's HostPluginValues.createMissing), so this window never has to ask.

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, progress, savedLabel, statusLabel, spacer, closeButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox bar = new VBox(6, orderLabel, buttons);
        bar.setPadding(new Insets(10));
        return bar;
    }

    /**
     * Summarises what the current wiring will do.
     *
     * <p>It used to print the run order, which a branching flow no longer has — where the bot goes depends on
     * what each activity reports at runtime, so the only honest static answers are where it starts and what
     * it can get to.
     */
    private void refreshOrderLabel() {
        if (canvas.edges().isEmpty()) {
            orderLabel.setText("No wiring yet — activities run in the order they were added.");
            return;
        }
        List<String> reachable = canvas.chain();
        List<String> orphans = canvas.orphans();
        List<String> unwired = canvas.unwiredOutcomes();
        StringBuilder summary = new StringBuilder("Starts at ")
                .append(canvas.resolvedStart().isEmpty() ? "—" : canvas.resolvedStart())
                .append("  ·  ").append(reachable.size()).append(" activities reachable");
        // Not a warning: an outcome with no wire is how a run ends, so this is a count, not a complaint.
        if (!unwired.isEmpty()) {
            summary.append("  ·  ").append(unwired.size()).append(" outcomes end the run");
        }
        if (!orphans.isEmpty()) {
            summary.append("    ⚠ not in the flow (won't run): ").append(String.join(", ", orphans));
        }
        // A warning: a sub-flow card sits mid-flow, so an end of its flow wired nowhere stops the whole run.
        List<String> exits = canvas.unwiredExits();
        if (!exits.isEmpty()) {
            summary.append("    ⚠ stops the run when the sub-flow ends on: ").append(String.join(", ", exits));
        }
        // Also not a warning, but worth saying out loud: an unwired DISABLED port is a silent stop, and it is
        // silent precisely where the drawing shows the flow carrying on.
        List<String> stopsWhenOff = canvas.unwiredWhenDisabled();
        if (!stopsWhenOff.isEmpty()) {
            summary.append("    ·  stops the run if switched off: ").append(String.join(", ", stopsWhenOff));
        }
        orderLabel.setText(summary.toString());
    }

    /** Notes that something changed and asks for a save — coalesced, so a drag writes once, not per frame. */
    private void markDirty() {
        dirty = true;
        savedLabel.setText("Saving…");
        autosaveDelay.playFromStart();
    }

    /** The flow as it currently stands, with each card's outcomes, ready to be written into the bot's Java. */
    private Board currentBoard() {
        return board(canvas.drafts(), canvas.edges(), canvas.start(), Flow.limits(maxSteps, stepDelayMs));
    }

    /**
     * The board {@code drafts} and {@code arrows} draw: a step per card, switched off to where its
     * {@link Arrow#DISABLED} arrow goes, an edge per other arrow, and each card's ports as its outcomes.
     */
    static Board board(List<ActivityDraft> drafts, List<Arrow> arrows, String start, Flow.Limits limits) {
        List<Flow.Step> steps = new ArrayList<>();
        Map<String, List<String>> exits = new LinkedHashMap<>();
        for (ActivityDraft d : drafts) {
            Flow.Node off = arrows.stream().filter(a -> a.isDisabled() && a.from().equals(d.name())).findFirst()
                    .map(a -> FlowNames.node(a.to())).orElse(Activity.NONE);
            steps.add(d.toStep(off));
            // A sub-flow card's ports are its flow's exits, which no enum of this bot declares for it.
            if (!d.isSubflow()) exits.put(d.name(), d.allOutcomes());
        }
        // A blank arrow leaves on no outcome, so it names no constant to be written as.
        List<Flow.Edge> edges = arrows.stream().filter(a -> !a.isDisabled() && !a.outcome().isBlank())
                .map(Arrow::toEdge).toList();
        // A start is a class literal or a flow, so a flow with cards always names one: the first card when none is set.
        Flow.Node first = steps.isEmpty() ? Activity.NONE : steps.getFirst().node();
        Flow.Node begin = start == null || start.isBlank() ? first : FlowNames.node(start);
        return new Board(new Flow(steps, edges, begin, limits), exits);
    }

    /**
     * The saved layout, or {@link FlowLayout#NONE}. A project with no {@code flowLayout()}, or one whose body
     * this plugin did not write, opens arranged, and the status line says the positions will not be kept.
     */
    private FlowLayout loadLayout() {
        layoutValue = FlowValue.LAYOUT.open(services);
        Optional<FlowLayout> read = layoutValue.flatMap(FlowValue.LAYOUT::read);
        if (read.isEmpty()) {
            layoutValue = Optional.empty();
            // The flow's own reason, when it has one, is the one worth reading.
            if (readOnlyReason == null) error("Card positions aren't kept: Sdk.java has no flowLayout() BotMaker can write.");
        }
        written = read.orElse(FlowLayout.NONE);
        return written;
    }

    /**
     * The layout with this flow's cards where they sit now, keyed by each one's enum name as the layout's javadoc
     * says, every other flow's kept as read but for the enums {@code renamedTypes} renames, plus the one editor
     * preference that rides along with them.
     */
    private FlowLayout currentLayout(Map<String, String> renamedTypes) {
        Map<String, FlowLayout.Spot> spots = new LinkedHashMap<>();
        for (ActivityDraft d : canvas.drafts()) {
            spots.put(FlowNames.layoutKey(d.name()), FlowLayout.at((int) Math.round(d.x()), (int) Math.round(d.y())));
        }
        return rekeyed(written, renamedTypes).with(target.name(), FlowLayout.cards(spots))
                .goingHomeByDefault(goHomeByDefault);
    }

    /**
     * {@code layout} with every flow's card of an enum {@code types} renames keyed by its new name: the rename is
     * the enum's, so it reaches every flow that runs the activity, not only the one on the canvas.
     */
    static FlowLayout rekeyed(FlowLayout layout, Map<String, String> types) {
        if (types.isEmpty()) return layout;
        FlowLayout out = layout;
        for (Map.Entry<String, FlowLayout.Cards> flow : layout.flows().entrySet()) {
            Map<String, FlowLayout.Spot> spots = new LinkedHashMap<>();
            flow.getValue().spots().forEach((card, spot) -> spots.put(types.getOrDefault(card, card), spot));
            out = out.with(flow.getKey(), FlowLayout.cards(spots));
        }
        return out;
    }

    /**
     * Writes the flow, if there is anything to write and nothing already in flight.
     *
     * <p><b>On the FX thread</b>: the flow is one expression handed to the host, which is what every slot
     * editor on the canvas does on every keystroke and which
     * {@link com.botmaker.plugin.api.slot.ValueContext#set} requires. The layout goes with it rather than
     * behind it, so a renamed card is written under its new name in the same save as the flow that renamed it.
     *
     * <p>{@link #saving} is still honoured, because the write can re-enter: the host's write lands in the
     * open buffer, and anything listening to that must not start a second save underneath this one.
     */
    private void flush() {
        if (!dirty || saving) return;
        if (readOnlyReason != null) {
            // Nothing to fix and nothing to retry: the value is one the user wrote, and it stays theirs.
            error(readOnlyReason);
            savedLabel.setText("Not saved");
            refused(readOnlyReason);
            return;
        }
        Board board = currentBoard();
        String problem = validate(board);
        if (problem != null) {
            // Refused, not failed — a duplicate name or a bad identifier. Stay dirty so the fix saves it, and
            // let go of any pending close unless the user says to drop the edit (see refused).
            error(problem);
            savedLabel.setText("Not saved");
            refused(problem);
            return;
        }
        error("");
        dirty = false;
        saving = true;
        progress.setVisible(true);
        savedLabel.setText("Saving…");

        // The enums this save renames, taken before the save consumes the renames: the other flows' cards follow.
        Map<String, String> renamedTypes = new LinkedHashMap<>();
        activityRenames.forEach((was, now) -> {
            String from = FlowNames.typeFor(was);
            String to = FlowNames.typeFor(now);
            if (from != null && to != null) renamedTypes.put(from, to);
        });
        // The constants first, so the flow written next names them (FlowConstants has the order and why).
        List<String> notes = new ArrayList<>();
        String refused = FlowEdits.save(services, target, board, savedActivities, activityRenames, outcomeRenames,
                notes);
        if (refused == null) {
            savedActivities = FlowEdits.labels(board.flow());
            // Read again: the first save of a project with no flow wrote Flows.MAIN, which the next save edits.
            target = FlowValue.target(services, target.name());
            // After the flow, so the run names the flow as just written; nothing when home and setup are as read.
            refused = FlowValue.writeRun(services, target, new Run(home, setup, popups));
        }
        Throwable failure = null;
        FlowLayout layout = currentLayout(renamedTypes);
        if (refused == null && layoutValue.isPresent() && !layout.equals(written)) {
            try {
                // After the flow, never before: a layout for a card the saved flow does not have is the one
                // way round that is merely untidy.
                Optional<String> layoutRefused = layoutValue.get().write(layout);
                if (layoutRefused.isPresent()) failure = new IllegalStateException(layoutRefused.get());
                else written = layout;
            } catch (Exception | LinkageError e) {
                failure = e;
            }
        }
        saved(refused, failure, notes);
    }

    /**
     * What happens once a write has landed — or has not. {@code notes} are what the save left undone without
     * failing: a constant still used, so not removed.
     */
    private void saved(String refused, Throwable err, List<String> notes) {
        saving = false;
        progress.setVisible(false);
        if (refused != null) {
            dirty = true;   // the next edit, or Close, tries again
            error(refused);
            savedLabel.setText("Not saved");
            refused(refused);
            return;
        }
        if (err != null) {
            // The flow itself is written; only the positions are not. Saying so rather than "not saved"
            // matters, because the two have very different consequences for what is lost.
            error("The flow was saved. The card positions weren't: " + rootMessage(err));
            savedLabel.setText("Saved");
        } else {
            if (!notes.isEmpty()) error(String.join(" ", notes));
            savedLabel.setText("Saved");
        }
        if (dirty) flush();
        else if (closeWhenSaved) stage.close();
    }

    /** Close was pressed: write anything outstanding first, then close — never the other way round. */
    private void closeRequested() {
        autosaveDelay.stop();
        if (!dirty && !saving) {
            stage.close();
            return;
        }
        closeWhenSaved = true;
        closeButton.setDisable(true);
        flush();
    }

    /**
     * A save did not land. If the user was closing, ask whether to close anyway. Otherwise a save that can never
     * succeed (no {@code Sdk.java}, a hand-written flow) consumed every ✕ and sealed the window. Not closing gives
     * the Close button back, so the user can fix the problem and try again.
     */
    private void refused(String reason) {
        boolean closing = closeWhenSaved;
        releaseClose();
        if (!closing) return;
        Alert confirm = services.theme().alert(Alert.AlertType.CONFIRMATION,
                reason + "\n\nClosing now drops the changes made in this window.",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(stage);
        confirm.setTitle("Close without saving");
        confirm.setHeaderText("The flow could not be saved. Close anyway?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            dirty = false;
            stage.close();
        }
    }

    /** Gives the Close button back after a save that didn't land, so the window is never sealed shut. */
    private void releaseClose() {
        closeWhenSaved = false;
        closeButton.setDisable(false);
    }

    /**
     * Returns an error message if the flow can't be written (bad or duplicate names, collisions), else null.
     *
     * <p><b>The generated-field check is gone</b>, and it was the largest of these. An activity's enable
     * flag and a project's variables used to become fields of one generated class, so their names had to be
     * unique valid identifiers within one namespace; the flag is now {@link Flow.Step#enabled()} and a
     * variable is a {@code @Param} field of the user's own class, so neither of them generates a field and
     * javac is what has an opinion about the names. What is left checks the names this editor is still the
     * author of.
     *
     * <p>A label is free text and the rule is about the name it becomes ({@link FlowNames#typeFor},
     * {@link FlowNames#constantFor}): every label must make one, and two labels making the same one are one
     * name spelled twice — among the activities, and among one activity's outcomes.
     */
    public static String validate(Board board) {
        return FlowEdits.validate(board);
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("dialog-subheading");
        return label;
    }

    private void error(String message) {
        statusLabel.setText(message);
    }

    private static String rootMessage(Throwable err) {
        Throwable t = err;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }
}
