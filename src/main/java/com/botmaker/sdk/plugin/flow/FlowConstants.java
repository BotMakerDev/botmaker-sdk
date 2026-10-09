package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.flow.Labels;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * <p>The canvas works on labels, and a label is its constant's name as words ({@code Labels}): the constant is
 * all a bot's enum holds. A save turns what changed into constants, in an order where every step leaves a bot
 * that compiles:
 * <ol>
 *   <li><b>Rename</b> each relabelled constant — the host renames it and every use (a body's
 *       {@code return Outcomes.WON;}, an {@code ActivitySwitch.disable(Activities.BATTLE)}, {@code Sdk.flow()}
 *       itself). First, so the label the canvas now shows lands on the constant the code already names, rather
 *       than on a fresh one beside it.</li>
 *   <li><b>Add</b> a constant for every label none holds.</li>
 *   <li>The caller writes the flow; each activity and outcome is written as its constant.</li>
 *   <li><b>Remove</b> the constant of each label the flow dropped ({@link #forget}). The host refuses while code
 *       still names it, and the constant stays with the refusal said.</li>
 * </ol>
 *
 * <p>A refused rename or add stops the save: a value no constant holds has nothing to be written as. A refused
 * remove does not — the constant is only left over.
 */
public final class FlowConstants {

    private FlowConstants() {}

    /** One of the two open sets, with what it holds. */
    public enum Kind {
        ACTIVITIES(new Named<>(SdkValues.ACTIVITIES, BotConstants::activity)),
        OUTCOMES(new Named<>(SdkValues.OUTCOMES, BotConstants::outcome));

        private final Named<?> constants;

        Kind(Named<?> constants) {
            this.constants = constants;
        }

        /** The class it is, for sentences: {@code Activities}. */
        public String holder() {
            return constants.set().value().holder();
        }
    }

    /** An enum set of the bot's, and the value each of its constants stands for. */
    private record Named<E>(ManagedSet<E> set, Function<String, E> byName) {

        Named(ManagedValue<E> value, Function<String, E> byName) {
            this(ManagedSet.of(value), byName);
        }

        Optional<String> add(PluginValues values, String constant) {
            return set.add(values, constant, byName.apply(constant));
        }
    }

    /** One change to an open set. */
    sealed interface Op {
        /** {@code from} becomes {@code to}; {@code original} is the rename's key. */
        record Rename(String original, String from, String to) implements Op {}

        record Add(String constant) implements Op {}
    }

    /** What to do before the flow is written, or why it cannot be. */
    record Plan(List<Op> ops, String refusal) {}

    /**
     * The renames and adds that bring an open set holding {@code members} (constant → its label) to one holding
     * every label in {@code wanted}, given the canvas renames {@code renames} (label at last save → label now).
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
            if (!to.equals(from) && working.containsKey(to)) continue;   // declared already; the old one is forgotten
            ops.add(new Op.Rename(rename.getKey(), from, to));
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
            ops.add(new Op.Add(constant));
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
     * @return null when the flow may be written, else why it may not
     */
    public static String prepare(PluginValues values, Kind kind, Collection<String> wanted,
                                 Map<String, String> renames) {
        Plan plan = plan(kind, members(values, kind), wanted, renames);
        if (plan.refusal() != null) return plan.refusal();
        for (Op op : plan.ops()) {
            switch (op) {
                case Op.Rename rename -> {
                    if (!rename.from().equals(rename.to())) {
                        Optional<String> refused = kind.constants.set().rename(values, rename.from(), rename.to());
                        if (refused.isPresent()) return refused.get();
                    }
                    renames.remove(rename.original());
                }
                case Op.Add add -> {
                    Optional<String> refused = kind.constants.add(values, add.constant());
                    if (refused.isPresent()) return refused.get();
                }
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
        return Optional.ofNullable(prepare(values, kind, List.of(label), new LinkedHashMap<>()));
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
            kind.constants.set().remove(values, constant).ifPresent(refused -> notes.add(kind.holder() + "." + constant
                    + " stays: " + refused));
        }
    }

    /**
     * Why {@code flow} cannot be written over {@code kind}'s enum as it stands — it names a constant the enum does
     * not declare — or null. Asked after {@link #prepare}, so what is left is a constant the canvas cannot name
     * back: one written by hand in another case ({@code bagFull}), whose label makes {@code BAG_FULL}. Written
     * anyway, the value would have no constant to be spelled as.
     */
    public static String unheld(PluginValues values, Kind kind, Flow flow) {
        Set<String> members = members(values, kind).keySet();
        Set<String> named = new LinkedHashSet<>();
        for (Flow.Step step : flow.steps()) {
            if (kind == Kind.ACTIVITIES) named.add(step.activity().name());
            else step.outcomes().forEach(outcome -> named.add(outcome.name()));
        }
        if (kind == Kind.OUTCOMES) {
            for (Flow.Edge edge : flow.edges()) {
                Outcome outcome = edge.outcome();
                if (outcome != Outcome.NEXT && outcome != Outcome.DISABLED) named.add(outcome.name());
            }
        }
        for (String name : named) {
            if (members.contains(name)) continue;
            return "The flow names " + kind.holder() + "." + name + ", which " + kind.holder() + ".java does not "
                    + "declare. The Activity Flow names constants in upper case: rename the one written in another "
                    + "case to " + name + " in " + kind.holder() + ".java.";
        }
        return null;
    }

    /** Every constant of {@code kind}'s set, with its label — its name as words — in file order. */
    static Map<String, String> members(PluginValues values, Kind kind) {
        Map<String, String> members = new LinkedHashMap<>();
        for (String member : kind.constants.set().members(values)) members.put(member, Labels.of(member));
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
            steps.add(step.reports(outcomes));
        }
        return Flow.of(steps, flow.edges(), flow.presets(), flow.start(), flow.limits());
    }

    /** The constant holding {@code label}, or null. */
    private static String holding(Map<String, String> members, String label) {
        for (Map.Entry<String, String> member : members.entrySet()) {
            if (label.equals(member.getValue())) return member.getKey();
        }
        return null;
    }
}
