package com.botmaker.sdk.api.bot;
import com.botmaker.sdk.api.Debug;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One thing the bot can do — a game task like "collect resources" or "do alchemy". A bot author subclasses
 * this once per activity (BotMaker Studio generates the subclass when you add an activity), overriding
 * {@link #isEnabled()} (usually {@code return Activities.MY_FLAG;}) and {@link #run()} (how to do it).
 *
 * <p>The macro loop iterates the enabled activities and calls {@link #execute()}, which wraps {@link #run()}
 * with the overridable {@link #before()}/{@link #after()} hooks and routes a {@link BotStuckException}
 * through {@link #onStuck(BotStuckException)} before rethrowing it to the supervisor.
 *
 * <p><b>Controlling activities by name.</b> Every activity registers itself by {@link #name()} on
 * construction, so any code — another activity, {@code GoHome}, {@code Startup} — can turn one on or off with
 * the static {@link #disable(String)} / {@link #enable(String)} without holding a reference. This is what the
 * Studio "disable activity ▾" block emits ({@code Activity.disable("Mining")}). The instance
 * {@link #disable()} / {@link #enable()} still work for an activity acting on itself.
 */
public abstract class Activity {

    /**
     * All constructed activities, keyed by {@link #name()} — the lookup behind the static
     * {@link #disable(String)} / {@link #enable(String)}. Insertion-ordered for predictable iteration; one
     * instance per activity name (the generated registry builds each subclass once), so last-registered wins.
     */
    private static final Map<String, Activity> REGISTRY = new LinkedHashMap<>();

    private final String name;

    /**
     * Runtime enable override, or {@code null} to defer to {@link #isEnabled()}. Set by {@link #setEnabled}
     * so a running bot can turn an activity on or off — the "do this once, then stop" pattern — without
     * touching {@code activities.json}. {@link #active()} reconciles it with the configured default.
     */
    private Boolean enabledOverride;

    /**
     * Names this activity after its own class — {@code class Mining extends Activity} is called "Mining". This
     * is what the Studio-generated subclasses use, so they need no constructor at all.
     */
    protected Activity() {
        this.name = getClass().getSimpleName();
        register(this);
    }

    /** Names this activity explicitly, for a name that shouldn't track the class name. */
    protected Activity(String name) {
        this.name = name;
        register(this);
    }

    private static void register(Activity activity) {
        REGISTRY.put(activity.name, activity);
    }

    /**
     * Turns the named activity off for the rest of the run (its {@link #active()} becomes {@code false} on the
     * next loop pass), regardless of where this is called from. Unknown name → a warning and no-op, so a typo
     * never crashes a running bot. This is the general "stop doing X" primitive, including one activity
     * disabling another.
     */
    public static void disable(String name) {
        setEnabled(name, false);
    }

    /** Turns the named activity on for the rest of the run. Unknown name → a warning and no-op. */
    public static void enable(String name) {
        setEnabled(name, true);
    }

    /** Sets the named activity's runtime enablement. Unknown name → a warning and no-op. */
    public static void setEnabled(String name, boolean enabled) {
        Activity activity = REGISTRY.get(name);
        if (activity == null) {
            Debug.error("[Activity] setEnabled: no activity named '" + name + "' — known: "
                    + REGISTRY.keySet() + ". Ignoring.");
            return;
        }
        activity.setEnabled(enabled);
    }

    /** Test-only: clear the process-global registry between tests. */
    static void clearRegistry() {
        REGISTRY.clear();
    }

    /** Human-readable name (used for logging / the activity registry). */
    public String name() {
        return name;
    }

    /**
     * The activity's <em>configured</em> default enablement — typically {@code return Activities.MY_FLAG;},
     * i.e. the value set in {@code activities.json} via Studio's <em>Set Activity Values</em>. This is the
     * starting point; a running bot can override it with {@link #setEnabled}. The macro loop does not call
     * this directly — it consults {@link #active()}, which layers any runtime override on top.
     */
    public abstract boolean isEnabled();

    /**
     * The <em>effective</em> enablement the macro loop checks each cycle: the runtime override from
     * {@link #setEnabled} if one has been set, otherwise the configured {@link #isEnabled()} default. This is
     * what lets a mid-run {@link #disable()} actually stop the activity from running on the next pass.
     */
    public final boolean active() {
        return enabledOverride != null ? enabledOverride : isEnabled();
    }

    /**
     * Overrides this activity's enablement for the rest of the run (until set again). Outranks
     * {@link #isEnabled()}: after {@code setEnabled(false)} the activity is skipped even if its configured
     * flag is {@code true}. Call it from an activity's {@code run()} to implement "do this once, then stop".
     */
    public void setEnabled(boolean enabled) {
        this.enabledOverride = enabled;
    }

    /** Enables this activity for the rest of the run — shorthand for {@code setEnabled(true)}. */
    public void enable() {
        setEnabled(true);
    }

    /** Disables this activity for the rest of the run — shorthand for {@code setEnabled(false)}. */
    public void disable() {
        setEnabled(false);
    }

    /** Do the activity. Runs against the current capture source; may throw {@link BotStuckException}. */
    public abstract void run();

    /** Overridable no-op: called before {@link #run()} (e.g. navigate to the activity's screen). */
    protected void before() {}

    /** Overridable no-op: called after {@link #run()} returns normally. */
    protected void after() {}

    /** Overridable no-op: called if {@link #run()} throws {@link BotStuckException}, before it is rethrown. */
    protected void onStuck(BotStuckException e) {}

    /**
     * Runs this activity end-to-end: {@link #before()} → {@link #run()} → {@link #after()}. A
     * {@link BotStuckException} from {@code run()} is handed to {@link #onStuck(BotStuckException)} and then
     * rethrown so the {@link Bot#supervise supervisor} can recover.
     */
    public final void execute() {
        before();
        try {
            run();
        } catch (BotStuckException e) {
            onStuck(e);
            throw e;
        }
        after();
    }
}
