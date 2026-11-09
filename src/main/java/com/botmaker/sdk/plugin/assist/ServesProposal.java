package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.bot.BotConstants;
import com.botmaker.sdk.internal.params.ParamCheck;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.types.ScreenTypes;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Discovery's guess at what an input is for (2026-11-08): the facts read before and after pressing it, and each goal
 * whose check turned from no to yes between them. Only a proposal: try_input says it, 🗺 Screens badges the input,
 * and the author writes {@code .serves(Goals.X)} or not. Nothing here writes the bot.
 */
public final class ServesProposal {

    /** What was proposed this Studio session, by project, then by {@code SCREEN.field}: the goals' constants. */
    private static final Map<Path, Map<String, Set<String>>> PROPOSED = new ConcurrentHashMap<>();

    private ServesProposal() {}

    /**
     * A goal a press seems to serve.
     *
     * @param goal  its constant
     * @param moved what changed, {@code gold 40 → 120}
     */
    public record Proposed(String goal, String moved) {}

    /**
     * The goals of {@code goals} whose check answers no over {@code before} and yes over {@code after}, but those
     * {@code action} (the action on the pressed input, null for none) serves already. A fact is keyed as a check names
     * it, {@code Progress::gold}, its value as read; one missing answers unknown, which proposes nothing.
     */
    public static List<Proposed> of(Map<String, Goal> goals, Do action, Map<String, String> before,
                                    Map<String, String> after) {
        Set<String> serves = new LinkedHashSet<>();
        if (action != null) action.serves().forEach(goal -> BotConstants.goalName(goal).ifPresent(serves::add));
        List<Proposed> out = new ArrayList<>();
        goals.forEach((name, goal) -> {
            if (serves.contains(name) || !(goal instanceof Goal.Of of)) return;
            if (answer(of.check(), before) != Answer.NO || answer(of.check(), after) != Answer.YES) return;
            List<String> moved = new ArrayList<>();
            for (String fact : facts(of.check())) {
                String was = before.get(fact);
                String is = after.get(fact);
                if (!Objects.equals(was, is)) moved.add(fact.substring(fact.indexOf("::") + 2) + " " + was + " → " + is);
            }
            out.add(new Proposed(name, String.join(", ", moved)));
        });
        return List.copyOf(out);
    }

    /** What {@code check} answers over {@code facts}: only a check built of facts answers; any other, unknown. */
    static Answer answer(BooleanSupplier check, Map<String, String> facts) {
        return switch (check) {
            case ParamCheck param -> param(param, facts.get(ScreenTypes.factOf(param)));
            case Condition.Not not -> switch (answer(not.condition(), facts)) {
                case YES -> Answer.NO;
                case NO -> Answer.YES;
                case UNKNOWN -> Answer.UNKNOWN;
            };
            case Condition.All all -> all(all.conditions(), facts, Answer.NO, Answer.YES);
            case Condition.Any any -> all(any.conditions(), facts, Answer.YES, Answer.NO);
            case null, default -> Answer.UNKNOWN;
        };
    }

