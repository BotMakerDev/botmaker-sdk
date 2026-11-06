package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.screens.Pressed;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.internal.config.ParamGroups;
import com.botmaker.sdk.internal.observe.Bots;
import com.botmaker.sdk.internal.params.RunFacts;
import com.botmaker.sdk.internal.plan.Round.Step;
import com.botmaker.sdk.internal.screens.RunReach;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import com.botmaker.sdk.internal.screens.ScreenWalk;
import com.botmaker.sdk.internal.trace.Say;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A {@link PlanRun}'s {@link PlanRun.Game} on the running bot's game: the map's walk and presses, its facts. */
public final class RunGame implements PlanRun.Game {

    /** The longest a wait sleeps at once, so a stop asked meanwhile ends it soon. */
    private static final long NAP_MS = 1_000;

    private final ScreenGraph map;

    private RunGame(ScreenGraph map) {
        this.map = map;
    }

    /**
     * The body that runs {@code plan} on the game, over the installed map and the bot's done-records; its goals are
     * named by the constants that hold them in the classes beside {@code holders}. One run of the plan for every time
     * the run's recovery calls the body again, so what it left be stays left be. Every goal met ends the run as a
     * flow's end does; a plan that cannot go on ends it as an error.
     */
    public static Runnable body(Plan plan, Class<?>... holders) {
        ScreenGraph map = ScreenMaps.installed();
        Map<Goal, String> names = names(holders);
        PlanRun run = new PlanRun(plan, map, DoneRecords.run(), new RunGame(map), names::get);
        return () -> {
            switch (run.run()) {
                case MET -> Bot.stop();
                case STUCK -> Bot.stop("The plan cannot go on: a goal of its tier has nothing that can be done for it."
                        + " Give the goal an action, or move it to a tier below.");
                case TOO_DEEP -> Bot.stop("The plan needs goals nested more than " + Planner.DEEPEST + " deep."
                        + " Have the action need a goal nearer the plan.");
            }
        };
    }

    /** Each {@code public static final Goal} of the classes beside {@code holders}, by its field's name. */
    static Map<Goal, String> names(Class<?>... holders) {
        Map<Goal, String> names = new IdentityHashMap<>();
        for (Class<?> type : ParamGroups.besideHolders(holders)) {
            try {
                for (Field field : type.getDeclaredFields()) {
                    int mods = field.getModifiers();
                    if (!Modifier.isStatic(mods) || !Modifier.isPublic(mods) || !Goal.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    if (field.get(null) instanceof Goal goal) names.putIfAbsent(goal, field.getName());
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unreadable) {
                // a class whose goals cannot be read names none
            }
        }
        return names;
    }

    @Override
    public Optional<String> where() {
        return Navigate.where().flatMap(map::constantOf);
    }

    @Override
    public boolean walk(String screen) {
        return map.instance(screen).map(Navigate::to).orElse(false);
    }

    @Override
    public Pressed.Result press(Step step) {
        // Its own record is the run's to write, once the facts it changed are read again.
        ScreenWalk.Press press = new ScreenWalk(map, new RunReach(), ScreenWalk.STEP_MS, null)
                .press(step.screen(), step.field(), null);
        if (press.result() != Pressed.Result.DONE) Debug.log(press.why());
        if (press.at() != null) {
            if (!press.at().equals(ScreenMaps.lastSeen())) Bots.fireScreen(press.at(), map.shown(press.at()));
            ScreenMaps.seen(press.at());
        }
        return press.result();
    }

    @Override
    public void readAgain(String screen, Set<Method> facts) {
        RunFacts.readAgain(screen, facts);
    }

    @Override
    public List<String> screensOf(Method fact) {
        return RunFacts.screensOf(fact);
    }

    @Override
    public boolean fresh(Method fact) {
        return RunFacts.fresh(fact);
    }

    @Override
    public void idle(Duration time) {
        long left = time.toMillis();
        while (left > 0) {
            long nap = Math.min(left, NAP_MS);
            Wait.milliseconds((int) nap);
            if (Thread.currentThread().isInterrupted()) Bot.stop();
            left -= nap;
        }
    }

    @Override
    public void say(String line, Map<String, String> values) {
        Say.info(line, values);
    }
}
