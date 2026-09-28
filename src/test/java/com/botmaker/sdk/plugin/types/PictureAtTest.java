package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import com.botmaker.plugin.api.record.RecordedValue;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The project picture under a recorded click, and the constant it is then written as. */
class PictureAtTest {

    private static final int FRAME = 200;

    @Test
    void aClickOnAPictureNamesItAndDeclaresItsConstant(@TempDir Path resources) throws IOException {
        BufferedImage button = noise(24, 24, 7);
        TemplateLibrary.saveTemplate(resources, button, "collect", FRAME, FRAME, null);
        Services services = new Services(resources);

        Optional<ImageTemplate> found = PictureAt.find(services, new RecordedValue.Spot(60, 70, frameWith(button)));

        assertEquals("src/main/resources/images/collect.png", found.orElseThrow().filePath());
        assertEquals(List.of("COLLECT"), services.values.added);
    }

    @Test
    void aClickBesideEveryPictureNamesNone(@TempDir Path resources) throws IOException {
        BufferedImage button = noise(24, 24, 7);
        TemplateLibrary.saveTemplate(resources, button, "collect", FRAME, FRAME, null);
        Services services = new Services(resources);

        assertTrue(PictureAt.find(services, new RecordedValue.Spot(150, 150, frameWith(button))).isEmpty());
        assertTrue(services.values.added.isEmpty(), "nothing recorded, nothing declared");
    }

    /** A frame of other noise with {@code picture} at (50, 60). */
    private static BufferedImage frameWith(BufferedImage picture) {
        BufferedImage frame = noise(FRAME, FRAME, 99);
        frame.createGraphics().drawImage(picture, 50, 60, null);
        return frame;
    }

    private static BufferedImage noise(int w, int h, long seed) {
        Random random = new Random(seed);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setRGB(x, y, random.nextInt(0xFFFFFF));
        return image;
    }

    private static final class Values implements PluginValues {
        final List<String> added = new ArrayList<>();

        @Override public List<String> ids() { return List.of(); }
        @Override public Optional<ValueContext> open(String id) { return Optional.empty(); }
        @Override public List<String> members(String id) { return List.copyOf(added); }

        @Override
        public Optional<String> add(String id, String member, Object value) {
            added.add(member);
            return Optional.empty();
        }
    }

    private static final class Services implements StudioServices {
        final Values values = new Values();
        private final Path resources;

        Services(Path resources) {
            this.resources = resources;
        }

        @Override public Path projectDir() { return resources.getParent(); }
        @Override public Path resourcesDir() { return resources; }
        @Override public Theme theme() { return null; }
        @Override public Dialogs dialogs() { return null; }
        @Override public PluginValues pluginValues() { return values; }
    }
}