    /** {@code decides} as soon as one answers it, else unknown when one is unknown, else {@code otherwise}. */
    private static Answer all(List<Condition> conditions, Map<String, String> facts, Answer decides, Answer otherwise) {
        boolean unknown = false;
        for (Condition each : conditions) {
            Answer one = answer(each, facts);
            if (one == decides) return decides;
            unknown |= one == Answer.UNKNOWN;
        }
        return unknown ? Answer.UNKNOWN : otherwise;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Answer param(ParamCheck check, String text) {
        if (text == null) return Answer.UNKNOWN;
        Object known = read(check.value(), check.test(), text.strip());
        if (known == null) return Answer.UNKNOWN;
        boolean holds = switch (check.test()) {
            case AT_LEAST -> ((Comparable) known).compareTo(check.value()) >= 0;
            case AT_MOST -> ((Comparable) known).compareTo(check.value()) <= 0;
            case IS -> known.equals(check.value());
            case IS_TRUE -> (Boolean) known;
        };
        return holds ? Answer.YES : Answer.NO;
    }

    /** {@code text} as the kind of value {@code limit} is; null when it does not read as one. */
    private static Object read(Object limit, ParamCheck.Test test, String text) {
        try {
            return switch (limit) {
                case Integer ignored -> Integer.valueOf(text.replace(",", ""));
                case Long ignored -> Long.valueOf(text.replace(",", ""));
                case String ignored -> text;
                case null -> test == ParamCheck.Test.IS_TRUE && (text.equalsIgnoreCase("true")
                        || text.equalsIgnoreCase("false")) ? Boolean.valueOf(text) : null;
                default -> null;
            };
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** The facts {@code check} reads, as it names them. */
    private static Set<String> facts(BooleanSupplier check) {
        Set<String> out = new LinkedHashSet<>();
        switch (check) {
            case ParamCheck param -> out.add(ScreenTypes.factOf(param));
            case Condition.Not not -> out.addAll(facts(not.condition()));
            case Condition.All all -> all.conditions().forEach(each -> out.addAll(facts(each)));
            case Condition.Any any -> any.conditions().forEach(each -> out.addAll(facts(each)));
            case null, default -> { }
        }
        return out;
    }

    /**
     * What try_input says after pressing {@code field} of {@code screen} ({@code on}): the facts its readouts show on
     * {@code before} and {@code after}, over the values a run kept, and each goal whose check turned yes, noted for
     * the map's badge. Empty when the press left the screen ({@code stayed} false), no readout feeds a fact a goal
     * checks, no goal turned, or no project is open.
     */
    static Optional<String> after(AgentContext context, String screen, MapScreen on, String field, boolean stayed,
                                  BufferedImage before, BufferedImage after) {
        // A press that left the screen shows another one after: its readouts' areas hold something else there.
        if (!stayed || before == null || after == null) return Optional.empty();
        Map<String, Readout> shown = new LinkedHashMap<>();
        on.readouts().values().forEach(readout -> {
            String fact = ScreenTypes.factOf(readout);
            if (!fact.isEmpty()) shown.putIfAbsent(fact, readout);
        });
        StudioServices services = context.services();
        PluginValues values = services.pluginValues();
        if (shown.isEmpty() || values == null) return Optional.empty();
        record Known(Map<String, Goal> goals, Map<String, String> kept) {}
        Known known = FxCall.call(() -> {
            Map<String, Goal> goals = new LinkedHashMap<>();
            for (Member member : values.members(SdkValues.GOALS)) {
                values.open(SdkValues.GOALS, member).flatMap(ctx -> ctx.value(Goal.class))
                        .ifPresent(goal -> goals.put(member.name(), goal));
            }
            Map<String, String> kept = new LinkedHashMap<>();
            values.runParameters().forEach(p -> kept.put(p.group() + "::" + p.name(), p.value()));
            return new Known(goals, kept);
        });
        // Only the facts a goal checks are read off the frames: each reading is a look at the shot.
        Set<String> checked = new LinkedHashSet<>();
        known.goals().values().forEach(goal -> {
            if (goal instanceof Goal.Of of) checked.addAll(facts(of.check()));
        });
        Map<String, Readout> feeding = new LinkedHashMap<>(shown);
        feeding.keySet().retainAll(checked);
        if (feeding.isEmpty()) return Optional.empty();
        Optional<ToolReach> reach = ToolReach.of(context);
        if (reach.isEmpty()) return Optional.empty();
        // A fact the screen shows is what the frame reads, unknown when it reads nothing; any other keeps its value.
        Map<String, String> was = new LinkedHashMap<>(known.kept());
        Map<String, String> is = new LinkedHashMap<>(known.kept());
        feeding.forEach((fact, readout) -> {
            was.remove(fact);
            is.remove(fact);
            reach.get().readoutOrNone(readout, before).ifPresent(value -> was.put(fact, value));
            reach.get().readoutOrNone(readout, after).ifPresent(value -> is.put(fact, value));
        });
        Map.Entry<String, Do> action = on.actions().entrySet().stream()
                .filter(e -> presses(e.getValue(), screen, on, field)).findFirst().orElse(null);
        List<Proposed> proposed = of(known.goals(), action == null ? null : action.getValue(), was, is);
        if (proposed.isEmpty()) return Optional.empty();
        note(services.projectDir(), screen, field, proposed);
        return Optional.of(said(screen, field, action == null ? null : action.getKey(), proposed));
    }

    /**
     * Whether {@code action} presses the input {@code field} of {@code screen} ({@code on}): a reference to that very
     * field, or the input itself.
     */
    public static boolean presses(Do action, String screen, MapScreen on, String field) {
        if (action.input() == null) return false;
        BotConstants.InputName named = BotConstants.inputName(action.input());
        return named != null ? named.screen().equals(screen) && named.field().equals(field)
                : action.input() == on.inputs().get(field);
    }

    /** Notes {@code proposed} for {@code screen.field} of {@code project}, for the map's badge. */
    public static void note(Path project, String screen, String field, List<Proposed> proposed) {
        if (project == null || proposed.isEmpty()) return;
        PROPOSED.computeIfAbsent(project, p -> new ConcurrentHashMap<>())
                .computeIfAbsent(screen + "." + field, k -> ConcurrentHashMap.newKeySet())
                .addAll(proposed.stream().map(Proposed::goal).toList());
    }

    /**
     * The goals proposed for {@code screen.field} this session that {@code action}, the action on that input now
     * (null for none), does not serve yet: written or turned down, a proposal goes once the author writes it.
     */
    public static List<String> proposed(Path project, String screen, String field, Do action) {
        if (project == null) return List.of();
        Set<String> noted = PROPOSED.getOrDefault(project, Map.of()).getOrDefault(screen + "." + field, Set.of());
        Set<String> serves = new LinkedHashSet<>();
        if (action != null) action.serves().forEach(goal -> BotConstants.goalName(goal).ifPresent(serves::add));
        return noted.stream().filter(goal -> !serves.contains(goal)).sorted().toList();
    }

    /** Every proposal of {@code project} still open on {@code graph}, one line each, as read_map lists them. */
    public static List<String> open(Path project, ScreenGraph graph) {
        List<String> out = new ArrayList<>();
        graph.screens().forEach((screen, on) -> on.inputs().keySet().forEach(field -> {
            Do action = on.actions().values().stream().filter(a -> presses(a, screen, on, field)).findFirst().orElse(null);
            List<String> goals = proposed(project, screen, field, action);
            if (!goals.isEmpty()) {
                out.add(graph.shown(screen) + "." + field + " may serve " + String.join(", ", goals.stream()
                        .map(g -> "Goals." + g).toList()) + ": its check turned yes when it was pressed. Proposed, "
                        + "not written: ask the author.");
            }
        }));
        return out;
    }

    /** What try_input says of {@code proposed} for the input {@code screen.field}, whose action is {@code action}. */
    public static String said(String screen, String field, String action, List<Proposed> proposed) {
        Map<String, String> each = new LinkedHashMap<>();
        proposed.forEach(p -> each.put(p.goal(), p.moved()));
        String goals = String.join(", ", each.entrySet().stream()
                .map(e -> "Goals." + e.getKey() + (e.getValue().isEmpty() ? "" : " (" + e.getValue() + ")")).toList());
        String serves = String.join(", ", each.keySet().stream().map(g -> "Goals." + g).toList());
        return "Pressing " + field + " turned " + goals + " from no to yes: it may serve "
                + (proposed.size() == 1 ? "it" : "them") + ". Proposed, not written: ask the author, then "
                + (action == null ? "add an action Do.press(" + field + ").serves(" + serves + ") on " + screen
                : "add .serves(" + serves + ") to " + screen + "." + action) + ".";
    }
}
