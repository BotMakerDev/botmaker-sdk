package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.screens.Pressed;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.internal.bot.Watchdog;
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
import java.time.Instant;
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
        PlanRun run = new PlanRun(plan, map, DoneRecords.run(), new RunGame(map), names::get,
                BotSettings.current().stuckAfter());
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

    /**
     * Presses {@code step}; when the game then shows no screen of the map, what covers it is cleared by the map's close
     * buttons, so the facts the run reads next are read off a screen it knows.
     */
    @Override
    public Pressed.Result press(Step step) {
        RunReach reach = new RunReach();
        // Its own record is the run's to write, once the facts it changed are read again.
        ScreenWalk walk = new ScreenWalk(map, reach, ScreenWalk.STEP_MS, null).uncovering();
        ScreenWalk.Press press = walk.press(step.screen(), step.field(), null);
        if (press.result() != Pressed.Result.DONE) Debug.log(press.why());
        String at = press.at();
        if (at == null) {
            Optional<ScreenWalk.Uncovered> cleared = reach.look().flatMap(frame -> walk.uncover(frame, step.screen()));
            if (cleared.isPresent()) {
                at = cleared.get().at();
                Debug.log(walk.covered(step.screen(), cleared.get()));
            }
        }
        if (at != null) {
            if (!at.equals(ScreenMaps.lastSeen())) Bots.fireScreen(at, map.shown(at));
            ScreenMaps.seen(at);
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
    public Optional<Instant> readAt(Method fact) {
        return RunFacts.readAt(fact);
    }

    @Override
    public boolean look() {
        Instant since = Instant.now();
        // Telling where the game is reads what is stale there; the rest is read once more after it.
        Optional<String> at = where();
        if (at.isEmpty()) return false;
        RunFacts.readEvery(at.get(), since);
        return new ScreenWalk(map, new RunReach(), ScreenWalk.STEP_MS, null).doing(DoneRecords.run())
                .doOnSight(at.get());
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

    @Override
    public void checkpoint() {
        // The match-repeat count is a flow's: the map's markers, found in place at every look, would grow it here.
        Watchdog.progress();
        Watchdog.checkpointGame();
    }
}
