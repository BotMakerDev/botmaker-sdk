package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Run;

/**
 * The run a bot does: the flow it walks, its home and its setup.
 *
 * <p>{@code Bot.runFlow(Sdk.class)} installs the value your {@code @SdkValue(SdkValue.Id.RUN)} method returns and
 * walks its flow. {@link #use} is the same hand-off for a bot that builds its run some other way. Named apart
 * from the bot's own {@code Flows} class, which a bot's code names and this one would shadow.
 *
 * <p>Kept out of the insert menus: a dropped {@code use} would be written {@code InstalledRun.use(null)} and clear
 * the run, {@code installed} hands back a {@code Run} nothing in Studio takes, and {@code enabled} is
 * {@code ActivitySwitch.active} without the overrides. Its members stay catalogued so the name resolves.
 */
public final class InstalledRun {

    private static volatile Run current = Run.NONE;

    private InstalledRun() {}

    /**
     * Uses {@code run} from now on. {@code null} clears it back to {@link Run#NONE}, which runs nothing.
     *
     * <p>Installing twice keeps the later run, which is what a reader would expect of a call named like this one.
     */
    public static void use(Run run) {
        current = run == null ? Run.NONE : run;
    }

    /** The installed run, or {@link Run#NONE} when nothing has been installed. Never {@code null}. */
    public static Run installed() {
        return current;
    }

    /** The installed run's flow, or {@link Flow#NONE}. Never {@code null}. */
    public static Flow flow() {
        return current.flow();
    }

    /**
     * Whether the flow has the activity switched on — its configured default, before any
     * {@code enable}/{@code disable} a running bot has made.
     *
     * <p><b>An activity the flow does not mention is on.</b> A bot may name an activity that is not on the
     * canvas at all, and reading an absent one as <em>off</em> would make it silently do nothing.
     */
    public static boolean enabled(Activity activity) {
        Flow.Step found = flow().step(activity);
        return found == null || found.enabled();
    }
}
