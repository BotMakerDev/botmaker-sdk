package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.bot.Outcome;
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
import java.util.function.Supplier;

/**
 * The bot's {@code Activities} class — one enum per activity, its constants the activity's outcomes — kept in
 * step with what the Activity Flow draws, by binding, through the contract's {@link PluginValues} grouped-set
 * operations (2026-10-10).
 *
 * <h2>Three moves around one write</h2>
 *
 * <p>The canvas works on labels, and a label is its name as words ({@code Labels}): the name is all a bot's enum
 * holds. A save turns what changed into names, in an order where every step leaves a bot that compiles:
 * <ol>
 *   <li><b>Rename</b> each relabelled activity's enum, then each relabelled outcome inside its enum — the host
 *       renames it and every use (a body's {@code return Activities.Collect.WON;}, an
 *       {@code ActivitySwitch.disable(Activities.Battle.class)}, the {@code Flows} constants themselves).</li>
 *   <li><b>Add</b> an enum for every activity none holds, seeded with its outcomes, and a constant for every
 *       outcome its enum does not hold.</li>
 *   <li>The caller writes the flow; each activity is written as its class literal and each outcome as its
 *       constant.</li>
 *   <li><b>Remove</b> what the canvas dropped ({@link #forget}): an outcome its enum still holds, an activity
 *       the last save had. The host refuses while code still names it, and it stays with the refusal said.</li>
 * </ol>
 *
 * <p>A refused rename or add stops the save: a value no name holds has nothing to be written as. A refused
 * remove does not — the name is only left over.
 */
public final class FlowConstants {

    private FlowConstants() {}

    /** The activities' enums, as one grouped open set. */
    static final ManagedSet<Outcome> SET = ManagedSet.of(SdkValues.ACTIVITIES);

    private static String id() {
        return SET.value().id();
    }

    /**
     * The outcome labels the enum of the activity {@code label} declares, in its order — what another flow running
     * it already reports — or none when it has no enum yet.
     */
    public static List<String> outcomesOf(PluginValues values, String label) {
        String type = FlowNames.typeFor(label);
        if (type == null) return List.of();
        return declared(values).getOrDefault(type, List.of()).stream().map(Labels::of).toList();
    }

    /**
     * Why the activity {@code from} cannot be renamed {@code to} — another activity, which another flow may run,
     * already has that enum — or null. The canvas checks its own cards; this is the bot's.
     */
    public static String renameProblem(PluginValues values, String from, String to) {
        String was = FlowNames.typeFor(from);
        String now = FlowNames.typeFor(to);
        if (now == null || now.equals(was) || !declared(values).containsKey(now)) return null;
        return FlowNames.ACTIVITIES + "." + now + " is already another activity of the bot's; pick another name, or "
                + "add that activity to this flow instead.";
    }

    /** Each enum the project's {@code Activities} class holds, by its name, with its constants in file order. */
    public static Map<String, List<String>> declared(PluginValues values) {
        Map<String, List<String>> declared = new LinkedHashMap<>();
        if (values == null) return declared;
        for (String group : values.groups(id())) declared.put(group, new ArrayList<>());
        for (String member : SET.members(values)) {
            int dot = member.indexOf('.');
            if (dot <= 0) continue;
            declared.computeIfAbsent(member.substring(0, dot), group -> new ArrayList<>()).add(member.substring(dot + 1));
        }
        return declared;
    }

    /**
     * The enum the bot's method {@code reference} ({@code Collect::body}) returns, by its name, or empty when it
     * returns none of them — asked of the host, which reads the method's return type, so a body the flow does not
     * link yet is still its activity's.
     */
    public static Optional<String> returnedBy(PluginValues values, String reference) {
        if (values == null || reference == null || reference.isBlank()) return Optional.empty();
        for (String group : values.groups(id())) {
            if (values.methods(id(), group).contains(reference)) return Optional.of(group);
        }
        return Optional.empty();
    }

