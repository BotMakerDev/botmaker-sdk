package com.botmaker.sdk.internal.authoring;

import com.botmaker.sdk.api.authoring.Authoring;
import com.botmaker.sdk.api.authoring.ProjectModel;
import com.botmaker.sdk.api.authoring.ProjectSpec;
import com.botmaker.sdk.api.authoring.SdkVersion;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.shared.config.ProjectProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creating a project writes the whole file set — or leaves the disk untouched.
 *
 * <p>What is <em>in</em> the generated Java is {@link ScaffoldEmitTest}'s question, and it answers it by
 * compiling the corpus. This file asks the other one: does the set of files a project is made of actually
 * arrive, and does a refusal arrive before any of it does.
 */
class ProjectCreateTest {

    private static final SdkVersion V = SdkVersion.latest();

    /**
     * What an editor hands in: the pom is the caller's, because it declares which SDK — and which other
     * plugins — the project has, and the SDK is only one of them. Its text is nothing to the SDK, so these
     * tests use the shortest thing that is still a file.
     */
    private static final Map<String, String> CALLER_FILES = Map.of("pom.xml", "<project>studio's</project>");

    private static ProjectSpec gameBot() {
        return new ProjectSpec("MyBot", "com.mybot", "MyBot", ProjectSpec.Kind.GAME_BOT, "1.2.0",
                new Size(1920, 1080));
    }

    @Test
    void aGameBotArrivesWhole(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("MyBot");
        Authoring.createProject(V, gameBot(), project, 3, CALLER_FILES);

        for (String expected : List.of("pom.xml", "src/main/java", "src/main/resources", "src/test/java",
                "src/test/resources", "src/main/java/com/mybot/MyBot.java",
                "src/main/java/com/mybot/GoHome.java", "src/main/java/com/mybot/Popups.java",
                "src/main/java/com/mybot/Parameters.java", "src/main/java/com/mybot/Templates.java",
                "src/main/resources/" + ProjectModel.FILE_NAME,
                "src/main/resources/" + ProjectProperties.FILE_NAME,
                "src/main/resources/images/default_template.png")) {
            assertTrue(Files.exists(project.resolve(expected)), expected + " is missing");
        }

        // Every .java the emitter claims it writes is really on disk — the claim is what the editor uses to
        // notice one has gone missing, so a claim nothing checks is worse than no claim.
        for (String file : Authoring.generatedFileNames(V, gameBot())) {
            assertTrue(Files.exists(project.resolve(file)), file + " was named but not written");
        }
    }

    /**
     * The caller's files are committed verbatim, in the same pass as the SDK's. That is the whole point of
     * handing the pom in rather than writing it before or after: the editor authors it, and creation is
     * still all of the project or none of it.
     */
    @Test
    void theCallersOwnFilesArriveWithTheRest(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("MyBot");
        Authoring.createProject(V, gameBot(), project, 3, CALLER_FILES);
        assertEquals("<project>studio's</project>", Files.readString(project.resolve("pom.xml")));
    }

    /** Whole-file ownership. Two authors of one file is the mistake the scaffold contract was deleted for. */
    @Test
    void aCallerFileCollidingWithAGeneratedOneIsRefused(@TempDir Path dir) {
        Path project = dir.resolve("MyBot");
        assertThrows(IllegalArgumentException.class, () -> Authoring.createProject(V, gameBot(), project, 3,
                Map.of("src/main/java/com/mybot/GoHome.java", "// mine now")));
        assertFalse(Files.exists(project));
    }

    @Test
    void anEmptyProjectHasNoModelFile(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("Blank");
        Authoring.createProject(V, new ProjectSpec("Blank", "com.blank", "Blank",
                ProjectSpec.Kind.EMPTY, "1.2.0", new Size(0, 0)), project, 3, CALLER_FILES);
        assertFalse(Files.exists(project.resolve("src/main/resources/" + ProjectModel.FILE_NAME)));
        assertTrue(Files.exists(project.resolve("src/main/java/com/blank/Blank.java")));
    }

    /**
     * The reference resolution is the size the editor snaps captures to <em>and</em> the size the bot scales
     * its matches against. Written once, at creation, so the two cannot be two numbers.
     */
    @Test
    void theReferenceResolutionIsStored(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("MyBot");
        Authoring.createProject(V, gameBot(), project, 3, CALLER_FILES);
        String props = Files.readString(
                project.resolve("src/main/resources/" + ProjectProperties.FILE_NAME));
        assertTrue(props.contains(ProjectProperties.KEY_CAPTURE_WIDTH + "=1920"), props);
        assertTrue(props.contains(ProjectProperties.KEY_CAPTURE_HEIGHT + "=1080"), props);
    }

    /** An unset reference size leaves the key out entirely — absence is what "never chose" reads as. */
    @Test
    void anUnsetResolutionWritesNoPropertiesFile(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("MyBot");
        Authoring.createProject(V, new ProjectSpec("MyBot", "com.mybot", "MyBot",
                ProjectSpec.Kind.GAME_BOT, "1.2.0", null), project, 3, CALLER_FILES);
        assertFalse(Files.exists(project.resolve("src/main/resources/" + ProjectProperties.FILE_NAME)));
    }

    @Test
    void theModelIsStampedWithTheCallersSchemaVersion(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("MyBot");
        Authoring.createProject(V, gameBot(), project, 7, CALLER_FILES);
        assertEquals(7, Authoring.readSchemaVersion(V, project.resolve("src/main/resources")));
    }

    @Test
    void thePlaceholderIsAReadableImage(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("MyBot");
        Authoring.createProject(V, gameBot(), project, 3, CALLER_FILES);
        var image = ImageIO.read(project
                .resolve("src/main/resources/images/default_template.png").toFile());
        assertEquals(32, image.getWidth());
        assertEquals(32, image.getHeight());
    }

    /**
     * The rule creation exists to keep: a refusal leaves nothing behind. An existing {@code pom.xml} is the
     * one thing that says "there is already a project here", and the check runs before a single directory is
     * made — otherwise a user gets a half-written folder they have to find and delete by hand. The SDK no
     * longer writes that file, but it still reads it as the marker: the question is whether a project is
     * there, not whose file answers it.
     */
    @Test
    void anExistingProjectIsRefusedBeforeAnythingIsWritten(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("MyBot");
        Files.createDirectories(project);
        Files.writeString(project.resolve("pom.xml"), "<project/>");

        assertThrows(IOException.class, () -> Authoring.createProject(V, gameBot(), project, 3, CALLER_FILES));

        assertEquals("<project/>", Files.readString(project.resolve("pom.xml")));
        assertFalse(Files.exists(project.resolve("src")), "not one directory may appear");
    }

    @Test
    void aSpecMissingANameIsRefusedBeforeAnythingIsWritten(@TempDir Path dir) {
        Path project = dir.resolve("MyBot");
        assertThrows(IllegalArgumentException.class, () -> Authoring.createProject(V,
                new ProjectSpec("", "com.mybot", "MyBot", ProjectSpec.Kind.GAME_BOT, "1.2.0", null),
                project, 3, CALLER_FILES));
        assertFalse(Files.exists(project));
    }
}
