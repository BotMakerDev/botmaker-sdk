package com.botmaker.sdk.api.bot;

/**
 * One thing the bot can do — a game task like "collect resources" or "do alchemy". A bot author subclasses
 * this once per activity (BotMaker Studio generates the subclass when you add an activity), overriding
 * {@link #isEnabled()} (usually {@code return Activities.MY_FLAG;}) and {@link #run()} (how to do it).
 *
 * <p>The macro loop iterates the enabled activities and calls {@link #execute()}, which wraps {@link #run()}
 * with the overridable {@link #before()}/{@link #after()} hooks and routes a {@link BotStuckException}
 * through {@link #onStuck(BotStuckException)} before rethrowing it to the supervisor.
 */
public abstract class Activity {

    private final String name;

    /**
     * Names this activity after its own class — {@code class Mining extends Activity} is called "Mining". This
     * is what the Studio-generated subclasses use, so they need no constructor at all.
     */
    protected Activity() {
        this.name = getClass().getSimpleName();
    }

    /** Names this activity explicitly, for a name that shouldn't track the class name. */
    protected Activity(String name) {
        this.name = name;
    }

    /** Human-readable name (used for logging / the activity registry). */
    public String name() {
        return name;
    }

    /** Whether this activity should run this cycle — typically {@code return Activities.MY_FLAG;}. */
    public abstract boolean isEnabled();

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
