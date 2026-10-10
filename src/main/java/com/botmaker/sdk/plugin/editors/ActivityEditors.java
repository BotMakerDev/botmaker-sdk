package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.flow.Labels;
import com.botmaker.sdk.plugin.flow.FlowConstants;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.flow.FlowValue;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * <p><b>The list is read from the project's own {@code Sdk.flow()}, not from a running bot.</b> The canvas
 * is the source of truth and what it writes is one expression in the bot's Java, so {@link FlowValue} is the
 * whole implementation. It is read when the dropdown is opened rather than when the block is drawn, so an
 * activity added in the flow window a moment ago is offered without reopening anything.
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

    /** An activity — the project's activities, as drawn. */
    public static Node activity(ValueContext ctx) {
        return labelBox(ctx, () -> activityLabels(flow(ctx)), "Activity",
                ctx.value(Activity.class).map(Activity::label).orElse(""), FlowNames::activity, null);
    }

    /**
     * An outcome — a constant of the activity whose body the slot sits in.
     *
     * <p>The flow links a body by the same text the host reports as the slot's enclosing method
     * ({@code Collect::body}), so the activity whose body is that method is the one asked, and its enum's
     * constants are listed by their labels. When none is — a helper method a body calls, a body not wired yet, a
     * slot outside a method — the list is every activity's, each as {@code "Activity: Outcome"}.
     */
    public static Node outcome(ValueContext ctx) {
        String owner = owner(ctx);
        String current = ctx.value(Outcome.class).map(outcome -> qualified(owner, outcome)).orElse("");
        return labelBox(ctx, () -> outcomeLabels(ctx.services(), owner), "Outcome", current,
                label -> outcomeOf(owner, label), owner == null ? null : () -> newOutcome(ctx, owner));
    }

    /**
     * An editable box of labels, writing the value a picked or typed label names. Whatever the value already
     * holds is kept on the list even when the flow no longer offers it — correcting what somebody wrote to
     * match the canvas would silently edit a bot.
     *
     * @param create when non-null, {@link #NEW_OUTCOME} ends the list and picking it runs this; the label it
     *               answers is then written like a picked one
     */
    private static Node labelBox(ValueContext ctx, Supplier<List<String>> options, String prompt, String current,
                                 Function<String, Object> value, Supplier<Optional<String>> create) {
        ComboBox<String> box = Styles.on(new ComboBox<>(), Styles.INSET_FIELD_FLAT);
        box.setEditable(true);
        box.setPromptText(prompt);
        if (!current.isBlank()) box.setValue(current);
        boolean[] reverting = {false};

        box.setOnShowing(event -> {
            List<String> items = new ArrayList<>(options.get());
            String now = box.getValue();
            if (now != null && !now.isBlank() && !items.contains(now)) items.add(now);
            if (create != null) items.add(NEW_OUTCOME);
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
            if (chosen == null) {
                // No constant holds it, so there is nothing to write it as: the box goes back, writing nothing.
                Platform.runLater(() -> {
                    reverting[0] = true;
                    box.setValue(was);
                    reverting[0] = false;
                });
                return;
            }
            ctx.set(value.apply(chosen));
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

    static List<String> activityLabels(Flow flow) {
        List<String> labels = new ArrayList<>();
        for (Flow.Step step : flow.steps()) {
            if (!step.label().isBlank()) labels.add(step.label());
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
                labels.add(own ? Labels.of(constant) : Labels.of(activity.getKey()) + SEPARATOR + Labels.of(constant));
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
     * The label of the activity whose body holds the slot: the step whose body is written as the slot's
     * enclosing method. Null for a slot in no activity's body.
     */
    private static String owner(ValueContext ctx) {
        String method = ctx.slot().flatMap(SlotContext::enclosingMethodSource).orElse(null);
        if (method == null || method.isEmpty()) return null;
        for (Flow.Step step : flow(ctx).steps()) {
            if (method.equals(FlowValue.bodySource(step))) return step.label();
        }
        return null;
    }

    /** The open project's flow, or {@link Flow#NONE}: a project with no flow yet draws an empty list. */
    private static Flow flow(ValueContext ctx) {
        return FlowValue.read(ctx.services());
    }
}
