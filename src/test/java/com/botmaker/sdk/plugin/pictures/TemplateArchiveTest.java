package com.botmaker.sdk.plugin.pictures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A {@code .bmtemplates} archive: what arrives, under which name, and filed under which tags. */
class TemplateArchiveTest {

    @Test
    void anImportedPictureArrivesFiledUnderItsCustomTag(@TempDir Path from, @TempDir Path to,
                                                        @TempDir Path out) throws IOException {
        save(from, "ore", Color.RED);
        TemplateLibrary.saveManifest(from, TemplateManifest.empty().declaring("Loot").withTags("ore", List.of("Loot")));
        Path archive = out.resolve("pictures" + TemplateArchive.EXTENSION);
        TemplateArchive.export(from, List.of(TemplateLibrary.fileForName(from, "ore")), archive);

        TemplateArchive.ImportResult result = TemplateArchive.importInto(to, archive);

        assertEquals(List.of("ore"), result.imported());
        TemplateManifest manifest = TemplateLibrary.manifest(to);
        assertEquals(Set.of("Loot"), Set.copyOf(manifest.customTags()), "declared, so it is not inert");
        assertEquals(List.of("ore"), manifest.byTag(List.of("ore"), TagCatalog.of(List.of(), manifest.customTags()))
                .get("Loot"));
        assertTrue(Files.isRegularFile(TemplateLibrary.sidecarFor(TemplateLibrary.fileForName(to, "ore"))),
                "the resolution sidecar travels with its picture");
    }

    @Test
    void theSamePictureIsSkippedAndADifferentOneComesInBesideIt(@TempDir Path from, @TempDir Path to,
                                                               @TempDir Path out) throws IOException {
        save(from, "ore", Color.RED);
        save(from, "gold", Color.YELLOW);
        save(to, "ore", Color.RED);
        save(to, "gold", Color.BLUE);
        Path archive = out.resolve("pictures" + TemplateArchive.EXTENSION);
        TemplateArchive.export(from, TemplateLibrary.list(from), archive);

        TemplateArchive.ImportResult result = TemplateArchive.importInto(to, archive);

        assertEquals(List.of("ore"), result.unchanged());
        assertEquals(Map.of("gold", "gold_2"), result.renamed());
        assertEquals(List.of("gold_2"), result.imported());
    }

    @Test
    void aTagNothingImportedCarriesIsNotDeclared(@TempDir Path from, @TempDir Path to, @TempDir Path out)
            throws IOException {
        save(from, "ore", Color.RED);
        save(to, "ore", Color.RED);
        TemplateLibrary.saveManifest(from, TemplateManifest.empty().declaring("Loot").withTags("ore", List.of("Loot")));
        Path archive = out.resolve("pictures" + TemplateArchive.EXTENSION);
        TemplateArchive.export(from, TemplateLibrary.list(from), archive);

        TemplateArchive.importInto(to, archive);

        assertTrue(TemplateLibrary.manifest(to).customTags().isEmpty(), "the picture was already here, as it was");
    }

    private static void save(Path resources, String name, Color colour) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) image.setRGB(x, y, colour.getRGB());
        TemplateLibrary.saveTemplate(resources, image, name, 0, 0, null);
    }
}
