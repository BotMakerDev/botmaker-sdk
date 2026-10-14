package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.internal.flow.Labels;
import com.botmaker.sdk.plugin.flow.Board;
import com.botmaker.sdk.plugin.flow.FlowConstants;
import com.botmaker.sdk.plugin.flow.FlowEdits;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.flow.FlowRefs;
import com.botmaker.sdk.plugin.flow.FlowValue;
import com.botmaker.sdk.plugin.types.FlowTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The assistant's activity flows: read one, change it as 🔀 Activity Flow does — each change through
 * {@link FlowEdits}, saved with the {@code Activities} enums it needs, on the FX thread — and add, rename or remove
 * a flow. Every flow tool takes the flow by its {@code Flows} constant's name, {@link FlowValue#defaultName} when
 * left out. A sub-flow card is taken by the flow it runs; a wire leaving one names that flow ({@code fromFlow}) and
 * the inner activity's outcome it ended on, one of the card's exits ({@link FlowRefs#exits}).
 */
final class FlowTools {

    /** What every tool says of its {@code flow} argument. */
    private static final String WHICH = "which of the bot's Flows: MAIN; leave out for MAIN, or the first when there is "
            + "no MAIN";

    record Which(@Describe(value = WHICH, optional = true) String flow) {
    }

    record NewActivity(@Describe("the activity's name: Collect") String name,
                       @Describe(value = "its body as a method reference, Collect::body; leave out for none yet, or "
                               + "for the body another flow running it has", optional = true) String body,
                       @Describe(value = WHICH, optional = true) String flow) {
    }

    record Rename(@Describe("the activity's current name") String from,
                  @Describe("its new name") String to,
                  @Describe(value = WHICH, optional = true) String flow) {
    }

    /** What a tool says of a card it takes by name. */
    private static final String CARD = "an activity, or a sub-flow card by the flow it runs: DAILY";

    /** What a wire's {@code fromFlow} says. */
    private static final String FROM_FLOW = "when the arrow leaves a sub-flow card: the flow it runs, DAILY; from and "
            + "outcome are then the activity in that flow it ended at and the outcome it ended on";

    record ActivityName(@Describe("the card's name: " + CARD) String name,
                        @Describe(value = WHICH, optional = true) String flow) {
    }

    /** What {@code set_activity} says of a switch it takes. */
    private static final String KEPT = "; leave out to keep it as it is";

    record Card(@Describe("the card's name: " + CARD) String name,
                @Describe(value = "its body as a method reference, Collect::body: one of the bot's public static "
                        + "methods with no parameter returning the activity's enum" + KEPT, optional = true) String body,
                @Describe(value = "the one line the canvas shows under it, empty for none" + KEPT, optional = true) String description,
                @Describe(value = "true runs the bot's home before it" + KEPT, optional = true) Boolean goesHome,
                @Describe(value = "true lets the person running the bot switch it off for their machine" + KEPT,
                        optional = true) Boolean switchable,
                @Describe(value = WHICH, optional = true) String flow) {
    }

    private static final String ON = "the outcome it leaves on; leave out for its first outcome, DISABLED for where "
            + "the run goes when the card is switched off or has no body";

    /** One arrow between two cards, on one outcome. */
    record Arrow(@Describe("the activity the arrow leaves") String from,
                 @Describe(value = ON, optional = true) String outcome,
                 @Describe("the card it goes to: " + CARD) String to,
                 @Describe(value = FROM_FLOW, optional = true) String fromFlow,
                 @Describe(value = WHICH, optional = true) String flow) {
    }

    record Leaving(@Describe("the activity the arrow leaves") String from,
                   @Describe(value = ON, optional = true) String outcome,
                   @Describe(value = FROM_FLOW, optional = true) String fromFlow,
                   @Describe(value = WHICH, optional = true) String flow) {
    }

    record NewSubflow(@Describe("the flow the card runs: DAILY") String name,
                      @Describe(value = WHICH + "; the flow the card goes in", optional = true) String flow) {
    }

    record NewFlow(@Describe("the new flow's name: DAILY") String name,
                   @Describe(value = "a flow to copy, MAIN; leave out for an empty one", optional = true) String copyOf) {
    }

    record RenameFlow(@Describe("the flow's current name") String from,
                      @Describe("its new name") String to) {
    }

    record FlowName(@Describe("the flow's name") String name) {
    }

    /** The run's own methods, one each for the whole bot: {@code .home(…)}, {@code .setup(…)}, {@code .popups(…)}. */
    enum Hook {
        HOME, SETUP, POPUPS;

        Runnable of(Run run) {
            return switch (this) {
                case HOME -> run.home();
                case SETUP -> run.setup();
                case POPUPS -> run.popups();
            };
        }

        Run set(Run run, Runnable method) {
            return switch (this) {
                case HOME -> run.home(method);
                case SETUP -> run.setup(method);
                case POPUPS -> run.popups(method);
            };
        }
    }

    record FlowMethod(@Describe("home: gets the game back to a known screen; setup: makes the game ready, "
                              + "its settings included, after the bot starts it when its doSetup parameter is "
                              + "ticked; popups: clears a popup the game opened on its own, run before every "
                              + "look at the screen of every activity") Hook which,
                      @Describe(value = "one of the bot's public static void methods with no parameter, as "
                              + "Gamebot::setup; leave out for none", optional = true) String method) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("read_flow")
                    .describedAs("The bot's flows by name, then one flow: each activity with its body, switches and "
                            + "outcomes, each sub-flow card with its exits, every arrow, where a run starts, its "
                            + "limits, and the bot's home, setup and popups")
                    .takes(Which.class).handledBy(FlowTools::readFlow),
            AssistantTool.named("add_subflow")
                    .describedAs("Adds a card running another of the bot's flows: the run walks that flow to its "
                            + "end, then leaves the card on the outcome it ended on (its exits; wire them with "
                            + "connect and fromFlow). A flow that runs this one can't be added")
                    .takes(NewSubflow.class).handledBy(FlowTools::addSubflow),
            AssistantTool.named("add_activity")
                    .describedAs("Adds an activity to a flow, as the Activity Flow window does; one another flow "
                            + "runs keeps its body and outcomes")
                    .takes(NewActivity.class).handledBy(FlowTools::addActivity),
            AssistantTool.named("set_activity")
                    .describedAs("Changes a card in one flow as the Activity Flow window does: its body, its line, "
                            + "and whether it goes home first or may be switched off by the runner")
                    .takes(Card.class).handledBy(FlowTools::setActivity),
            AssistantTool.named("rename_activity")
                    .describedAs("Renames an activity everywhere: its card in every flow, arrows and constant")
                    .takes(Rename.class).handledBy(FlowTools::renameActivity),
            AssistantTool.named("remove_activity")
                    .describedAs("Removes an activity and its arrows from a flow; other flows keep it")
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
                    .describedAs("Makes an activity the one a run of the flow starts at")
                    .takes(ActivityName.class).handledBy(FlowTools::setStart),
            AssistantTool.named("add_flow")
                    .describedAs("Adds a flow, empty or a copy of another; the bot's flow parameter picks which "
                            + "one runs")
                    .takes(NewFlow.class).handledBy(FlowTools::addFlow),
            AssistantTool.named("rename_flow")
                    .describedAs("Renames a flow and every use of it, the bot's flow parameter included")
                    .takes(RenameFlow.class).handledBy(FlowTools::renameFlow),
            AssistantTool.named("remove_flow")
                    .describedAs("Deletes a flow no code names; its activities stay for the other flows")
                    .takes(FlowName.class).handledBy(FlowTools::removeFlow),
            AssistantTool.named("set_flow_method")
                    .describedAs("Sets the bot's home, setup or popups (one each, whichever flow runs) to one of the bot's "
                            + "methods; create the method with the code tools first")
                    .takes(FlowMethod.class).handledBy(FlowTools::setMethod));

    private FlowTools() {}

    static AgentReply readFlow(Which which, AgentContext context) {
        StudioServices services = context.services();
        return FxCall.call(() -> {
            FlowValue.Target target = FlowValue.target(services, which.flow());
            if (target.problem() != null) return AgentReply.refused(target.problem());
            FlowRefs refs = FlowRefs.of(services.pluginValues());
            List<String> names = refs.names().stream().map(name -> {
                List<String> users = refs.usersOf(name);
                return users.isEmpty() ? name : name + " (a card in " + String.join(", ", users) + ")";
            }).toList();
            String flows = "Flows: " + (names.isEmpty() ? "none yet" : String.join(", ", names)) + ".\n";
            return AgentReply.text(flows + describe(Board.read(services.pluginValues(), target.flow()), target, refs));
        });
    }

    /** {@code board}, {@code target}'s flow, with its run's home and setup, as {@code read_flow} answers. */
    static String describe(Board board, FlowValue.Target target, FlowRefs refs) {
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
            List<String> switches = new ArrayList<>();
            if (!step.enabled()) switches.add("off");
            if (step.goHome()) switches.add("goes home first");
            if (step.runnerSwitch()) switches.add("the runner may switch it off");
            String switched = switches.isEmpty() ? "" : ", " + String.join(", ", switches);
            String described = step.description().isBlank() ? "" : ". " + step.description();
            String inner = FlowRefs.flowOf(step.node());
            if (inner != null) {
                // A card's exits are its flow's ends, read off that flow; a wire on one it no longer ends on stays.
                FlowRefs.Exits exits = refs.exitsIn(flow, inner);
                lines.add("• " + step.label() + " — runs Flows." + inner + switched + "; exits "
                        + (exits.live().isEmpty() ? "none (Flows." + inner + " has no card yet)" : exits.live())
                        + (exits.broken().isEmpty() ? "" : "; wired but no longer an end of Flows." + inner + ": "
                        + exits.broken())
                        + described);
            } else {
                String body = FlowValue.bodySource(step);
                lines.add("• " + step.label() + " — " + (body.isEmpty() ? "no body yet" : body) + switched
                        + "; outcomes " + board.exitsOf(step.label()) + described);
            }
            for (Flow.Edge edge : flow.edges()) {
                if (!edge.from().equals(step.node()) || edge.on() == null) continue;
                String on = edge.via() == null ? edge.on().label() : Labels.ofExit(edge.on());
                lines.add("    on " + on + " → " + edge.to().label());
            }
            if (!step.whenOff().isNone()) lines.add("    when off → " + step.whenOff().label());
        }
        return String.join("\n", lines);
    }

    static AgentReply addSubflow(NewSubflow sub, AgentContext context) {
        StudioServices services = context.services();
        String typed = sub.name() == null ? "" : sub.name().strip();
        // First the inner flow goes above this one, as Add sub-flow in 🔀 Activity Flow moves it; then the card.
        Placed placed = FxCall.call(() -> {
            FlowValue.Target target = FlowValue.target(services, sub.flow());
            if (target.problem() != null) return Placed.refused(target.problem());
            FlowRefs refs = FlowRefs.of(services.pluginValues());
            String inner = FlowNames.constantFor(typed);
            if (inner == null || !refs.names().contains(inner)) {
                return Placed.refused("There is no Flows." + typed + "; the bot's flows are " + refs.names()
                        + ". add_flow adds one.");
            }
            if (!refs.offerable(target.name()).contains(inner)) {
                return Placed.refused(inner.equals(target.name()) ? "A flow can't run itself as a card."
                        : "Flows." + inner + " runs Flows." + target.name() + ", so a card running it there would "
                        + "walk forever.");
            }
            // Checked before anything moves, so a refusal leaves Flows.java as it was.
            if (target.flow().steps().stream().anyMatch(s -> inner.equals(FlowRefs.flowOf(s.node())))) {
                return Placed.refused("Flows." + target.name() + " already has a card running Flows." + inner + ".");
            }
            String refused = FlowValue.placeAbove(services, inner, target.name());
            if (refused != null) {
                return Placed.refused("Flows." + inner + " is declared below Flows." + target.name() + " and couldn't "
                        + "be moved above it, so a card running it would read nothing: " + refused);
            }
            return new Placed(inner, refs.exits(inner), null);
        });
        if (placed.refused() != null) return AgentReply.refused(placed.refused());
        String inner = placed.inner();
        return editFlow(context, sub.flow(), board -> FlowEdits.addSubflow(board, inner), flow -> Map.of(),
                "Added " + FlowNames.subflowLabel(inner) + ", which runs Flows." + inner + "; "
                        + (placed.exits().isEmpty() ? "it has no exit until Flows." + inner + " has a card."
                        : "its exits, each wired with connect and fromFlow " + inner + ": " + placed.exits() + "."));
    }

    /** Where {@link #addSubflow} stands once the inner flow is checked and above: its name and exits, or why not. */
    private record Placed(String inner, List<String> exits, String refused) {
        static Placed refused(String why) {
            return new Placed(null, List.of(), why);
        }
    }

    static AgentReply addActivity(NewActivity activity, AgentContext context) {
        StudioServices services = context.services();
        return editFlow(context, activity.flow(), board -> {
            // An activity another flow runs is the same enum: its outcomes, and its step as there unless a body
            // is given — as Add from another flow in 🔀 Activity Flow.
            PluginValues values = services.pluginValues();
            String label = FlowNames.activityLabel(activity.name());
            List<String> outcomes = FlowConstants.outcomesOf(values, label);
            boolean bodyGiven = activity.body() != null && !activity.body().isBlank();
            Optional<Flow.Step> shared = bodyGiven ? Optional.empty() : FlowValue.stepElsewhere(values, label, null);
            return shared.isPresent() ? FlowEdits.addShared(board, shared.get(), outcomes)
                    : FlowEdits.addActivity(board, activity.name(), activity.body(), outcomes);
        }, flow -> Map.of(), "Added " + activity.name() + ".");
    }

    /** Only a body the bot has: one returning another enum, or no method at all, would not compile. */
    static AgentReply setActivity(Card card, AgentContext context) {
        StudioServices services = context.services();
        return editFlow(context, card.flow(), board -> {
            String label = card(board, card.name());
            String type = FlowNames.typeFor(label);
            // A card that is no activity of this flow is the edit's to refuse, with its own sentence.
            if (!blank(card.body()) && type != null && FlowEdits.labelOf(board.flow(), label).isPresent()
                    && FlowNames.subflowOf(label) == null) {
                List<String> bodies = FlowConstants.bodies(services.pluginValues(), type);
                if (!bodies.contains(card.body().strip())) {
                    throw new IllegalArgumentException("The bot has no public static method " + card.body().strip()
                            + "() with no parameter returning Activities." + type + ". It has: "
                            + (bodies.isEmpty() ? "none; add_method writes one" : String.join(", ", bodies)) + ".");
                }
            }
            return FlowEdits.setCard(board, label, card.body(), card.description(), card.goesHome(),
                    card.switchable());
        }, flow -> Map.of(), "Changed " + card.name() + ".");
    }

    static AgentReply renameActivity(Rename rename, AgentContext context) {
        // Keyed by the label the flow holds and the label the rename makes, which is what the constants'
        // plan looks up: "collect" typed for the activity "Collect" still renames Activities.COLLECT.
        StudioServices services = context.services();
        return editFlow(context, rename.flow(), board -> {
            String held = FlowEdits.labelOf(board.flow(), rename.from()).orElse(rename.from());
            String problem = FlowConstants.renameProblem(services.pluginValues(), held, rename.to());
            if (problem != null) throw new IllegalArgumentException(problem);
            return FlowEdits.renameActivity(board, rename.from(), rename.to());
        },
                flow -> FlowEdits.labelOf(flow, rename.from()).<Map<String, String>>map(
                        held -> Map.of(held, FlowNames.activityLabel(rename.to()))).orElse(Map.of()),
                "Renamed " + rename.from() + " to " + rename.to() + ".");
    }

    static AgentReply removeActivity(ActivityName name, AgentContext context) {
        return editFlow(context, name.flow(), board -> FlowEdits.removeActivity(board, card(board, name.name())),
                flow -> Map.of(), "Removed " + name.name() + ".");
    }

    static AgentReply connect(Arrow arrow, AgentContext context) {
        StudioServices services = context.services();
        if (blank(arrow.fromFlow())) {
            return editFlow(context, arrow.flow(),
                    board -> FlowEdits.connect(board, card(board, arrow.from()), arrow.outcome(),
                            card(board, arrow.to())),
                    flow -> Map.of(), arrow.from() + " on " + outcome(arrow.outcome()) + " now goes to " + arrow.to()
                            + ".");
        }
        return editFlow(context, arrow.flow(), board -> {
            // A broken exit — wired, but no longer an end of the inner flow — may be moved as well as removed.
            String inner = innerOf(board, arrow.fromFlow());
            List<String> exits = FlowRefs.of(services.pluginValues()).exitsIn(board.flow(), inner).all();
            String exit = exit(exits, inner, arrow.from(), arrow.outcome());
            return FlowEdits.connectExit(board, inner, exit, card(board, arrow.to()));
        }, flow -> Map.of(), arrow.fromFlow() + "'s card on " + arrow.from() + " ending on "
                + outcome(arrow.outcome()) + " now goes to " + arrow.to() + ".");
    }

    static AgentReply disconnect(Leaving leaving, AgentContext context) {
        StudioServices services = context.services();
        if (blank(leaving.fromFlow())) {
            return editFlow(context, leaving.flow(),
                    board -> FlowEdits.disconnect(board, card(board, leaving.from()), leaving.outcome()),
                    flow -> Map.of(),
                    leaving.from() + " on " + outcome(leaving.outcome()) + " now goes nowhere.");
        }
        return editFlow(context, leaving.flow(), board -> {
            // A broken exit — wired, but no longer an end of the inner flow — is unwired like any other.
            String inner = innerOf(board, leaving.fromFlow());
            List<String> exits = FlowRefs.of(services.pluginValues()).exitsIn(board.flow(), inner).all();
            return FlowEdits.disconnectExit(board, inner, exit(exits, inner, leaving.from(), leaving.outcome()));
        }, flow -> Map.of(), leaving.fromFlow() + "'s card on " + leaving.from() + " ending on "
                + outcome(leaving.outcome()) + " now goes nowhere.");
    }

    static AgentReply setStart(ActivityName name, AgentContext context) {
        return editFlow(context, name.flow(), board -> FlowEdits.setStart(board, card(board, name.name())),
                flow -> Map.of(), "A run now starts at " + name.name() + ".");
    }

    /**
     * The label of the card {@code typed} names on {@code board}: an activity's as typed, a sub-flow card's for its
     * label ({@code ▸ DAILY}) or for the flow it runs ({@code daily}). Anything else as typed, for the edit to refuse.
     */
    private static String card(Board board, String typed) {
        if (typed == null || FlowEdits.labelOf(board.flow(), typed).isPresent()) return typed;
        String flow = FlowNames.subflowOf(typed.strip());
        String inner = FlowNames.constantFor(flow == null ? typed : flow);
        if (inner == null) return typed;
        return FlowEdits.labelOf(board.flow(), FlowNames.subflowLabel(inner)).orElse(typed);
    }

    /** The flow a card on {@code board} runs that {@code typed} names — {@code daily} or {@code ▸ DAILY} — or refused. */
    private static String innerOf(Board board, String typed) {
        // A card, never an activity: "daily" is ▸ DAILY here even beside an activity called Daily.
        String flow = FlowNames.subflowOf(typed.strip());
        String constant = FlowNames.constantFor(flow == null ? typed.strip() : flow);
        String label = constant == null ? typed : FlowNames.subflowLabel(constant);
        String inner = FlowNames.subflowOf(label);
        if (inner == null || FlowEdits.labelOf(board.flow(), label).isEmpty()) {
            throw new IllegalArgumentException("This flow has no card running Flows." + typed.strip()
                    + "; add_subflow adds one.");
        }
        return inner;
    }

    /**
     * The exit among {@code exits} ({@code Claim · Done}) of the card running {@code inner} that {@code activity}
     * ending on {@code outcome} names; a blank outcome is that activity's first exit.
     */
    private static String exit(List<String> exits, String inner, String activity, String outcome) {
        String prefix = FlowNames.activityLabel(activity == null ? "" : activity) + Labels.EXIT;
        List<String> own = exits.stream().filter(e -> e.regionMatches(true, 0, prefix, 0, prefix.length())).toList();
        String wanted = blank(outcome) ? null : prefix + FlowNames.label(outcome);
        for (String exit : own) {
            if (wanted == null || exit.equalsIgnoreCase(wanted)) return exit;
        }
        throw new IllegalArgumentException("The card running Flows." + inner + " has no exit "
                + (wanted == null ? "from " + activity : wanted) + "; its exits are " + exits + ".");
    }

    /** " Sub-flow cards: [▸ DAILY]." when {@code flow} has any, else nothing. */
    private static String cards(Flow flow) {
        List<String> cards = flow.steps().stream().filter(s -> FlowRefs.flowOf(s.node()) != null)
                .map(Flow.Step::label).toList();
        return cards.isEmpty() ? "" : " Sub-flow cards: " + cards + ".";
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    static AgentReply addFlow(NewFlow flow, AgentContext context) {
        StudioServices services = context.services();
        return FxCall.call(() -> {
            boolean copy = flow.copyOf() != null && !flow.copyOf().isBlank();
            String refused;
            if (copy) {
                FlowValue.Target from = FlowValue.target(services, flow.copyOf());
                refused = from.problem() != null ? from.problem()
                        : FlowValue.duplicate(services, from.name(), flow.name());
            } else {
                refused = FlowValue.create(services, flow.name(), Flow.NONE);
            }
            if (refused != null) return AgentReply.refused(refused);
            return AgentReply.text("Added Flows." + FlowNames.constantFor(flow.name())
                    + (copy ? ", a copy of " + flow.copyOf().strip() : ", empty") + ". Flows: "
                    + String.join(", ", FlowValue.names(services)) + ".");
        });
    }

    static AgentReply renameFlow(RenameFlow rename, AgentContext context) {
        StudioServices services = context.services();
        return FxCall.call(() -> {
            FlowValue.Target from = FlowValue.target(services, rename.from());
            if (from.value().isEmpty()) return AgentReply.refused(from.problem() != null ? from.problem()
                    : "Flows." + from.name() + " is not written yet; add_activity writes it.");
            String refused = FlowValue.rename(services, from.name(), rename.to());
            if (refused != null) return AgentReply.refused(refused);
            return AgentReply.text("Renamed Flows." + from.name() + " to Flows." + FlowNames.constantFor(rename.to())
                    + ", and every use of it.");
        });
    }

    static AgentReply removeFlow(FlowName name, AgentContext context) {
        StudioServices services = context.services();
        return FxCall.call(() -> {
            FlowValue.Target target = FlowValue.target(services, name.name());
            if (target.value().isEmpty()) return AgentReply.refused(target.problem() != null ? target.problem()
                    : "There is no Flows." + target.name() + " to remove.");
            String refused = FlowValue.remove(services, target.name());
            if (refused != null) return AgentReply.refused(refused);
            List<String> left = FlowValue.names(services);
            return AgentReply.text("Removed Flows." + target.name() + ". Flows: "
                    + (left.isEmpty() ? "none" : String.join(", ", left)) + ".");
        });
    }

    /** Only a method the bot has: a name it lacks would not compile. */
    static AgentReply setMethod(FlowMethod set, AgentContext context) {
        if (set.which() == null) return AgentReply.refused("Say which: home, setup or popups.");
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
            String refused = FlowValue.writeRun(services, target, run);
            if (refused != null) return AgentReply.refused(refused);
            return AgentReply.text(method.isEmpty() ? "The bot has no " + which + " now."
                    : "The bot's " + which + " is now " + method + ".");
        });
    }

    /** Reads the flow {@code name} names, changes it, and saves it as 🔀 Activity Flow does — all on the FX thread. */
    private static AgentReply editFlow(AgentContext context, String name, UnaryOperator<Board> change,
                                       Function<Flow, Map<String, String>> activityRenames, String done) {
        StudioServices services = context.services();
        return FxCall.call(() -> {
            // A flow or run the user wrote by hand reads as none, and writing over it would lose it: 🔀 Activity
            // Flow opens read-only for the same reason.
            FlowValue.Target target = FlowValue.target(services, name);
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
            return AgentReply.text("Flows." + target.name() + ": " + done
                    + (notes.isEmpty() ? "" : " " + String.join(" ", notes))
                    + " Activities: " + FlowEdits.labels(after.flow()) + "."
                    + cards(after.flow()));
        });
    }

    /** The outcome as the canvas labels it: its constant's words, "Bag full" for "bag full". */
    private static String outcome(String typed) {
        return typed == null || typed.isBlank() ? "its first outcome" : FlowNames.label(typed);
    }
}
