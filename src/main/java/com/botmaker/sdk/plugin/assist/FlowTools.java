package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.plugin.flow.Board;
import com.botmaker.sdk.plugin.flow.FlowConstants;
import com.botmaker.sdk.plugin.flow.FlowEdits;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.flow.FlowValue;
import com.botmaker.sdk.plugin.types.FlowTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The assistant's activity flow: read it, and change it as 🔀 Activity Flow does — each change through
 * {@link FlowEdits}, saved with the {@code Activities} enums it needs, on the FX thread.
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
                 @Describe(value = "the outcome it leaves on; leave out for its first outcome", optional = true) String outcome,
                 @Describe("the activity it goes to") String to) {
    }

    record Leaving(@Describe("the activity the arrow leaves") String from,
                   @Describe(value = "the outcome it leaves on; leave out for its first outcome", optional = true) String outcome) {
    }

    /** The run's own methods, one each for the whole bot: {@code .home(…)} and {@code .setup(…)}. */
    enum Hook {
        HOME, SETUP;

        Runnable of(Run run) {
            return this == HOME ? run.home() : run.setup();
        }

        Run set(Run run, Runnable method) {
            return this == HOME ? run.home(method) : run.setup(method);
        }
    }

    record FlowMethod(@Describe("home: gets the game back to a known screen; setup: makes the game ready, "
                              + "its settings included, after the bot starts it when its doSetup parameter is "
                              + "ticked") Hook which,
                      @Describe(value = "one of the bot's public static void methods with no parameter, as "
                              + "Gamebot::setup; leave out for none", optional = true) String method) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("read_flow")
                    .describedAs("The bot's activity flow (the first of its Flows): each activity with "
                            + "its body, switches and outcomes, every arrow, where a run starts, its limits, and "
                            + "the bot's home and setup")
                    .takesNothing().handledBy(FlowTools::readFlow),
            AssistantTool.named("add_activity")
                    .describedAs("Adds an activity to the bot's flow, as the Activity Flow window does")
                    .takes(NewActivity.class).handledBy(FlowTools::addActivity),
            AssistantTool.named("rename_activity")
                    .describedAs("Renames an activity everywhere: its card, arrows and constant")
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
                    .takes(ActivityName.class).handledBy(FlowTools::setStart),
            AssistantTool.named("set_flow_method")
                    .describedAs("Sets the bot's home or setup (one each, whichever flow runs) to one of the bot's "
                            + "methods; create the method with the code tools first")
                    .takes(FlowMethod.class).handledBy(FlowTools::setMethod));

    private FlowTools() {}

    static AgentReply readFlow(AssistantTool.None none, AgentContext context) {
        StudioServices services = context.services();
        return FxCall.call(() -> {
            FlowValue.Target target = FlowValue.target(services);
            if (target.problem() != null) return AgentReply.refused(target.problem());
            return AgentReply.text(describe(Board.read(services.pluginValues(), target.flow()), target));
        });
    }

    /** {@code board}, {@code target}'s flow, with its run's home and setup, as {@code read_flow} answers. */
    static String describe(Board board, FlowValue.Target target) {
        Flow flow = board.flow();
        Run run = target.run();
        List<String> lines = new ArrayList<>();
        lines.add(target.said());
        for (Hook hook : Hook.values()) {
            Runnable method = hook.of(run);
            String source = FlowTypes.sourceOf(method);
            lines.add(hook.name().charAt(0) + hook.name().substring(1).toLowerCase(Locale.ROOT) + ": "
                    + (method == null ? "none" : source.isEmpty() ? "written by hand" : source) + ".");
        }
        if (flow.steps().isEmpty()) {
            lines.add("The flow has no activities yet; add_activity adds one.");
            return String.join("\n", lines);
        }
        lines.add("Starts at " + (flow.start().label().isEmpty() ? "nothing" : flow.start().label()) + "; at most "
                + flow.limits().maxSteps() + " steps, " + flow.limits().stepDelayMs() + " ms apart.");
        for (Flow.Step step : flow.steps()) {
            String body = FlowValue.bodySource(step);
            List<String> switches = new ArrayList<>();
            if (!step.enabled()) switches.add("off");
            if (step.goHome()) switches.add("goes home first");
            if (step.popupCheck()) switches.add("checks popups");
            if (step.runnerSwitch()) switches.add("the runner may switch it off");
            lines.add("• " + step.label() + " — " + (body.isEmpty() ? "no body yet" : body)
                    + (switches.isEmpty() ? "" : ", " + String.join(", ", switches))
                    + "; outcomes " + board.exitsOf(step.label())
                    + (step.description().isBlank() ? "" : ". " + step.description()));
            for (Flow.Edge edge : flow.edges()) {
                if (!edge.from().equals(step.activity())) continue;
                lines.add("    on " + edge.on().label() + " → " + edge.to().label());
            }
            if (!step.whenOff().isNone()) lines.add("    when off → " + step.whenOff().label());
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
                        held -> Map.of(held, FlowNames.activityLabel(rename.to()))).orElse(Map.of()),
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

    /** Only a method the bot has: a name it lacks would not compile. */
    static AgentReply setMethod(FlowMethod set, AgentContext context) {
        if (set.which() == null) return AgentReply.refused("Say which: home or setup.");
        String which = set.which().name().toLowerCase(Locale.ROOT);
        String method = set.method() == null ? "" : set.method().strip();
        if (!method.isEmpty()) {
            List<String> held = FxCall.call(() -> FlowConstants.homes(context.services().pluginValues()));
            if (!held.contains(method)) {
                return AgentReply.refused("The bot has no public static void method " + method + "() with no "
                        + "parameter. It has: " + (held.isEmpty() ? "none" : String.join(", ", held)) + ".");
            }
        }
        StudioServices services = context.services();
        return FxCall.call(() -> {
            FlowValue.Target target = FlowValue.target(services);
            Run run = set.which().set(target.run(), FlowTypes.method(method));
            String refused = FlowValue.writeRun(services, target, run.home(), run.setup());
            if (refused != null) return AgentReply.refused(refused);
            return AgentReply.text(method.isEmpty() ? "The bot has no " + which + " now."
                    : "The bot's " + which + " is now " + method + ".");
        });
    }

    /** Reads the flow, changes it, and saves it as 🔀 Activity Flow does — all on the FX thread. */
    private static AgentReply editFlow(AgentContext context, UnaryOperator<Board> change,
                                       Function<Flow, Map<String, String>> activityRenames, String done) {
        StudioServices services = context.services();
        return FxCall.call(() -> {
            // A flow or run the user wrote by hand reads as none, and writing over it would lose it: 🔀 Activity
            // Flow opens read-only for the same reason.
            FlowValue.Target target = FlowValue.target(services);
            if (target.problem() != null) return AgentReply.refused(target.problem());
            Board before = Board.read(services.pluginValues(), target.flow());
            Board after;
            try {
                after = change.apply(before);
            } catch (IllegalArgumentException e) {
                return AgentReply.refused(e.getMessage());
            }
            List<String> notes = new ArrayList<>();
            String refused = FlowEdits.save(services, target, after, FlowEdits.labels(before.flow()),
                    new LinkedHashMap<>(activityRenames.apply(before.flow())), new LinkedHashMap<>(), notes);
            if (refused != null) return AgentReply.refused(refused);
            return AgentReply.text(done + (notes.isEmpty() ? "" : " " + String.join(" ", notes))
                    + " Activities: " + FlowEdits.labels(after.flow()) + ".");
        });
    }

    /** The outcome as the canvas labels it: its constant's words, "Bag full" for "bag full". */
    private static String outcome(String typed) {
        return typed == null || typed.isBlank() ? "its first outcome" : FlowNames.label(typed);
    }
}
