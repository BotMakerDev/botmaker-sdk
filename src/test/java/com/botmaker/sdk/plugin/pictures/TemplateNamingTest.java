package com.botmaker.sdk.plugin.pictures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Manage Pictures' rename refuses in the words a new name is refused in, and never refuses a picture itself. */
class TemplateNamingTest {

    @Test
    void aRenameMayNotBeBlankTakenOrReserved(@TempDir Path resources) throws IOException {
        save(resources, "ore");
        save(resources, "gold");

        assertTrue(TemplateNaming.renameProblem(resources, "ore", "").contains("needs a name"));
        assertTrue(TemplateNaming.renameProblem(resources, "ore", "gold").contains("already exists"));
        assertTrue(TemplateNaming.renameProblem(resources, "ore", "templates").contains("reserved"));
        assertNull(TemplateNaming.renameProblem(resources, "ore", "iron"));
    }

    @Test
    void aPictureNamedBeforeNamesWereLowercaseCanBeRenamedToItsLowercaseSelf(@TempDir Path resources)
            throws IOException {
        save(resources, "Ore");

        assertNull(TemplateNaming.renameProblem(resources, "Ore", "ore"),
                "the only picture called ore, case aside, is the one being renamed");
    }

    private static void save(Path resources, String name) throws IOException {
        TemplateLibrary.saveTemplate(resources, new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), name, 0, 0,
                null);
    }
}
