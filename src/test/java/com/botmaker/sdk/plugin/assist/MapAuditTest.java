package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.plugin.assist.MapAudit.Audit;
import com.botmaker.sdk.plugin.assist.MapAudit.Spot;
import com.botmaker.sdk.plugin.assist.MapAudit.Where;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A screen's shot checked: each picture it names at its place, away from it, or not on the shot. */
class MapAuditTest {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int SIZE = 40;

    @TempDir
    Path resources;

    /**
     * Home's shot: its marker and {@code here} where they were cut, {@code moved} 100 px right and 50 down of its place,
     * {@code gone} and the {@code event} that comes and goes not on it.
     */
    private Map<String, FrameCut> home() throws IOException {
        Map<String, Rect> places = Map.of("home", new Rect(20, 20, SIZE, SIZE), "here", new Rect(200, 100, SIZE, SIZE),
                "moved", new Rect(400, 200, SIZE, SIZE), "gone", new Rect(100, 250, SIZE, SIZE),
                "event", new Rect(300, 20, SIZE, SIZE));
        BufferedImage shot = noise(WIDTH, HEIGHT, 1);
        Graphics2D g = shot.createGraphics();
        long seed = 10;
        for (String name : List.of("home", "here", "moved", "gone", "event")) {
            BufferedImage picture = noise(SIZE, SIZE, seed++);
            Path file = TemplateLibrary.fileForName(resources, name);
            Files.createDirectories(file.getParent());
            ImageIO.write(picture, "png", file.toFile());
            Rect at = places.get(name);
            if (name.equals("moved")) g.drawImage(picture, at.x() + 100, at.y() + 50, null);
            else if (!name.equals("gone") && !name.equals("event")) g.drawImage(picture, at.x(), at.y(), null);
        }
        g.dispose();
        Path shotFile = ScreenShots.file(resources, "HOME");
        Files.createDirectories(shotFile.getParent());
        ImageIO.write(shot, "png", shotFile.toFile());
        return Map.of("home", FrameCut.of(WIDTH, HEIGHT, places.get("home")),
                "here", FrameCut.of(WIDTH, HEIGHT, places.get("here")),
                "moved", FrameCut.of(WIDTH, HEIGHT, places.get("moved")),
                "gone", FrameCut.of(WIDTH, HEIGHT, places.get("gone")),
                "event", FrameCut.of(WIDTH, HEIGHT, places.get("event")));
    }

    private static GameScreen screen() {
        return GameScreen.named("Home").markers(marks("home")).inputs(List.of(Input.click(picture("here")),
                Input.click(picture("moved")), Input.click(picture("gone")), Input.click(picture("event")).sometimes()));
    }

    @Test
    void eachPictureIsAtItsPlaceAwayFromItOrNotOnTheShot() throws IOException {
        Map<String, FrameCut> cuts = home();
        Audit audit = MapAudit.of(resources, 0.9, cuts, "HOME", screen()).orElseThrow();
        assertTrue(audit.showsMarker());
        assertEquals(Where.AT_PLACE, audit.of("home").orElseThrow().where());
        assertEquals(Where.AT_PLACE, audit.of("here").orElseThrow().where());
        Spot moved = audit.of("moved").orElseThrow();
        assertEquals(Where.ELSEWHERE, moved.where());
        assertEquals(100, moved.dx());
        assertEquals(50, moved.dy());
        Spot gone = audit.of("gone").orElseThrow();
        assertEquals(Where.MISSING, gone.where());
        assertTrue(gone.sure(), "an input always there that is missing is a fault");
        Spot event = audit.of("event").orElseThrow();
        assertEquals(Where.MISSING, event.where());
        assertFalse(event.sure(), "one that comes and goes, missing, is only a note");
        // Made once for the same files and places.
        assertSame(audit, MapAudit.of(resources, 0.9, cuts, "HOME", screen()).orElseThrow());
    }

    @Test
    void aShotWithoutItsMarkerSaysSo() throws IOException {
        Map<String, FrameCut> cuts = home();
        // A splash in place of the screen: nothing it names is on it.
        ImageIO.write(noise(WIDTH, HEIGHT, 2), "png", ScreenShots.file(resources, "HOME").toFile());
        Audit audit = MapAudit.of(resources, 0.9, cuts, "HOME", screen()).orElseThrow();
        assertFalse(audit.showsMarker());
    }

    @Test
    void aScreenWithNoShotHasNoAudit() {
        assertTrue(MapAudit.of(resources, 0.9, Map.of(), "HOME", screen()).isEmpty());
    }

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    /** A screen's markers: the pictures {@code ids}. */
    private static List<ImageTemplate> marks(String... ids) {
        return Arrays.stream(ids).map(MapAuditTest::picture).toList();
    }

    private static BufferedImage noise(int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.setRGB(x, y, rnd.nextInt(0xFFFFFF));
        }
        return image;
    }
}
