package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.Size;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Desktop;
import com.botmaker.sdk.api.capture.Monitor;

import java.io.InputStream;
import java.util.Properties;

/**
 * Reads the optional per-project defaults that Studio bakes into a generated bot as a classpath
 * resource ({@value #RESOURCE}). Everything here is best-effort: a missing file, missing key, or
 * unparseable value yields {@code null} so callers fall back to their own defaults (the whole
 * {@link Desktop} for the source, native pixels for the resolution). Loaded once and cached.
 *
 * <p>Recognised keys:
 * <ul>
 *   <li>{@code capture.source} — {@code desktop} | {@code monitor:<index>} | {@code window:<titleSubstring>}
 *       | {@code emulator:<instanceName>}</li>
 *   <li>{@code capture.width} / {@code capture.height} — the resolution templates were authored at</li>
 *   <li>{@code launch.target} — what the bot launches:
 *       {@code steam:<appId>} | {@code epic:<appName>} | {@code exe:<path>} | {@code emu-app:<pkg>@<instance>}
 *       (parsed by {@code api.launch.LaunchTarget}, read raw here)</li>
 * </ul>
 */
public final class ProjectDefaults {

    public static final String RESOURCE = "/botmaker-project.properties";

    private static volatile Properties cached;
    private static volatile boolean loaded;

    private ProjectDefaults() {}

    private static Properties props() {
        if (!loaded) {
            synchronized (ProjectDefaults.class) {
                if (!loaded) {
                    Properties p = new Properties();
                    try (InputStream in = ProjectDefaults.class.getResourceAsStream(RESOURCE)) {
                        if (in != null) {
                            p.load(in);
                        }
                    } catch (Exception ignored) {
                        // best-effort: absent/unreadable config leaves p empty
                    }
                    cached = p;
                    loaded = true;
                }
            }
        }
        return cached;
    }

    /** The configured project default capture source, or {@code null} when unset/unparseable. */
    public static CaptureSource source() {
        String spec = props().getProperty("capture.source");
        if (spec == null || spec.isBlank()) {
            return null;
        }
        spec = spec.trim();
        try {
            if (spec.equalsIgnoreCase("desktop")) {
                return new Desktop();
            }
            if (spec.regionMatches(true, 0, "monitor:", 0, 8)) {
                return new Monitor(Integer.parseInt(spec.substring(8).trim()));
            }
            if (spec.regionMatches(true, 0, "window:", 0, 7)) {
                return CaptureSource.window(spec.substring(7).trim());
            }
            if (spec.regionMatches(true, 0, "emulator:", 0, 9)) {
                return new com.botmaker.sdk.api.emulator.EmulatorSource(spec.substring(9).trim());
            }
        } catch (RuntimeException ignored) {
            // unparseable — fall through to null
        }
        return null;
    }

    /**
     * The raw {@code launch.target} spec, or {@code null} when unset — {@code api.launch.Target} parses it via
     * {@code api.launch.LaunchTarget}. Kept as a raw string here so this internal config reader stays free of the
     * launch facade.
     */
    public static String launchTarget() {
        String spec = props().getProperty("launch.target");
        return (spec == null || spec.isBlank()) ? null : spec.trim();
    }

    /**
     * The project's default capture resolution (the resolution its templates were authored at), or
     * {@code null} when unset. Used by the matcher to rescale a live capture taken at a different
     * resolution before template matching.
     */
    public static Size defaultResolution() {
        String w = props().getProperty("capture.width");
        String h = props().getProperty("capture.height");
        if (w == null || h == null) {
            return null;
        }
        try {
            int width = Integer.parseInt(w.trim());
            int height = Integer.parseInt(h.trim());
            if (width > 0 && height > 0) {
                return new Size(width, height);
            }
        } catch (NumberFormatException ignored) {
            // unparseable — treat as unset
        }
        return null;
    }
}
