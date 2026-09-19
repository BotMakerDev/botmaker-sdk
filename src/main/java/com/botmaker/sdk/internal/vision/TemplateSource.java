package com.botmaker.sdk.internal.vision;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Where a template's bytes come from: the path as written, then the classpath — one lookup order for the
 * image and its sidecar.
 *
 * <p><b>The path alone resolved against the process's working directory and nothing else.</b> Studio runs a
 * bot with the project directory as its working directory, so {@code src/main/resources/images/collect.png}
 * worked there and nowhere else: IntelliJ's run configuration used the umbrella root and OpenCV answered
 * {@code can't open/read file}, and a bot run from its own jar has no {@code src/} at all.
 *
 * <p><b>So the classpath is the second place, not a convenience for one IDE.</b> Maven packages
 * {@code src/main/resources/images/collect.png} as {@code images/collect.png}, so that resource name is
 * tried with a leading {@code src/main/resources/} stripped. The file on disk stays first, so nothing that
 * loads today loads from somewhere else tomorrow.
 */
public final class TemplateSource {

    private static final String RESOURCES = "src/main/resources/";

    /** The bytes, and a description of where they were found. */
    public record Found(byte[] bytes, String where) {
    }

    private TemplateSource() {
    }

    /** Looks for {@code path} relative to the working directory, then on the thread's classpath. */
    public static Optional<Found> read(String path, List<String> tried) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return read(path, Path.of("").toAbsolutePath(),
                loader != null ? loader : TemplateSource.class.getClassLoader(), tried);
    }

    /**
     * @param workingDirectory what a relative path is resolved against — a parameter so a test need not
     *                         move {@code user.dir}
     * @param tried            appended with each location looked at, for the caller's failure message
     */
    public static Optional<Found> read(String path, Path workingDirectory, ClassLoader loader,
                                       List<String> tried) {
        Path file = workingDirectory.resolve(path).normalize();
        tried.add(file.toString());
        if (Files.isRegularFile(file)) {
            try {
                return Optional.of(new Found(Files.readAllBytes(file), file.toString()));
            } catch (IOException e) {
                tried.set(tried.size() - 1, file + " (unreadable: " + e.getMessage() + ")");
            }
        }
        String resource = resourceName(path);
        if (resource.isEmpty() || Path.of(path).isAbsolute()) {
            return Optional.empty();
        }
        tried.add("classpath:" + resource);
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in != null) {
                return Optional.of(new Found(in.readAllBytes(), "classpath:" + resource));
            }
        } catch (IOException e) {
            tried.set(tried.size() - 1, "classpath:" + resource + " (unreadable: " + e.getMessage() + ")");
        }
        return Optional.empty();
    }

    /** {@code src/main/resources/images/a.png} and {@code ./images/a.png} both name {@code images/a.png}. */
    static String resourceName(String path) {
        String name = path.replace('\\', '/');
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (name.startsWith(RESOURCES)) {
            name = name.substring(RESOURCES.length());
        }
        return name.startsWith("/") ? name.substring(1) : name;
    }
}
