package com.botmaker.sdk.plugin.pictures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** A {@code .bmtemplates} archive: what arrives, and under which name. Tags stay on the constants they are on. */
class TemplateArchiveTest {

    @Test
    void theSamePictureIsSkippedAndADifferentOneComesInBesideIt(@TempDir Path from, @TempDir Path to,
                                                               @TempDir Path out) throws IOException {
        save(from, "ore", Color.RED);
        save(from, "gold", Color.YELLOW);
        save(to, "ore", Color.RED);
        save(to, "gold", Color.BLUE);
        Path archive = out.resolve("pictures" + TemplateArchive.EXTENSION);
        TemplateArchive.export(TemplateLibrary.list(from), Map.of(), archive);

        TemplateArchive.ImportResult result = TemplateArchive.importInto(to, archive);

        assertEquals(List.of("ore"), result.unchanged());
        assertEquals(Map.of("gold", "gold_2"), result.renamed());
        assertEquals(List.of("gold_2"), result.imported());
    }

    @Test
    void anOlderArchivesTagFileIsSkippedAndWrittenNowhere(@TempDir Path to, @TempDir Path out) throws IOException {
        Path archive = out.resolve("old" + TemplateArchive.EXTENSION);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("images/ore.png"));
            zip.write(png(Color.RED));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("templates.json"));
            zip.write("{\"templates\":{\"ore\":{\"tags\":[\"Loot\"]}}}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        TemplateArchive.ImportResult result = TemplateArchive.importInto(to, archive);

        assertEquals(List.of("ore"), result.imported());
        assertFalse(Files.exists(to.resolve("images").resolve("templates.json")));
    }

    @Test
    void framedPictureSurvivesExportImport(@TempDir Path from, @TempDir Path to, @TempDir Path out)
            throws IOException {
        TemplateLibrary.saveTemplate(from, FrameStrip.join(List.of(image(Color.RED), image(Color.GREEN),
                image(Color.BLUE))), "plant");
        save(from, "ore", Color.YELLOW);
        Path archive = out.resolve("pictures" + TemplateArchive.EXTENSION);
        TemplateArchive.export(TemplateLibrary.list(from), Map.of("plant", 3, "ore", 1), archive);

        TemplateArchive.ImportResult result = TemplateArchive.importInto(to, archive);

        assertEquals(3, result.framesOf("plant"), "the import declares .frames(3) again");
        assertEquals(1, result.framesOf("ore"));
        assertEquals(12, ImageIO.read(TemplateLibrary.fileForName(to, "plant").toFile()).getWidth());
        assertFalse(Files.exists(to.resolve("images").resolve(TemplateArchive.FRAMES_FILE)));
    }

    @Test
    void aCountThatDoesNotSplitItsFileImportsStill(@TempDir Path from, @TempDir Path to, @TempDir Path out)
            throws IOException {
        save(from, "plant", Color.RED);
        Path archive = out.resolve("pictures" + TemplateArchive.EXTENSION);
        TemplateArchive.export(TemplateLibrary.list(from), Map.of("plant", 3), archive);

        assertEquals(1, TemplateArchive.importInto(to, archive).framesOf("plant"), "4 px do not split into 3");
    }

    private static BufferedImage image(Color colour) {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) image.setRGB(x, y, colour.getRGB());
        return image;
    }

    private static byte[] png(Color colour) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image(colour), "png", bytes);
        return bytes.toByteArray();
    }

    private static void save(Path resources, String name, Color colour) throws IOException {
        TemplateLibrary.saveTemplate(resources, image(colour), name);
    }
}
