package com.botmaker.sdk.internal.authoring;

import com.botmaker.sdk.api.authoring.Authoring;
import com.botmaker.sdk.api.authoring.ProjectModel;
import com.botmaker.sdk.api.authoring.ProjectSpec;
import com.botmaker.sdk.api.authoring.SdkVersion;
import com.botmaker.sdk.api.authoring.TemplateNames;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.shared.config.ProjectProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Creates a bot project on disk: everything rendered first, then committed.
 *
 * <h2>All of it, or none of it</h2>
 *
 * <p>Every byte the SDK owns — the source files, {@code activities.json}, the project properties and the
 * placeholder image — is built in memory before the first directory exists, and so is every byte the
 * <em>caller</em> hands in. Anything that can refuse (a name that is not a package, a model the generator
 * cannot render, a target that already holds a project) refuses while there is nothing to clean up. A
 * half-created project is worse than no project: the editor lists it, opening it fails in a different place
 * each time, and the user has to find and delete it by hand.
 *
 * <p>Two things are deliberately <em>not</em> the SDK's here, for the same reason by two different routes.
 * The editor's own {@code settings.json} is not written at all: no bot reads it, and an SDK writing it would
 * be an SDK with an opinion about an editor it has never seen. {@code pom.xml} <em>is</em> written, but its
 * text comes in through {@code callerFiles} — it is the file that declares which SDK and which other plugins
 * the project has, and the SDK is one plugin among them. It gets committed here only so that "all of it or
 * none of it" still means the whole project.
 */
public final class ProjectWriter {

    private ProjectWriter() {}

    /**
     * Renders and commits the whole project.
     *
     * @param schemaVersion the migration stamp {@code activities.json} carries — the caller's ledger, for the
     *                      same reason {@link Authoring#writeModel} takes it rather than deriving it
     * @param callerFiles   the caller's own files, project-relative, committed in the same pass
     */
    public static void create(SdkVersion version, ProjectSpec spec, Path projectDir, int schemaVersion,
                              Map<String, String> callerFiles) throws IOException {
        requireName(spec.projectName(), "project name");
        requireName(spec.entryClassName(), "entry class name");
        if (spec.packageName().isBlank()) {
            throw new IllegalArgumentException("A project needs a package name.");
        }
        if (Files.exists(projectDir.resolve("pom.xml"))) {
            throw new IOException("There is already a project at " + projectDir + ".");
        }

        // ---- render ----------------------------------------------------------------------------------
        ProjectModel model = ProjectModel.empty();
        // The placeholder is the one image a new project has, so it is the one entry Templates is built
        // from. Passing the folder's contents would be the same list — there is no folder yet.
        List<String> images = List.of(TemplateNames.DEFAULT_TEMPLATE_NAME);

        Map<String, String> files = new LinkedHashMap<>();
        files.putAll(SourceEmitter.sources(spec, model, images));
        if (spec.kind() == ProjectSpec.Kind.GAME_BOT) {
            files.put("src/main/resources/" + ProjectModel.FILE_NAME,
                    Authoring.modelJson(version, model, schemaVersion));
        }
        String properties = captureProperties(spec.referenceSize());
        if (properties != null) {
            files.put("src/main/resources/" + ProjectProperties.FILE_NAME, properties);
        }
        byte[] placeholder = placeholderPng();

        // The caller's files last, and only where they collide with nothing. Whole-file ownership: two
        // authors of one file is the mistake the scaffold contract made and was deleted for, so a collision
        // is an error here and never a merge.
        for (Map.Entry<String, String> file : callerFiles.entrySet()) {
            if (files.containsKey(file.getKey())) {
                throw new IllegalArgumentException(
                        "The SDK already writes " + file.getKey() + "; a caller cannot also write it.");
            }
            files.put(file.getKey(), file.getValue());
        }

        // ---- commit ----------------------------------------------------------------------------------
        for (String dir : List.of("src/main/java", "src/main/resources", "src/test/java",
                "src/test/resources", "src/main/resources/images")) {
            Files.createDirectories(projectDir.resolve(dir));
        }
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = projectDir.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        Files.write(projectDir.resolve("src/main/resources/images")
                .resolve(TemplateNames.DEFAULT_TEMPLATE_FILE), placeholder);
    }

    /** The paths of every {@code .java} file this spec's project is created with, project-relative. */
    public static List<String> generatedFileNames(ProjectSpec spec) {
        return List.copyOf(SourceEmitter.sources(spec, ProjectModel.empty(), List.of()).keySet());
    }

    /**
     * The project's {@code botmaker-project.properties}, or null when there is nothing to say yet.
     *
     * <p>Only the capture resolution: it is the size the editor snaps captures to <em>and</em> the size the
     * bot scales its matches against, which is why the two must be one number rather than an editor setting
     * the bot re-guesses. A spec with no reference size leaves the file out entirely — the first capture
     * seeds it, and an absent key is what "never chose" reads as everywhere else in that file.
     */
    private static String captureProperties(Size referenceSize) throws IOException {
        if (referenceSize == null || referenceSize.width() <= 0 || referenceSize.height() <= 0) return null;
        Properties props = new Properties();
        props.setProperty(ProjectProperties.KEY_CAPTURE_WIDTH, Integer.toString(referenceSize.width()));
        props.setProperty(ProjectProperties.KEY_CAPTURE_HEIGHT, Integer.toString(referenceSize.height()));
        StringWriter out = new StringWriter();
        props.store(out, "BotMaker project defaults");
        return out.toString();
    }

    private static byte[] placeholderPng() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(TemplateNames.defaultTemplateImage(), "png", bytes);
        return bytes.toByteArray();
    }

    private static void requireName(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A project needs a " + what + ".");
        }
    }
}
