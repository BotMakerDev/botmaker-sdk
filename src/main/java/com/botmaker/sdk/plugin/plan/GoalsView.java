package com.botmaker.sdk.plugin.plan;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.TraceLine;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.graph.GraphCanvas;
import com.botmaker.plugin.toolkit.graph.GraphCanvas.Arrow;
import com.botmaker.plugin.toolkit.graph.LayeredLayout;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.plan.DoneRecords;
import com.botmaker.sdk.internal.plan.PlanModel;
import com.botmaker.sdk.internal.plan.PlanValues;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenGraph.Edge;
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
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 🎯 Goals (2026-11-06, layout A): the goals of a plan, each a card listing the actions that serve it in the order
 * {@link com.botmaker.sdk.internal.plan.Planner#round} would try them; beside them the route of the picked row, only
 * the screens a walk crosses, numbered. A double click opens the action's screen in 🗺 Screens; a row dragged onto
 * another is tried before it ({@code .over}), onto ★ or ⤓ first or last. During a run the action it is on is marked.
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
    /** One window a project, as 🗺 Screens has. */
    private static final Map<Path, GoalsView> OPEN = new HashMap<>();

    private final StudioServices services;
    private final BorderPane root = new BorderPane();
    private final ChoiceBox<String> plans = new ChoiceBox<>();
    private final VBox cards = new VBox();
    private final Label said = new Label();
    private final GraphCanvas<Stop, Edge> route = new GraphCanvas<>(Stop::screen, this::stopView, GoalsView::arrow,
            (laid, links) -> LayeredLayout.arrange(laid, links, List.of()));
    private final Map<String, List<HBox>> rows = new HashMap<>();
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private GoalsViewModel view;
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
        Button again = new Button("⟳ Read again");
        again.setOnAction(e -> read());
        plans.setOnAction(e -> read());
        HBox bar = new HBox(new Label("Plan"), plans, again);
        bar.getStyleClass().add("goals-bar");
        ScrollPane left = new ScrollPane(new VBox(said, cards));
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
        String wanted = plans.getValue();
        CompletableFuture.supplyAsync(() -> Read.of(services, wanted)).whenComplete((read, failed) ->
                Platform.runLater(() -> {
                    if (failed != null) {
                        refuse("The bot could not be read: " + failed.getMessage());
                        return;
                    }
                    draw(read);
                }));
    }

    /** What one read found: every plan's name, the one shown and its view, or why there is none. */
    private record Read(List<String> plans, String shown, GoalsViewModel view, String refused) {

        static Read of(StudioServices services, String wanted) {
            PluginValues values = services.pluginValues();
            if (values == null) return new Read(List.of(), null, null, "Studio has no source tree open.");
            Map<String, Plan> plans = new LinkedHashMap<>();
            for (Member member : values.members(SdkValues.PLANS)) {
                values.open(SdkValues.PLANS, member).flatMap(ctx -> ctx.value(Plan.class))
                        .ifPresent(plan -> plans.put(member.name(), plan));
            }
            if (plans.isEmpty()) {
                return new Read(List.of(), null, null, "The bot has no plan yet: a plan is tiers of the goals in "
                        + "Goals.java, written in Plans.java.");
            }
            String shown = plans.containsKey(wanted) ? wanted : plans.keySet().iterator().next();
            Map<String, Goal> goals = new LinkedHashMap<>();
            for (Member member : values.members(SdkValues.GOALS)) {
                values.open(SdkValues.GOALS, member).flatMap(ctx -> ctx.value(Goal.class))
                        .ifPresent(goal -> goals.put(member.name(), goal));
            }
            ScreenGraph map = ScreenMapRead.read(services);
            StudioPlan plan = StudioPlan.of(plans.get(shown), goals, map);
            DoneRecords records = services.stateDir().map(dir -> DoneRecords.in(dir, Clock.systemDefaultZone()))
                    .orElseGet(DoneRecords::none);
            List<String> names = List.copyOf(plans.keySet());
            try {
                PlanModel model = new PlanModel(plan.plan(), plan.map(), records, null);
                return new Read(names, shown, GoalsViewModel.of(model, plan), null);
            } catch (IllegalArgumentException cycle) {
                return new Read(names, shown, null, cycle.getMessage());
            }
        }
    }

    private void draw(Read read) {
        if (!plans.getItems().equals(read.plans())) plans.getItems().setAll(read.plans());
        if (read.shown() != null && !read.shown().equals(plans.getValue())) plans.setValue(read.shown());
        view = read.view();
        cards.getChildren().clear();
        rows.clear();
        if (view == null) {
            refuse(read.refused());
            route.show(List.of(), List.of());
            return;
        }
        refuse(null);
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
        acceptDrop(pill, dragged -> write(dragged, action -> ScreenEdits.setMark(services, action[0], action[1], mark)));
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
            write(dragged, action -> ScreenEdits.setOver(services, action[0], action[1], row.step().screen(),
                    row.step().field()));
        });
        rows.computeIfAbsent(row.name(), n -> new ArrayList<>()).add(line);
        return line;
    }

    /** Lets {@code target} take a dragged row, the action's {@code SCREEN.field}, and hands it to {@code dropped}. */
    private static void acceptDrop(Node target, Consumer<String> dropped) {
        target.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        target.setOnDragEntered(e -> target.pseudoClassStateChanged(TARGET, true));
        target.setOnDragExited(e -> target.pseudoClassStateChanged(TARGET, false));
        target.setOnDragDropped(e -> {
            boolean taken = e.getDragboard().hasString();
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
        boolean refused = answer.startsWith(ScreenEdits.NOT_WRITTEN);
        picked = dragged;
        read();
        if (refused) Platform.runLater(() -> refuse(answer));
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
