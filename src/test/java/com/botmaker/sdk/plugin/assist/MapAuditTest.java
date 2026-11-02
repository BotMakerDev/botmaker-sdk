package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.MapFixtures;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
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
import java.util.LinkedHashMap;
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

    /** Where Home's Java names its shot. */
    private static final String SHOT = "screens/HOME.png";

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
        Path shotFile = resources.resolve(SHOT);
        Files.createDirectories(shotFile.getParent());
        ImageIO.write(shot, "png", shotFile.toFile());
        return Map.of("home", FrameCut.of(WIDTH, HEIGHT, places.get("home")),
                "here", FrameCut.of(WIDTH, HEIGHT, places.get("here")),
                "moved", FrameCut.of(WIDTH, HEIGHT, places.get("moved")),
                "gone", FrameCut.of(WIDTH, HEIGHT, places.get("gone")),
                "event", FrameCut.of(WIDTH, HEIGHT, places.get("event")));
    }

    private static MapScreen screen() {
        return MapFixtures.screen(Setup.named("Home").markers(marks("home")).shot(SHOT), Button.of(picture("here")),
                Button.of(picture("moved")), Button.of(picture("gone")), Button.of(picture("event")).occasional());
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

    /** Hades II: the Options skull crowns Save slots too, whose shot came after the skull was cut. */
    @Test
    void aMarkerAnotherScreensShotShowsIsSaid() throws IOException {
        BufferedImage skull = noise(SIZE, SIZE, 20);
        BufferedImage choose = noise(SIZE, SIZE, 21);
        for (Map.Entry<String, BufferedImage> picture : Map.of("skull", skull, "choose", choose).entrySet()) {
            Path file = TemplateLibrary.fileForName(resources, picture.getKey());
            Files.createDirectories(file.getParent());
            ImageIO.write(picture.getValue(), "png", file.toFile());
        }
        writeShot("screens/OPTIONS.png", Map.of(skull, new Rect(300, 10, SIZE, SIZE)));
        writeShot("screens/SAVE_SLOTS.png", Map.of(skull, new Rect(300, 10, SIZE, SIZE),
                choose, new Rect(200, 100, SIZE, SIZE)));
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("OPTIONS", MapFixtures.screen(Setup.named("Options").markers(marks("skull"))
                .shot("screens/OPTIONS.png")));
        screens.put("SAVE_SLOTS", MapFixtures.screen(Setup.named("Save slots").markers(marks("choose"))
                .shot("screens/SAVE_SLOTS.png")));

        List<String> said = MapAudit.clashes(resources, 0.9, ScreenGraph.of(screens), null);

        assertEquals(1, said.size(), said.toString());
        assertTrue(said.getFirst().startsWith("Options: "), said.toString());
        assertTrue(said.getFirst().contains("Save slots's shot too"), said.toString());
    }

    /**
     * A picture with no place is looked for over the whole frame once: the near look already did it, and looking again
     * where it was missing doubled read_map's 400 s on Sword x Staff (2026-11-02).
     */
    @Test
    void aPictureWithNoPlaceIsLookedForOnce() throws IOException {
        Path file = TemplateLibrary.fileForName(resources, "lost");
        Files.createDirectories(file.getParent());
        ImageIO.write(noise(SIZE, SIZE, 30), "png", file.toFile());
        long before = MapAudit.SEARCHED.sum();
        List<MapAudit.Fit> fits = MapAudit.fits(resources, 0.9, Map.of(), List.of("lost"), noise(WIDTH, HEIGHT, 3));
        assertEquals(Where.MISSING, fits.getFirst().where());
        assertEquals(1, MapAudit.SEARCHED.sum() - before);
    }

    /** Each screen's markers on each other shot are matched once for the same files: a second read_map matches none. */
    @Test
    void clashesAreMatchedOnceForTheSameFiles() throws IOException {
        BufferedImage skull = noise(SIZE, SIZE, 40);
        BufferedImage choose = noise(SIZE, SIZE, 41);
        for (Map.Entry<String, BufferedImage> picture : Map.of("skull2", skull, "choose2", choose).entrySet()) {
            Path file = TemplateLibrary.fileForName(resources, picture.getKey());
            Files.createDirectories(file.getParent());
            ImageIO.write(picture.getValue(), "png", file.toFile());
        }
        writeShot("screens/A.png", Map.of(skull, new Rect(300, 10, SIZE, SIZE)));
        writeShot("screens/B.png", Map.of(choose, new Rect(200, 100, SIZE, SIZE)));
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", MapFixtures.screen(Setup.named("A").markers(marks("skull2")).shot("screens/A.png")));
        screens.put("B", MapFixtures.screen(Setup.named("B").markers(marks("choose2")).shot("screens/B.png")));
        Path third = TemplateLibrary.fileForName(resources, "third2");
        ImageIO.write(noise(SIZE, SIZE, 42), "png", third.toFile());
        writeShot("screens/C.png", Map.of());
        screens.put("C", MapFixtures.screen(Setup.named("C").markers(marks("third2")).shot("screens/C.png")));
        ScreenGraph graph = ScreenGraph.of(screens);

        long batches = MapAudit.BATCHES.sum();
        List<String> first = MapAudit.clashes(resources, 0.9, graph, null);
        assertEquals(3, MapAudit.BATCHES.sum() - batches, "one batch a shot, every screen's markers in it");
        long before = MapAudit.SEARCHED.sum();
        assertEquals(first, MapAudit.clashes(resources, 0.9, graph, null));
        assertEquals(0, MapAudit.SEARCHED.sum() - before);
    }

    private void writeShot(String path, Map<BufferedImage, Rect> pictures) throws IOException {
        BufferedImage shot = noise(WIDTH, HEIGHT, 2);
        Graphics2D g = shot.createGraphics();
        pictures.forEach((picture, at) -> g.drawImage(picture, at.x(), at.y(), null));
        g.dispose();
        Path file = resources.resolve(path);
        Files.createDirectories(file.getParent());
        ImageIO.write(shot, "png", file.toFile());
    }

    /** In a list, anywhere in it is its place, and one scrolled out of the shot is only a note. */
    @Test
    void anInputInItsListIsAtItsPlaceAnywhereInItAndScrolledOutIsANote() throws IOException {
        Map<String, FrameCut> cuts = home();
        // The list runs along its members' places, grown by the largest: 400..600 by 200..290, grown by 40.
        ImageTemplate moved = picture("moved").at(new Rect(400, 200, SIZE, SIZE));
        ImageTemplate out = picture("gone").at(new Rect(560, 250, SIZE, SIZE));
        Rect column = new Rect(360, 160, 280, 170);
        MapScreen listed = MapFixtures.screen(Setup.named("Home").markers(marks("home")).shot(SHOT), Button.of(moved),
                Button.of(out), Strip.of(Scroll.wheel(-3), List.of(moved, out)));
        Audit audit = MapAudit.of(resources, 0.9, cuts, "HOME", listed).orElseThrow();
        assertEquals(Where.AT_PLACE, audit.of("moved").orElseThrow().where(), "another slot of its list");
        Spot gone = audit.of("gone").orElseThrow();
        assertEquals(Where.MISSING, gone.where());
        assertFalse(gone.sure(), "scrolled out of the shot's list is a note, not a fault");
        assertEquals(column, gone.area());
        // Out of a list that does not hold it, it sits elsewhere again.
        ImageTemplate atHome = picture("moved").at(new Rect(20, 20, SIZE, SIZE));
        MapScreen narrow = MapFixtures.screen(Setup.named("Home").markers(marks("home")).shot(SHOT), Button.of(atHome),
                Strip.of(Scroll.wheel(-3), List.of(atHome)));
        assertEquals(Where.ELSEWHERE,
                MapAudit.of(resources, 0.9, cuts, "HOME", narrow).orElseThrow().of("moved").orElseThrow().where());
    }

    @Test
    void aShotWithoutItsMarkerSaysSo() throws IOException {
        Map<String, FrameCut> cuts = home();
        // A splash in place of the screen: nothing it names is on it.
        ImageIO.write(noise(WIDTH, HEIGHT, 2), "png", resources.resolve(SHOT).toFile());
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
