package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Size;
import com.botmaker.sdk.internal.opencv.OpenCvNative;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 */
public class ImageTemplate implements AutoCloseable {

    static { OpenCvNative.ensureLoaded(); }

    private final String filePath;
    private final String id;
    private double threshold = 0.8; // Default confidence

    // Lazily-loaded OpenCV image data. Null until getMat() is first called.
    private Mat mat;

    // Lazily-loaded authored capture resolution from the "<name>.json" sidecar (best-effort). The
    // boolean gates the one-time load; the Size stays null when there is no (readable) sidecar.
    private boolean metadataLoaded;
    private Size captureResolution;

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

    /**
     * Constructor with custom threshold.
     */
    public ImageTemplate(String filePath, double threshold) {
        this(filePath);
        this.threshold = threshold;
    }

    public String getId() {
        return id;
    }

    public String getFilePath() {
        return filePath;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    /**
     * Returns the OpenCV image data, loading it from disk on first access.
     * The returned {@link Mat} is owned by this template — do not release it directly; use
     * {@link #unload()} instead.
     */
    public Mat getMat() {
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
     * The resolution (in physical pixels) of the target window/screen this template was captured from,
     * read once from the {@code <name>.json} sidecar written by Studio. Used by the matcher to rescale
     * the template when the live capture is a different resolution. Returns {@code null} when there is no
     * sidecar (older templates), so the matcher falls back to the project-wide default resolution.
     */
    public Size captureResolution() {
        if (!metadataLoaded) {
            metadataLoaded = true;
            captureResolution = loadCaptureResolution();
        }
        return captureResolution;
    }

    /** Best-effort read of {@code captureWidth}/{@code captureHeight} from the sidecar next to the PNG. */
    private Size loadCaptureResolution() {
        int dot = filePath.lastIndexOf('.');
        String sidecar = (dot == -1 ? filePath : filePath.substring(0, dot)) + ".json";
        File file = new File(sidecar).getAbsoluteFile();
        if (!file.isFile()) {
            return null;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(file);
            JsonNode w = root.get("captureWidth");
            JsonNode h = root.get("captureHeight");
            if (w != null && h != null && w.asInt() > 0 && h.asInt() > 0) {
                return new Size(w.asInt(), h.asInt());
            }
        } catch (Exception ignored) {
            // best-effort: an absent/unreadable/invalid sidecar leaves the resolution unknown (null)
        }
        return null;
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
