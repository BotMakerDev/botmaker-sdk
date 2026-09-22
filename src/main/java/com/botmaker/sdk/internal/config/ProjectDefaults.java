package com.botmaker.sdk.internal.config;

import com.botmaker.shared.config.ProjectProperties;

/**
 * Typed view of the per-project defaults Studio bakes into a generated bot.
 *
 * <p>The file itself — its classpath location, its key names, the caching and the best-effort parsing —
 * belongs to shared's {@link ProjectProperties}, because Studio <em>writes</em> those very keys and two
 * hand-kept copies of a key set do not stay identical.
 *
 * <p>What is left here is the SDK-shaped face of four of those keys. It used to be more: mapping the raw
 * values onto SDK types was this class's whole reason to exist, and both mappings have gone — the capture
 * source and the capture resolution, on 2026-09-22, for the reasons in the two tombstones below. What the
 * keys that remain have in common is that **no {@code @Managed} value says them**: whether a bot isolates
 * itself, which backend it uses, whether debug output is on, and what to launch are facts about running
 * this bot on this machine rather than facts about the bot.
 *
 * <p>Still best-effort throughout: a missing file, missing key or unparseable value yields {@code null} so
 * callers fall back to their own defaults.
 */
public final class ProjectDefaults {

    private ProjectDefaults() {}

    // source() stood here until 2026-09-22, mapping botmaker-project.properties' capture.source onto a
    // CaptureSource. It was the bot's half of a fact the editor also kept in capture.json, and the one-way
    // projection between the two is what @Managed("capture") replaced: a project's capture source is the
    // expression Sdk.captureSource() returns, handed to Source.set by Bot.run before the bot starts. A
    // properties key read here as well would be a second author of one answer, racing the one the user can
    // read in their own Java -- which is the disagreement CaptureTargetModel's javadoc was written to end,
    // solved there by making the editor write both and solved here by there being only one.
    //
    // Source.resolveDefault is the whole desktop now, and CaptureSource.fromProjectDefault is
    // Source.current(). Nothing reads capture.source.

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

    // defaultResolution() stood here until 2026-09-22, mapping capture.width / capture.height onto Size.
    // Both keys are deleted in shared: nothing in any module ever wrote either, so this answered null on
    // every call and SessionBootstrap.size() took SessionBackends' own default every time. See shared's
    // ProjectProperties tombstone, and the SDK's own for capture.json's `reference` — the authoring half
    // of the same fact, written by nothing either.
}
