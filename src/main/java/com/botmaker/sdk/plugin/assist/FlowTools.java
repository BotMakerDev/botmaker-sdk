package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.plugin.flow.FlowEdits;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.flow.FlowValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The assistant's activity flow: read it, and change it as 🔀 Activity Flow does — each change through
 * {@link FlowEdits}, saved with the {@code Activities} and {@code Outcomes} constants it needs, on the FX thread.
 */
final class FlowTools {

    record NewActivity(@Describe("the activity's name: Collect") String name,
                       @Describe(value = "its body as a method reference, Collect::body; leave out for none yet",
                               optional = true) String body) {
    }

    record Rename(@Describe("the activity's current name") String from,
                  @Describe("its new name") String to) {
    }

    record ActivityName(@Describe("the activity's name") String name) {
    }

    /** One arrow between two activities, on one outcome. */
    record Arrow(@Describe("the activity the arrow leaves") String from,
                 @Describe(value = "the outcome it leaves on; leave out for NEXT", optional = true) String outcome,
                 @Describe("the activity it goes to") String to) {
    }

    record Leaving(@Describe("the activity the arrow leaves") String from,
                   @Describe(value = "the outcome it leaves on; leave out for NEXT", optional = true) String outcome) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("read_flow")
                    .describedAs("The bot's activity flow: each activity with its body, switches and outcomes, "
                            + "every arrow, where a run starts, and its limits")
                    .takesNothing().handledBy(FlowTools::readFlow),
            AssistantTool.named("add_activity")
                    .describedAs("Adds an activity to the bot's flow, as the Activity Flow window does")
                    .takes(NewActivity.class).handledBy(FlowTools::addActivity),
            AssistantTool.named("rename_activity")
                    .describedAs("Renames an activity everywhere: its card, arrows, presets and constant")
                    .takes(Rename.class).handledBy(FlowTools::renameActivity),
            AssistantTool.named("remove_activity")
                    .describedAs("Removes an activity and its arrows from the flow")
                    .takes(ActivityName.class).handledBy(FlowTools::removeActivity),
            AssistantTool.named("connect")
                    .describedAs("Draws an arrow from an activity's outcome to the activity that runs next, "
                            + "replacing the arrow that outcome had")
                    .takes(Arrow.class).handledBy(FlowTools::connect),
            AssistantTool.named("disconnect")
                    .describedAs("Removes the arrow an activity's outcome leaves on; a run reporting it then "
                            + "stops there")
                    .takes(Leaving.class).handledBy(FlowTools::disconnect),
            AssistantTool.named("set_start")
                    .describedAs("Makes an activity the one a run starts at")
                    .takes(ActivityName.class).handledBy(FlowTools::setStart));

    private FlowTools() {}

    static AgentReply readFlow(AssistantTool.None none, AgentContext context) {
        StudioServices services = context.services();
        return FxCall.call(() -> {
            Optional<ValueContext> value = FlowValue.FLOW.open(services);
            if (value.isEmpty()) return AgentReply.refused("This project has no Sdk.flow().");
            if (!FlowValue.FLOW.readable(value.get())) return AgentReply.refused(handWritten());
            return AgentReply.text(describe(FlowValue.read(value.get())));
        });
    }

    /** {@code flow} as the lines {@code read_flow} answers. */
    static String describe(Flow flow) {
        if (flow.steps().isEmpty()) return "The flow has no activities yet; add_activity adds one.";
        List<String> lines = new ArrayList<>();
        lines.add("Starts at " + (flow.start().label().isEmpty() ? "nothing" : flow.start().label()) + "; at most "
                + flow.limits().maxSteps() + " steps, " + flow.limits().stepDelayMs() + " ms apart.");
        for (Flow.Step step : flow.steps()) {
            String body = FlowValue.bodySource(step);
            List<String> switches = new ArrayList<>();
            if (!step.enabled()) switches.add("off");
            if (step.goHome()) switches.add("goes home first");
            if (step.popupCheck()) switches.add("checks popups");
            lines.add("• " + step.label() + " — " + (body.isEmpty() ? "no body yet" : body)
                    + (switches.isEmpty() ? "" : ", " + String.join(", ", switches))
                    + (step.outcomes().isEmpty() ? "" : "; outcomes " + step.outcomes().stream().map(Outcome::label)
                    .toList())
                    + (step.description().isBlank() ? "" : ". " + step.description()));
            for (Flow.Edge edge : flow.edges()) {
                if (!edge.from().equals(step.activity())) continue;
                lines.add("    on " + edge.outcome().label() + " → " + edge.to().label());
            }
        }
        return String.join("\n", lines);
    }

    static AgentReply addActivity(NewActivity activity, AgentContext context) {
        return editFlow(context, flow -> FlowEdits.addActivity(flow, activity.name(), activity.body()),
                flow -> Map.of(), "Added " + activity.name() + ".");
    }

    static AgentReply renameActivity(Rename rename, AgentContext context) {
        // Keyed by the label the flow holds and the label the rename makes, which is what the constants'
        // plan looks up: "collect" typed for the activity "Collect" still renames Activities.COLLECT.
        return editFlow(context, flow -> FlowEdits.renameActivity(flow, rename.from(), rename.to()),
                flow -> FlowEdits.labelOf(flow, rename.from()).<Map<String, String>>map(
                        held -> Map.of(held, FlowNames.label(rename.to()))).orElse(Map.of()),
                "Renamed " + rename.from() + " to " + rename.to() + ".");
    }

    static AgentReply removeActivity(ActivityName name, AgentContext context) {
        return editFlow(context, flow -> FlowEdits.removeActivity(flow, name.name()), flow -> Map.of(),
                "Removed " + name.name() + ".");
    }

    static AgentReply connect(Arrow arrow, AgentContext context) {
        return editFlow(context, flow -> FlowEdits.connect(flow, arrow.from(), arrow.outcome(), arrow.to()),
                flow -> Map.of(), arrow.from() + " on " + outcome(arrow.outcome()) + " now goes to " + arrow.to() + ".");
    }

    static AgentReply disconnect(Leaving leaving, AgentContext context) {
        return editFlow(context, flow -> FlowEdits.disconnect(flow, leaving.from(), leaving.outcome()),
                flow -> Map.of(), leaving.from() + " on " + outcome(leaving.outcome()) + " now goes nowhere.");
    }

    static AgentReply setStart(ActivityName name, AgentContext context) {
        return editFlow(context, flow -> FlowEdits.setStart(flow, name.name()), flow -> Map.of(),
                "A run now starts at " + name.name() + ".");
    }

    /** Reads the flow, changes it, and saves it as 🔀 Activity Flow does — all on the FX thread. */
    private static AgentReply editFlow(AgentContext context, UnaryOperator<Flow> change,
                                       Function<Flow, Map<String, String>> activityRenames, String done) {
        StudioServices services = context.services();
        return FxCall.call(() -> {
            Optional<ValueContext> value = FlowValue.FLOW.open(services);
            if (value.isEmpty()) return AgentReply.refused("This project has no Sdk.flow() to write to.");
            // A flow the user wrote by hand reads as none, and writing over it would lose it: 🔀 Activity Flow
            // opens read-only for the same reason.
            if (!FlowValue.FLOW.readable(value.get())) return AgentReply.refused(handWritten());
            Flow before = FlowValue.read(value.get());
            Flow after;
            try {
                after = change.apply(before);
            } catch (IllegalArgumentException e) {
                return AgentReply.refused(e.getMessage());
            }
            List<String> notes = new ArrayList<>();
            String refused = FlowEdits.save(services.pluginValues(), value.get(), after, FlowEdits.Saved.of(before),
                    new LinkedHashMap<>(activityRenames.apply(before)), new LinkedHashMap<>(), notes);
            if (refused != null) return AgentReply.refused(refused);
            return AgentReply.text(done + (notes.isEmpty() ? "" : " " + String.join(" ", notes))
                    + " Activities: " + FlowEdits.labels(after) + ".");
        });
    }

    /** The outcome as the canvas labels it: its constant's words, "Bag full" for "bag full". */
    private static String outcome(String typed) {
        return typed == null || typed.isBlank() ? "NEXT" : FlowNames.outcome(typed).label();
    }

    private static String handWritten() {
        return "Sdk.flow() was written by hand, so it is left as it is. Edit it in the code, or rewrite it as "
                + "Flow.of(…) for the tools to edit.";
    }
}
