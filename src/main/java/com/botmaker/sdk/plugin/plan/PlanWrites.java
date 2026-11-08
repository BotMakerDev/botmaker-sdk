package com.botmaker.sdk.plugin.plan;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.ScreenEdits;
import com.botmaker.sdk.plugin.pictures.ScreenMapRead;
import com.botmaker.sdk.plugin.plan.GoalsViewModel.Mark;

import java.util.Optional;
import java.util.function.UnaryOperator;

import static com.botmaker.sdk.plugin.assist.ScreenEdits.NOT_WRITTEN;

/**
 * The 🎯 Goals window's writes (2026-11-06), each one step of Studio's history through the contract: an action's
 * {@code .over}, {@code .first()} or {@code .last()}, a goal moved between a plan's tiers, a new goal or plan. Each
 * answers what it did, or {@link ScreenEdits#NOT_WRITTEN} and why not. The values come from {@link PlanEdits}.
 */
public final class PlanWrites {

    private PlanWrites() {}

    /**
     * Has the action {@code constant}.{@code field} tried before the action {@code aboveScreen}.{@code aboveField}: one
     * {@code .over(() -> Screens.<aboveScreen>.<aboveField>)} more. Refused with the planner's sentence when the two
     * would then each come before the other, through any chain of {@code .over}s.
     */
    public static String setOver(StudioServices services, String constant, String field, String aboveScreen,
                                 String aboveField) {
        return writeAction(services, constant, field, action -> PlanEdits.over(action, aboveScreen, aboveField),
                field + " is tried before " + aboveScreen + "." + aboveField + ".");
    }

    /** Puts the action {@code constant}.{@code field} first among those serving its goals, or last, or neither. */
    public static String setMark(StudioServices services, String constant, String field, Mark mark) {
        return writeAction(services, constant, field, action -> PlanEdits.marked(action, mark), switch (mark) {
            case FIRST -> field + " is tried first.";
            case LAST -> field + " is tried last, when nothing else can be.";
            case NONE -> field + " is tried in its turn.";
        });
    }

    private static String writeAction(StudioServices services, String constant, String field, UnaryOperator<Do> change,
                                      String done) {
        PluginValues values = services.pluginValues();
        if (values == null) return NOT_WRITTEN + "Studio has no source tree open.";
        ScreenGraph map = ScreenMapRead.read(services);
        Optional<Do> now = map.screen(constant).map(screen -> screen.actions().get(field));
        if (now.isEmpty()) return NOT_WRITTEN + constant + "." + field + " is no action of the map.";
        Do next = change.apply(now.get());
        Optional<String> refused = PlanEdits.refusedOver(map, constant, field, next);
        if (refused.isPresent()) return NOT_WRITTEN + refused.get();
        return said(ScreenEdits.historyStep(services, "Edit " + constant + "." + field,
                () -> ScreenMapRead.writeAction(values, constant, field, next)), done);
    }

    /**
     * Moves the goal {@code goal} of the plan {@code plan} into its tier {@code to}, or, when {@code newTier}, into a
     * tier of its own put there ({@link PlanEdits#moved}).
     */
    public static String setTier(StudioServices services, String plan, String goal, int to, boolean newTier) {
        PluginValues values = services.pluginValues();
        if (values == null) return NOT_WRITTEN + "Studio has no source tree open.";
        Optional<ValueContext> context = values.open(SdkValues.PLANS, Member.of(plan));
        Optional<Plan> now = context.flatMap(ctx -> ctx.value(Plan.class));
        if (now.isEmpty()) return NOT_WRITTEN + "Plans." + plan + " is written by hand, so it is left as it is.";
        Plan next;
        try {
            next = PlanEdits.moved(now.get(), goal, to, newTier);
        } catch (IllegalArgumentException noTier) {
            return NOT_WRITTEN + noTier.getMessage();
        }
        if (next.equals(now.get())) return goal + " is in that tier already.";
        int tier = PlanEdits.tierOf(next, goal) + 1;
        return said(ScreenEdits.historyStep(services, "Edit Plans." + plan, () -> context.get().write(next)),
                goal + (newTier ? " has a tier of its own, tier " : " is in tier ") + tier + " of " + plan + ".");
    }

    /** Adds the goal {@code name} to the bot's {@code Goals}, its check {@code goal}. */
    public static String addGoal(StudioServices services, String name, Goal goal) {
        PluginValues values = services.pluginValues();
        if (values == null) return NOT_WRITTEN + "Studio has no source tree open.";
        return said(ScreenEdits.historyStep(services, "Add Goals." + name,
                () -> values.add(SdkValues.GOALS, Member.of(name), goal)), "Goals." + name + " is added.");
    }

    /** Adds the plan {@code name} to the bot's {@code Plans}: one tier, the goal {@code goal} alone. */
    public static String addPlan(StudioServices services, String name, String goal) {
        PluginValues values = services.pluginValues();
        if (values == null) return NOT_WRITTEN + "Studio has no source tree open.";
        return said(ScreenEdits.historyStep(services, "Add Plans." + name,
                () -> values.add(SdkValues.PLANS, Member.of(name), PlanEdits.startedWith(goal))),
                "Plans." + name + " is added, " + goal + " its one tier.");
    }

    private static String said(Optional<String> failed, String done) {
        return failed.map(why -> NOT_WRITTEN + why).orElse(done);
    }
}
