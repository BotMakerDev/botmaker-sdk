package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.bot.SdkValues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The bot's {@code Activities} and {@code Outcomes} constants, kept in step with what the Activity Flow draws
 * — by binding, through the contract's {@link PluginValues} open-set operations, as {@code TemplateUses} keeps
 * {@code Pictures} in step with the picture folder.
 *
 * <h2>Three moves around one write</h2>
 *
 * <p>The canvas works on labels. A save turns what changed into constants, in an order where every step
 * leaves a bot that compiles:
 * <ol>
 *   <li><b>Rename</b> each relabelled constant — the host renames it and every use (a body's
 *       {@code return Outcomes.WON;}, an {@code ActivitySwitch.disable(Activities.BATTLE)}, {@code Sdk.flow()}
 *       itself) — then sets its label. First, so the label the canvas now shows lands on the constant the code
 *       already names, rather than on a fresh one beside it.</li>
 *   <li><b>Add</b> a constant for every label none holds.</li>
 *   <li>The caller writes the flow; a value equal to a constant is written as that constant.</li>
 *   <li><b>Remove</b> the constant of each label the flow dropped ({@link #forget}). The host refuses while code
 *       still names it, and the constant stays with the refusal said.</li>
 * </ol>
 *
 * <p>A refused rename stops the save: writing the flow under the new label would leave the code returning a
 * constant whose label no arrow leaves from. A refused add or remove does not — the flow is then written with
 * the value spelled out ({@code Outcome.named("…")}), which compiles and routes the same.
 */
public final class FlowConstants {

    private FlowConstants() {}

    /** One of the two open sets, with what it holds. */
    public enum Kind {
        ACTIVITIES(SdkValues.ACTIVITIES, Activity.class, Activity::named, v -> ((Activity) v).label()),
        OUTCOMES(SdkValues.OUTCOMES, Outcome.class, Outcome::named, v -> ((Outcome) v).label());

        private final ManagedValue<?> set;
        private final Class<?> type;
        private final Function<String, Object> named;
        private final Function<Object, String> label;

        Kind(ManagedValue<?> set, Class<?> type, Function<String, Object> named, Function<Object, String> label) {
            this.set = set;
            this.type = type;
            this.named = named;
            this.label = label;
        }

        /** The open set's id, {@code "activities"}. */
        public String id() {
            return set.id();
        }

        /** The class it is, for sentences: {@code Activities}. */
        public String holder() {
            return set.holder();
        }

        Object named(String label) {
            return named.apply(label);
        }

        /** The label a constant's initialiser holds, or empty when it is not one the grammar reads. */
        Optional<String> label(ValueContext initializer) {
            return initializer.value(type).map(label);
        }
    }

    /** One change to an open set. */
    sealed interface Op {
        /** {@code from} becomes {@code to} holding {@code label}; {@code original} is the rename's key. */
        record Rename(String original, String from, String to, String label) implements Op {}

        record Add(String constant, String label) implements Op {}
    }

    /** What to do before the flow is written, or why it cannot be. */
    record Plan(List<Op> ops, String refusal) {}

    /**
     * The renames and adds that bring an open set holding {@code members} (constant → label, null when
     * unreadable) to one holding every label in {@code wanted}, given the canvas renames {@code renames}
     * (label at last save → label now).
     */
    static Plan plan(Kind kind, Map<String, String> members, Collection<String> wanted,
                     Map<String, String> renames) {
        Map<String, String> working = new LinkedHashMap<>(members);
        List<Op> ops = new ArrayList<>();
        for (Map.Entry<String, String> rename : renames.entrySet()) {
            String from = holding(working, rename.getKey());
            if (from == null) continue;   // nothing named it: the add below declares the new label
            String label = rename.getValue();
            String to = FlowNames.constantFor(label);
            if (to == null) return refused(label, kind);
            if (!to.equals(from) && working.containsKey(to)) {
                if (label.equals(working.get(to))) continue;   // already declared; the old one goes on forget
                return new Plan(List.of(), kind.holder() + "." + to + " already holds '" + working.get(to)
                        + "', so " + kind.holder() + "." + from + " can't be renamed to it for '" + label
                        + "'. Rename or remove that constant in " + kind.holder() + ".java first.");
            }
            ops.add(new Op.Rename(rename.getKey(), from, to, label));
            working.remove(from);
            working.put(to, label);
        }
        for (String label : new LinkedHashSet<>(wanted)) {
            if (holding(working, label) != null) continue;
            String constant = FlowNames.constantFor(label);
            if (constant == null) return refused(label, kind);
            if (working.containsKey(constant)) {
                return new Plan(List.of(), kind.holder() + "." + constant + " already holds '"
                        + working.get(constant) + "', so '" + label + "' has no constant of its own. Rename one "
                        + "of them.");
            }
            ops.add(new Op.Add(constant, label));
            working.put(constant, label);
        }
        return new Plan(List.copyOf(ops), null);
    }

    private static Plan refused(String label, Kind kind) {
        return new Plan(List.of(), "'" + label + "' can't become a constant in " + kind.holder() + ".java.");
    }

    /**
     * Renames and adds what {@link #plan} says, against the project's own constants. A rename that lands is
     * taken out of {@code renames}, so a later refusal does not run it twice.
     *
     * @param notes collects what was not done and did not need to stop the save
     * @return null when the flow may be written, else why it may not
     */
    public static String prepare(PluginValues values, Kind kind, Collection<String> wanted,
                                 Map<String, String> renames, List<String> notes) {
        Plan plan = plan(kind, members(values, kind), wanted, renames);
        if (plan.refusal() != null) return plan.refusal();
        for (Op op : plan.ops()) {
            switch (op) {
                case Op.Rename rename -> {
                    if (!rename.from().equals(rename.to())) {
                        Optional<String> refused = values.rename(kind.id(), rename.from(), rename.to());
                        if (refused.isPresent()) return refused.get();
                    }
                    relabel(values, kind, rename.to(), rename.label(), notes);
                    renames.remove(rename.original());
                }
                case Op.Add add -> values.add(kind.id(), add.constant(), kind.named(add.label()))
                        .ifPresent(notes::add);
            }
        }
        return null;
    }

    /**
     * Declares the constant for {@code label} when none holds it — what the return slot's <i>+ New outcome…</i>
     * does before it writes the value.
     *
     * @return empty when it is declared now or already was; else why not
     */
    public static Optional<String> declare(PluginValues values, Kind kind, String label) {
        List<String> notes = new ArrayList<>();
        String refused = prepare(values, kind, List.of(label), new LinkedHashMap<>(), notes);
        if (refused != null) return Optional.of(refused);
        return notes.isEmpty() ? Optional.empty() : Optional.of(notes.getFirst());
    }

    /**
     * Removes the constant holding each of {@code gone}, a label the flow no longer draws. A constant code
     * still names stays, and the host's sentence listing where goes into {@code notes}.
     */
    public static void forget(PluginValues values, Kind kind, Collection<String> gone, List<String> notes) {
        Map<String, String> members = members(values, kind);
        for (String label : gone) {
            String constant = holding(members, label);
            if (constant == null) continue;
            values.remove(kind.id(), constant).ifPresent(refused -> notes.add(kind.holder() + "." + constant
                    + " stays: " + refused));
        }
    }

    /** Every constant of {@code kind}'s set, with the label it holds (null when unreadable), in file order. */
    static Map<String, String> members(PluginValues values, Kind kind) {
        Map<String, String> members = new LinkedHashMap<>();
        for (String member : values.members(kind.id())) {
            members.put(member, values.open(kind.id(), member).flatMap(kind::label).orElse(null));
        }
        return members;
    }

    /** The labels every step of {@code flow} declares, in order: what the {@code Outcomes} set must hold. */
    public static Set<String> outcomeLabels(Flow flow) {
        Set<String> labels = new LinkedHashSet<>();
        for (Flow.Step step : flow.steps()) {
            for (Outcome outcome : step.outcomes()) labels.add(outcome.label());
        }
        return labels;
    }

    /** The labels of {@code flow}'s activities, in order. */
    public static Set<String> activityLabels(Flow flow) {
        Set<String> labels = new LinkedHashSet<>();
        for (Flow.Step step : flow.steps()) labels.add(step.label());
        return labels;
    }

    /**
     * {@code flow} with {@code outcome} declared by every step whose body is written {@code method} — what a
     * new outcome made from a body's return slot needs, so the canvas draws a port for it.
     */
    public static Flow withOutcome(Flow flow, String method, Outcome outcome) {
        List<Flow.Step> steps = new ArrayList<>(flow.steps().size());
        for (Flow.Step step : flow.steps()) {
            boolean mine = method != null && !method.isEmpty() && method.equals(FlowValue.bodySource(step))
                    && !step.outcomes().contains(outcome);
            if (!mine) {
                steps.add(step);
                continue;
            }
            List<Outcome> outcomes = new ArrayList<>(step.outcomes());
            outcomes.add(outcome);
            steps.add(Flow.activity(step.activity(), step.body(), step.description(), step.enabled(),
                    step.goHome(), step.popupCheck(), outcomes));
        }
        return Flow.of(steps, flow.edges(), flow.presets(), flow.start(), flow.limits());
    }

    private static void relabel(PluginValues values, Kind kind, String constant, String label, List<String> notes) {
        Optional<ValueContext> initializer = values.open(kind.id(), constant);
        if (initializer.isEmpty()) {
            notes.add(kind.holder() + "." + constant + " was renamed, but its label could not be rewritten.");
            return;
        }
        if (!Objects.equals(kind.label(initializer.get()).orElse(null), label)) {
            initializer.get().set(kind.named(label));
        }
    }

    /** The constant holding {@code label}, or null. */
    private static String holding(Map<String, String> members, String label) {
        for (Map.Entry<String, String> member : members.entrySet()) {
            if (label.equals(member.getValue())) return member.getKey();
        }
        return null;
    }
}
