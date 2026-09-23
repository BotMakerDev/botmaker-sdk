package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.sdk.api.flow.Flow;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Activity Flow editor — the single place activities are defined, ordered and switched on. Cards are
 * dropped on a canvas, wired into the order they should run, and ticked.
 *
 * <p>Three panes: the {@link FlowCanvas} in the middle, a side panel with the selected card's name, outcomes
 * and go-home/popup ticks, and a top bar of presets — named on/off selections that flip the enable ticks
 * without touching the wiring. Parameters themselves are <em>defined</em> in the editor's own Parameters
 * window, not here: this dialog is about the graph.
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
 * <p>No file. The graph is one expression — the one {@code Sdk.flow()} returns —
 * written through {@link com.botmaker.plugin.api.source.PluginValues}, so it lands in the bot's own Java, in the
 * open buffer as well as on disk, as one entry in the project's history. The card positions go to the
 * gitignored {@link FlowLayout} sidecar, because dragging a node is not a change to the bot.
 *
 * <p>A flow whose expression the plugin cannot read — hand-written, or a call to something else — is
 * <b>shown empty and refused</b> rather than overwritten, which is the same rule every other value editor
 * follows.
 */
public final class ActivityFlowDialog {

    private final Window owner;
    private final StudioServices services;

    /** The project's resources directory — where the gitignored {@link FlowLayout} sidecar lives. */
    private final Path resourcesDir;

    private final FlowCanvas canvas = new FlowCanvas();
    private final List<Flow.Preset> presets = new ArrayList<>();

    /**
     * The {@code @Managed("flow")} value this window edits, or empty when the project has none — no
     * {@code Sdk.java}, or a {@code flow()} whose body somebody wrote by hand.
     */
    private Optional<ValueContext> value = Optional.empty();

    /** Why the flow cannot be edited, or {@code null} when it can. Shown once, on the status line. */
    private String readOnlyReason;

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
    private final ComboBox<Flow.Preset> presetCombo = new ComboBox<>();

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

    public ActivityFlowDialog(StudioServices services, Window owner) {
        this.services = services;
        this.owner = owner;
        this.resourcesDir = services.resourcesDir();
    }

    public void show() {
        stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Activity Flow");

        loadCurrent();

        BorderPane root = new BorderPane();
        root.setTop(buildTopBar());
        root.setCenter(canvas);
        root.setRight(buildSidePanel());
        root.setBottom(buildBottomBar());

        canvas.setOnMessage(this::error);
        canvas.setOnChainChanged(this::refreshOrderLabel);
        canvas.setOnCanvasDoubleClick(this::createActivityAt);
        // Wired *after* loadCurrent, so seeding the canvas doesn't read as a dozen edits by the user; and the
        // history is cleared for the same reason — the flow as it was loaded is the state undo bottoms out at.
        canvas.setOnFlowMutated(this::markDirty);
        canvas.history().clear();
        autosaveDelay.setOnFinished(e -> flush());
        canvas.selectedProperty().addListener((o, was, is) -> showInSidePanel(is));
        showInSidePanel(null);
        refreshOrderLabel();

        // The window's own ✕ is the same door as the Close button, and it must not be the one that loses the
        // last edit: both go through closeRequested, which flushes anything outstanding first.
        stage.setOnCloseRequest(e -> {
            if (dirty || saving || closeWhenSaved) {
                e.consume();
                closeRequested();
            }
        });

        stage.setScene(FlowStyles.apply(services.theme().scene(root, 1040, 680)));
        stage.setMinWidth(760);
        stage.setMinHeight(480);
        stage.show();
        // Cards have real bounds only after the first layout pass; re-draw so the wires land on the ports —
        // and auto-arrange there too, since it stacks cards by their real heights and would otherwise lay the
        // first-ever open out against the fallback height.
        Platform.runLater(() -> {
            if (arrangeOnOpen) canvas.autoArrange();
            else canvas.refresh();
        });
    }

