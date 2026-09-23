package com.botmaker.sdk.internal.config;

import com.botmaker.shared.config.ProjectProperties;

/**
 * Typed view of the per-project defaults Studio bakes into a generated bot.
 *
 * <p>The file itself — its classpath location, its key names, the caching and the best-effort parsing —
 * belongs to shared's {@link ProjectProperties}, because Studio <em>writes</em> those very keys and two
 * hand-kept copies of a key set do not stay identical.
 *
 * <p>What is here is the SDK-shaped face of four of those keys. What they have in common is that <b>no
 * {@code @Managed} value says them</b>: whether a bot isolates itself, which backend it uses, whether debug
 * output is on, and what to launch are facts about running this bot on this machine rather than facts about
 * the bot. The capture source is not one of them: it is {@code @Managed("capture")}, in the bot's own Java.
 *
 * <p>Still best-effort throughout: a missing file, missing key or unparseable value yields {@code null} so
 * callers fall back to their own defaults.
 */
public final class ProjectDefaults {

    private ProjectDefaults() {}

    /**
     * The raw {@code launch.target} spec, or {@code null} when unset — {@code api.launch.Target} parses it
     * via {@code api.launch.LaunchTarget}. Kept as a raw string so this reader stays free of the launch
     * facade.
     */
    public static String launchTarget() {
        return ProjectProperties.launchTarget();
    }

    /**
     * The configured debug-output default, or {@code null} when the key is absent/unparseable so
     * {@link com.botmaker.sdk.api.util.Debug} keeps its default (on).
     */
    public static Boolean debug() {
        return ProjectProperties.debug();
    }

    /**
     * Whether the project wants the bot to run isolated on a private nested display — <b>default true</b>
     * (see {@link ProjectProperties#sessionIsolated()}), so a bot run anywhere with its project file on the
     * classpath isolates unless it explicitly opts out with {@code session.isolated=false}. Never {@code null}.
     */
    public static boolean sessionIsolated() {
        return ProjectProperties.sessionIsolated();
    }

    /** The explicit backend override ({@code gamescope}/{@code xephyr}), or {@code null} to let the kind pick. */
    public static String sessionBackend() {
        return ProjectProperties.sessionBackend();
    }
}
