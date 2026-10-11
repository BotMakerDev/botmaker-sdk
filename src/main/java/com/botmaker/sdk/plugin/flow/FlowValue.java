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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The bot's flows, the {@link Run} holding the game's home and setup, and the card positions beside them, read
 * and written as values.
 *
 * <p>Each is a handle on {@link SdkValues}' declarations, so no id is spelled here.
 *
 * <p><b>Which flow is edited.</b> Which flow runs is the bot's {@code @Param}, which this plugin does not read.
 * Until the editor picks among flows (named flows, phase 4), the editor and the assistant's tools edit the first
 * {@code Flows} constant. A project with no flow yet edits {@link #FIRST}, written on the first save.
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
            return new Target(FIRST, Optional.empty(), Flow.NONE, Run.NONE, problem);
        }

        /** The sentence that says which flow this is, for a reader who cannot see the code. */
        public String said() {
            if (value.isPresent()) return "Flows." + name + ".";
            return "No flow yet; the first change writes Flows." + name + ".";
        }
    }

    /**
     * The flow edited, opened for writing: the first {@code Flows} constant, else {@link #FIRST}, not written yet.
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
            return Target.refused("Sdk.run() isn't written as Run.of() this editor reads, so the flow is shown empty "
                    + "and left alone. Edit it in Sdk.java, or replace it with Run.of() to draw it here.");
        }
        PluginValues values = services.pluginValues();
        List<String> members = FLOWS.members(values);
        if (members.isEmpty()) return new Target(FIRST, Optional.empty(), Flow.NONE, run.get(), null);
        String name = members.getFirst();
        Optional<ValueContext> value = FLOWS.open(values, name);
        Optional<Flow> flow = value.flatMap(FLOWS::read);
        if (flow.isEmpty()) {
            return new Target(name, Optional.empty(), Flow.NONE, run.get(), "Flows." + name + " isn't one this "
                    + "editor wrote, so it is shown empty and left alone. Edit it in Flows.java, or replace it with "
                    + "Flow.of(…) to draw it here.");
        }
        return new Target(name, value, flow.get(), run.get(), null);
    }

    /** The flow the editor edits, or {@link Flow#NONE}. Read afresh every time. */
    public static Flow read(StudioServices services) {
        PluginValues values = services.pluginValues();
        return FLOWS.members(values).stream().findFirst().flatMap(name -> FLOWS.read(values, name))
                .orElse(Flow.NONE);
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
            target.value().get().set(written);
            return null;
        }
        return FLOWS.add(services.pluginValues(), target.name(), written).orElse(null);
    }

    /**
     * Writes the run's home and setup: {@code Sdk.run()} is written only when one of them changed. On the JavaFX
     * application thread.
     *
     * @return null when it was written or nothing changed, else why it could not be
     */
    public static String writeRun(StudioServices services, Target target, Runnable home, Runnable setup) {
        if (target.problem() != null) return target.problem();
        Run run = target.run();
        if (Objects.equals(run.home(), home) && Objects.equals(run.setup(), setup)) return null;
        return RUN.write(services, new Run(home, setup));
    }
}