    /** Seeds the canvas from the stored flow: a card per activity, at its stored spot or a fresh one. */
    private void loadCurrent() {
        value = FlowValue.open(services);
        Flow flow = readFlow();
        FlowLayout.Layout layout = FlowLayout.read(resourcesDir);
        boolean anyPlaced = false;
        for (Flow.Activity a : flow.activities()) {
            FlowLayout.Spot placed = layout.spot(a.name());
            anyPlaced |= placed != null;
            Point2D at = placed == null ? canvas.nextFreeSpot() : new Point2D(placed.x(), placed.y());
            canvas.add(ActivityDraft.of(a, at.getX(), at.getY()));
        }
        // Only when nothing at all was placed: one saved position is enough to mean someone laid this out.
        // With the sidecar gitignored this is now the ordinary state of a fresh clone, rather than the state
        // of a flow nobody has opened — which is exactly why auto-arrange has to be good enough to land on.
        arrangeOnOpen = !anyPlaced && !flow.activities().isEmpty();
        canvas.edges().setAll(flow.edges());
        canvas.setStart(flow.start());
        maxSteps = flow.limits().maxSteps();
        stepDelayMs = flow.limits().stepDelayMs();
        goHomeByDefault = layout.goHomeByDefault();
        canvas.select(null);
        presets.addAll(flow.presets());
        canvas.refresh();
    }

    /**
     * Reads the stored flow, or says why it cannot be edited and opens empty.
     *
     * <p>Three states, one shape of answer. <b>No value</b> means the project has no {@code Sdk.java} — the
     * SDK was never added, or the file was deleted — and the window opens empty saying so. <b>An expression
     * the plugin did not write</b> means somebody wrote the flow by hand, and the window opens empty,
     * refuses to save, and says which it is; overwriting it is exactly what this whole design exists to
     * avoid. Otherwise there is a flow, and it may perfectly well be {@link Flow#NONE}.
     *
     * <p>The window still opens in every case, because closing it outright leaves the user no way to see
     * what the project has.
     */
    private Flow readFlow() {
        if (value.isEmpty()) {
            readOnly("This project has no Sdk.java with a @Managed(\"flow\") method, so there is nothing to "
                    + "draw into. Add the BotMaker SDK to the project and it will be put there.");
            return Flow.NONE;
        }
        if (!FlowValue.readable(value.get())) {
            readOnly("The flow in Sdk.flow() isn't one this editor wrote, so it is shown empty and left "
                    + "alone. Edit it in Sdk.java, or replace it with Flow.of(…) to draw it here.");
            return Flow.NONE;
        }
        return FlowValue.read(value.get());
    }

    /** Records why the flow cannot be written, and says it once. */
    private void readOnly(String reason) {
        readOnlyReason = reason;
        error(reason);
    }

    // --- top bar: presets + add activity ---

