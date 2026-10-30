package com.botmaker.sdk.internal.vision;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where a template's bytes come from: the file on disk, then the classpath.
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
 *
 * <p><b>The SDK's own folder first</b> (2026-10-30): a picture is in {@link #FOLDER} of the resources, the folder
 * Studio gives this plugin, and its path is written under it, {@code images/collect.png}. A path is looked for
 * there, then at the resources' root, where a bot kept its pictures before; a path written the old way,
 * {@code src/main/resources/images/collect.png}, at the root first, then in the folder. So a bot whose files moved
 * and whose Java did not, or the other way round, finds every picture, and a name in both places is the one its path
 * was written for.
 */
public final class TemplateSource {

    private static final String RESOURCES = "src/main/resources/";

    /**
     * The SDK's folder of a bot's resources, as Studio names a plugin's ({@code StudioServices.resourcesDir}): its
     * groupId and artifactId, each as a package name.
     */
    public static final String FOLDER = "plugins/com_github_botmakerdev/botmaker_sdk/";

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
        for (Path file : files(path, workingDirectory)) {
            tried.add(file.toString());
            if (!Files.isRegularFile(file)) continue;
            try {
                return Optional.of(new Found(Files.readAllBytes(file), file.toString()));
            } catch (IOException e) {
                tried.set(tried.size() - 1, file + " (unreadable: " + e.getMessage() + ")");
            }
        }
        for (String resource : resourceNames(path)) {
            tried.add("classpath:" + resource);
            try (InputStream in = loader.getResourceAsStream(resource)) {
                if (in != null) {
                    return Optional.of(new Found(in.readAllBytes(), "classpath:" + resource));
                }
            } catch (IOException e) {
                tried.set(tried.size() - 1, "classpath:" + resource + " (unreadable: " + e.getMessage() + ")");
            }
        }
        return Optional.empty();
    }

    /** The first of {@link #files} on disk: the file {@code path} names, or empty when it is on the classpath only. */
    public static Optional<Path> file(String path, Path workingDirectory) {
        return files(path, workingDirectory).stream().filter(Files::isRegularFile).findFirst();
    }

    /**
     * The files {@code path} may be, in order: each of {@link #resourceNames} in the project's resources, then the
     * path as written from the working directory. An absolute path is itself alone.
     */
    static List<Path> files(String path, Path workingDirectory) {
        if (path == null || path.isBlank()) return List.of();
        if (Path.of(path).isAbsolute()) return List.of(Path.of(path).normalize());
        List<Path> files = new ArrayList<>();
        for (String name : resourceNames(path)) files.add(workingDirectory.resolve(RESOURCES + name).normalize());
        Path written = workingDirectory.resolve(path).normalize();
        if (!files.contains(written)) files.add(written);
        return files;
    }

    /**
     * The resource names {@code path} may stand for, where it was written for first: {@code images/a.png} is
     * {@code plugins/…/images/a.png}, then {@code images/a.png}; {@code src/main/resources/images/a.png}, written for
     * the root, is {@code images/a.png}, then {@code plugins/…/images/a.png}, where a move put it. None for an
     * absolute path.
     */
    static List<String> resourceNames(String path) {
        if (path == null || Path.of(path).isAbsolute()) return List.of();
        String name = resourceName(path);
        if (name.isEmpty()) return List.of();
        if (name.startsWith(FOLDER)) return List.of(name);
        return path.replace('\\', '/').startsWith(RESOURCES) ? List.of(name, FOLDER + name) : List.of(FOLDER + name, name);
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
