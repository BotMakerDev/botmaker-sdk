package com.botmaker.sdk.api.vision;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.sdk.internal.vision.TemplateMetadata;
import com.botmaker.sdk.internal.vision.TemplateSource;
import com.botmaker.shared.opencv.OpenCvNative;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Public handle for a template image used by the vision API.
 *
 * <p>It holds the file path and the id derived from it, and lazily owns the underlying OpenCV {@link Mat}. The
 * {@code Mat} is loaded from disk on first use and released by {@link #unload()} / {@link #close()}. How sure a
 * match must be is {@code BotSettings}' confidence, not the template's: the per-template threshold was never
 * read by a matcher nor set by the picture picker, and was deleted on 2026-10-01.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): the members that describe the template are offered
 * ({@link #id()}, {@link #filePath()}, {@link #width()}, {@link #height()}). {@link #unload()} and
 * {@link #close()} are hidden as the pair they are: they are memory management for a {@code Mat} the bot
 * cannot see, on a handle whose loading is lazy precisely so nobody has to think about it. A bot that offers
 * them a menu entry is being invited to release image data it did not know it had allocated, and the failure
 * mode is a silent reload rather than an error — which is to say, nothing the user could learn from.
 * {@code AutoCloseable} is implemented for the matchers' own try-with-resources, not for a bot to call.
 */
public class ImageTemplate implements AutoCloseable {

    private final String filePath;
    private final String id;

    // Lazily-loaded OpenCV image data. Null until getMat() is first called.
    private Mat mat;

    /**
     * Constructor using file path.
     * @param filePath Path to the image (e.g. "images/accept_button.png")
     */
    public ImageTemplate(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("File path cannot be empty");
        }
        this.filePath = filePath;

        // Extract ID from filename: "images/btn_ok.png" -> "btn_ok"
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        this.id = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    public String id() {
        return id;
    }

    public String filePath() {
        return filePath;
    }

    /**
     * Returns the OpenCV image data, loading it from disk on first access.
     * The returned {@link Mat} is owned by this template — do not release it directly; use
     * {@link #unload()} instead.
     *
     * <p><b>Package-private since 1.1.0, deliberately.</b> {@code Mat} is {@code org.opencv.core}'s type, and
     * a public method returning it put a third-party class the SDK does not version into the surface that is
     * under contract from 1.1.0 — an OpenCV upgrade could then break a bot with nothing here to notice. Every
     * caller is a matcher in this package ({@link ImageFinder}, {@link ImageClicker}) plus this package's own
     * tests; no bot has ever had a reason to hold a {@code Mat}. A bot that genuinely needs the pixels should
     * be given an SDK-owned type instead, which stays possible as an addition at any time.
     *
     * <p><b>The OpenCV native is loaded here rather than in a {@code static {}} block.</b> This class links
     * {@code Mat} in exactly one method, and holding a template is something a bot does without ever asking
     * for its pixels, so a class-initialiser load would make every bot that merely names a picture extract
     * and link the native library. {@link OpenCvNative#ensureLoaded()} is idempotent and
     * synchronized, so paying it per call costs a volatile read after the first one.
     */
    Mat getMat() {
        OpenCvNative.ensureLoaded();
        if (mat == null || mat.empty()) {
            // The file as written first, then the classpath (TemplateSource says why): a bot run from an IDE
            // rooted elsewhere, or from its own jar, has no src/main/resources relative to its working dir.
            List<String> tried = new ArrayList<>();
            TemplateSource.Found found = TemplateSource.read(filePath, tried).orElseThrow(() ->
                    new RuntimeException("Failed to load image template \"" + filePath + "\". Looked in: "
                            + String.join("; ", tried) + " (working directory "
                            + Path.of("").toAbsolutePath() + ")"));
            // IMREAD_UNCHANGED keeps a transparent PNG's alpha channel (4-channel BGRA) so the matcher can
            // use it as a mask (ignoring transparent pixels); opaque PNGs still load as 3-channel BGR.
            MatOfByte encoded = new MatOfByte(found.bytes());
            try {
                mat = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_UNCHANGED);
            } finally {
                encoded.release();                      // native memory; the decoded Mat is a copy
            }
            if (mat.empty()) {
                throw new RuntimeException("Failed to load image template \"" + filePath + "\": "
                        + found.where() + " is not an image OpenCV can decode");
            }
        }
        return mat;
    }

    /**
     * The resolution the matcher rescales against: the size of the window or screen this template was captured
     * from, or {@code null} when that is unknown. Read from the template's Studio-written sidecar by
     * {@link TemplateMetadata}, which is where the sidecar's format and caching live — a template is a path and
     * a threshold, not a reader of editor metadata.
     *
     * <p>A {@link java.awt.Dimension} because that is the form shared's matcher takes ({@code shared.opencv}
     * cannot see the SDK's {@code Size}). This one method is the whole SDK↔shared mapping for authored
     * resolution; the matching call sites go through it rather than each converting.
     */
    java.awt.Dimension authoredSize() {
        return TemplateMetadata.authoredSize(filePath);
    }

    public int width() {
        return getMat().cols();
    }

    public int height() {
        return getMat().rows();
    }

    /**
     * Releases the underlying image memory. Safe to call repeatedly; the Mat is reloaded on the
     * next {@link #getMat()}.
     */
    @Hidden("memory management the runtime does on its own; a bot that unloads a template mid-run "
            + "pays a reload on the next find and gains nothing")
    public void unload() {
        if (mat != null) {
            mat.release();
            mat = null;
        }
    }

    @Override
    @Hidden("the AutoCloseable half of unload(), and hidden for the same reason")
    public void close() {
        unload();
    }

    @Override
    public String toString() {
        return "ImageTemplate{id='" + id + "', path='" + filePath + "'}";
    }
}
