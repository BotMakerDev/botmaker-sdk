package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.meta.Palette;
import com.botmaker.sdk.api.meta.Scaffolding;
import com.botmaker.sdk.internal.vision.TemplateMetadata;
import com.botmaker.shared.opencv.OpenCvNative;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Public handle for a template image used by the vision API.
 *
 * <p>It holds the configuration (file path, derived id, match threshold) and lazily owns the
 * underlying OpenCV {@link Mat}. The {@code Mat} is loaded from disk on first use and released by
 * {@link #unload()} / {@link #close()}. (This class previously delegated to an internal
 * {@code Template} wrapper; that indirection has been collapsed now that OpenCV loads reliably via
 * {@link OpenCvNative}.)
 *
 * <p><b>Curated for the palette</b> (see {@link Palette}): six of the eight public methods are offered — the
 * four that describe the template ({@link #id()}, {@link #filePath()}, {@link #threshold()}, {@link #width()},
 * {@link #height()}) and the one that tunes it ({@link #setThreshold(double)}). {@link #unload()} and
 * {@link #close()} are hidden as the pair they are: they are memory management for a {@code Mat} the bot
 * cannot see, on a handle whose loading is lazy precisely so nobody has to think about it. A bot that offers
 * them a menu entry is being invited to release image data it did not know it had allocated, and the failure
 * mode is a silent reload rather than an error — which is to say, nothing the user could learn from.
 * {@code AutoCloseable} is implemented for the matchers' own try-with-resources, not for a bot to call.
 */
// The generated Activities declares one per image-template variable, and builds it from the stored path.
@Scaffolding
@Palette
public class ImageTemplate implements AutoCloseable {

    static { OpenCvNative.ensureLoaded(); }

    private final String filePath;
    private final String id;
    private double threshold = 0.8; // Default confidence

    // Lazily-loaded OpenCV image data. Null until getMat() is first called.
    private Mat mat;

    /**
     * Constructor using file path.
     * @param filePath Path to the image (e.g. "images/accept_button.png")
     */
    @Scaffolding   // the generated Activities' template(String) helper
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

    /**
     * Constructor with custom threshold.
     */
    public ImageTemplate(String filePath, double threshold) {
        this(filePath);
        this.threshold = threshold;
    }

    @Palette
    public String id() {
        return id;
    }

    @Palette
    public String filePath() {
        return filePath;
    }

    @Palette
    public double threshold() {
        return threshold;
    }

    @Palette
    public void setThreshold(double threshold) {
        this.threshold = threshold;
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
     */
    Mat getMat() {
        if (mat == null || mat.empty()) {
            String absPath = new File(filePath).getAbsolutePath();
            // IMREAD_UNCHANGED keeps a transparent PNG's alpha channel (4-channel BGRA) so the matcher can
            // use it as a mask (ignoring transparent pixels); opaque PNGs still load as 3-channel BGR.
            mat = Imgcodecs.imread(absPath, Imgcodecs.IMREAD_UNCHANGED);
            if (mat.empty()) {
                throw new RuntimeException("Failed to load image template. Path: " + absPath);
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

    @Palette
    public int width() {
        return getMat().cols();
    }

    @Palette
    public int height() {
        return getMat().rows();
    }

    /**
     * Releases the underlying image memory. Safe to call repeatedly; the Mat is reloaded on the
     * next {@link #getMat()}.
     */
    public void unload() {
        if (mat != null) {
            mat.release();
            mat = null;
        }
    }

    @Override
    public void close() {
        unload();
    }

    @Override
    public String toString() {
        return "ImageTemplate{id='" + id + "', path='" + filePath + "'}";
    }
}
