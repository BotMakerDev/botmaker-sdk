package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;

/**
 * The flow a bot runs.
 *
 * <p>{@code Bot.run(goHome, Sdk.class)} installs the value your {@code @SdkValue(SdkValue.Id.FLOW)} method returns and
 * walks it. {@link #use} is the same hand-off for a bot that builds its flow some other way.
 *
 * <p>Kept out of the insert menus: a dropped {@code use} would be written {@code Flows.use(null)} and clear the
 * flow, {@code installed} hands back a {@code Flow} nothing in Studio takes, and {@code enabled} is
 * {@code ActivitySwitch.active} without the overrides. Its members stay catalogued so the name resolves.
 */
public final class Flows {

    private static volatile Flow current = Flow.NONE;

    private Flows() {}

    /**
     * Uses {@code flow} from now on. {@code null} clears it back to {@link Flow#NONE}, which runs nothing.
     *
     * <p>Installing twice keeps the later flow, which is what defining the same activity twice has always
     * done, and what a reader would expect of a call named like this one.
     */
    public static void use(Flow flow) {
        current = flow == null ? Flow.NONE : flow;
    }

    /** The installed flow, or {@link Flow#NONE} when nothing has been installed. Never {@code null}. */
    public static Flow installed() {
        return current;
    }

    /**
     * Whether the flow has the activity switched on — its configured default, before any
     * {@code enable}/{@code disable} a running bot has made.
     *
     * <p><b>An activity the flow does not mention is on.</b> A bot may name an activity that is not on the
     * canvas at all, and reading an absent one as <em>off</em> would make it silently do nothing.
     */
    public static boolean enabled(Activity activity) {
        Flow.Step found = current.step(activity);
        return found == null || found.enabled();
    }
}
