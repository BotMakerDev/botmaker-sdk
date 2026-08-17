package com.botmaker.sdk.internal.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.Dimension;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code <name>.json} sidecar Studio writes beside a captured template, and the one fact a bot reads out of
 * it: the resolution of the window or screen the template was captured from.
 *
 * <p>It lives here, and not on {@code ImageTemplate}, because the sidecar is a <em>Studio</em> artefact — its
 * file name convention, its key names and the JSON parser that reads them are editor-authored plumbing, while
 * {@code ImageTemplate} is a value a bot constructs from a path. The template's contract to the matcher stays
 * {@code authoredSize()}; where that number comes from is this class's business, and a bot that has no sidecar
 * (a hand-written template, an older project) is answered {@code null} and falls back on the project-wide
 * default resolution.
 *
 * <p><b>Cached by path, not per template instance.</b> A bot routinely builds a fresh {@code ImageTemplate} for
 * the same file on every iteration — once inside a loop body, once in a group literal — and the sidecar cannot
 * change under a running bot. One read per path per JVM is therefore both correct and the difference between a
 * file stat per match and none.
 */
public final class TemplateMetadata {

    /** Path → authored size. {@link #ABSENT} stands in for "looked, found nothing", so a miss is cached too. */
    private static final Map<String, Dimension> byPath = new ConcurrentHashMap<>();

    private static final Dimension ABSENT = new Dimension(0, 0);

    private TemplateMetadata() {}

    /**
     * The resolution (in physical pixels) of the target window/screen {@code templatePath} was captured from,
     * or {@code null} when there is no readable sidecar.
     *
     * <p>A {@link Dimension} rather than the SDK's {@code Size} because this number exists to be handed to
     * shared's matcher, which cannot see the SDK's types.
     */
    public static Dimension authoredSize(String templatePath) {
        if (templatePath == null || templatePath.isBlank()) return null;
        Dimension cached = byPath.computeIfAbsent(templatePath, TemplateMetadata::read);
        return cached == ABSENT ? null : cached;
    }

    /** Best-effort read of {@code captureWidth}/{@code captureHeight} from the sidecar next to the image. */
    private static Dimension read(String templatePath) {
        int dot = templatePath.lastIndexOf('.');
        String sidecar = (dot == -1 ? templatePath : templatePath.substring(0, dot)) + ".json";
        File file = new File(sidecar).getAbsoluteFile();
        if (!file.isFile()) {
            return ABSENT;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(file);
            JsonNode w = root.get("captureWidth");
            JsonNode h = root.get("captureHeight");
            if (w != null && h != null && w.asInt() > 0 && h.asInt() > 0) {
                return new Dimension(w.asInt(), h.asInt());
            }
        } catch (Exception ignored) {
            // best-effort: an absent/unreadable/invalid sidecar leaves the resolution unknown
        }
        return ABSENT;
    }

    /** Test seam: forgets every cached sidecar, so a test can write one and be read. */
    public static void clearCache() {
        byPath.clear();
    }
}
