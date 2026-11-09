package com.botmaker.sdk.plugin.plan;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.TraceLine;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.api.source.RunParameter;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.graph.GraphCanvas;
import com.botmaker.plugin.toolkit.graph.GraphCanvas.Arrow;
import com.botmaker.plugin.toolkit.graph.LayeredLayout;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.internal.plan.DoneRecords;
import com.botmaker.sdk.internal.plan.PlanModel;
import com.botmaker.sdk.internal.plan.PlanValues;
import com.botmaker.sdk.internal.plan.StudioPlan;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenGraph.Edge;
import com.botmaker.sdk.plugin.assist.MapAudit;
import com.botmaker.sdk.plugin.assist.ScreenEdits;
import com.botmaker.sdk.plugin.map.ScreenMap;
import com.botmaker.sdk.plugin.pictures.ScreenMapRead;
import com.botmaker.sdk.plugin.plan.GoalsViewModel.Card;
import com.botmaker.sdk.plugin.plan.GoalsViewModel.Mark;
import com.botmaker.sdk.plugin.plan.GoalsViewModel.Route;
import com.botmaker.sdk.plugin.plan.GoalsViewModel.Row;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import javax.lang.model.SourceVersion;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 🎯 Goals (2026-11-06, layout A): the goals of a plan, each a card listing the actions that serve it in the order
 * {@link com.botmaker.sdk.internal.plan.Planner#round} would try them; beside them the route of the picked row, only
 * the screens a walk crosses, numbered. A double click opens the action's screen in 🗺 Screens; a row dragged onto
 * another is tried before it ({@code .over}), onto ★ or ⤓ first or last. During a run the action it is on is marked.
 * Above the cards, the plan's tiers as rows of goal chips (met struck, waiting ⏳ with its due time): a chip dragged onto
 * a tier moves there, onto ＋ into a tier of its own; {@code + Goal} adds a goal from a {@code Need} over a fact,
 * {@code + Plan} a plan of one tier (2026-11-06).
 * What it shows is read from the bot's Java and its done-records, so nothing here runs the game.
 */
public final class GoalsView {

    private static final String SHEET = Objects.requireNonNull(GoalsView.class.getResource("goals.css"))
            .toExternalForm();
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass RUNNING = PseudoClass.getPseudoClass("running");
    private static final PseudoClass OFF = PseudoClass.getPseudoClass("off");
    private static final PseudoClass TARGET = PseudoClass.getPseudoClass("target");
    private static final PseudoClass ACTION = PseudoClass.getPseudoClass("action");
    private static final PseudoClass MET = PseudoClass.getPseudoClass("met");
    private static final PseudoClass WAITING = PseudoClass.getPseudoClass("waiting");
    /** What a dragged goal chip carries before its name; a dragged row carries {@code SCREEN.field}. */
    private static final String GOAL_DRAG = "goal:";
    /** One window a project, as 🗺 Screens has. */
    private static final Map<Path, GoalsView> OPEN = new HashMap<>();

    private final StudioServices services;
    private final BorderPane root = new BorderPane();
    private final ChoiceBox<String> plans = new ChoiceBox<>();
    private final VBox cards = new VBox();
    private final VBox tiers = new VBox();
    private final ChoiceBox<String> factPick = new ChoiceBox<>();
    private final ChoiceBox<PlanEdits.Shortcut> needPick = new ChoiceBox<>();
    private final TextField limitText = new TextField();
    private final TextField planName = new TextField();
    private final ChoiceBox<String> planGoal = new ChoiceBox<>();
    private final Button addPlan = new Button("+ Plan");
    /** Around {@link #addPlan}: a disabled button shows no tooltip, so the one saying why it waits is on this. */
    private final StackPane addPlanBox = new StackPane(addPlan);
    private final Tooltip addPlanTip = new Tooltip();
    private final Label said = new Label();
    private final Label warnings = new Label();
    private final GraphCanvas<Stop, Edge> route = new GraphCanvas<>(Stop::screen, this::stopView, GoalsView::arrow,
            (laid, links) -> LayeredLayout.arrange(laid, links, List.of()));
    private final Map<String, List<HBox>> rows = new HashMap<>();
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private GoalsViewModel view;
    /** What the last read found: the names {@code + Goal} and {@code + Plan} must not take. */
    private Read last = Read.none("");
    /** While the plan pick is set from here, so it reads nothing itself. */
    private boolean choosing;
    private String picked;
    private String running;
    private Stage window;

    /** One screen of a route: its place in it, and the action's field on the last. */
    record Stop(int number, String screen, String action) {}

    private GoalsView(StudioServices services) {
        this.services = services;
        root.getStylesheets().add(SHEET);
        root.getStyleClass().add("goals-view");
        cards.getStyleClass().add("goals-cards");
        said.getStyleClass().add("goals-refused");
        said.setManaged(false);
        said.setVisible(false);
        warnings.getStyleClass().add("goals-warnings");
        warnings.setManaged(false);
        warnings.setVisible(false);
        Button again = new Button("⟳ Read again");
        again.setOnAction(e -> read());
        plans.setOnAction(e -> {
            if (!choosing) read();
        });
        HBox bar = new HBox(new Label("Plan"), plans, again);
        bar.getStyleClass().add("goals-bar");
        tiers.getStyleClass().add("plan-tiers");
        Node adding = adding();
        adding.getStyleClass().add("plan-adding");
        ScrollPane left = new ScrollPane(new VBox(said, warnings, tiers, adding, cards));
        left.setFitToWidth(true);
        SplitPane split = new SplitPane(left, route);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.55);
        root.setTop(bar);
        root.setCenter(split);
    }

    /** The 🎯 Goals press: the goals of the bot's first plan, in a window of their own. */
    public static void open(ActionContext context) {
        open(context.services());
    }

    /** The view in its window; asked again, the open one comes to the front and reads the bot again. */
    public static void open(StudioServices services) {
        Path project = services.projectDir();
        GoalsView open = project == null ? null : OPEN.get(project);
        if (open != null && open.window != null) {
            open.window.toFront();
            open.read();
            return;
        }
        GoalsView goals = new GoalsView(services);
        Stage stage = Modals.window(services, Modals.owner(services),
                Modals.Frame.modeless("Goals", 1100, 680, 700, 420), goals.root);
        goals.window = stage;
        if (project != null) OPEN.put(project, goals);
        stage.setOnHidden(e -> {
            goals.stop();
            if (project != null) OPEN.remove(project, goals);
        });
        stage.show();
        goals.subscriptions.add(services.runs().onTrace(goals::traced));
        goals.subscriptions.add(services.runs().onStateChanged(on -> Platform.runLater(() -> goals.runningOn(null))));
        goals.read();
    }

    private void stop() {
        for (AutoCloseable each : subscriptions) {
            try {
                each.close();
            } catch (Exception ignored) {
                // a handle that fails to close leaves a listener feeding a window nobody shows
            }
        }
        subscriptions.clear();
    }

    /** Reads the plans, the goals and the map off the FX thread, then draws the plan picked. */
    private void read() {
        read(null);
    }

    /** {@link #read()}, then {@code after}, a write's refusal, shown over what the read says; null for none. */
    private void read(String after) {
        String wanted = plans.getValue();
        CompletableFuture.supplyAsync(() -> Read.of(services, wanted)).whenComplete((read, failed) ->
                Platform.runLater(() -> {
                    if (failed != null) {
                        refuse("The bot could not be read: " + failed.getMessage());
                        return;
                    }
                    draw(read);
                    if (after != null) refuse(after);
                }));
    }

    /**
     * What one read found: every plan's name that reads, the one shown and its view, or why there is none; the name of
     * every constant of the bot's {@code Plans} and {@code Goals}, read or not, and its facts, for {@code + Plan} and
     * {@code + Goal}; and what the goals and plans ask that a run cannot do ({@link MapAudit#planWarnings}).
     * {@code readGoals} are the goals whose value reads, which {@code + Plan} offers; {@code planWaits} says why
     * {@code + Plan} cannot start a plan, null when it can.
     */
    private record Read(List<String> plans, String shown, GoalsViewModel view, String refused, List<String> goals,
                        List<String> planNames, List<RunParameter> facts, List<String> warnings,
                        List<String> readGoals, String planWaits) {

        static Read none(String why) {
            return new Read(List.of(), null, null, why, List.of(), List.of(), List.of(), List.of(), List.of(), why);
        }

        static Read of(StudioServices services, String wanted) {
            PluginValues values = services.pluginValues();
            if (values == null) return none("Studio has no source tree open.");
            Map<String, Plan> plans = new LinkedHashMap<>();
            List<String> planNames = new ArrayList<>();
            for (Member member : values.members(SdkValues.PLANS)) {
                planNames.add(member.name());
                values.open(SdkValues.PLANS, member).flatMap(ctx -> ctx.value(Plan.class))
                        .ifPresent(plan -> plans.put(member.name(), plan));
            }
            Map<String, Goal> goals = new LinkedHashMap<>();
            List<String> goalNames = new ArrayList<>();
            for (Member member : values.members(SdkValues.GOALS)) {
                goalNames.add(member.name());
                values.open(SdkValues.GOALS, member).flatMap(ctx -> ctx.value(Goal.class))
                        .ifPresent(goal -> goals.put(member.name(), goal));
            }
            List<RunParameter> facts = values.runParameters().stream()
                    .filter(fact -> !PlanEdits.shortcuts(fact.type()).isEmpty()).toList();
            ScreenGraph map = ScreenMapRead.read(services);
            List<String> warnings = MapAudit.planWarnings(plans, goals, map);
            List<String> readGoals = List.copyOf(goals.keySet());
            String waits = PlanEdits.planWaits(readGoals, !facts.isEmpty()).orElse(null);
            if (plans.isEmpty()) {
                return new Read(List.of(), null, null,
                        PlanEdits.noPlanYet(planNames, !readGoals.isEmpty(), !facts.isEmpty()),
                        List.copyOf(goalNames), List.copyOf(planNames), facts, warnings, readGoals, waits);
            }
            String shown = plans.containsKey(wanted) ? wanted : plans.keySet().iterator().next();
            StudioPlan plan = StudioPlan.of(plans.get(shown), goals, map);
            // Timed in the game server's zone, as a run is, so a daily's reset reads the same here.
            Clock clock = Clock.system(InstalledGame.zone(values.open(SdkValues.GAME_ZONE)
                    .flatMap(ctx -> ctx.value(String.class)).orElse("")));
            DoneRecords records = services.stateDir().map(dir -> DoneRecords.in(dir, clock))
                    .orElseGet(() -> new DoneRecords(null, clock));
            List<String> names = List.copyOf(plans.keySet());
            try {
                PlanModel model = new PlanModel(plan.plan(), plan.map(), records, null);
                return new Read(names, shown, GoalsViewModel.of(model, plan), null, List.copyOf(goalNames),
                        List.copyOf(planNames), facts, warnings, readGoals, waits);
            } catch (IllegalArgumentException cycle) {
                // The cycle is the refusal: said once, not again among the warnings, which found it on their own read.
                Optional<String> found = PlanModel.overCycle(StudioPlan.of(PlanModel.NO_PLAN, goals, map).map());
                return new Read(names, shown, null, cycle.getMessage(), List.copyOf(goalNames),
                        List.copyOf(planNames), facts, warnings.stream()
                        .filter(w -> found.map(f -> !f.equals(w)).orElse(true)).toList(), readGoals, waits);
            }
        }
    }

    private void draw(Read read) {
        if (!plans.getItems().equals(read.plans())) plans.getItems().setAll(read.plans());
        if (read.shown() != null && !read.shown().equals(plans.getValue())) choosePlan(read.shown());
        last = read;
        view = read.view();
        cards.getChildren().clear();
        rows.clear();
        offer(factPick, read.facts().stream().map(GoalsView::factName).toList(), false);
        offer(planGoal, read.readGoals(), true);
        addPlan.setDisable(read.planWaits() != null);
        addPlanTip.setText(read.planWaits() != null ? read.planWaits() : PlanEdits.PLAN_HELP);
        warnings.setText(String.join("\n", read.warnings().stream().map(w -> "⚠ " + w).toList()));
        warnings.setManaged(!read.warnings().isEmpty());
        warnings.setVisible(!read.warnings().isEmpty());
        if (view == null) {
            tiers.getChildren().clear();
            refuse(read.refused());
            route.show(List.of(), List.of());
            return;
        }
        refuse(null);
        drawTiers(PlanViewModel.of(view, ZoneId.systemDefault()));
        for (Card card : view.cards()) cards.getChildren().add(card(card));
        Optional<Row> keep = picked == null ? Optional.empty() : view.row(picked);
        Optional<Row> first = keep.or(() -> view.cards().stream().flatMap(c -> c.rows().stream()).findFirst());
        first.ifPresentOrElse(this::pick, () -> route.show(List.of(), List.of()));
        runningOn(running);
    }

    private void refuse(String why) {
        said.setText(why == null ? "" : why);
        said.setManaged(why != null);
        said.setVisible(why != null);
    }

    // ---- the plan's tiers (2026-11-06): goal chips, dragged between tiers or into a tier of their own ----------

    private void drawTiers(PlanViewModel plan) {
        tiers.getChildren().setAll(newTier("＋ new tier first", 0));
        for (PlanViewModel.Tier tier : plan.tiers()) {
            Label name = new Label("tier " + (tier.index() + 1));
            name.getStyleClass().add("goals-fill");
            HBox line = new HBox(6, name);
            line.getStyleClass().add("plan-tier");
            line.setAlignment(Pos.CENTER_LEFT);
            for (PlanViewModel.Chip chip : tier.chips()) line.getChildren().add(chip(chip));
            Region space = new Region();
            HBox.setHgrow(space, Priority.ALWAYS);
            line.getChildren().addAll(space, newTier("＋ new tier below", tier.index() + 1));
            acceptGoal(line, goal -> tierEdit(goal, tier.index(), false));
            tiers.getChildren().add(line);
        }
    }

    private Node chip(PlanViewModel.Chip chip) {
        Label name = new Label(chip.name());
        name.getStyleClass().add("plan-chip-name");
        Label said = new Label(chip.said());
        said.getStyleClass().add("goals-fill");
        HBox box = new HBox(4, name, said);
        box.getStyleClass().add("plan-chip");
        box.pseudoClassStateChanged(MET, chip.state() == PlanViewModel.State.MET);
        box.pseudoClassStateChanged(WAITING, chip.state() == PlanViewModel.State.WAITING);
        // A goal written inline in the tier names no constant of Goals: there is nothing to move it by.
        if (!SourceVersion.isName(chip.name())) return box;
        box.setOnDragDetected(e -> {
            Dragboard board = box.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(GOAL_DRAG + chip.name());
            board.setContent(content);
            e.consume();
        });
        return box;
    }

    /** A pill a goal chip is dropped on to give it a tier of its own at {@code at}. */
    private Node newTier(String text, int at) {
        Label pill = new Label(text);
        pill.getStyleClass().add("goals-drop");
        acceptGoal(pill, goal -> tierEdit(goal, at, true));
        return pill;
    }

    /** Lets {@code target} take a dragged goal chip, and only that, and hands its goal's name to {@code dropped}. */
    private static void acceptGoal(Node target, Consumer<String> dropped) {
        target.setOnDragOver(e -> {
            if (isGoal(e.getDragboard())) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        target.setOnDragEntered(e -> target.pseudoClassStateChanged(TARGET, isGoal(e.getDragboard())));
        target.setOnDragExited(e -> target.pseudoClassStateChanged(TARGET, false));
        target.setOnDragDropped(e -> {
            boolean taken = isGoal(e.getDragboard());
            if (taken) dropped.accept(e.getDragboard().getString().substring(GOAL_DRAG.length()));
            e.setDropCompleted(taken);
            e.consume();
        });
    }

    private static boolean isGoal(Dragboard board) {
        return board.hasString() && board.getString().startsWith(GOAL_DRAG);
    }

    /** A dragged action row, {@code SCREEN.field}: any string but a goal chip's. */
    private static boolean isRow(Dragboard board) {
        return board.hasString() && !isGoal(board);
    }

    private void tierEdit(String goal, int to, boolean newTier) {
        String plan = plans.getValue();
        if (plan == null) return;
        shown(PlanWrites.setTier(services, plan, goal, to, newTier));
    }

    /** Reads the bot again after a write, then shows the write's refusal, if it was one, over what the read says. */
    private void shown(String answer) {
        read(answer.startsWith(ScreenEdits.NOT_WRITTEN) ? answer : null);
    }

    /** {@code + Goal} and {@code + Plan}, built once: a read refreshes what they offer, never what was typed. */
    private Node adding() {
        needPick.setConverter(new StringConverter<>() {
            @Override
            public String toString(PlanEdits.Shortcut s) {
                return s == null ? "" : s.label();
            }

            @Override
            public PlanEdits.Shortcut fromString(String text) {
                return null;
            }
        });
        limitText.setPromptText("limit");
        limitText.setPrefColumnCount(6);
        factPick.setOnAction(e -> {
            RunParameter picked = pickedFact().orElse(null);
            needPick.getItems().setAll(picked == null ? List.of() : PlanEdits.shortcuts(picked.type()));
            if (!needPick.getItems().isEmpty()) needPick.setValue(needPick.getItems().getFirst());
        });
        needPick.setOnAction(e -> limitText.setDisable(needPick.getValue() == null
                || !needPick.getValue().takesLimit()));
        Button addGoal = new Button("+ Goal");
        addGoal.setOnAction(e -> addGoal());
        HBox goal = new HBox(6, new Label("Goal:"), factPick, needPick, limitText, addGoal);
        goal.setAlignment(Pos.CENTER_LEFT);
        planName.setPromptText("PLAN_NAME");
        planName.setPrefColumnCount(10);
        addPlan.setOnAction(e -> addPlan());
        Tooltip.install(addPlanBox, addPlanTip);
        HBox plan = new HBox(6, new Label("Plan:"), planName, planGoal, addPlanBox);
        plan.setAlignment(Pos.CENTER_LEFT);
        return new VBox(4, goal, plan);
    }

    /**
     * {@code choice} offering {@code items}, its pick kept while still offered; else the first when
     * {@code pickFirst}, or none.
     */
    private static <T> void offer(ChoiceBox<T> choice, List<T> items, boolean pickFirst) {
        T was = choice.getValue();
        if (!choice.getItems().equals(items)) choice.getItems().setAll(items);
        if (was != null && items.contains(was)) {
            if (!was.equals(choice.getValue())) choice.setValue(was);
        } else if (pickFirst && !items.isEmpty()) {
            choice.setValue(items.getFirst());
        }
    }

    /** {@code + Goal}: the fact, the {@code Need} shortcut its type takes and a limit, named after them. */
    private void addGoal() {
        RunParameter picked = pickedFact().orElse(null);
        PlanEdits.Shortcut need = needPick.getValue();
        if (picked == null || need == null) {
            refuse(ScreenEdits.NOT_WRITTEN + "pick a fact and what it needs.");
            return;
        }
        Optional<Object> bound = need.takesLimit() ? PlanEdits.limit(picked.type(), limitText.getText())
                : Optional.empty();
        if (need.takesLimit() && bound.isEmpty()) {
            refuse(ScreenEdits.NOT_WRITTEN + "\"" + limitText.getText() + "\" is no " + picked.type() + ".");
            return;
        }
        String reference = picked.group() + "::" + picked.name();
        Object value = bound.orElse(null);
        String name = PlanEdits.goalName(reference, need, value, Set.copyOf(last.goals()));
        shown(PlanWrites.addGoal(services, name, PlanEdits.fromNeed(reference, need, value)));
    }

    /** The fact picked, {@code Progress::gold}, as the last read found it. */
    private Optional<RunParameter> pickedFact() {
        String picked = factPick.getValue();
        return last.facts().stream().filter(fact -> factName(fact).equals(picked)).findFirst();
    }

    private static String factName(RunParameter fact) {
        return fact.group() + "::" + fact.name();
    }

    /** {@code + Plan}: a name and the goal of its one tier; the new plan is the one shown next. */
    private void addPlan() {
        String constant = planName.getText().strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        if (!SourceVersion.isName(constant) || planGoal.getValue() == null) {
            refuse(ScreenEdits.NOT_WRITTEN + "a plan needs a name and a goal for its first tier.");
            return;
        }
        if (last.planNames().contains(constant)) {
            refuse(ScreenEdits.NOT_WRITTEN + "Plans." + constant + " is there already.");
            return;
        }
        String answer = PlanWrites.addPlan(services, constant, planGoal.getValue());
        if (!answer.startsWith(ScreenEdits.NOT_WRITTEN)) {
            plans.getItems().add(constant);
            choosePlan(constant);
            planName.clear();
        }
        shown(answer);
    }

    /** Shows {@code plan} in the plan pick without its own read: the caller reads. */
    private void choosePlan(String plan) {
        choosing = true;
        try {
            plans.setValue(plan);
        } finally {
            choosing = false;
        }
    }

    private Node card(Card card) {
        Label name = new Label(card.name());
        name.getStyleClass().add("goals-card-name");
        Label tier = new Label("tier " + (card.tier() + 1));
        tier.getStyleClass().add("goals-fill");
        Label fill = new Label(card.fill());
        fill.getStyleClass().add("goals-fill");
        Label met = new Label(switch (card.met()) {
            case YES -> "met";
            case NO -> "not met";
            case UNKNOWN -> "known in a run";
        });
        met.getStyleClass().add(card.met() == GoalsViewModel.Met.YES ? "goals-met" : "goals-unmet");
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        HBox head = new HBox(8, name, tier, fill, space, met, drop("★ first", Mark.FIRST), drop("⤓ last", Mark.LAST),
                drop("· neither", Mark.NONE));
        head.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(head);
        box.getStyleClass().add("goals-card");
        if (card.rows().isEmpty()) {
            Label none = new Label("No action serves it: give one .serves(Goals." + card.name() + ") in Screens.java.");
            none.getStyleClass().add("goals-fill");
            box.getChildren().add(none);
        }
        for (Row row : card.rows()) box.getChildren().add(row(row));
        return box;
    }

    /** A ★/⤓ pill a row is dropped on to put it first or last. */
    private Node drop(String text, Mark mark) {
        Label pill = new Label(text);
        pill.getStyleClass().add("goals-drop");
        acceptDrop(pill, dragged -> write(dragged, action -> PlanWrites.setMark(services, action[0], action[1], mark)));
        return pill;
    }

    private HBox row(Row row) {
        Label mark = new Label(switch (row.mark()) {
            case FIRST -> "★";
            case LAST -> "⤓";
            case NONE -> "·";
        });
        Label name = new Label(row.step().field());
        Label screen = new Label(row.step().screen());
        screen.getStyleClass().add("goals-row-screen");
        Label why = new Label(row.said());
        why.getStyleClass().add("goals-row-why");
        Label walk = new Label(row.crossing() < 0 ? "no way" : row.crossing() + " screens · " + seconds(row.walk()));
        walk.getStyleClass().add("goals-row-walk");
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        HBox line = new HBox(mark, name, screen, why, space, walk);
        line.getStyleClass().add("goals-row");
        line.pseudoClassStateChanged(OFF, !row.possible());
        line.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (e.getClickCount() == 2) ScreenMap.open(services, row.step().screen());
            else pick(row);
        });
        line.setOnDragDetected(e -> {
            Dragboard board = line.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(row.name());
            board.setContent(content);
            e.consume();
        });
        acceptDrop(line, dragged -> {
            if (dragged.equals(row.name())) return;
            write(dragged, action -> PlanWrites.setOver(services, action[0], action[1], row.step().screen(),
                    row.step().field()));
        });
        rows.computeIfAbsent(row.name(), n -> new ArrayList<>()).add(line);
        return line;
    }

    /** Lets {@code target} take a dragged row, the action's {@code SCREEN.field}, and hands it to {@code dropped}. */
    private static void acceptDrop(Node target, Consumer<String> dropped) {
        target.setOnDragOver(e -> {
            if (isRow(e.getDragboard())) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        target.setOnDragEntered(e -> target.pseudoClassStateChanged(TARGET, true));
        target.setOnDragExited(e -> target.pseudoClassStateChanged(TARGET, false));
        target.setOnDragDropped(e -> {
            boolean taken = isRow(e.getDragboard());
            if (taken) dropped.accept(e.getDragboard().getString());
            e.setDropCompleted(taken);
            e.consume();
        });
    }

    /** Runs {@code edit} on the dragged action, split into its screen and field, then shows what it said and reads. */
    private void write(String dragged, Function<String[], String> edit) {
        String[] action = dragged.split("\\.", 2);
        if (action.length != 2) return;
        String answer = edit.apply(action);
        picked = dragged;
        shown(answer);
    }

    private void pick(Row row) {
        picked = row.name();
        rows.forEach((name, lines) -> lines.forEach(l -> l.pseudoClassStateChanged(SELECTED, name.equals(picked))));
        Optional<Route> way = view.route(row);
        if (way.isEmpty()) {
            route.show(List.of(new Stop(1, row.step().screen(), row.step().field())), List.of());
            return;
        }
        List<String> screens = way.get().screens();
        List<Stop> stops = new ArrayList<>();
        for (int i = 0; i < screens.size(); i++) {
            stops.add(new Stop(i + 1, screens.get(i), i == screens.size() - 1 ? way.get().action() : null));
        }
        route.show(stops, way.get().edges());
        route.fit();
    }

    private Node stopView(Stop stop) {
        Label number = new Label(stop.number() + ".");
        Label screen = new Label(stop.screen());
        screen.getStyleClass().add("goals-card-name");
        VBox box = new VBox(2, new HBox(6, number, screen));
        if (stop.action() != null) box.getChildren().add(new Label("▶ " + stop.action()));
        box.getStyleClass().add("goals-stop");
        box.pseudoClassStateChanged(ACTION, stop.action() != null);
        return box;
    }

    private static Arrow arrow(Edge edge) {
        Arrow arrow = Arrow.solid(edge.from(), edge.to());
        return edge.input() == null ? arrow : arrow.withLabel(new Label(edge.input()));
    }

    /** A line of the run: the action it is about is the one marked. */
    private void traced(TraceLine line) {
        String action = line.values().get(PlanValues.ACTION);
        if (action != null) Platform.runLater(() -> runningOn(action));
    }

    private void runningOn(String action) {
        running = action;
        rows.forEach((name, lines) -> lines.forEach(l -> l.pseudoClassStateChanged(RUNNING, name.equals(action))));
    }

    private static String seconds(long ms) {
        return ms < 1000 ? ms + " ms" : String.format(Locale.ROOT, "%.1f s", ms / 1000.0);
    }
}
