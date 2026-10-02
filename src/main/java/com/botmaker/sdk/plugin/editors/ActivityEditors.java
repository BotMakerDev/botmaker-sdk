package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.plugin.flow.FlowValue;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
 */
public final class ActivityEditors {

    private ActivityEditors() {}

    /** An activity — the project's activities, as drawn. */
    public static Node activity(ValueContext ctx) {
        return labelBox(ctx, () -> activityLabels(flow(ctx)), "Activity",
                ctx.value(Activity.class).map(Activity::label).orElse(""), Activity::named);
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
                ctx.value(Outcome.class).map(Outcome::label).orElse(""), Outcome::named);
    }

    /**
     * An editable box of labels, writing the value a picked or typed label names. Whatever the value already
     * holds is kept on the list even when the flow no longer offers it — correcting what somebody wrote to
     * match the canvas would silently edit a bot.
     */
    private static Node labelBox(ValueContext ctx, Supplier<List<String>> options, String prompt, String current,
                                 Function<String, Object> value) {
        ComboBox<String> box = Styles.on(new ComboBox<>(), Styles.INSET_FIELD_FLAT);
        box.setEditable(true);
        box.setPromptText(prompt);
        if (!current.isBlank()) box.setValue(current);

        box.setOnShowing(event -> {
            List<String> items = new ArrayList<>(options.get());
            String now = box.getValue();
            if (now != null && !now.isBlank() && !items.contains(now)) items.add(now);
            box.getItems().setAll(items);
        });
        box.valueProperty().addListener((obs, was, now) -> {
            if (now != null && !now.isBlank() && !now.equals(was)) ctx.set(value.apply(now));
        });
        // An editable ComboBox commits its editor on Enter only; clicking away would lose a typed label.
        box.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) return;
            String typed = box.getEditor() == null ? null : box.getEditor().getText();
            if (typed != null && !typed.isBlank()) box.setValue(typed.strip());
        });
        return box;
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
        List<Flow.Step> own = method == null ? List.of() : flow.steps().stream()
                .filter(step -> method.equals(FlowValue.bodySource(step)))
                .toList();
        Set<String> labels = new LinkedHashSet<>();
        labels.add(Outcome.NEXT.label());
        for (Flow.Step step : own.isEmpty() ? flow.steps() : own) {
            for (Outcome outcome : step.outcomes()) labels.add(outcome.label());
        }
        return List.copyOf(labels);
    }

    /** The open project's flow, or {@link Flow#NONE}: a project with no flow yet draws an empty list. */
    private static Flow flow(ValueContext ctx) {
        return FlowValue.read(ctx.services());
    }
}
