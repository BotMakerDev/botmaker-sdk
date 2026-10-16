package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.internal.bot.BotStuckException;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The run a bot does: the flow it walks, its home and setup, the activities its runner switched off, and the names
 * of the bot's flows.
 *
 * <p>{@code Bot.runFlow(Sdk.class)} installs the value your {@code @SdkValue(SdkValue.Id.RUN)} method
 * returns ({@link #use}) and the flow its {@code @Param} names ({@link #walk}), then walks that flow: two hand-offs,
 * since the run holds no flow. Named apart from the bot's own {@code Flows} class, which a bot's code names
 * and this one would shadow.
 *
 * <p>Kept out of the insert menus: a dropped {@code use} would be written {@code InstalledRun.use(null)} and clear
 * the run, {@code installed} hands back a {@code Run} nothing in Studio takes, and {@code enabled} is
 * {@code ActivitySwitch.active} without the overrides. Its members stay catalogued so the name resolves.
 */
public final class InstalledRun {

    private static volatile Run current = Run.NONE;

    private static volatile Flow flow = Flow.NONE;

    private static volatile Set<Activity> skipped = Set.of();

    /** Each of the bot's flows by its constant's name, by identity: two flows may be equal and named apart. */
    private static volatile Map<Flow, String> names = Map.of();

    private InstalledRun() {}

    /**
     * Uses {@code run} from now on. {@code null} clears it back to {@link Run#NONE}, which has no home and no setup.
     *
     * <p>Installing twice keeps the later run, which is what a reader would expect of a call named like this one.
     */
    public static void use(Run run) {
        current = run == null ? Run.NONE : run;
    }

    /** Walks {@code walked} from now on. {@code null} clears it back to {@link Flow#NONE}, which runs nothing. */
    public static void walk(Flow walked) {
        flow = walked == null ? Flow.NONE : walked;
    }

    /** The installed run, or {@link Run#NONE} when nothing has been installed. Never {@code null}. */
    public static Run installed() {
        return current;
    }

    /**
     * What gets the game home: the run's {@link Run#home()}, else a {@code Navigate.to} the map's root screen
     * (2026-10-16), else {@code null} for nothing. Read at each call, so a map installed later counts. A walk to the
     * root that does not arrive throws, so the run's recovery restarts the game rather than taking it as home.
     */
    public static Runnable home() {
        if (current.home() != null) return current.home();
        ScreenGraph map = ScreenMaps.installed();
        return map.root().flatMap(root -> map.instance(root).<Runnable>map(screen -> () -> {
            if (!Navigate.to(screen)) {
                throw new BotStuckException("The run could not walk home to " + map.shown(root) + ".");
            }
        })).orElse(null);
    }

    /** The flow being walked, or {@link Flow#NONE}. Never {@code null}. */
    public static Flow flow() {
        return flow;
    }

    /** Switches {@code activities} off for this run, wherever a step drawn {@code .switchable()} runs them. */
    public static void skip(Set<Activity> activities) {
        skipped = activities == null ? Set.of() : Set.copyOf(activities);
    }

    /** Whether the runner switched {@code step} off: it is switchable, and its activity is listed to skip. */
    public static boolean skipped(Flow.Step step) {
        return step.runnerSwitch() && skipped.contains(step.node());
    }

    /**
     * Whether the flow has the activity switched on — its configured default, before any
     * {@code enable}/{@code disable} a running bot has made. The first step running it, in the flow or in one of its
     * sub-flows, answers.
     *
     * <p><b>An activity the flow does not mention is on.</b> A bot may name an activity that is not on the
     * canvas at all, and reading an absent one as <em>off</em> would make it silently do nothing.
     */
    public static boolean enabled(Activity activity) {
        Flow.Step found = FlowTree.find(flow(), activity);
        return found == null || (found.enabled() && !skipped(found));
    }

    /**
     * Learns the names of the bot's flows: each {@code static final Flow} of the {@code Flows} class beside each of
     * {@code holders} ({@code Sdk.class}), and of the holders themselves. A sub-flow's trace line and label name it.
     */
    public static void nameFlows(Class<?>... holders) {
        Map<Flow, String> found = new IdentityHashMap<>();
        for (Class<?> holder : holders == null ? new Class<?>[0] : holders) {
            if (holder == null) continue;
            try {
                collect(Class.forName(holder.getPackageName() + ".Flows", true, holder.getClassLoader()), found);
            } catch (ClassNotFoundException | LinkageError none) {
                // A plugin's holder with no Flows beside it names no flow.
            }
            collect(holder, found);
        }
        names = found;
    }

    private static void collect(Class<?> holder, Map<Flow, String> into) {
        for (Field field : holder.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            // Final only: the bot's flow parameter, Sdk.flow, holds one of these and is not its name.
            if (field.getType() != Flow.class || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                continue;
            }
            try {
                if (field.get(null) instanceof Flow value) into.putIfAbsent(value, field.getName());
            } catch (IllegalAccessException | LinkageError unreadable) {
                // Not the bot's to read here: the flow keeps no name, and its trace says "a sub-flow".
            }
        }
    }

    /**
     * The name the bot declares {@code flow} as, {@code DAILY}, or blank when it was never learned. An editor's
     * reference ({@link BotConstants#flow}) is its own name.
     */
    public static String nameOf(Flow flow) {
        String referred = BotConstants.flowName(flow);
        if (referred != null) return referred;
        String name = flow == null ? null : names.get(flow);
        return name == null ? "" : name;
    }
}