    private Node buildTopBar() {
        presetCombo.setPromptText("Preset…");
        presetCombo.setPrefWidth(180);
        presetCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Flow.Preset preset) { return preset == null ? "" : preset.name(); }
            @Override public Flow.Preset fromString(String s) { return null; } // display-only
        });
        refreshPresetCombo();

        Button applyPreset = new Button("Apply");
        applyPreset.setOnAction(e -> {
            Flow.Preset preset = presetCombo.getValue();
            if (preset == null) { error("Pick a preset first."); return; }
            // One step, not one per activity: a preset flips every switch at once, and taking that back should
            // be a single ↶ rather than a dozen.
            canvas.mutate("apply preset " + preset.name(), () -> {
                for (ActivityDraft d : canvas.drafts()) d.enabledProperty().set(preset.enables(d.name()));
            });
            error("");
        });

        Button savePreset = new Button("Save selection as preset…");
        savePreset.setOnAction(e -> saveCurrentSelectionAsPreset());

        Button addActivity = new Button("Add activity");
        addActivity.setTooltip(new javafx.scene.control.Tooltip(
                "Name it and declare its outcomes up front. Double-clicking empty canvas opens the same "
                        + "dialog, and drops the card where you clicked."));
        addActivity.setOnAction(e -> createActivityAt(canvas.nextFreeSpot()));

        Button recenter = new Button("⌖ Recenter");
        recenter.setTooltip(new javafx.scene.control.Tooltip("Reset the zoom and scroll back to the cards"));
        recenter.setOnAction(e -> canvas.recenter());

        Button arrange = new Button("⇄ Auto-arrange");
        arrange.setTooltip(new javafx.scene.control.Tooltip(
                "Lay the cards out in layers, by how many steps they are from the start, with anything "
                        + "unreachable below"));
        arrange.setOnAction(e -> canvas.autoArrange());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(8, new Label("Presets:"), presetCombo, applyPreset, savePreset,
                new Separator(javafx.geometry.Orientation.VERTICAL), undoButton(), redoButton(),
                new Separator(javafx.geometry.Orientation.VERTICAL), recenter, arrange,
                spacer, addActivity);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10));
        return bar;
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
        Optional<ActivityDraft> made = new NewActivityDialog(stage, services.theme(), placedNames(),
                goHomeByDefault).showAt(at.getX(), at.getY());
        if (made.isEmpty()) return;
        canvas.add(made.get());
        refreshPresetCombo();   // the built-in "Everything" preset is derived from what's on the canvas
        error("");
    }

    private Set<String> placedNames() {
        Set<String> names = new HashSet<>();
        for (ActivityDraft d : canvas.drafts()) names.add(d.name());
        return names;
    }

    private void saveCurrentSelectionAsPreset() {
        TextInputDialog prompt = new TextInputDialog();
        services.theme().apply(prompt);
        prompt.initOwner(stage);
        prompt.setTitle("Save preset");
        prompt.setHeaderText("Name this selection of activities");
        prompt.setContentText("Preset name:");
        Optional<String> chosen = prompt.showAndWait();
        if (chosen.isEmpty()) return;

        String name = chosen.get().trim();
        if (name.isEmpty()) { error("A preset needs a name."); return; }

        List<String> on = new ArrayList<>();
        for (ActivityDraft d : canvas.drafts()) {
            if (d.enabled()) on.add(d.name());
        }
        presets.removeIf(p -> p.name().equals(name)); // re-saving a name overwrites it
        presets.add(Flow.preset(name, on));
        refreshPresetCombo();
        presetCombo.getSelectionModel().select(presets.size() - 1);
        error("");
        markDirty();
    }

    /**
     * The built-in presets plus the user's saved ones — built-ins are derived from what's on the canvas.
     *
     * <p>The two built-ins are built here rather than stored, which is what keeps them true: "Everything"
     * over a canvas that has gained a card means the new card too, and a stored copy of the list would mean
     * every card except the new one.
     */
    private void refreshPresetCombo() {
        List<String> all = new ArrayList<>();
        for (ActivityDraft d : canvas.drafts()) all.add(d.name());
        List<Flow.Preset> items = new ArrayList<>();
        items.add(Flow.preset(EVERYTHING, all));
        items.add(Flow.preset(NOTHING, List.of()));
        items.addAll(presets);
        presetCombo.getItems().setAll(items);
    }

    /** The two presets the editor offers over whatever is on the canvas; never saved into the flow. */
    private static final String EVERYTHING = "Everything";
    private static final String NOTHING = "Nothing";

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
            sidePanel.getChildren().addAll(new Separator(), buildFlowLimitsSection());
            return;
        }

        TextField name = new TextField(draft.name());
        name.focusedProperty().addListener((o, was, is) -> {
            if (is) return;
            String candidate = name.getText() == null ? "" : name.getText().trim();
            renameDraft(draft, candidate, name);
        });
        TextField description = new TextField(draft.description());
        description.textProperty().addListener((o, was, is) -> {
            draft.descriptionProperty().set(is);
            markDirty();
        });

        CheckBox goHome = new CheckBox("Go home first");
        goHome.selectedProperty().bindBidirectional(draft.goHomeProperty());
        // The canvas records what it owns — position, wiring, the enable switch. These three live on the draft
        // and nowhere else, so they ask for the save themselves. They are not undoable, which is the honest
        // answer for a text field and a tick you can simply set back.
        goHome.selectedProperty().addListener((o, was, is) -> markDirty());
        goHome.setTooltip(new javafx.scene.control.Tooltip(
                "Call GoHome.run() immediately before this activity, so it starts from a known screen. Same "
                        + "tick as the ⌂ on the card."));

        CheckBox popupCheck = new CheckBox("Check for popups");
        popupCheck.selectedProperty().bindBidirectional(draft.popupCheckProperty());
        popupCheck.selectedProperty().addListener((o, was, is) -> markDirty());
        popupCheck.setTooltip(new javafx.scene.control.Tooltip(
                "Let Popups.run() dismiss popups before each vision step of this activity. Turn it off for an "
                        + "activity that works through a popup itself — otherwise the guard closes it "
                        + "underneath."));

        TextField body = new TextField(draft.body());
        body.setPromptText("Collect::body");
        body.setTooltip(new javafx.scene.control.Tooltip(
                "The method that does this activity's work, as a method reference. Leave it blank and the "
                        + "card is a placeholder: the flow walks through it and it does nothing."));
        body.focusedProperty().addListener((o, was, is) -> {
            if (is) return;
            commitBody(draft, body);
        });
        body.setOnAction(e -> {
            commitBody(draft, body);
            e.consume();
        });

        GridPane head = new GridPane();
        head.setHgap(8);
        head.setVgap(6);
        head.addRow(0, new Label("Name"), name);
        head.addRow(1, new Label("Runs"), body);
        head.addRow(2, new Label("Description"), description);
        head.add(goHome, 1, 3);
        head.add(popupCheck, 1, 4);
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
     * The outcome list for one activity: what it can report having happened, one card port each.
     *
     * <p>Deliberately says nothing about <em>where</em> an outcome goes — that is the canvas's job. Keeping
     * the two apart is the whole reason an activity's code never names another activity.
     */
    private Node buildOutcomeEditor(ActivityDraft draft) {
        VBox box = new VBox(6);

        Label explain = new Label("What this activity can report. Return one from its run() method and wire "
                + "each one on the canvas. Every activity also has a NEXT outcome, and any outcome "
                + "you leave unwired ends the run.");
        explain.setWrapText(true);
        explain.getStyleClass().add("dialog-hint-text");
        box.getChildren().add(explain);

        for (String outcome : List.copyOf(draft.outcomes())) {
            TextField field = new TextField(outcome);
            field.focusedProperty().addListener((o, was, is) -> {
                if (is) return;
                renameOutcome(draft, outcome, field.getText(), field);
            });
            // Enter has to commit here too: it is the obvious way to finish a rename, and without a consuming
            // handler it would reach the default button and close the dialog with the old name still set.
            field.setOnAction(e -> {
                renameOutcome(draft, outcome, field.getText(), field);
                e.consume();
            });
            Button remove = new Button("✕");
            remove.setTooltip(new javafx.scene.control.Tooltip(
                    "Remove this outcome. Any wire leaving it is removed too."));
            remove.setOnAction(e -> {
                draft.outcomes().remove(outcome);
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
            String candidate = FlowNames.normalizeOutcome(newOutcome.getText());
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

    /** Renames an outcome, carrying its wire across — as renaming an activity carries its wires. */
    private void renameOutcome(ActivityDraft draft, String oldName, String typed, TextField field) {
        String candidate = FlowNames.normalizeOutcome(typed);
        if (candidate.equals(oldName)) {
            field.setText(oldName);   // normalisation may have changed the text without changing the outcome
            return;
        }
        String problem = FlowNames.outcomeProblem(draft.outcomes(), draft.name(), candidate, oldName);
        if (problem != null) {
            error(problem);
            field.setText(oldName);
            return;
        }
        int at = draft.outcomes().indexOf(oldName);
        if (at < 0) return;
        draft.outcomes().set(at, candidate);
        field.setText(candidate);
        List<Flow.Edge> rewired = new ArrayList<>(canvas.edges().size());
        for (Flow.Edge e : canvas.edges()) {
            boolean mine = e.from().equals(draft.name()) && e.outcomeOrNext().equals(oldName);
            rewired.add(mine ? new Flow.Edge(e.from(), e.to(), candidate) : e);
        }
        canvas.edges().setAll(rewired);
        error("");
        canvas.refresh();
    }

    /**
     * Removes {@code draft} from the flow for good — the card and its wires.
     *
     * <p>This replaced "Archive activity", which promised a reversible retirement and could not deliver one.
     * A user who wants to stop an activity running without losing it turns its <em>enable flag</em> off —
     * that is what the flag is for, it survives everything, and it needs no second mechanism.
     *
     * <p>It asks first, because the wiring goes with it. What it does <b>not</b> touch is the user's own Java:
     * an activity's behaviour is a {@code define("Mining", …)} call in a file this editor has never known the
     * location of, and deleting somebody's source because a card left a canvas is not this window's to do.
     */
    private void deleteActivity(ActivityDraft draft) {
        Alert confirm = services.theme().alert(Alert.AlertType.CONFIRMATION,
                "Its card and every wire into or out of it are removed when you save. The file you wrote its "
                        + "steps in is left exactly as it is.\n\n"
                        + "To stop it running without losing it, turn its switch off instead.",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(stage);
        confirm.setTitle("Delete activity");
        confirm.setHeaderText("Delete “" + draft.name() + "”?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        canvas.remove(draft); // also drops any wires into or out of it
        refreshPresetCombo();
        error("");
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
     * Points {@code draft} at the method that does its work, or says why the text is not one.
     *
     * <p>Checked here and not only on save, because it is the one field in this window whose value is
     * <em>code</em>: what is typed goes into the bot's own Java verbatim, and a form with a space in it
     * would be a file that does not compile. Blank is legal and means the card is a placeholder.
     *
     * <p>What is <b>not</b> checked is whether the class and method exist. This editor has no classpath for
     * the bot it is drawing, and javac says so in the user's own file, on the line, far better than a
     * dialog could.
     */
    private void commitBody(ActivityDraft draft, TextField field) {
        String candidate = field.getText() == null ? "" : field.getText().trim();
        if (candidate.equals(draft.body())) return;
        if (!candidate.isEmpty() && !FlowNames.isMethodReference(candidate)) {
            error("“Runs” has to be a method reference like Collect::body — reverted.");
            field.setText(draft.body());
            return;
        }
        draft.setBody(candidate);
        field.setText(candidate);
        error("");
        markDirty();
    }

    private void renameDraft(ActivityDraft draft, String candidate, TextField field) {
        if (candidate.equals(draft.name())) return;
        if (!FlowNames.isValidIdentifier(candidate)) {
            error("Invalid activity name — reverted.");
            field.setText(draft.name());
            return;
        }
        if (canvas.drafts().stream().anyMatch(d -> d != draft && d.name().equals(candidate))) {
            error("Activity '" + candidate + "' already exists — reverted.");
            field.setText(draft.name());
            return;
        }
        draft.nameProperty().set(candidate); // the card re-labels and its wires follow the new name
        refreshPresetCombo();
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

    /** The flow as it currently stands, ready to be written into the bot's Java. */
    private Flow currentFlow() {
        List<Flow.Activity> activities = new ArrayList<>();
        for (ActivityDraft d : canvas.drafts()) activities.add(d.toActivity());
        return Flow.of(activities, List.copyOf(canvas.edges()), List.copyOf(presets), canvas.start(),
                Flow.limits(maxSteps, stepDelayMs));
    }

    /** Where every card sits, plus the one editor preference that rides along with them. */
    private FlowLayout.Layout currentLayout() {
        Map<String, FlowLayout.Spot> spots = new LinkedHashMap<>();
        for (ActivityDraft d : canvas.drafts()) {
            spots.put(d.name(), new FlowLayout.Spot(d.x(), d.y()));
        }
        return new FlowLayout.Layout(spots, goHomeByDefault);
    }

    /**
     * Writes the flow, if there is anything to write and nothing already in flight.
     *
     * <p><b>On the FX thread</b>: the flow is one expression handed to the host, which is what every slot
     * editor on the canvas does on every keystroke and which
     * {@link com.botmaker.plugin.api.slot.ValueContext#set} requires. The layout sidecar goes with it rather than
     * behind it, so a save is one thing that either happened or did not.
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
            releaseClose();
            return;
        }
        Flow flow = currentFlow();
        String problem = validate(flow);
        if (problem != null) {
            // Refused, not failed — a duplicate name or a bad identifier. Stay dirty so the fix saves it, and
            // let go of any pending close: closing now would leave the edit only in the window.
            error(problem);
            savedLabel.setText("Not saved");
            releaseClose();
            return;
        }
        error("");
        dirty = false;
        saving = true;
        progress.setVisible(true);
        savedLabel.setText("Saving…");

        String refused = FlowValue.write(value.orElse(null), flow);
        Throwable failure = null;
        if (refused == null) {
            try {
                // After the flow, never before: a layout for a card the saved flow does not have is the one
                // way round that is merely untidy.
                FlowLayout.write(resourcesDir, currentLayout());
            } catch (Exception | LinkageError e) {
                failure = e;
            }
        }
        saved(refused, failure);
    }

    /** What happens once a write has landed — or has not. */
    private void saved(String refused, Throwable err) {
        saving = false;
        progress.setVisible(false);
        if (refused != null) {
            dirty = true;   // the next edit, or Close, tries again
            error(refused);
            savedLabel.setText("Not saved");
            releaseClose();
            return;
        }
        if (err != null) {
            // The flow itself is written; only the positions are not. Saying so rather than "not saved"
            // matters, because the two have very different consequences for what is lost.
            error("The flow was saved. The card positions weren't: " + rootMessage(err));
            savedLabel.setText("Saved");
        } else {
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
     * unique valid identifiers within one namespace; the flag is now {@link Flow.Activity#enabled()} and a
     * variable is a {@code @Param} field of the user's own class, so neither of them generates a field and
     * javac is what has an opinion about the names. What is left checks the names this editor is still the
     * author of.
     */
    public static String validate(Flow flow) {
        Set<String> actNames = new HashSet<>();
        Set<String> registryFields = new HashSet<>();
        for (Flow.Activity a : flow.activities()) {
            if (!FlowNames.isValidIdentifier(a.name())) return "Invalid activity name: '" + a.name() + "'.";
            if (!actNames.add(a.name())) return "Duplicate activity name: '" + a.name() + "'.";
            // Two activities differing only in case is a project whose stub files collide on a
            // case-insensitive filesystem, and a canvas on which two cards read the same. Neither is worth
            // letting through for the sake of a distinction only Linux can see.
            if (!registryFields.add(a.name().toUpperCase())) {
                return "'" + a.name() + "' clashes with another activity whose name differs only in case.";
            }
            // Checked against the declared list, not allOutcomes(): that one de-duplicates defensively, so
            // validating it would report a clash as clean and leave the user with an outcome that silently
            // has no port.
            Set<String> outcomeNames = new HashSet<>();
            outcomeNames.add(Flow.Edge.NEXT);
            outcomeNames.add(Flow.Edge.DISABLED);
            for (String outcome : a.outcomes()) {
                if (!FlowNames.isValidIdentifier(outcome)) {
                    return "Invalid outcome in " + a.name() + ": '" + outcome + "'.";
                }
                if (!outcomeNames.add(outcome)) {
                    if (Flow.Edge.NEXT.equals(outcome)) {
                        return a.name() + " already has a NEXT outcome — every activity does.";
                    }
                    if (Flow.Edge.DISABLED.equals(outcome)) {
                        return a.name() + " can't declare a DISABLED outcome — that port is always there, "
                                + "and an activity can't report it because it didn't run.";
                    }
                    return "Duplicate outcome '" + outcome + "' in " + a.name() + ".";
                }
            }
        }
        return null;
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
