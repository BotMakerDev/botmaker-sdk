package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.plugin.types.FlowTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * The bot's flows, the {@link Run} holding the game's home and setup, and the card positions beside them, read
 * and written as values.
 *
 * <p>Each is a handle on {@link SdkValues}' declarations, so no id is spelled here.
 *
 * <p><b>Which flow is edited.</b> A flow is found by its constant's name, never by its value: two constants may
 * hold equal flows (a duplicate not yet changed). Which flow runs is the bot's {@code @Param}, which this plugin
 * does not read, so when no name is given the editor and the assistant's tools edit {@link #defaultName}:
 * {@code Flows.MAIN}, else the first constant. A project with no flow yet edits {@link #FIRST}, written on the first
 * save.
 *
 * <p><b>Reading may answer nothing, and that is ordinary.</b> A hand-written initialiser, a call to something
 * other than {@code Flow.of}, an activity whose body is a lambda rather than a method reference — each of them
 * leaves the expression exactly as the user wrote it and answers {@link Flow#NONE} here. The editor's job then
 * is to say so, not to replace it.
 */
public final class FlowValue {

    /** The flows, {@code Flows.MAIN} and its siblings. */
    public static final ManagedSet<Flow> FLOWS = ManagedSet.of(SdkValues.FLOWS);

    /** The game's home and setup, {@code Sdk.run()}. */
    public static final ManagedHandle<Run> RUN = ManagedHandle.of(SdkValues.RUN);

    /** Where each flow's cards sit, {@code Sdk.flowLayout()}. */
    public static final ManagedHandle<FlowLayout> LAYOUT = ManagedHandle.of(SdkValues.FLOW_LAYOUT);

    /** The flow a project with none gets on its first save. */
    public static final String FIRST = "MAIN";

    private FlowValue() {}

    /**
     * The flow being edited: its constant's name, its value context when the constant is already written,
     * and the run around it; or why it cannot be edited.
     *
     * @param name    the {@code Flows} constant, {@code MAIN}
     * @param value   that constant's initialiser, or empty when it is not written yet
     * @param flow    what the constant holds, {@link Flow#NONE} when it is not written yet
     * @param run     {@code Sdk.run()} as read, {@link Run#NONE} when it is not written yet
     * @param problem why nothing can be written, or {@code null}
     */
    public record Target(String name, Optional<ValueContext> value, Flow flow, Run run, String problem) {

        public Target {
            value = value == null ? Optional.empty() : value;
            flow = flow == null ? Flow.NONE : flow;
            run = run == null ? Run.NONE : run;
        }

        static Target refused(String problem) {
            return refused(FIRST, problem);
        }

        static Target refused(String name, String problem) {
            return new Target(name, Optional.empty(), Flow.NONE, Run.NONE, problem);
        }

        /** The sentence that says which flow this is, for a reader who cannot see the code. */
        public String said() {
            if (value.isPresent()) return "Flows." + name + ".";
            return "No flow yet; the first change writes Flows." + name + ".";
        }
    }

    /** The bot's flows, the names of its {@code Flows} constants in file order. */
    public static List<String> names(StudioServices services) {
        return flowNames(services.pluginValues());
    }

    /** What a change naming no flow is refused with. */
    private static final String NO_FLOW_NAMED = "No flow was named.";

    /** The names of the bot's {@code Flows} constants, in file order. */
    public static List<String> flowNames(PluginValues values) {
        return FLOWS.members(values).stream().map(Member::name).toList();
    }

    /** The flow edited when none is named: {@link #FIRST} when there is one or none at all, else the first. */
    public static String defaultName(List<String> names) {
        return names.isEmpty() || names.contains(FIRST) ? FIRST : names.getFirst();
    }

    /** The flow edited when none is named ({@link #defaultName}), opened for writing. */
    public static Target target(StudioServices services) {
        return target(services, null);
    }

    /**
     * The flow {@code name} names — {@code DAILY}, or "daily" typed — opened for writing; {@link #defaultName} when
     * {@code name} is blank. A name the bot has no constant for is refused, but for {@link #FIRST} on a project with
     * no flow at all, which the first save writes.
     */
    public static Target target(StudioServices services, String name) {
        Optional<ValueContext> runValue = RUN.openOrCreate(services);
        if (runValue.isEmpty()) {
            // The host writes a missing Sdk.java, and adds run() to one that lacks it (2026-10-12), so what is left
            // is one of three, and the host's reason does not reach a plugin through openOrCreate.
            return Target.refused("BotMaker has no @SdkValue(RUN) method it can write, so there is nothing to save "
                    + "the flow into. Either the project is open read-only, Sdk.run() is hand-written in a form "
                    + "this editor cannot read, or the Sdk class is outside plugins/sdk/.");
        }
        Optional<Run> run = RUN.read(runValue.get());
        if (run.isEmpty()) {
            return Target.refused("Sdk.run() isn't written as Run.of() this editor reads, so the flow is shown empty "
                    + "and left alone. Edit it in Sdk.java, or replace it with Run.of() to draw it here.");
        }
        PluginValues values = services.pluginValues();
        List<String> members = flowNames(values);
        String picked = name == null || name.isBlank() ? defaultName(members) : named(name);
        if (!members.contains(picked)) {
            if (members.isEmpty() && picked.equals(FIRST)) {
                return new Target(FIRST, Optional.empty(), Flow.NONE, run.get(), null);
            }
            return Target.refused(picked, "The bot has no flow Flows." + picked + "; it has " + listed(members) + ".");
        }
        Optional<ValueContext> value = FLOWS.open(values, Member.of(picked));
        Optional<Flow> flow = value.flatMap(FLOWS::read);
        if (flow.isEmpty()) {
            return new Target(picked, Optional.empty(), Flow.NONE, run.get(), "Flows." + picked + " isn't one this "
                    + "editor wrote, so it is shown empty and left alone. Edit it in Flows.java, or replace it with "
                    + "Flow.of(…) to draw it here.");
        }
        return new Target(picked, value, flow.get(), run.get(), null);
    }

    /**
     * The labels of the activities any of the bot's flows draws but {@code besides}, in flow order: an activity is
     * one enum whichever flow runs it, so one flow dropping it leaves it to the others. {@code besides} may be null.
     */
    public static Set<String> labels(PluginValues values, String besides) {
        Set<String> labels = new LinkedHashSet<>();
        for (String member : flowNames(values)) {
            if (member.equals(besides)) continue;
            FLOWS.read(values, Member.of(member)).ifPresent(flow -> labels.addAll(FlowEdits.labels(flow)));
        }
        return labels;
    }

    /**
     * The step of the activity {@code label} as the first flow but {@code besides} running it has it — its body,
     * description and switches — or empty when no other flow runs it. {@code besides} may be null.
     */
    public static Optional<Flow.Step> stepElsewhere(PluginValues values, String label, String besides) {
        for (String member : flowNames(values)) {
            if (member.equals(besides)) continue;
            for (Flow.Step step : FLOWS.read(values, Member.of(member)).orElse(Flow.NONE).steps()) {
                if (step.label().equals(label)) return Optional.of(step);
            }
        }
        return Optional.empty();
    }

    /**
     * Adds the flow {@code typed} names, holding {@code flow} — {@link Flow#NONE} for an empty one — on the JavaFX
     * application thread. A bot with no flow yet gets {@link #FIRST} first: the editor shows it before it is
     * written, and a second flow would otherwise make it vanish.
     *
     * @return null when it was written, else why it could not be
     */
    public static String create(StudioServices services, String typed, Flow flow) {
        PluginValues values = services.pluginValues();
        String name = FlowNames.constantFor(typed);
        List<String> names = flowNames(values);
        String problem = nameProblem(typed, name, names);
        if (problem != null) return problem;
        if (names.isEmpty() && !FIRST.equals(name)) {
            String refused = FLOWS.add(values, Member.of(FIRST), Flow.NONE).orElse(null);
            if (refused != null) return refused;
        }
        return FLOWS.add(values, Member.of(name), FlowTypes.written(flow)).orElse(null);
    }

    /**
     * Adds the flow {@code typed} names as a copy of {@code from}'s, its cards where {@code from}'s sit. The two hold
     * equal flows until one changes, and a parameter picking either is written as the first of them.
     *
     * @return null when it was written, else why it could not be
     */
    public static String duplicate(StudioServices services, String from, String typed) {
        if (from == null || from.isBlank()) return NO_FLOW_NAMED;
        Optional<Flow> flow = FLOWS.read(services.pluginValues(), Member.of(from));
        if (flow.isEmpty()) return "Flows." + from + " isn't one this editor reads, so it can't be copied.";
        String refused = create(services, typed, flow.get());
        if (refused == null) relayout(services, layout -> layout.with(FlowNames.constantFor(typed), layout.cardsOf(from)));
        return refused;
    }

    /**
     * Renames the flow {@code from} to the one {@code typed} names, and every use of it ({@code Sdk.flow} included),
     * its cards following it. On the JavaFX application thread.
     *
     * @return null when it was renamed, else why it could not be
     */
    public static String rename(StudioServices services, String from, String typed) {
        if (from == null || from.isBlank()) return NO_FLOW_NAMED;
        PluginValues values = services.pluginValues();
        String name = FlowNames.constantFor(typed);
        if (from.equals(name)) return null;
        List<String> others = new ArrayList<>(flowNames(values));
        others.remove(from);
        String problem = nameProblem(typed, name, others);
        if (problem != null) return problem;
        String refused = FLOWS.rename(values, Member.of(from), name).orElse(null);
        if (refused == null) relayout(services, layout -> subflowMoved(moved(layout, from, name), from, name));
        return refused;
    }

    /**
     * Deletes the flow {@code name} and its cards' places; refused while another flow runs it as a sub-flow card,
     * or code names it — the bot's {@code flow} parameter, most often. The activities stay: they are the bot's,
     * whichever flow runs them.
     *
     * @return null when it was deleted, else why it could not be
     */
    public static String remove(StudioServices services, String name) {
        if (name == null || name.isBlank()) return NO_FLOW_NAMED;
        PluginValues values = services.pluginValues();
        List<String> users = FlowRefs.of(values).usersOf(name);
        if (!users.isEmpty()) {
            return "Flows." + name + " runs as a sub-flow card in " + listed(users) + ": remove "
                    + (users.size() == 1 ? "that card" : "those cards") + " first.";
        }
        String refused = FLOWS.remove(values, Member.of(name)).orElse(null);
        if (refused == null) relayout(services, layout -> moved(layout, name, null));
        return refused;
    }

    /**
     * Declares the flow {@code inner} above {@code host}, when it is below: a card of {@code host} running
     * {@code Flows.DAILY} reads a constant not yet set when {@code DAILY} is declared below it, and a run refuses
     * that. The flows {@code inner}'s own cards run, at any depth, go above it first, each one's before its user,
     * so none is left below a card reading it. Nothing moves when it is already above, or when {@code host} is not
     * written yet — its first save declares it last. On the JavaFX application thread.
     *
     * @return null when it is above now, else why not
     */
    public static String placeAbove(StudioServices services, String inner, String host) {
        PluginValues values = services.pluginValues();
        if (!flowNames(values).contains(host)) return null;
        List<String> order = new ArrayList<>();
        innermostFirst(FlowRefs.of(values), inner, order);
        for (String flow : order) {
            List<String> members = flowNames(values);
            if (members.indexOf(flow) < members.indexOf(host)) continue;
            String refused = FLOWS.moveBefore(values, Member.of(flow), Member.of(host)).orElse(null);
            if (refused != null) return refused;
        }
        return null;
    }

    /** {@code name} and every flow its cards run, each after the ones it runs; each once. */
    private static void innermostFirst(FlowRefs refs, String name, List<String> order) {
        if (order.contains(name)) return;
        order.add(name);   // marks it seen, so a hand-written loop ends; put back at its place below
        for (String inner : refs.subflows(name)) innermostFirst(refs, inner, order);
        order.remove(name);
        order.add(name);
    }

    /** {@code layout} with every flow's sub-flow card running {@code from} kept under {@code to}'s card. */
    static FlowLayout subflowMoved(FlowLayout layout, String from, String to) {
        String was = FlowNames.subflowLabel(from);
        String now = FlowNames.subflowLabel(to);
        FlowLayout out = layout;
        for (Map.Entry<String, FlowLayout.Cards> flow : layout.flows().entrySet()) {
            if (!flow.getValue().spots().containsKey(was)) continue;
            Map<String, FlowLayout.Spot> spots = new LinkedHashMap<>();
            flow.getValue().spots().forEach((card, spot) -> spots.put(card.equals(was) ? now : card, spot));
            out = out.with(flow.getKey(), FlowLayout.cards(spots));
        }
        return out;
    }

    /** Why {@code typed}, written {@code name}, cannot name a flow beside {@code taken}, or null. */
    static String nameProblem(String typed, String name, List<String> taken) {
        if (name == null) return "'" + (typed == null ? "" : typed.strip()) + "' makes no constant name: start it "
                + "with a letter.";
        if (taken.contains(name)) return "The bot already has a flow Flows." + name + ".";
        return null;
    }

    /** {@code layout} with {@code from}'s cards under {@code to}, or dropped when {@code to} is null. */
    static FlowLayout moved(FlowLayout layout, String from, String to) {
        if (!layout.flows().containsKey(from)) return layout;
        Map<String, FlowLayout.Cards> flows = new LinkedHashMap<>();
        layout.flows().forEach((flow, cards) -> {
            if (!flow.equals(from)) flows.put(flow, cards);
            else if (to != null) flows.put(to, cards);
        });
        return FlowLayout.of(flows, layout.goHomeByDefault());
    }

    /** Rewrites {@code Sdk.flowLayout()} by {@code change}, when the project has one this plugin reads. */
    private static void relayout(StudioServices services, UnaryOperator<FlowLayout> change) {
        Optional<ValueContext> value = LAYOUT.open(services);
        Optional<FlowLayout> read = value.flatMap(LAYOUT::read);
        if (read.isEmpty()) return;
        FlowLayout next = change.apply(read.get());
        if (!next.equals(read.get())) value.get().set(next);
    }

    private static String named(String typed) {
        String constant = FlowNames.constantFor(typed);
        return constant == null ? typed.strip() : constant;
    }

    private static String listed(List<String> names) {
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    /**
     * How {@code activity}'s body is written in the file — {@code Collect::body} — or {@code ""}: the text the
     * host also reports as a slot's enclosing method, so an editor can tell which activity a body belongs to.
     */
    public static String bodySource(Flow.Step step) {
        return FlowTypes.sourceOf(step.body());
    }

    /**
     * Writes {@code flow} as {@code target}'s constant, on the JavaFX application thread: into its initialiser when
     * it is written, else as a new {@code Flows} constant. An activity whose body is not a method reference is
     * refused where it is typed, so there is nothing else to report from here.
     *
     * @return null when it was written, else why it could not be
     */
    public static String write(StudioServices services, Target target, Flow flow) {
        if (target.problem() != null) return target.problem();
        Flow written = FlowTypes.written(flow);
        if (target.value().isPresent()) {
            // write, not set: set only puts a refusal on the status line, and the editor then said "Saved".
            return target.value().get().write(written).orElse(null);
        }
        return FLOWS.add(services.pluginValues(), Member.of(target.name()), written).orElse(null);
    }

    /**
     * Writes the run's home, setup and popups: {@code Sdk.run()} is written only when one of them changed. On the
     * JavaFX application thread.
     *
     * @return null when it was written or nothing changed, else why it could not be
     */
    public static String writeRun(StudioServices services, Target target, Run run) {
        if (target.problem() != null) return target.problem();
        if (Objects.equals(target.run(), run)) return null;
        return RUN.write(services, run).orElse(null);
    }
}
