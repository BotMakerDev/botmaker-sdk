package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.flow.FlowTree;
import com.botmaker.sdk.internal.flow.Labels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The bot's flows by name, with each sub-flow card resolved to the flow it names: what a card's exits are, which
 * flows run a flow, and which flows one may run without running itself — what the canvas, the flow tree and the
 * guards around them ask (2026-10-12).
 *
 * <p>An editor holds a sub-flow card's flow as a reference by name ({@link BotConstants#flow}), so every answer
 * here reads that name's constant. Each flow is read once per instance: make a new one after a change. Links no
 * JavaFX.
 */
public final class FlowRefs {

    private final List<String> names;
    private final Function<String, Optional<Flow>> read;
    private final Map<String, List<String>> declared;
    private final Map<String, Flow> flows = new HashMap<>();

    /**
     * @param names    the bot's {@code Flows} constants, in file order
     * @param read     what one of them holds, empty when the editor cannot read it
     * @param declared each activity enum's name → its constants' names, as {@link FlowConstants#declared} answers
     */
    FlowRefs(List<String> names, Function<String, Optional<Flow>> read, Map<String, List<String>> declared) {
        this.names = List.copyOf(names);
        this.read = read;
        this.declared = declared;
    }

    /** The open project's flows, as they stand now. */
    public static FlowRefs of(PluginValues values) {
        return new FlowRefs(FlowValue.flowNames(values),
                name -> FlowValue.FLOWS.read(values, PluginValues.Member.of(name)),
                FlowConstants.declared(values));
    }

    /** The bot's flows, in file order. */
    public List<String> names() {
        return names;
    }

    /** What the flow {@code name} holds: {@link Flow#NONE} when the bot has none of that name or it is unread. */
    public Flow flow(String name) {
        return flows.computeIfAbsent(name, n -> names.contains(n) ? read.apply(n).orElse(Flow.NONE) : Flow.NONE);
    }

    /** The flow a card runs, {@code DAILY}, when it is a sub-flow card; else null. */
    public static String flowOf(Flow.Node node) {
        return node instanceof Flow.Sub sub ? BotConstants.flowName(sub.inner()) : null;
    }

    /** The flows {@code name}'s sub-flow cards run, in card order, each once. */
    public List<String> subflows(String name) {
        Set<String> out = new LinkedHashSet<>();
        for (Flow.Step step : flow(name).steps()) {
            String inner = flowOf(step.node());
            if (inner != null) out.add(inner);
        }
        return List.copyOf(out);
    }

    /**
     * The outcomes the flow {@code name} can end on — a card running it has one exit each — in the order its cards
     * and their outcomes come ({@link FlowTree#endOutcomes}).
     */
    public List<Outcome> endOutcomes(String name) {
        return FlowTree.endOutcomes(flow(name), this::outcomesOf, this::resolved);
    }

    /** {@link #endOutcomes} as a card's port labels, {@code Claim · Done}. */
    public List<String> exits(String name) {
        return endOutcomes(name).stream().map(Labels::ofExit).toList();
    }

    /** The flows with a card running {@code name}, in file order. */
    public List<String> usersOf(String name) {
        return names.stream().filter(user -> subflows(user).contains(name)).toList();
    }

    /** Whether running {@code from} runs {@code to}: it is {@code to}, or a sub-flow card of it, at any depth, is. */
    public boolean runs(String from, String to) {
        return runs(from, to, new HashSet<>());
    }

    private boolean runs(String from, String to, Set<String> seen) {
        if (from.equals(to)) return true;
        if (!seen.add(from)) return false;
        for (String inner : subflows(from)) {
            if (runs(inner, to, seen)) return true;
        }
        return false;
    }

    /**
     * The flows a card in {@code host} may run: every other flow that does not run {@code host} itself, which would
     * walk it forever.
     */
    public List<String> offerable(String host) {
        return names.stream().filter(name -> !runs(name, host)).toList();
    }

    private List<? extends Outcome> outcomesOf(Activity activity) {
        return declared.getOrDefault(activity.name(), List.of()).stream()
                .map(constant -> BotConstants.outcome(activity.name(), constant)).toList();
    }

    /** The flow a reference names, the same object each time; any other flow as it is. */
    private Flow resolved(Flow inner) {
        String name = BotConstants.flowName(inner);
        return name == null ? inner : flow(name);
    }

    /** The exits {@code name} ends on, and the ones {@code wired} leaves on that are no longer among them. */
    public record Exits(List<String> live, List<String> broken) {

        public Exits {
            live = List.copyOf(live);
            broken = List.copyOf(broken);
        }

        /** Every port the card has: its live exits, then the broken ones. */
        public List<String> all() {
            List<String> all = new ArrayList<>(live);
            all.addAll(broken);
            return all;
        }
    }

    /**
     * The exits of a card running {@code name} in {@code host}: the ones its flow ends on, and the ones a wire of
     * {@code host} leaves on that it no longer ends on — an inner card was wired since, or its activity is gone —
     * kept so the wire is drawn broken rather than lost.
     */
    public Exits exitsIn(Flow host, String name) {
        List<String> live = exits(name);
        List<String> broken = new ArrayList<>();
        Flow.Node card = new Flow.Sub(BotConstants.flow(name));
        for (Flow.Edge edge : host.edges()) {
            if (edge.via() == null || !edge.from().equals(card) || edge.on() == null) continue;
            String exit = Labels.ofExit(edge.on());
            if (!live.contains(exit) && !broken.contains(exit)) broken.add(exit);
        }
        return new Exits(live, broken);
    }
}