    /**
     * The bot's methods that can be the body of the activity {@code type}: each {@code public static} one with no
     * parameter returning its enum, as the reference a flow writes ({@code Collect::body}). None while the enum
     * is not declared yet.
     */
    public static List<String> bodies(PluginValues values, String type) {
        return values == null || type == null ? List.of() : values.methods(id(), type);
    }

    /**
     * The bot's methods that can be the flow's way home or its setup: each {@code public static void} one with no
     * parameter.
     */
    public static List<String> homes(PluginValues values) {
        return values == null ? List.of() : values.methods(null, null);
    }

    /**
     * Writes {@code public static <type> method() { return <type>.<its first constant>; }} into the bot's class
     * {@code className} — or, with {@code type} null, {@code public static void method() {}} — creating the class
     * when there is none.
     */
    public static PluginValues.MethodWrite createMethod(PluginValues values, String type, String className,
                                                        String method) {
        if (values == null) return new PluginValues.MethodWrite(null, PluginValues.NO_SOURCE_TREE);
        return values.createMethod(type == null ? null : id(), type, className, method);
    }

    /**
     * Renames and adds what bringing the project's enums to {@code board} takes, given the canvas renames —
     * {@code activityRenames} (label at last save → label now) and {@code outcomeRenames} (activity's label now →
     * outcome label at last save → label now). A rename that lands is taken out of its map, so a later refusal
     * does not run it twice.
     *
     * @return null when the flow may be written, else why it may not
     */
    public static String prepare(PluginValues values, Board board, Map<String, String> activityRenames,
                                 Map<String, Map<String, String>> outcomeRenames) {
        Map<String, List<String>> declared = declared(values);
        for (Map.Entry<String, String> rename : List.copyOf(activityRenames.entrySet())) {
            String from = FlowNames.typeFor(rename.getKey());
            String to = FlowNames.typeFor(rename.getValue());
            if (to == null) return refused(rename.getValue(), FlowNames.ACTIVITIES);
            if (from != null && !from.equals(to) && declared.containsKey(from) && !declared.containsKey(to)) {
                Optional<String> refused = values.renameGroup(id(), from, to);
                if (refused.isPresent()) return refused.get();
                declared.put(to, declared.remove(from));
            }
            activityRenames.remove(rename.getKey());
        }
        for (Map.Entry<String, List<String>> activity : board.exits().entrySet()) {
            String type = FlowNames.typeFor(activity.getKey());
            if (type == null) return refused(activity.getKey(), FlowNames.ACTIVITIES);
            List<String> wanted = new ArrayList<>();
            for (String label : activity.getValue()) {
                String constant = FlowNames.constantFor(label);
                if (constant == null) return refused(label, FlowNames.ACTIVITIES + "." + type);
                if (!wanted.contains(constant)) wanted.add(constant);
            }
            if (wanted.isEmpty()) {
                return activity.getKey() + " needs an outcome: an enum with no constant has nothing to return.";
            }
            List<String> held = declared.get(type);
            if (held == null) {
                Optional<String> refused = values.addGroup(id(), type, wanted);
                if (refused.isPresent()) return refused.get();
                declared.put(type, new ArrayList<>(wanted));
                continue;
            }
            Map<String, String> renames = outcomeRenames.getOrDefault(activity.getKey(), new LinkedHashMap<>());
            for (Map.Entry<String, String> rename : List.copyOf(renames.entrySet())) {
                String from = FlowNames.constantFor(rename.getKey());
                String to = FlowNames.constantFor(rename.getValue());
                if (to == null) return refused(rename.getValue(), FlowNames.ACTIVITIES + "." + type);
                if (from != null && !from.equals(to) && held.contains(from) && !held.contains(to)) {
                    Optional<String> refused = SET.rename(values, type + "." + from, to);
                    if (refused.isPresent()) return refused.get();
                    held.set(held.indexOf(from), to);
                }
                renames.remove(rename.getKey());
            }
            for (String constant : wanted) {
                if (held.contains(constant)) continue;
                Optional<String> refused = SET.add(values, type + "." + constant, BotConstants.outcome(type, constant));
                if (refused.isPresent()) return refused.get();
                held.add(constant);
            }
        }
        return null;
    }

