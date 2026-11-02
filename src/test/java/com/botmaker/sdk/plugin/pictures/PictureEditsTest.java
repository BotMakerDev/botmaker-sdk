package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.plugin.SpelledValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A moving picture's frames through the edits the assistant and the windows make: save, replace, add one. */
class PictureEditsTest {

    private static final Rect PLACE = new Rect(10, 20, 16, 12);

    /** Noise of {@code seed}: two seeds never match each other. */
    private static BufferedImage noise(int seed, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(seed);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, 0xFF000000 | rnd.nextInt());
        return image;
    }

    private static List<BufferedImage> frames(int... seeds) {
        List<BufferedImage> frames = new ArrayList<>();
        for (int seed : seeds) frames.add(noise(seed, 16, 12));
        return frames;
    }

    @Test
    void savedFramesAreOneStripAndOneConstant(@TempDir Path resources) throws IOException {
        Host host = new Host();
        PictureCuts.Cut cut = PictureCuts.saveFrames(host.services(resources), frames(1, 2, 3), PLACE, "plant", null);
        assertEquals("plant", cut.name());
        assertEquals(48, png(resources, "plant").getWidth());
        assertEquals(new ImageTemplate("images/plant.png").at(PLACE).frames(3), host.held("PLANT"));
    }

    @Test
    void oneSavedFrameIsAStillPicture(@TempDir Path resources) throws IOException {
        Host host = new Host();
        PictureCuts.saveFrames(host.services(resources), frames(1), PLACE, "plant", null);
        assertEquals(1, host.held("PLANT").frames());
        assertEquals(16, png(resources, "plant").getWidth());
    }

    @Test
    void replaceWithStillCutDropsFrames(@TempDir Path resources) throws IOException {
        Host host = new Host();
        StudioServices services = host.services(resources);
        PictureCuts.saveFrames(services, frames(1, 2, 3), PLACE, "plant", null);
        PictureEdits.Done done = PictureEdits.replace(services, "plant", noise(9, 16, 12), FrameCut.of(0, 0, PLACE));
        assertTrue(done.ok(), done.said());
        assertEquals(16, png(resources, "plant").getWidth(), "one frame's PNG");
        assertEquals(new ImageTemplate("images/plant.png").at(PLACE), host.held("PLANT"), "no .frames");
    }

    @Test
    void addFrameAppends(@TempDir Path resources) throws IOException {
        Host host = new Host();
        StudioServices services = host.services(resources);
        PictureCuts.saveFrames(services, frames(1, 2), PLACE, "plant", null);
        BufferedImage third = noise(3, 16, 12);
        PictureEdits.Done done = PictureEdits.addFrame(services, "plant", third);
        assertTrue(done.ok(), done.said());
        assertEquals(new ImageTemplate("images/plant.png").at(PLACE).frames(3), host.held("PLANT"));
        BufferedImage strip = png(resources, "plant");
        assertEquals(48, strip.getWidth());
        assertEquals(third.getRGB(5, 5), FrameStrip.split(strip, 3).get(2).getRGB(5, 5), "the new frame is last");
    }

    @Test
    void addFrameRefusesOtherSize(@TempDir Path resources) throws IOException {
        Host host = new Host();
        StudioServices services = host.services(resources);
        PictureCuts.saveFrames(services, frames(1, 2), PLACE, "plant", null);
        PictureEdits.Done done = PictureEdits.addFrame(services, "plant", noise(3, 17, 12));
        assertFalse(done.ok());
        assertTrue(done.said().contains("16×12"), done.said());
        assertEquals(2, host.held("PLANT").frames());
        assertEquals(32, png(resources, "plant").getWidth());
    }

    @Test
    void addFrameRefusesNothingNew(@TempDir Path resources) throws IOException {
        Host host = new Host();
        StudioServices services = host.services(resources);
        PictureCuts.saveFrames(services, frames(1, 2), PLACE, "plant", null);
        PictureEdits.Done done = PictureEdits.addFrame(services, "plant", noise(2, 16, 12));
        assertFalse(done.ok());
        assertTrue(done.said().contains("frame 2"), done.said());
        assertEquals(2, host.held("PLANT").frames());
    }

    @Test
    void addFrameRefusesOnePastTheMost(@TempDir Path resources) throws IOException {
        Host host = new Host();
        StudioServices services = host.services(resources);
        PictureCuts.saveFrames(services, frames(IntStream.rangeClosed(1, FrameStrip.MOST).toArray()), PLACE, "plant",
                null);
        PictureEdits.Done done = PictureEdits.addFrame(services, "plant", noise(FrameStrip.MOST + 1, 16, 12));
        assertFalse(done.ok());
        assertTrue(done.said().contains(FrameStrip.MOST + " frames"), done.said());
    }

    private static BufferedImage png(Path resources, String name) throws IOException {
        return ImageIO.read(TemplateLibrary.fileForName(resources, name).toFile());
    }

    /** The bot's {@code Pictures}, held in a map: what an edit writes is what the next one reads. */
    private static final class Host extends SpelledValues {
        private final Map<String, ImageTemplate> start = new HashMap<>();
        private final Map<String, TestContexts.Recording> opened = new HashMap<>();

        ImageTemplate held(String member) {
            TestContexts.Recording written = opened.get(member);
            return written != null ? (ImageTemplate) written.value() : start.get(member);
        }

        StudioServices services(Path resources) {
            return (StudioServices) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{StudioServices.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "resourcesDir" -> resources;
                        case "pluginValues" -> this;
                        default -> null;
                    });
        }

        @Override public List<String> ids() { return List.of("pictures"); }
        @Override public Optional<ValueContext> open(String id) { return Optional.empty(); }
        @Override public List<String> members(String id) { return List.copyOf(start.keySet()); }

        @Override
        public Optional<ValueContext> open(String id, String member) {
            if (!start.containsKey(member)) return Optional.empty();
            TestContexts.Recording row = TestContexts.row(ImageTemplate.class, "new ImageTemplate(\"x.png\")")
                    .withValue(held(member));
            opened.put(member, row);
            return Optional.of(row);
        }

        @Override
        public Optional<String> add(String id, String member, Object value) {
            start.put(member, (ImageTemplate) value);
            opened.remove(member);
            return Optional.empty();
        }
    }
}
