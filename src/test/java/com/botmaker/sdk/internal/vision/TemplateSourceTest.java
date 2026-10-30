package com.botmaker.sdk.internal.vision;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateSourceTest {

    private static final byte[] PIXELS = {1, 2, 3};

    private static ClassLoader over(Path root) throws Exception {
        return new URLClassLoader(new URL[]{root.toUri().toURL()}, null);
    }

    @Test
    void aPathRelativeToTheWorkingDirectoryIsReadFromDisk(@TempDir Path project) throws Exception {
        Path image = project.resolve("src/main/resources/images/collect.png");
        Files.createDirectories(image.getParent());
        Files.write(image, PIXELS);

        Optional<TemplateSource.Found> found = TemplateSource.read("src/main/resources/images/collect.png",
                project, over(project.resolve("nowhere")), new ArrayList<>());

        assertArrayEquals(PIXELS, found.orElseThrow().bytes());
        assertEquals(image.toString(), found.get().where());
    }

    @Test
    void theSamePathFromAnotherWorkingDirectoryIsFoundOnTheClasspath(@TempDir Path root) throws Exception {
        // The IntelliJ case: working directory one level up, target/classes on the classpath.
        Path classes = root.resolve("farm/target/classes");
        Files.createDirectories(classes.resolve("images"));
        Files.write(classes.resolve("images/collect.png"), PIXELS);

        Optional<TemplateSource.Found> found = TemplateSource.read("src/main/resources/images/collect.png",
                root, over(classes), new ArrayList<>());

        assertArrayEquals(PIXELS, found.orElseThrow().bytes());
        assertEquals("classpath:images/collect.png", found.get().where());
    }

    @Test
    void aMissingTemplateNamesEveryPlaceItLooked(@TempDir Path root) throws Exception {
        List<String> tried = new ArrayList<>();

        Optional<TemplateSource.Found> found = TemplateSource.read("src/main/resources/images/gone.png",
                root, over(root), tried);

        assertTrue(found.isEmpty());
        assertEquals(List.of(root.resolve("src/main/resources/images/gone.png").toString(),
                root.resolve("src/main/resources/" + TemplateSource.FOLDER + "images/gone.png").toString(),
                "classpath:images/gone.png", "classpath:" + TemplateSource.FOLDER + "images/gone.png"), tried);
    }

    @Test
    void aShortPathIsInTheSdksFolderFirstThenAtTheRoot(@TempDir Path project) throws Exception {
        Path root = project.resolve("src/main/resources/images/collect.png");
        Files.createDirectories(root.getParent());
        Files.write(root, new byte[]{9});
        ClassLoader none = over(project.resolve("nowhere"));

        assertArrayEquals(new byte[]{9}, TemplateSource.read("images/collect.png", project, none, new ArrayList<>())
                .orElseThrow().bytes(), "a bot whose files have not moved");

        Path moved = project.resolve("src/main/resources/" + TemplateSource.FOLDER + "images/collect.png");
        Files.createDirectories(moved.getParent());
        Files.write(moved, PIXELS);
        assertEquals(moved.toString(), TemplateSource.read("images/collect.png", project, none, new ArrayList<>())
                .orElseThrow().where());
        assertEquals(root.toString(), TemplateSource.read("src/main/resources/images/collect.png", project, none,
                new ArrayList<>()).orElseThrow().where(), "a path written for the root is that file while it is there");
        assertEquals(Optional.of(moved), TemplateSource.file("images/collect.png", project));

        Files.delete(root);
        assertEquals(moved.toString(), TemplateSource.read("src/main/resources/images/collect.png", project, none,
                new ArrayList<>()).orElseThrow().where(), "a path written the old way finds the moved file");
    }

    @Test
    void aShortPathIsInTheSdksFolderOnTheClasspath(@TempDir Path root) throws Exception {
        Path classes = root.resolve("bot.jar-contents");
        Path packaged = classes.resolve(TemplateSource.FOLDER + "images/collect.png");
        Files.createDirectories(packaged.getParent());
        Files.write(packaged, PIXELS);

        Optional<TemplateSource.Found> found = TemplateSource.read("images/collect.png", root, over(classes),
                new ArrayList<>());

        assertEquals("classpath:" + TemplateSource.FOLDER + "images/collect.png", found.orElseThrow().where());
        assertEquals(Optional.empty(), TemplateSource.file("images/collect.png", root));
    }

    @Test
    void theResourceNameIsWhatMavenPackages() {
        assertEquals("images/a.png", TemplateSource.resourceName("src/main/resources/images/a.png"));
        assertEquals("images/a.png", TemplateSource.resourceName("./images/a.png"));
        assertEquals("images/a.png", TemplateSource.resourceName("images/a.png"));
        assertEquals("images/a.png", TemplateSource.resourceName("src\\main\\resources\\images\\a.png"));
    }
}