    private static String refused(String label, String holder) {
        return "'" + label + "' can't become a name in " + holder + ".";
    }

    /**
     * Declares the outcome {@code label} in the enum of the activity {@code type} when it does not hold it — what
     * the return slot's <i>+ New outcome…</i> does before it writes the value.
     *
     * @return empty when it is declared now or already was; else why not
     */
    public static Optional<String> declare(PluginValues values, String type, String label) {
        String constant = FlowNames.constantFor(label);
        if (constant == null) return Optional.of(refused(label, FlowNames.ACTIVITIES + "." + type));
        if (declared(values).getOrDefault(type, List.of()).contains(constant)) return Optional.empty();
        return SET.add(values, type + "." + constant, BotConstants.outcome(type, constant));
    }

    /**
     * Removes what the canvas dropped: each outcome an activity's enum holds that {@code board} no longer lists,
     * and the enum of each of {@code saved} — the activities the last save had — that it no longer draws, unless
     * another flow draws it ({@code elsewhere}, labels, asked only when a card left: it reads every other flow). A
     * name code still uses stays, and the host's sentence listing where goes into {@code notes}.
     */
    public static void forget(PluginValues values, Board board, Collection<String> saved,
                              Supplier<? extends Collection<String>> elsewhere, List<String> notes) {
        Map<String, List<String>> declared = declared(values);
        Set<String> drawn = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> activity : board.exits().entrySet()) {
            String type = FlowNames.typeFor(activity.getKey());
            if (type == null) continue;
            drawn.add(type);
            // An enum keeps a constant, for a body to return: prepare refuses an empty list.
            if (activity.getValue().isEmpty()) continue;
            // Every flow running the activity reads its outcomes off the one enum, so the canvas's list is the enum's.
            Set<String> wanted = new LinkedHashSet<>();
            for (String label : activity.getValue()) wanted.add(FlowNames.constantFor(label));
            for (String constant : declared.getOrDefault(type, List.of())) {
                if (wanted.contains(constant)) continue;
                SET.remove(values, type + "." + constant).ifPresent(refused ->
                        notes.add(FlowNames.ACTIVITIES + "." + type + "." + constant + " stays: " + refused));
            }
        }
        Set<String> others = null;
        for (String label : saved) {
            String type = FlowNames.typeFor(label);
            if (type == null || drawn.contains(type) || !declared.containsKey(type)) continue;
            if (others == null) {
                others = new LinkedHashSet<>();
                for (String other : elsewhere.get()) others.add(FlowNames.typeFor(other));
            }
            if (others.contains(type)) continue;
            values.removeGroup(id(), type).ifPresent(refused ->
                    notes.add(FlowNames.ACTIVITIES + "." + type + " stays: " + refused));
        }
    }

    /**
     * Why {@code flow} cannot be written over the project's enums as they stand — it names an activity or an
     * outcome none declares — or null. Asked after {@link #prepare}, so what is left is a name the canvas cannot
     * name back: one written by hand in another case ({@code bagFull}), whose label makes {@code BAG_FULL}.
     */
    public static String unheld(PluginValues values, Flow flow) {
        Map<String, List<String>> declared = declared(values);
        for (Flow.Step step : flow.steps()) {
            if (step.node() instanceof Flow.Sub || declared.containsKey(step.activity().name())) continue;
            return "The flow names " + FlowNames.ACTIVITIES + "." + step.activity().name() + ", which "
                    + FlowNames.ACTIVITIES + ".java does not declare.";
        }
        for (Flow.Edge edge : flow.edges()) {
            // The enum declaring the outcome: the card's own, or, out of a sub-flow card, the inner activity's.
            String type = edge.on() == null ? "" : edge.on().activity().name();
            if (declared.getOrDefault(type, List.of()).contains(edge.on().name())) continue;
            return "The flow names " + FlowNames.ACTIVITIES + "." + type + "." + edge.on().name() + ", which "
                    + FlowNames.ACTIVITIES + ".java does not declare. The Activity Flow names outcomes in upper "
                    + "case: rename the one written in another case to " + edge.on().name() + ".";
        }
        return null;
    }
}
