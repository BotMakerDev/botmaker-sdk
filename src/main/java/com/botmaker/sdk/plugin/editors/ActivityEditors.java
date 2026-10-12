package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.flow.Labels;
import com.botmaker.sdk.plugin.flow.FlowConstants;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.flow.FlowRefs;
import com.botmaker.sdk.plugin.flow.FlowTreeView;
import com.botmaker.sdk.plugin.flow.FlowValue;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The editors of the two values that tie a bot's code to its Activity Flow canvas: an {@link Activity}
 * (an activity's enum, {@code Activities.Mining.class}) and an {@link Outcome} ({@code return
 * Activities.Mining.BAG_FULL;}).
 *
 * <p>Both are claimed by type: the editor hands back the name a label makes, held by name
 * ({@code FlowNames.activity("Mining")}), and the host writes it as the bot's own — {@code Activities.Mining.class},
 * {@code Activities.Mining.BAG_FULL} — so what the picker writes is bound, not spelled.
 *
 * <p><b>The list is read from the project's own {@code Activities} class, not from a running bot</b>
 * ({@link FlowConstants#declared}): an activity is its enum, so an enum is offered whether or not the canvas
 * draws it yet. It is read when the dropdown is opened rather than when the block is drawn, so an activity added
 * in the flow window a moment ago is offered without reopening anything.
 *
 * <p><b>Both boxes stay typeable.</b> Writing the body before drawing the activity is an ordinary way to work;
 * a label no constant holds has nothing to be written as, so it is refused until the constant exists. The
 * outcome box also offers <i>+ New outcome…</i>, which makes the constant, declares it on the activity whose
 * body this is, and writes it — the one way to name a new outcome without leaving the body.
 */
public final class ActivityEditors {

    /** The outcome list's last entry, which makes a new outcome rather than naming one. */
    static final String NEW_OUTCOME = "+ New outcome…";

    private ActivityEditors() {}

    /** An activity — the enums the project's {@code Activities} class declares, drawn on the canvas or not. */
    public static Node activity(ValueContext ctx) {
        return labelBox(ctx, () -> activityLabels(FlowConstants.declared(ctx.services().pluginValues()).keySet()),
                () -> false, "Activity",
                ctx.value(Activity.class).map(Activity::label).orElse(""), FlowNames::activity, null);
    }

    /**
     * An activity the person running the bot may switch off — one some flow draws {@code .switchable()}: an entry
     * of the bot's {@code skip} parameter, {@code Activities.Rest.class}.
     */
    public static Node switchable(ValueContext ctx) {
        return labelBox(ctx, () -> switchableLabels(ctx.services().pluginValues()), () -> false, "Activity",
                ctx.value(Activity.class).map(Activity::label).orElse(""), FlowNames::activity, null);
    }

    /**
     * Whether {@code ctx} is an entry of a parameter listing activities: a {@code Class} with no call site, new or
     * holding an activity's enum. A class literal of anything else is left to the host.
     */
    public static boolean isSkippedActivity(ValueContext ctx) {
        return ctx.slot().isEmpty() && ctx.type().is(Class.class)
                && (ctx.source().isBlank() || ctx.value(Activity.class).filter(a -> !a.isNone()).isPresent());
    }

    /**
     * Whether {@code ctx} is a flow parameter: a {@code Flow} with no call site, as a row of the Parameters window or
     * the Runner is. A flow in code, a {@code Flows} constant's {@code Flow.of(…)} included, is left to its own editor.
     */
    public static boolean isFlowChoice(ValueContext ctx) {
        return ctx.slot().isEmpty() && ctx.type().is(Flow.class);
    }

    /** The labels of the activities any of the bot's flows lets the runner switch off, in flow order. */
    static List<String> switchableLabels(PluginValues values) {
        return FlowValue.FLOWS.members(values).stream()
                .flatMap(member -> FlowValue.FLOWS.read(values, member).stream())
                .flatMap(flow -> flow.steps().stream())
                .filter(Flow.Step::runnerSwitch)
                .map(Flow.Step::label)
                .distinct()
                .toList();
    }

    /** The flow list's entry for {@link Flow#NONE}: the bot runs nothing until a flow is picked. */
    static final String NO_FLOW = "(no flow)";

    /**
     * A flow — one of the bot's {@code Flows} constants, by name: the bot's {@code flow} parameter, and the flow
     * {@code Bot.runFlow} walks. A button showing the flow picked opens the flow tree read-only
     * ({@link FlowTreeView}, 2026-10-12): each flow with its cards, a sub-flow card with the cards of the flow it
     * runs, and a click on a flow picks it. The picked name is written ({@code Flows.DAILY}), never the first flow
     * equal to it: a fresh flow equals {@link Flow#NONE} and a duplicate its source. {@value #NO_FLOW} writes
     * {@code Flow.NONE}; while it is the value, a bot with one flow is prompted to pick it. The prompt is decided as
     * the row is drawn.
     */
    public static Node flow(ValueContext ctx) {
        PluginValues values = ctx.services().pluginValues();
        List<String> flows = FlowValue.flowNames(values);
        String current = FlowValue.FLOWS.picked(ctx).map(PluginValues.Member::name)
                .orElseGet(() -> shown(ctx, values, flows));
        String prompt = flows.size() == 1 ? "Run " + flows.getFirst() : "Pick a flow";
        if (NO_FLOW.equals(current) && flows.size() == 1) current = "";
        Button button = Styles.on(new Button(), Styles.INSET_FIELD_FLAT);
        String[] picked = {current};
        // What the button says: the flow picked, or the prompt; a pick the host refused leaves it as it was.
        Consumer<String> show = name -> {
            picked[0] = name;
            button.setText(name.isBlank() ? prompt : name + "  ▾");
        };
        show.accept(current);
        button.setOnAction(e -> {
            Popup popup = new Popup();
            popup.setAutoHide(true);
            TreeView<FlowTreeView.Item> tree = FlowTreeView.of(FlowRefs.of(values), picked[0]);
            tree.setPrefSize(260, 320);
            Hyperlink none = new Hyperlink(NO_FLOW);
            none.setOnAction(a -> {
                popup.hide();
                Optional<String> refused = ctx.write(Flow.NONE);
                refused.ifPresent(ctx.services()::status);
                if (refused.isEmpty()) show.accept(NO_FLOW);
            });
            tree.setOnMouseClicked(click -> {
                TreeItem<FlowTreeView.Item> row = tree.getSelectionModel().getSelectedItem();
                // Read-only: only a flow at the top is a choice; its cards are there to tell flows apart.
                if (row == null || row.getParent() != tree.getRoot()) return;
                popup.hide();
                String flow = row.getValue().flow();
                Optional<String> refused = FlowValue.FLOWS.pick(ctx, PluginValues.Member.of(flow));
                refused.ifPresent(ctx.services()::status);
                if (refused.isEmpty()) show.accept(flow);
            });
            VBox box = new VBox(4, none, tree);
            box.setPadding(new Insets(6));
            // A popup is a scene of its own: the theme's sheets, and the root's colours, follow the row it opens from.
            box.setStyle("-fx-background-color: -fx-background; -fx-border-color: -fx-box-border;");
            popup.getContent().add(box);
            if (button.getScene() != null) popup.getScene().getStylesheets().setAll(button.getScene().getStylesheets());
            Bounds at = button.localToScreen(button.getBoundsInLocal());
            popup.show(button, at.getMinX(), at.getMaxY());
        });
        return button;
    }

    /**
     * What the box shows when the host names no member — the value is spelled out, or written in a way it does
     * not resolve: {@value #NO_FLOW} for {@link Flow#NONE}, else the first flow equal to the value, else nothing.
     */
    private static String shown(ValueContext ctx, PluginValues values, List<String> flows) {
        Optional<Flow> value = ctx.value(Flow.class);
        if (value.isEmpty()) return "";
        if (Flow.NONE.equals(value.get())) return NO_FLOW;
        String referred = BotConstants.flowName(value.get());
        if (referred != null) return flows.contains(referred) ? referred : "";
        return flows.stream()
                .filter(name -> FlowValue.FLOWS.read(values, PluginValues.Member.of(name))
                        .filter(value.get()::equals).isPresent())
                .findFirst().orElse("");
    }

    /**
     * An outcome — a constant of the activity whose body the slot sits in.
     *
     * <p>That activity is the enum the slot's enclosing method ({@code Collect::body}) returns, which the host
     * reads off the method's declaration ({@link FlowConstants#returnedBy}) — so a body the flow does not link
     * yet lists its own constants just the same, by their labels. A method returning none of them — a helper
     * taking parameters, a slot outside a method — lists every activity's, each as {@code "Activity: Outcome"}.
     */
    public static Node outcome(ValueContext ctx) {
        // Asked again whenever the list opens: the method's return type may have changed since the block was drawn.
        String[] owner = {owner(ctx)};
        String current = ctx.value(Outcome.class).filter(outcome -> !Outcome.NONE.equals(outcome))
                .map(outcome -> qualified(owner[0], outcome)).orElse("");
        Supplier<List<String>> options = () -> outcomeLabels(ctx.services(), owner[0]);
        return labelBox(ctx, options, () -> {
                    owner[0] = owner(ctx);
                    return owner[0] != null;
                }, "Outcome", current, label -> outcomeOf(owner[0], label), () -> newOutcome(ctx, owner[0]));
    }

    /**
     * An editable box of labels, writing the value a picked or typed label names. Whatever the value already
     * holds is kept on the list even when the flow no longer offers it — correcting what somebody wrote to
     * match the canvas would silently edit a bot.
     *
     * @param opening run as the list opens, before {@code options}; true when {@link #NEW_OUTCOME} ends the list
     * @param create  what picking {@link #NEW_OUTCOME} runs; the label it answers is then written like a picked one
     */
    private static Node labelBox(ValueContext ctx, Supplier<List<String>> options, BooleanSupplier opening,
                                 String prompt, String current, Function<String, Object> value,
                                 Supplier<Optional<String>> create) {
        return labelBox(ctx, options, opening, prompt, current, value, create, label -> {
            ctx.set(value.apply(label));
            return Optional.empty();
        });
    }

    /**
     * The same, writing the chosen option through {@code write}, which answers why not when it wrote nothing — and
     * the box then goes back to what it held.
     */
    private static Node labelBox(ValueContext ctx, Supplier<List<String>> options, BooleanSupplier opening,
                                 String prompt, String current, Function<String, Object> value,
                                 Supplier<Optional<String>> create, Function<String, Optional<String>> write) {
        ComboBox<String> box = Styles.on(new ComboBox<>(), Styles.INSET_FIELD_FLAT);
        box.setEditable(true);
        box.setPromptText(prompt);
        if (!current.isBlank()) box.setValue(current);
        boolean[] reverting = {false};

        box.setOnShowing(event -> {
            boolean creates = opening.getAsBoolean();
            List<String> items = new ArrayList<>(options.get());
            String now = box.getValue();
            if (now != null && !now.isBlank() && !items.contains(now)) items.add(now);
            if (creates) items.add(NEW_OUTCOME);
            box.getItems().setAll(items);
        });
        box.valueProperty().addListener((obs, was, now) -> {
            if (NEW_OUTCOME.equals(now)) {
                // Later, not now: a value listener may not set the value it is reporting, and the prompt is
                // modal. The box goes back to what it held, so a cancelled prompt leaves the slot as it was.
                Platform.runLater(() -> {
                    box.setValue(was);
                    create.get().ifPresent(box::setValue);
                });
                return;
            }
            if (NEW_OUTCOME.equals(was)) return;   // the step back above, not an edit
            if (reverting[0] || now == null || now.isBlank() || now.equals(was)) return;
            // Typed or picked, it is the option naming the same constant: "next" is NEXT, "bag full" Bag full.
            // A label that makes no constant ("!!") names nothing, rather than the NEXT it would fall back to.
            List<String> known = options.get();
            Object typed = known.contains(now) || FlowNames.constantFor(now) != null ? value.apply(now) : null;
            String chosen = typed == null ? null : known.stream()
                    .filter(option -> value.apply(option).equals(typed)).findFirst().orElse(null);
            Optional<String> refused = chosen == null ? Optional.of("") : write.apply(chosen);
            if (refused.isPresent()) {
                // No constant holds it, so there is nothing to write it as: the box goes back, writing nothing.
                if (!refused.get().isBlank()) ctx.services().status(refused.get());
                Platform.runLater(() -> {
                    reverting[0] = true;
                    box.setValue(was);
                    reverting[0] = false;
                });
            }
        });
        // An editable ComboBox commits its editor on Enter only; clicking away would lose a typed label.
        box.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) return;
            String typed = box.getEditor() == null ? null : box.getEditor().getText();
            if (typed != null && !typed.isBlank() && !NEW_OUTCOME.equals(typed)) box.setValue(typed.strip());
        });
        return box;
    }

    /**
     * Asks for an outcome's label and declares it in the enum of the activity {@code owner} — so its body may
     * return it and the canvas draws its port. Empty when cancelled or refused, the refusal said in an alert.
     */
    private static Optional<String> newOutcome(ValueContext ctx, String owner) {
        StudioServices services = ctx.services();
        TextInputDialog prompt = new TextInputDialog();
        services.theme().apply(prompt);
        prompt.initOwner(Modals.owner(services));
        prompt.setTitle("New outcome");
        prompt.setHeaderText("What can " + owner + " report?");
        prompt.setContentText("Outcome:");
        Optional<String> typed = prompt.showAndWait().map(FlowNames::label).filter(label -> !label.isEmpty());
        if (typed.isEmpty()) return Optional.empty();
        String label = typed.get();

        List<String> declared = outcomeLabels(services, owner);
        String problem = FlowNames.outcomeProblem(declared, owner, label, null);
        if (declared.contains(label)) return typed;   // already this activity's: just pick it
        Optional<String> refused = problem != null ? Optional.of(problem)
                : FlowConstants.declare(services.pluginValues(), FlowNames.typeFor(owner), label);
        if (refused.isPresent()) {
            Alert alert = services.theme().alert(Alert.AlertType.INFORMATION, refused.get(), ButtonType.OK);
            alert.initOwner(Modals.owner(services));
            alert.setTitle("New outcome");
            alert.setHeaderText("'" + label + "' was not added");
            alert.showAndWait();
            return Optional.empty();
        }
        return typed;
    }

    /** The labels of the activity enums {@code types}, by their names. */
    static List<String> activityLabels(Collection<String> types) {
        List<String> labels = new ArrayList<>();
        for (String type : types) {
            if (!type.isBlank()) labels.add(Labels.ofType(type));
        }
        return labels;
    }

    /**
     * The outcomes the project's enums declare: the labels of {@code owner}'s constants, or every activity's as
     * {@code "Activity: Outcome"} when {@code owner} is null.
     */
    private static List<String> outcomeLabels(StudioServices services, String owner) {
        return outcomeLabels(FlowConstants.declared(services.pluginValues()), owner);
    }

    /** {@link #outcomeLabels(StudioServices, String)} over the enums {@code declared}, name → constants. */
    static List<String> outcomeLabels(Map<String, List<String>> declared, String owner) {
        List<String> labels = new ArrayList<>();
        for (Map.Entry<String, List<String>> activity : declared.entrySet()) {
            boolean own = owner != null && activity.getKey().equals(FlowNames.typeFor(owner));
            if (owner != null && !own) continue;
            for (String constant : activity.getValue()) {
                labels.add(own ? Labels.of(constant) : Labels.ofType(activity.getKey()) + SEPARATOR + Labels.of(constant));
            }
        }
        return labels;
    }

    /** Between an activity and an outcome in a label that names both. */
    private static final String SEPARATOR = ": ";

    /** {@code outcome} as the box shows it: bare when it is {@code owner}'s, else with its activity. */
    private static String qualified(String owner, Outcome outcome) {
        String activity = outcome.activity().label();
        return owner != null && owner.equals(activity) ? outcome.label() : activity + SEPARATOR + outcome.label();
    }

    /** The outcome a label in the box names: one of {@code owner}'s, or {@code "Activity: Outcome"}. */
    static Outcome outcomeOf(String owner, String label) {
        if (owner != null) return FlowNames.outcome(owner, label);
        int at = label.indexOf(SEPARATOR);
        return at < 0 ? FlowNames.outcome("", label)
                : FlowNames.outcome(label.substring(0, at), label.substring(at + SEPARATOR.length()));
    }

    /**
     * The label of the activity whose body holds the slot: the enum the slot's enclosing method returns. Null for
     * a slot in a method returning none of them.
     */
    private static String owner(ValueContext ctx) {
        String method = ctx.slot().flatMap(SlotContext::enclosingMethodSource).orElse(null);
        return FlowConstants.returnedBy(ctx.services().pluginValues(), method).map(Labels::ofType).orElse(null);
    }

}
