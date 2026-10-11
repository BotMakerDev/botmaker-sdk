package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.Runs;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.internal.bot.SetupOnce;
import com.botmaker.sdk.internal.config.ProjectDefaults;
import com.botmaker.sdk.internal.flow.RunChoices;
import com.botmaker.sdk.plugin.flow.FlowValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What this computer runs: which of the bot's flows, which of its switchable activities, and whether the setup is
 * due. Run properties ({@link RunChoices}, {@link SetupOnce}), never the bot's Java, so any later run here — the
 * assistant's included — takes them.
 */
final class RunTools {

    record Choices(@Describe(value = "the Flows constant this computer runs, DAILY; \"default\" for the one "
                           + "Sdk.run() names", optional = true) String flow,
                   @Describe(value = "activities to switch on, each one the runner may switch", optional = true)
                   List<String> on,
                   @Describe(value = "activities to switch off, each one the runner may switch", optional = true)
                   List<String> off,
                   @Describe(value = "true runs the setup again at the next run; false skips it", optional = true)
                   Boolean setup) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("get_run_choices")
                    .describedAs("What this computer runs: which flow, which activities the runner may switch and "
                            + "whether each is on, and whether the setup runs at the next run")
                    .takesNothing().handledBy(RunTools::getChoices),
            AssistantTool.named("set_run_choices")
                    .describedAs("Chooses for this computer, as the person running the bot does: the flow, the "
                            + "switchable activities on or off, and whether the setup runs again. Kept on this "
                            + "computer, not in the bot's files; every later run takes it")
                    .takes(Choices.class).handledBy(RunTools::setChoices));

    private RunTools() {}

    static AgentReply getChoices(AssistantTool.None none, AgentContext context) {
        StudioServices services = context.services();
        return FxCall.call(() -> AgentReply.text(describe(services)));
    }

    static AgentReply setChoices(Choices choices, AgentContext context) {
        StudioServices services = context.services();
        return FxCall.call(() -> {
            PluginValues values = services.pluginValues();
            Runs runs = services.runs();
            List<String> flows = FlowValue.FLOWS.members(values);
            String flow = runs.property(RunChoices.FLOW);
            boolean flowChanged = choices.flow() != null && !choices.flow().isBlank();
            if (flowChanged) {
                String asked = choices.flow().strip();
                if (asked.equalsIgnoreCase("default")) {
                    flow = null;
                } else {
                    Optional<String> known = flows.stream().filter(asked::equalsIgnoreCase).findFirst();
                    if (known.isEmpty()) {
                        return AgentReply.refused("Flows has no " + asked + "; it has " + flows + ".");
                    }
                    flow = known.get();
                }
            }
            Flow chosen = flowOf(services, flow);
            Map<String, String> switches = new LinkedHashMap<>();
            for (boolean on : new boolean[]{true, false}) {
                List<String> named = on ? choices.on() : choices.off();
                if (named == null) continue;
                for (String name : named) {
                    Flow.Step step = switchable(chosen, name);
                    if (step == null) {
                        return AgentReply.refused(name + " is not an activity the runner may switch in this flow; "
                                + "those are " + switchableNames(chosen) + ". Tick \"Runner may switch this off\" "
                                + "on its card to allow it.");
                    }
                    switches.put(RunChoices.property(step.activity()), on ? "on" : "off");
                }
            }
            if (flowChanged) runs.setProperty(RunChoices.FLOW, flow);
            switches.forEach(runs::setProperty);
            if (choices.setup() != null) {
                // Off with no hash: the next run reports the Setup parameters' values it is off for, so a change
                // to one still ticks it again. A stale hash would make it run anyway.
                runs.setProperty(SetupOnce.PARAMS_PROPERTY, null);
                runs.setProperty(SetupOnce.PROPERTY, choices.setup() ? "on" : "off");
            }
            return AgentReply.text(describe(services));
        });
    }

    /** This computer's choices, as {@code get_run_choices} says them. */
    static String describe(StudioServices services) {
        Runs runs = services.runs();
        List<String> flows = FlowValue.FLOWS.members(services.pluginValues());
        String byDefault = byDefault(services, flows);
        String flow = runs.property(RunChoices.FLOW);
        List<String> lines = new ArrayList<>();
        lines.add(flows.isEmpty() ? "The bot has no flow yet."
                : "Flows: " + String.join(", ", flows) + (byDefault == null ? "; Sdk.run() names none of them."
                : "; Sdk.run() runs " + byDefault + " by default."));
        if (flow != null && !flow.isBlank()) {
            lines.add("This computer runs Flows." + flow.strip()
                    + (flows.contains(flow.strip()) ? "." : ", which Flows does not have, so the default runs."));
        } else if (byDefault != null) {
            lines.add("This computer runs the default, Flows." + byDefault + ".");
        }
        Flow chosen = flowOf(services, flow);
        List<String> switches = new ArrayList<>();
        for (Flow.Step step : chosen.steps()) {
            if (!step.runnerSwitch()) continue;
            Boolean said = ProjectDefaults.parseBoolean(runs.property(RunChoices.property(step.activity())));
            boolean on = said != null ? said : step.enabled();
            switches.add(step.activity().name() + " " + (on ? "on" : "off") + (said == null ? " (as drawn)" : ""));
        }
        lines.add(switches.isEmpty() ? "The runner may switch none of its activities."
                : "Activities the runner may switch: " + String.join(", ", switches) + ".");
        // Whether a Setup parameter changed is known only to the run, which reads their values: said as such.
        boolean hasSetup = FlowValue.RUN.read(services).map(Run::setup).isPresent();
        boolean off = Boolean.FALSE.equals(ProjectDefaults.parseBoolean(runs.property(SetupOnce.PROPERTY)));
        lines.add(!hasSetup ? "The bot has no setup." : !off ? "The setup runs at the next run."
                : "The setup is off on this computer: the next run skips it unless a Setup parameter changed since.");
        return String.join("\n", lines);
    }

    /**
     * The constant of {@code flows} that {@code Sdk.run()} names, or null. Read only: {@link FlowValue#target} would
     * write {@code Sdk.java} into a project that has none.
     */
    private static String byDefault(StudioServices services, List<String> flows) {
        Flow flow = FlowValue.read(services);
        if (flow.equals(Flow.NONE)) return null;
        for (String member : flows) {
            if (FlowValue.FLOWS.read(services.pluginValues(), member).filter(flow::equals).isPresent()) return member;
        }
        return null;
    }

    /** The flow {@code name} picks, or the default one when it names none Flows has. */
    private static Flow flowOf(StudioServices services, String name) {
        if (name != null && !name.isBlank()) {
            Optional<Flow> named = FlowValue.FLOWS.read(services.pluginValues(), name.strip());
            if (named.isPresent()) return named.get();
        }
        return FlowValue.read(services);
    }

    /** The step of {@code flow} the runner may switch whose activity is {@code name} (its enum or label), or null. */
    private static Flow.Step switchable(Flow flow, String name) {
        if (name == null) return null;
        String wanted = name.strip();
        for (Flow.Step step : flow.steps()) {
            if (step.runnerSwitch() && (step.activity().name().equalsIgnoreCase(wanted)
                    || step.label().equalsIgnoreCase(wanted))) {
                return step;
            }
        }
        return null;
    }

    private static List<String> switchableNames(Flow flow) {
        return flow.steps().stream().filter(Flow.Step::runnerSwitch).map(step -> step.activity().name()).toList();
    }
}
