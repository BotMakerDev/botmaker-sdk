package com.botmaker.sdk.api.flow;

import com.botmaker.plugin.api.palette.Palette;

/**
 * The flow a bot runs.
 *
 * <p>{@code Bot.run(goHome, Sdk.class)} installs the value your {@code @Managed("flow")} method returns and
 * walks it. {@link #use} is the same hand-off for a bot that builds its flow some other way.
 */
@Palette(category = "flow", categoryLabel = "Flow", icon = "⑃", order = 99)
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
     * Whether the flow has the named activity switched on — its configured default, before any
     * {@code enable}/{@code disable} a running bot has made.
     *
     * <p><b>An activity the flow does not mention is on.</b> That is the answer
     * {@code Settings.enabled} gave for a name with no entry, and it has to stay: a bot may define an
     * activity that is not on the canvas at all, and reading an absent name as <em>off</em> would make such
     * an activity silently do nothing.
     */
    public static boolean enabled(String activity) {
        Flow.Activity found = current.activity(activity);
        return found == null || found.enabled();
    }
}
