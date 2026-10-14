package com.botmaker.sdk.internal.vision;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pictures decoded once: each file's pixels are kept by its place and its modified time and size, so a template
 * opened again (the assistant's look builds every picture anew on each call) is a copy of memory, not a read and
 * a PNG decode. A file that changed on disk is read again; one on the classpath is read once per class loader. The least recently used are dropped past {@value #MAX_BYTES} bytes.
 *
 * <p>Each caller gets its own copy and releases it; the kept pixels are released only here, so an evicted
 * picture never pulls the pixels from under a template still matching with them.
 */
public final class TemplateCache {

    static final long MAX_BYTES = 64L << 20;

    /**
     * What a kept picture was decoded from: a file's modified time, size and identity (a picture replaced by a
     * new file within the same tick at the same size is still told apart), or nothing for the classpath.
     */
    private record Stamp(long modified, long size, Object fileKey) {
        static final Stamp CLASSPATH = new Stamp(-1, -1, null);
    }

    private record Kept(Mat mat, Stamp stamp) {
        long bytes() {
            return mat.total() * mat.elemSize();
        }
    }

    private static final LinkedHashMap<String, Kept> KEPT = new LinkedHashMap<>(64, 0.75f, true);
    private static long bytes;

    private TemplateCache() {
    }

    /**
     * The picture at {@code path} (as {@link TemplateSource} finds it, relative to the working directory),
     * decoded with its alpha channel; the caller owns the copy and releases it.
     *
     * @throws RuntimeException naming every place looked at when there is no such picture or it is no image
     */
    public static Mat load(String path) {
        return load(path, Path.of("").toAbsolutePath(), contextLoader());
    }

    static Mat load(String path, Path workingDirectory, ClassLoader loader) {
        Path file = workingDirectory.resolve(path).normalize();
        String key;
        Stamp stamp;
        if (Files.isRegularFile(file)) {
            key = file.toString();
            try {
                BasicFileAttributes seen = Files.readAttributes(file, BasicFileAttributes.class);
                stamp = new Stamp(seen.lastModifiedTime().toMillis(), seen.size(), seen.fileKey());
            } catch (IOException e) {
                stamp = null;
            }
        } else {
            // Per class loader: Studio's warm JVM loads a rebuilt bot through a new one.
            key = "classpath:" + System.identityHashCode(loader) + ":" + path;
            stamp = Stamp.CLASSPATH;
        }
        synchronized (KEPT) {
            Kept kept = KEPT.get(key);
            if (kept != null && kept.stamp().equals(stamp)) return kept.mat().clone();
        }
        Mat decoded = decode(path, workingDirectory, loader);
        if (stamp != null) keep(key, new Kept(decoded.clone(), stamp));
        return decoded;
    }

    private static void keep(String key, Kept kept) {
        synchronized (KEPT) {
            Kept old = KEPT.put(key, kept);
            if (old != null) {
                bytes -= old.bytes();
                old.mat().release();
            }
            bytes += kept.bytes();
            Iterator<Map.Entry<String, Kept>> eldest = KEPT.entrySet().iterator();
            while (bytes > MAX_BYTES && KEPT.size() > 1 && eldest.hasNext()) {
                Kept dropped = eldest.next().getValue();
                eldest.remove();
                bytes -= dropped.bytes();
                dropped.mat().release();
            }
        }
    }

    private static Mat decode(String path, Path workingDirectory, ClassLoader loader) {
        List<String> tried = new ArrayList<>();
        TemplateSource.Found found = TemplateSource.read(path, workingDirectory, loader, tried).orElseThrow(() ->
                new RuntimeException("Failed to load image template \"" + path + "\". Looked in: "
                        + String.join("; ", tried) + " (working directory " + workingDirectory + ")"));
        // IMREAD_UNCHANGED keeps a transparent PNG's alpha channel (4-channel BGRA) so the matcher can
        // use it as a mask (ignoring transparent pixels); opaque PNGs still load as 3-channel BGR.
        MatOfByte encoded = new MatOfByte(found.bytes());
        Mat mat;
        try {
            mat = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_UNCHANGED);
        } finally {
            encoded.release();                      // native memory; the decoded Mat is a copy
        }
        if (mat.empty()) {
            throw new RuntimeException("Failed to load image template \"" + path + "\": "
                    + found.where() + " is not an image OpenCV can decode");
        }
        return mat;
    }

    private static ClassLoader contextLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : TemplateCache.class.getClassLoader();
    }
}
