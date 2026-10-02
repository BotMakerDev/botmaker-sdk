package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The editors of the two values that tie a bot's code to its Activity Flow canvas: an {@link Activity}
 * ({@code ActivitySwitch.disable(Activities.MINING)}) and an {@link Outcome} ({@code return Outcomes.BAG_FULL;}).
 *
 * <p>Both are claimed by type since 2026-10-02, when the two stopped being strings: the editor hands back an
 * {@code Activity.named("Mining")}, and the host writes a value equal to one of the bot's {@code Activities}
 * constants as that constant — {@code Activities.MINING} — so what the picker writes is bound, not spelled.
 *
 * <p><b>The list is read from the project's own {@code Sdk.flow()}, not from a running bot.</b> The canvas
 * is the source of truth and what it writes is one expression in the bot's Java, so {@link FlowValue} is the
 * whole implementation. It is read when the dropdown is opened rather than when the block is drawn, so an
 * activity added in the flow window a moment ago is offered without reopening anything.
 *
 * <p><b>Both boxes stay typeable.</b> Writing the body before drawing the activity is an ordinary way to work;
 * a label no constant holds is written {@code Outcome.named("…")}, which compiles and is matched by its label.
 * The outcome box also offers <i>+ New outcome…</i>, which makes the constant, declares it on the activity whose
 * body this is, and writes it — the one way to name a new outcome without leaving the body.
 */
public final class ActivityEditors {

    /** The outcome list's last entry, which makes a new outcome rather than naming one. */
    static final String NEW_OUTCOME = "+ New outcome…";

    private ActivityEditors() {}

    /** An activity — the project's activities, as drawn. */
    public static Node activity(ValueContext ctx) {
        return labelBox(ctx, () -> activityLabels(flow(ctx)), "Activity",
                ctx.value(Activity.class).map(Activity::label).orElse(""), Activity::named, null);
    }

    /**
     * An outcome — the outcomes of the activity whose body the slot sits in.
     *
     * <p>The flow links a body by the same text the host reports as the slot's enclosing method
     * ({@code Collect::body}), so the activities whose body is that method are the ones asked. When none is —
     * a helper method a body calls, a body not wired yet, a slot outside a method — the list is every outcome
     * the project declares, duplicates collapsed.
     *
     * <p>{@code NEXT} heads the list: it is the outcome every activity has without declaring one.
     */
    public static Node outcome(ValueContext ctx) {
        return labelBox(ctx, () -> outcomeLabels(ctx), "Outcome",
                ctx.value(Outcome.class).map(Outcome::label).orElse(""), Outcome::named, () -> newOutcome(ctx));
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
            if (now != null && !now.isBlank() && !now.equals(was)) ctx.set(value.apply(now));
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
     * Asks for an outcome's label, declares its {@code Outcomes} constant, and adds it to the outcomes of the
     * activity whose body holds the slot — so the canvas draws its port. Empty when cancelled or refused, the
     * refusal said in an alert.
     */
    private static Optional<String> newOutcome(ValueContext ctx) {
        StudioServices services = ctx.services();
        TextInputDialog prompt = new TextInputDialog();
        services.theme().apply(prompt);
        prompt.initOwner(Modals.owner(services));
        prompt.setTitle("New outcome");
        prompt.setHeaderText("What can this activity report?");
        prompt.setContentText("Outcome:");
        Optional<String> typed = prompt.showAndWait().map(FlowNames::label).filter(label -> !label.isEmpty());
        if (typed.isEmpty()) return Optional.empty();
        String label = typed.get();

        String method = ctx.slot().flatMap(SlotContext::enclosingMethodSource).orElse(null);
        Flow flow = flow(ctx);
        List<Flow.Step> own = ownSteps(flow, method);
        List<String> declared = new ArrayList<>();
        Set<String> elsewhere = new LinkedHashSet<>();
        for (Flow.Step step : flow.steps()) {
            List<String> labels = step.outcomes().stream().map(Outcome::label).toList();
            if (own.contains(step)) declared.addAll(labels);
            else elsewhere.addAll(labels);
        }
        String owner = own.isEmpty() ? "this activity" : own.getFirst().label();
        String problem = FlowNames.outcomeProblem(declared, elsewhere, owner, label, null);
        if (problem == null && declared.contains(label)) return typed;   // already this activity's: just pick it
        Optional<String> refused = problem != null ? Optional.of(problem)
                : FlowConstants.declare(services.pluginValues(), FlowConstants.Kind.OUTCOMES, label);
        if (refused.isEmpty() && !own.isEmpty()) {
            refused = Optional.ofNullable(FlowValue.FLOW.write(services,
                    FlowConstants.withOutcome(flow, method, Outcome.named(label))));
        }
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

    private static List<String> outcomeLabels(ValueContext ctx) {
        String method = ctx.slot().flatMap(SlotContext::enclosingMethodSource).orElse(null);
        return outcomeLabels(flow(ctx), method);
    }

    /** {@code NEXT}, then the outcomes of the steps whose body is {@code method}, or every step's when none is. */
    static List<String> outcomeLabels(Flow flow, String method) {
        List<Flow.Step> own = ownSteps(flow, method);
        Set<String> labels = new LinkedHashSet<>();
        labels.add(Outcome.NEXT.label());
        for (Flow.Step step : own.isEmpty() ? flow.steps() : own) {
            for (Outcome outcome : step.outcomes()) labels.add(outcome.label());
        }
        return List.copyOf(labels);
    }

    /** The steps whose body is written {@code method}; empty for a slot in no activity's body. */
    private static List<Flow.Step> ownSteps(Flow flow, String method) {
        return method == null || method.isEmpty() ? List.of() : flow.steps().stream()
                .filter(step -> method.equals(FlowValue.bodySource(step)))
                .toList();
    }

    /** The open project's flow, or {@link Flow#NONE}: a project with no flow yet draws an empty list. */
    private static Flow flow(ValueContext ctx) {
        return FlowValue.read(ctx.services());
    }
}
