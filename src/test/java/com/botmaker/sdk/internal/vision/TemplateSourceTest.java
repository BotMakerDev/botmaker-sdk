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
        Path classes = root.resolve("gamebot/target/classes");
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
                "classpath:images/gone.png"), tried);
    }

    @Test
    void theResourceNameIsWhatMavenPackages() {
        assertEquals("images/a.png", TemplateSource.resourceName("src/main/resources/images/a.png"));
        assertEquals("images/a.png", TemplateSource.resourceName("./images/a.png"));
        assertEquals("images/a.png", TemplateSource.resourceName("images/a.png"));
        assertEquals("images/a.png", TemplateSource.resourceName("src\\main\\resources\\images\\a.png"));
    }
}
