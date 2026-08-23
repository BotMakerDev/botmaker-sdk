package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.internal.capture.Desktop;
import com.botmaker.sdk.internal.capture.Monitor;
import com.botmaker.shared.config.CaptureSourceKind;
import com.botmaker.shared.config.ProjectProperties;

import java.awt.Dimension;

/**
 * Typed view of the per-project defaults Studio bakes into a generated bot.
 *
 * <p>The file itself — its classpath location, its key names, the caching and the best-effort parsing —
 * belongs to shared's {@link ProjectProperties}, because Studio <em>writes</em> those very keys and two
 * hand-kept copies of a key set do not stay identical. What is left here is only the part shared cannot do:
 * mapping the raw values onto SDK types ({@link CaptureSource}, {@link Size}).
 *
 * <p>Still best-effort throughout: a missing file, missing key or unparseable value yields {@code null} so
 * callers fall back to their own defaults (the whole {@link Desktop} for the source, native pixels for the
 * resolution).
 */
public final class ProjectDefaults {

    private ProjectDefaults() {}

    /**
     * The configured project default capture source, or {@code null} when unset/unparseable. The spec's
     * grammar belongs to shared's {@link CaptureSourceKind} — Studio writes those same four forms — so all
     * that happens here is the mapping onto the SDK's {@link CaptureSource} implementations.
     */
    public static CaptureSource source() {
        String spec = ProjectProperties.captureSource();
        if (spec == null) {
            return null;
        }
        CaptureSourceKind kind = CaptureSourceKind.of(spec);
        if (kind == null) {
            return null;
        }
        String argument = kind.argumentOf(spec);
        if (kind.takesArgument() && argument == null) {
            // "window:" with nothing after it names no window — the caller's own default is the better answer
            return null;
        }
        try {
            return switch (kind) {
                case DESKTOP -> new Desktop();
                case MONITOR -> new Monitor(Integer.parseInt(argument));
                case WINDOW -> CaptureSource.window(argument);
                case EMULATOR -> new com.botmaker.sdk.api.emulator.EmulatorSource(argument);
            };
        } catch (RuntimeException ignored) {
            // unparseable argument (a non-numeric monitor index, an empty name) — no default source
            return null;
        }
    }

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

    /**
     * The project's default capture resolution (the resolution its templates were authored at) as the SDK's
     * {@link Size}, or {@code null} when unset. The matcher itself reads the {@link Dimension} form straight
     * from {@link ProjectProperties#defaultResolution()} — this exists for API-shaped callers.
     */
    public static Size defaultResolution() {
        Dimension d = ProjectProperties.defaultResolution();
        return d == null ? null : new Size(d.width, d.height);
    }
}
