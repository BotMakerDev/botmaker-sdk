package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.plugin.types.FlowTypes;

import java.util.Objects;
import java.util.Optional;

/**
 * The bot's flows, the {@link Run} that names the one running by default, and the card positions beside them,
 * read and written as values.
 *
 * <p>Each is a handle on {@link SdkValues}' declarations, so no id is spelled here.
 *
 * <p><b>Which flow is edited.</b> Until the editor picks among flows (named flows, phase 4), the editor and the
 * assistant's tools edit the flow {@code Sdk.run()} names. A {@code Run} holds the flow's value, not its name,
 * so the name is the {@code Flows} constant holding that value ({@link #target}). A project with no flow yet
 * edits {@link #FIRST}, written on the first save together with the {@code Run} naming it.
 *
 * <p><b>Reading may answer nothing, and that is ordinary.</b> A hand-written initialiser, a call to something
 * other than {@code Flow.of}, an activity whose body is a lambda rather than a method reference — each of them
 * leaves the expression exactly as the user wrote it and answers {@link Flow#NONE} here. The editor's job then
 * is to say so, not to replace it.
 */
public final class FlowValue {

    /** The flows, {@code Flows.MAIN} and its siblings. */
    public static final ManagedSet<Flow> FLOWS = ManagedSet.of(SdkValues.FLOWS);

    /** Which flow runs by default, and the game's home and setup, {@code Sdk.run()}. */
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
     * @param named   whether {@code Sdk.run()} already names this flow; when not, the next write points it here
     * @param problem why nothing can be written, or {@code null}
     */
    public record Target(String name, Optional<ValueContext> value, Flow flow, Run run, boolean named,
                         String problem) {

        public Target {
            value = value == null ? Optional.empty() : value;
            flow = flow == null ? Flow.NONE : flow;
            run = run == null ? Run.NONE : run;
        }

        static Target refused(String problem) {
            return new Target(FIRST, Optional.empty(), Flow.NONE, Run.NONE, false, problem);
        }

        /** The sentence that says which flow this is, for a reader who cannot see the code. */
        public String said() {
            if (named) return "Flows." + name + ", the flow Sdk.run() runs by default.";
            if (value.isPresent()) {
                return "Flows." + name + "; Sdk.run() names no flow yet, and the next change makes it run this one.";
            }
            return "No flow yet; the first change writes Flows." + name + " and makes Sdk.run() run it.";
        }
    }

    /**
     * The flow {@code Sdk.run()} names, opened for writing: the {@code Flows} constant equal to the run's flow,
     * else — a run naming nothing yet — the first constant there is, else {@link #FIRST}, not written yet.
     */
    public static Target target(StudioServices services) {
        Optional<ValueContext> runValue = RUN.openOrCreate(services);
        if (runValue.isEmpty()) {
            return Target.refused("This project has no Sdk.java with an @SdkValue(RUN) method, so there is nothing "
                    + "to save the flow into. BotMaker writes plugins/sdk/Sdk.java when the project opens and has no "
                    + "Sdk class anywhere; reopen the project to get it.");
        }
        Optional<Run> run = RUN.read(runValue.get());
        if (run.isEmpty()) {
            return Target.refused("Sdk.run() isn't written as Run.of(Flows.…) this editor reads, so the flow is shown"
                    + " empty and left alone. Edit it in Sdk.java, or replace it with Run.of(Flows.MAIN) to draw it here.");
        }
        PluginValues values = services.pluginValues();
        boolean runsNothing = run.get().flow().equals(Flow.NONE);
        String named = null;
        String first = null;
        for (String member : FLOWS.members(values)) {
            if (first == null) first = member;
            // A run of nothing names no constant, even an empty one: the first is edited, and the save points
            // Sdk.run() at it.
            if (named == null && !runsNothing
                    && FLOWS.read(values, member).filter(run.get().flow()::equals).isPresent()) {
                named = member;
            }
        }
        String name = named != null ? named : runsNothing ? first : null;
        if (name == null && !runsNothing) {
            return new Target(FIRST, Optional.empty(), Flow.NONE, run.get(), false, "Sdk.run() names a flow that isn't "
                    + "a constant of Flows this editor reads, so it is shown empty and left alone. Name one of Flows' "
                    + "constants in Run.of(…) to draw it here.");
        }
        if (name == null) return new Target(FIRST, Optional.empty(), Flow.NONE, run.get(), false, null);
        Optional<ValueContext> value = FLOWS.open(values, name);
        Optional<Flow> flow = value.flatMap(FLOWS::read);
        if (flow.isEmpty()) {
            return new Target(name, Optional.empty(), Flow.NONE, run.get(), named != null, "Flows." + name + " isn't "
                    + "one this editor wrote, so it is shown empty and left alone. Edit it in Flows.java, or replace it "
                    + "with Flow.of(…) to draw it here.");
        }
        return new Target(name, value, flow.get(), run.get(), named != null, null);
    }

    /** The flow {@code services}' project runs by default, or {@link Flow#NONE}. Read afresh every time. */
    public static Flow read(StudioServices services) {
        return RUN.read(services).map(Run::flow).orElse(Flow.NONE);
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
     * it is written, else as a new {@code Flows} constant; then, when {@code Sdk.run()} did not name it, points
     * {@code Sdk.run()} at it. An activity whose body is not a method reference is refused where it is typed, so
     * there is nothing else to report from here.
     *
     * @return null when it was written, else why it could not be
     */
    public static String write(StudioServices services, Target target, Flow flow) {
        if (target.problem() != null) return target.problem();
        Flow written = FlowTypes.written(flow);
        if (target.value().isPresent()) {
            target.value().get().set(written);
        } else {
            Optional<String> refused = FLOWS.add(services.pluginValues(), target.name(), written);
            if (refused.isPresent()) return refused.get();
        }
        if (target.named()) return null;
        return RUN.write(services, new Run(written, target.run().home(), target.run().setup()));
    }

    /**
     * Writes the run's home and setup, the flow it names kept: {@code Sdk.run()} is written only when one of them
     * changed. On the JavaFX application thread, after the flow, so the flow it names is the one just written.
     *
     * @return null when it was written or nothing changed, else why it could not be
     */
    public static String writeRun(StudioServices services, Target target, Flow flow, Runnable home, Runnable setup) {
        if (target.problem() != null) return target.problem();
        Run run = target.run();
        if (Objects.equals(run.home(), home) && Objects.equals(run.setup(), setup)) return null;
        return RUN.write(services, new Run(FlowTypes.written(flow), home, setup));
    }
}
