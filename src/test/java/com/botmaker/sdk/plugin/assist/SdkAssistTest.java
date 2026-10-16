package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.History;
import com.botmaker.plugin.api.Runs;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.overlay.Marks;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.run.RunProperty;
import com.botmaker.sdk.plugin.SpelledValues;
import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Kind;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.PictureTag;
import com.botmaker.sdk.internal.capture.core.RecordingNativeController;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.trace.MissFrames;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.flow.FlowValue;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.pictures.TemplateManifest;
import com.botmaker.sdk.plugin.types.FlowTypes;
import com.botmaker.sdk.plugin.types.ScreenTypes;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every assistant tool the SDK serves, run headless the way the host runs it — {@code invoke} with the JSON the
 * assistant sent — on generated frames and a host that writes values into maps. No JavaFX is started: the writes
 * run on the caller's thread ({@link FxCall}).
 */
class SdkAssistTest {

    private static final int SIZE = 48;
    private static final Area WATCHED = new Area(1000, 500, 400, 300);

    @TempDir
    Path bot;
    private Path resources;

    private Host host;
    private Agent agent;

    @BeforeEach
    void setUp() throws IOException {
        Frames.forget();
        resources = Files.createDirectories(bot.resolve("src/main/resources"));
        host = new Host(resources);
        agent = new Agent(host);
        TemplateLibrary.saveTemplate(resources, patch(), "ore");
        host.set("pictures").put("ORE", TestContexts.row(Object.class, "").withValue(
                new ImageTemplate(TemplateLibrary.pathForName("ore"))));
    }

    @AfterEach
    void tearDown() {
        NativeControllerFactory.setForTesting(null);
    }

    // ── seeing ──────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void aLargeFrameIsShrunkUnlessAScaleIsGiven() throws IOException {
        agent.frame = new java.awt.image.BufferedImage(1920, 1080, java.awt.image.BufferedImage.TYPE_INT_RGB);
        AgentReply look = run("screenshot");
        assertEquals(new java.awt.Dimension(768, 432), size(look));
        assertTrue(text(look).contains("Shrunk to 768×432: multiply what you see by 2.5000"), text(look));
        assertEquals(new java.awt.Dimension(1920, 1080), size(run("screenshot", "scale", 1.0)));
        assertEquals(new java.awt.Dimension(1920, 1080), size(run("screenshot", "grid", 100)), "a grid is read whole");
        assertEquals(new java.awt.Dimension(768, 432),
                imageSize(Frames.boxed(agent.frame, java.util.List.of(), Frames.FOUND)), "a find's image is small");
    }

    private static java.awt.Dimension imageSize(java.awt.image.BufferedImage image) {
        return new java.awt.Dimension(image.getWidth(), image.getHeight());
    }

    @Test
    void aScreenshotSaysItsSizeAndShowsAPartScaledWithAGrid() throws IOException {
        agent.frame = withPatchAt(120, 80);
        AgentReply whole = run("screenshot");
        assertTrue(text(whole).startsWith("400×300 at 1000,500"), text(whole));
        assertEquals(new java.awt.Dimension(400, 300), size(whole));

        AgentReply part = run("screenshot", "x", 100, "y", 50, "width", 200, "height", 100, "grid", 50,
                "scale", 0.5);
        assertEquals(new java.awt.Dimension(100, 50), size(part));
        assertTrue(text(part).contains("Showing 100,50 (200×100)"), text(part));
        assertTrue(Frames.shown().isPresent(), "the whole frame is kept for crop_pictures");

        Frames.forget();
        assertTrue(run("screenshot", "x", 10).isRefused(), "a part needs all four numbers");
        assertTrue(Frames.shown().isEmpty(), "a refused screenshot keeps no frame");
        assertTrue(run("screenshot", "grid", 10).isRefused());
        agent.frame = null;
        assertTrue(run("screenshot").isRefused());
    }

    @Test
    void aPictureIsFoundWithItsScoreAndMarkedOnTheGame() {
        agent.frame = withPatchAt(120, 80);
        AgentReply found = run("find_picture", "picture", "ore");
        assertTrue(text(found).startsWith("ore best at 120,80 (48×48), score 1.00; a run needs 0.80, so it finds it"),
                text(found));
        assertEquals(List.of("FOUND 1120,580 48×48 ore"), agent.marks);
        assertTrue(run("find_picture", "picture", "gold").isRefused());
    }

    @Test
    void aFindAnswersInTextAndShowsItsBoxesOnlyWhenAsked() throws IOException {
        agent.frame = withPatchAt(120, 80);
        AgentReply waited = run("wait_for_picture", "picture", "ore", "timeoutMs", 0);
        assertTrue(text(waited).startsWith("ore is there"), text(waited));
        for (AgentReply plain : List.of(run("find_picture", "picture", "ore"), run("which_screen"), waited,
                run("find_color", "color", "#000000"))) {
            assertTrue(plain.parts().stream().noneMatch(p -> p.kind() == AgentReply.Kind.IMAGE),
                    "the text already gives the places: " + text(plain));
        }
        assertEquals(new java.awt.Dimension(400, 300), size(run("find_picture", "picture", "ore", "show", true)));
        assertEquals(new java.awt.Dimension(400, 300), size(run("which_screen", "show", true)));
        assertEquals(new java.awt.Dimension(400, 300),
                size(run("wait_for_picture", "picture", "ore", "timeoutMs", 0, "show", true)));

        agent.frame = withText("BotMaker 42", 30, 100);
        AgentReply text = run("find_text", "text", "botmaker");
        assertTrue(text.parts().stream().noneMatch(p -> p.kind() == AgentReply.Kind.IMAGE), text(text));
        assertEquals(new java.awt.Dimension(400, 300), size(run("find_text", "text", "botmaker", "show", true)));
    }

    @Test
    void whichScreenScoresEveryPictureOnOneFrame() throws IOException {
        TemplateLibrary.saveTemplate(resources, noise(SIZE, SIZE, 3), "elsewhere");
        agent.frame = withPatchAt(120, 80);
        String said = text(run("which_screen"));
        assertTrue(said.startsWith("1 on screen"), said);
        assertTrue(said.contains("✓ ore 1.00 at 120,80"), said);
        assertTrue(said.indexOf("ore") < said.indexOf("elsewhere"), "best first: " + said);
    }

    @Test
    void waitingForAPictureAnswersWhenItIsThereOrWhenTheTimeIsUp() {
        agent.frame = withPatchAt(120, 80);
        assertTrue(text(run("wait_for_picture", "picture", "ore", "timeoutMs", 1000)).startsWith("ore is there after"));
        agent.frame = noise(WATCHED.width(), WATCHED.height(), 11);
        assertTrue(text(run("wait_for_picture", "picture", "ore", "timeoutMs", 0)).startsWith("ore did not come in 0 ms"));
        assertTrue(run("wait_for_picture", "picture", "ore", "timeoutMs", 40_000).isRefused());
    }

    @Test
    void textIsReadWhereItIsAndFoundByAnyCase() {
        agent.frame = withText("BotMaker 42", 30, 100);
        String read = text(run("read_text"));
        assertTrue(read.contains("BotMaker 42"), read);
        String inArea = text(run("read_text", "x", 0, "y", 0, "width", 400, "height", 40));
        assertEquals("No text could be read there.", inArea, "nothing is written in the top strip");

        AgentReply found = run("find_text", "text", "botmaker");
        assertTrue(text(found).startsWith("\"BotMaker 42\" at "), text(found));
        assertEquals(1, agent.marks.size());
        assertEquals("\"nothing\" is not on the screen as text.", text(run("find_text", "text", "nothing")));
    }

    @Test
    void aPixelsColourIsReadAndAColourIsFoundAsPatches() {
        BufferedImage frame = plain(Color.GRAY);
        Graphics2D g = frame.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(200, 100, 20, 10);
        g.dispose();
        agent.frame = frame;

        assertEquals("205,105 is #FF0000 (255,0,0).", text(run("pixel_color", "x", 205, "y", 105)));
        assertTrue(run("pixel_color", "x", 400, "y", 0).isRefused());

        String patches = text(run("find_color", "color", "#ff0000"));
        assertTrue(patches.startsWith("1 patch of #FF0000"), patches);
        assertTrue(patches.contains("200 px at 200,100 (20×10), centre 210,105"), patches);
        assertTrue(text(run("find_color", "color", "255,0,0", "x", 0, "y", 0, "width", 100, "height", 100))
                .startsWith("#FF0000 is not on the screen there"));
        assertTrue(run("find_color", "color", "red").isRefused());
    }

    @Test
    void theLatestRunsSavedFramesAreListedAndShownByNameOnly() throws IOException {
        assertTrue(text(run("missed_frames")).startsWith("The latest run saved no frame"));
        Path run = Files.createDirectories(bot.resolve(MissFrames.FOLDER).resolve("20261011-120000"));
        ImageIO.write(new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB), "png",
                run.resolve("01-ore-not-found.png").toFile());

        String listed = text(run("missed_frames"));
        assertTrue(listed.endsWith("01-ore-not-found.png"), listed);
        AgentReply shown = run("missed_frames", "name", "01-ore-not-found.png");
        assertEquals(new java.awt.Dimension(768, 432), size(shown));
        assertTrue(run("missed_frames", "name", "../../src/main/resources/ore.png").isRefused());
    }

    // ── pictures and places ─────────────────────────────────────────────────────────────────────────────

    @Test
    void aCropIsCutFromTheLastScreenshotAndDeclared() {
        assertTrue(cropOne("name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE).isRefused(),
                "no screenshot yet");
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        agent.frame = null;
        AgentReply saved = cropOne("name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE);
        assertEquals("Saved gold as Pictures.GOLD.", text(saved));
        assertTrue(TemplateLibrary.exists(resources, "gold"));
        assertTrue(host.set("pictures").containsKey("GOLD"));
        assertTrue(text(run("list_pictures")).contains("gold → Pictures.GOLD"));
    }

    @Test
    void aFreeShapeIsCutThroughItsPointsAndItsOutsideLeftOut() throws IOException {
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        agent.frame = null;
        List<Map<String, Integer>> triangle = List.of(Map.of("x", 120, "y", 80), Map.of("x", 167, "y", 80),
                Map.of("x", 120, "y", 127));
        assertTrue(cropOne("name", "tri", "points", triangle.subList(0, 2)).isRefused(), "three points at least");
        assertTrue(cropOne("name", "tri", "x", 120, "y", 80, "width", SIZE, "height", SIZE, "points", triangle)
                .isRefused(), "a polygon's box is its points'");
        assertTrue(cropOne("name", "tri", "shape", "ellipse", "points", triangle).isRefused(), "points are a polygon's");
        assertFalse(TemplateLibrary.exists(resources, "tri"));

        AgentReply saved = cropOne("name", "tri", "points", triangle);
        assertTrue(text(saved).startsWith("Saved tri as Pictures.TRI."), text(saved));
        BufferedImage tri = ImageIO.read(TemplateLibrary.fileForName(resources, "tri").toFile());
        assertEquals(SIZE, tri.getWidth(), "cut to the box around the points");
        assertEquals(SIZE, tri.getHeight());
        assertEquals(255, tri.getRGB(4, 4) >>> 24, "inside the triangle is kept");
        assertEquals(0, tri.getRGB(SIZE - 3, SIZE - 3) >>> 24, "outside it is transparent");
        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(saved.parts().stream()
                .filter(p -> p.kind() == AgentReply.Kind.IMAGE).findFirst().orElseThrow().png()));
        assertEquals(255, preview.getRGB(SIZE - 3, SIZE - 3) >>> 24, "the preview checkers what is left out");

        AgentReply round = cropOne("name", "coin", "x", 120, "y", 80, "width", SIZE, "height", SIZE, "shape", "ellipse");
        assertTrue(text(round).startsWith("Saved coin as Pictures.COIN."), text(round));
        BufferedImage coin = ImageIO.read(TemplateLibrary.fileForName(resources, "coin").toFile());
        assertEquals(255, coin.getRGB(SIZE / 2, SIZE / 2) >>> 24);
        assertEquals(0, coin.getRGB(0, 0) >>> 24, "a corner is outside the oval");
    }

    @Test
    void aBoxThatChangesOverTimeIsNotCut() {
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        agent.frame = noise(WATCHED.width(), WATCHED.height(), 9);
        AgentReply refused = cropOne("name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE);
        assertTrue(refused.isRefused() && text(refused).startsWith("That box changes"), text(refused));
        assertFalse(TemplateLibrary.exists(resources, "gold"));
        assertTrue(run("edit_pictures", "action", "replace", "picture", "ore", "x", 120, "y", 80, "width", SIZE, "height", SIZE)
                .isRefused());

        agent.frame = withPatchAt(120, 80);
        assertFalse(cropOne("name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE)
                .isRefused(), "the same frame again is still");
    }

    @Test
    void aPictureIsTaggedOnlyWithDeclaredTagsAndEveryTagEditIsATool() {
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        agent.frame = null;
        assertTrue(cropOne("name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE,
                "tags", List.of("Home")).isRefused(), "Home is not declared");
        assertFalse(TemplateLibrary.exists(resources, "gold"), "a refused tag saves nothing");

        assertEquals("Declared Home in Tags; edit_pictures' tag puts it on pictures.",
                text(run("edit_tags", "action", "declare", "tag", " Home ")));
        assertEquals(PictureTag.named("Home"), host.set("tags").get("HOME").value(), "Tags.HOME, in the bot's Java");
        assertTrue(run("edit_tags", "action", "declare", "tag", "home").isRefused(), "already there, in another case");
        assertTrue(run("edit_tags", "action", "declare", "tag", TemplateManifest.UNTAGGED).isRefused(), "a built-in group");
        assertEquals("Saved gold as Pictures.GOLD, tagged Home.", text(cropOne("name", "gold",
                "x", 120, "y", 80, "width", SIZE, "height", SIZE, "tags", List.of("home", "HOME"))),
                "spelled as declared, once");
        assertEquals(List.of(BotConstants.tag("HOME")),
                ((ImageTemplate) host.set("pictures").get("GOLD").value()).tags(), ".tags(List.of(Tags.HOME))");
        assertTrue(text(run("list_pictures")).contains("gold → Pictures.GOLD [Home]"));

        run("edit_tags", "action", "declare", "tag", "Popups");
        assertTrue(run("edit_pictures", "action", "tag", "tag", "Nope", "pictures", List.of("gold")).isRefused());
        assertTrue(run("edit_pictures", "action", "tag", "tag", "Popups", "pictures", List.of("gold", "nothing")).isRefused());
        assertFalse(run("edit_pictures", "action", "tag", "tag", "popups", "pictures", List.of("gold", "ore")).isRefused());
        assertEquals(java.util.Set.of("Home", "Popups"), TemplateLibrary.manifest(host).tagsOf("gold"));
        String listed = text(run("list_tags"));
        assertTrue(listed.contains("Popups: gold, ore"), listed);

        assertEquals("Took Popups off ore.", text(run("edit_pictures", "action", "untag", "tag", "Popups", "pictures", List.of("ore"))));
        assertTrue(TemplateLibrary.manifest(host).tagsOf("ore").isEmpty());
        assertTrue(run("edit_pictures", "action", "untag", "tag", "Popups", "pictures", List.of("ore")).isRefused(), "ore has none");
        assertEquals("gold carries Popups.", text(run("edit_pictures", "action", "set_tags", "picture", "gold", "tags", List.of("Popups"))));

        assertTrue(run("edit_tags", "action", "rename", "tag", "Popups", "to", "Home").isRefused(), "taken");
        assertEquals("Renamed Popups to Dialogs.", text(run("edit_tags", "action", "rename", "tag", "Popups", "to", "Dialogs")));
        assertEquals(PictureTag.named("Dialogs"), host.set("tags").get("DIALOGS").value(), "constant and words");
        assertEquals(java.util.Set.of("Dialogs"), TemplateLibrary.manifest(host).tagsOf("gold"));
        assertEquals("Deleted Dialogs; its pictures stay.", text(run("edit_tags", "action", "delete", "tag", "Dialogs")));
        assertTrue(TemplateLibrary.manifest(host).tagsOf("gold").isEmpty());
        assertFalse(host.set("tags").containsKey("DIALOGS"));
        assertTrue(TemplateLibrary.exists(resources, "gold"));
        assertTrue(run("edit_tags", "action", "delete", "tag", "Dialogs").isRefused());
    }

    @Test
    void aPictureIsFiledUnderAnActivityByItsEnum() {
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        assertFalse(run("edit_flow", "action", "add_activity", "name", "Collect").isRefused());

        assertEquals("ore carries Collect.", text(run("edit_pictures", "action", "set_tags", "picture", "ore", "tags", List.of("collect"))));
        assertEquals(List.of(new Activity("Collect")),
                ((ImageTemplate) host.set("pictures").get("ORE").value()).activities(),
                ".activities(List.of(Activities.Collect.class))");
        assertTrue(((ImageTemplate) host.set("pictures").get("ORE").value()).tags().isEmpty());
    }

    @Test
    void theFirstCutSetsTheBotsScreenAndALaterOneFromAnotherFrameNamesItsOwn() {
        host.values.put("screen", TestContexts.row(Size.class, "").withValue(new Size(0, 0)));
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        cropOne("name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE);
        assertEquals(new Size(WATCHED.width(), WATCHED.height()), host.values.get("screen").value(),
                "the first picture's frame is the bot's size");
        assertEquals(new ImageTemplate(TemplateLibrary.pathForName("gold")).at(new Rect(120, 80, SIZE, SIZE)),
                host.set("pictures").get("GOLD").value(), "cut at the bot's size: no .in");

        agent.frame = noise(800, 600, 5);
        run("screenshot");
        cropOne("name", "iron", "x", 0, "y", 0, "width", SIZE, "height", SIZE);
        assertEquals(new ImageTemplate(TemplateLibrary.pathForName("iron")).at(new Rect(0, 0, SIZE, SIZE))
                .in(new Size(800, 600)), host.set("pictures").get("IRON").value());
        assertEquals(new Size(WATCHED.width(), WATCHED.height()), host.values.get("screen").value(), "kept");
    }

    @Test
    void aReplacementRewritesWhereThePictureWasCut() {
        host.values.put("screen", TestContexts.row(Size.class, "").withValue(new Size(800, 600)));
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        assertFalse(run("edit_pictures", "action", "replace", "picture", "ore", "x", 120, "y", 80, "width", SIZE, "height", SIZE)
                .isRefused());
        assertEquals(new ImageTemplate(TemplateLibrary.pathForName("ore")).at(new Rect(120, 80, SIZE, SIZE))
                .in(new Size(WATCHED.width(), WATCHED.height())), host.set("pictures").get("ORE").value());
    }

    @Test
    void aPictureIsShownWithItsConstantAndUses() {
        assertEquals("ore: 48×48, Pictures.ORE, unused.", text(run("show_picture", "picture", "ore")));
        host.uses.put("ORE", List.of(new PluginValues.Use(Path.of("/bot/Collect.java"), 4, "click(Pictures.ORE);")));
        assertTrue(text(run("show_picture", "picture", "ore")).endsWith("1 use in 1 file."));
    }

    @Test
    void aRenameMovesTheFileAndTheConstant() {
        host.set("pictures").get("ORE").set(new ImageTemplate(TemplateLibrary.pathForName("ore"))
                .at(new Rect(1, 2, 3, 4)).in(new Size(800, 600)));
        assertEquals("Renamed ore to iron.", text(run("edit_pictures", "action", "rename", "picture", "ore", "to", "iron")));
        assertEquals(new ImageTemplate(TemplateLibrary.pathForName("iron")).at(new Rect(1, 2, 3, 4))
                .in(new Size(800, 600)), host.set("pictures").get("IRON").value(), "where it was cut stays");
        assertTrue(TemplateLibrary.exists(resources, "iron"));
        assertFalse(TemplateLibrary.exists(resources, "ore"));
        assertEquals(List.of("rename pictures ORE IRON"), host.calls);
    }

    @Test
    void aPictureInUseIsDeletedOnlyAfterItsUsesMove() throws IOException {
        TemplateLibrary.saveTemplate(resources, patch(), "gold");
        host.uses.put("ORE", List.of(new PluginValues.Use(Path.of("/bot/Collect.java"), 4, "click(Pictures.ORE);")));

        AgentReply refused = run("edit_pictures", "action", "delete", "picture", "ore");
        assertTrue(refused.isRefused());
        assertTrue(text(refused).contains("Collect.java:4"), text(refused));
        assertTrue(TemplateLibrary.exists(resources, "ore"));

        AgentReply deleted = run("edit_pictures", "action", "delete", "picture", "ore", "pointUsesAt", "gold");
        assertFalse(deleted.isRefused(), text(deleted));
        assertFalse(TemplateLibrary.exists(resources, "ore"));
        assertTrue(host.calls.contains("add pictures GOLD"), host.calls.toString());
        assertTrue(host.calls.stream().anyMatch(c -> c.startsWith("repoint pictures ORE GOLD")), host.calls.toString());
        assertTrue(host.calls.contains("remove pictures ORE"), host.calls.toString());
    }

    /** A picture only the bot's Screens use leaves them with its file; a delete that stops puts them back. */
    @Test
    void aPictureOnlyTheMapUsesLeavesItWithItsFile() {
        ImageTemplate ore = new ImageTemplate(TemplateLibrary.pathForName("ore"));
        host.set("screen_map").put("HOME", TestContexts.row(Object.class, "").withValue(GameScreen.named("Home")
                .markers(List.of(ore)).inputs(List.of(Input.click(ore)))));
        host.uses.put("ORE", List.of(new PluginValues.Use(Path.of("Screens.java"), 40, "helper(Pictures.ORE);")));

        AgentReply kept = run("edit_pictures", "action", "delete", "picture", "ore");
        assertTrue(kept.isRefused() && text(kept).contains("Screens.java:40"), text(kept));
        assertEquals("ore", screen("HOME").firstMarker().id(), "a use the map cannot take puts the screens back");
        assertEquals(1, screen("HOME").inputs().size());

        host.uses.clear();
        String deleted = text(run("edit_pictures", "action", "delete", "picture", "ore"));
        assertEquals("Deleted ore; it left the map's Home.", deleted);
        assertFalse(screen("HOME").hasMarker());
        assertTrue(screen("HOME").inputs().isEmpty());
        assertFalse(TemplateLibrary.exists(resources, "ore"));
    }

    @Test
    void aReplacementKeepsTheNameAndTakesTheNewImage() throws IOException {
        host.values.put("screen", TestContexts.row(Size.class, "").withValue(new Size(WATCHED.width(), WATCHED.height())));
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        AgentReply replaced = run("edit_pictures", "action", "replace", "picture", "ore", "x", 0, "y", 0, "width", 30, "height", 20);
        assertFalse(replaced.isRefused(), text(replaced));
        BufferedImage now = ImageIO.read(TemplateLibrary.fileForName(resources, "ore").toFile());
        assertEquals(30, now.getWidth());
        assertEquals(20, now.getHeight());
        assertEquals(new ImageTemplate(TemplateLibrary.pathForName("ore")).at(new Rect(0, 0, 30, 20)),
                host.set("pictures").get("ORE").value(), "its constant says where the new image was cut");
        assertTrue(host.calls.stream().noneMatch(c -> c.contains("pictures")), "nothing renamed: " + host.calls);
    }

    @Test
    void aPlaceIsSavedInTheBotsPixelsFromTheGamesCorner() {
        host.values.put("screen", TestContexts.row(Size.class, "").withValue(new Size(0, 0)));
        agent.frame = withPatchAt(120, 80);
        AgentReply region = run("save_region", "name", "bag", "x", 300, "y", 200, "width", 200, "height", 200);
        assertTrue(text(region).startsWith("Saved Zones.BAG = 300,200 (100×100)"), "clipped: " + text(region));
        assertEquals(new Zone(300, 200, 100, 100), host.set("zones").get("BAG").value());
        assertEquals(new Size(400, 300), host.values.get("screen").value(),
                "a bot naming no size takes the screenshot's");
        assertTrue(run("save_region", "name", "2nd", "x", 10, "y", 20, "width", 5, "height", 5).isRefused(),
                "no constant starts with a digit");
        assertEquals(List.of("create zones", "add zones BAG"), host.calls);
    }

    @Test
    void aPlaceOnAGameShowingAnotherSizeIsWrittenAtTheBotsSize() {
        host.values.put("screen", TestContexts.row(Size.class, "").withValue(new Size(200, 150)));
        agent.frame = withPatchAt(120, 80);
        assertTrue(text(run("save_region", "name", "claim", "x", 100, "y", 60, "width", 40, "height", 20))
                .startsWith("Saved Zones.CLAIM = 50,30 (20×10) of the bot's 200×150 screen"),
                "the 400×300 game is twice it");
        assertEquals(new Zone(50, 30, 20, 10), host.set("zones").get("CLAIM").value());
    }

    @Test
    void aNumbersDigitsAreCutIntoADigitSetAndReadBack() {
        host.values.put("screen", TestContexts.row(Size.class, "").withValue(new Size(0, 0)));
        BufferedImage frame = plain(new Color(0x10, 0x20, 0x50));
        Graphics2D g = frame.createGraphics();
        g.setColor(new Color(0xf0, 0xd0, 0x30));
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
        g.drawString("4071", 100, 100);
        g.dispose();
        agent.frame = frame;
        run("screenshot");
        assertTrue(run("save_digits", "name", "gold", "x", 94, "y", 76, "width", 64, "height", 30, "shows", "407")
                .isRefused(), "four characters in the box, three named");
        AgentReply saved = run("save_digits", "name", "gold", "x", 94, "y", 76, "width", 64, "height", 30,
                "shows", "4071");
        assertTrue(text(saved).startsWith("Saved digits 0 1 4 7 into Digits.GOLD (src/main/resources/digits/gold) of"
                + " the bot's 400×300 screen. It holds 0 1 4 7; it lacks 2 3 5 6 8 9"), text(saved));
        assertTrue(text(saved).contains("It reads this box as 4071."), text(saved));
        assertEquals(new DigitSet("src/main/resources/digits/gold"), host.set("digits").get("GOLD").value());
    }

    // ── the flow ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void theFlowIsBuiltReadAndRewired() {
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        String empty = text(run("read_flow"));
        assertTrue(empty.startsWith("Flows: none yet.\nNo flow yet; the first change writes Flows.MAIN"), empty);
        assertTrue(empty.endsWith("The flow has no activities yet; edit_flow's add_activity adds one."), empty);

        // A project with no flow gets Flows.MAIN on its first change.
        assertFalse(run("edit_flow", "action", "add_activity", "name", "Collect", "body", "Collect::body").isRefused());
        assertTrue(host.calls.contains("add flows MAIN"), host.calls.toString());
        assertFalse(run("edit_flow", "action", "add_activity", "name", "Battle").isRefused());
        assertEquals(1, host.set("flows").size(), "the second change edits the flow the first one wrote");
        assertFalse(run("edit_flow", "action", "connect", "from", "Collect", "outcome", "bag full", "to", "Battle").isRefused());
        assertFalse(run("edit_flow", "action", "connect", "from", "Battle", "to", "Collect").isRefused());
        String read = text(run("read_flow"));
        assertTrue(read.startsWith("Flows: MAIN.\nFlows.MAIN.\n"), read);
        assertTrue(read.contains("\nStarts at Collect;"), read);
        assertTrue(read.contains("• Collect — Collect::body; outcomes [Next, Bag full]"), read);
        assertTrue(read.contains("    on Bag full → Battle"), read);
        assertTrue(read.contains("• Battle — no body yet"), read);

        assertEquals("Flows.MAIN: Collect on Bag full now goes nowhere. Activities: [Collect, Battle].",
                text(run("edit_flow", "action", "disconnect", "from", "Collect", "outcome", "bag full")));
        assertTrue(run("edit_flow", "action", "disconnect", "from", "Collect", "outcome", "bag full").isRefused());
        assertFalse(run("edit_flow", "action", "set_start", "name", "Battle").isRefused());
        assertEquals(FlowNames.activity("Battle"), flow().start());
        assertFalse(run("edit_flow", "action", "rename_activity", "from", "Battle", "to", "Fight").isRefused());
        assertFalse(run("edit_flow", "action", "remove_activity", "name", "Collect").isRefused());
        assertEquals(List.of("Fight"), flow().steps().stream().map(Flow.Step::label).toList());
        assertEquals(List.of("Fight.NEXT"), List.copyOf(host.set("activities").keySet()), host.calls.toString());
        assertTrue(host.calls.contains("add activities Collect.BAG_FULL"), host.calls.toString());
        assertTrue(host.calls.contains("renameGroup activities Battle Fight"), host.calls.toString());
    }

    /** A card drawn with no body gets one later, and its switches, as the card editor sets them. */
    @Test
    void aCardGetsItsBodyAndSwitchesLater() {
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        assertFalse(run("edit_flow", "action", "add_activity", "name", "Research").isRefused());
        host.set("flows").put("DAILY", TestContexts.row(Flow.class, "").withValue(Flow.NONE));
        assertFalse(run("edit_flow", "action", "add_subflow", "name", "DAILY").isRefused());

        AgentReply missing = run("edit_flow", "action", "set_activity", "name", "research", "body", "Research::body");
        assertTrue(missing.isRefused());
        assertTrue(text(missing).contains("It has: none; add_method writes one."), text(missing));
        host.bodies.put("Research", List.of("Research::body"));
        AgentReply other = run("edit_flow", "action", "set_activity", "name", "Research", "body", "Collect::body");
        assertTrue(other.isRefused());
        assertTrue(text(other).contains("It has: Research::body."), text(other));
        assertFalse(run("edit_flow", "action", "set_activity", "name", "research", "body", "Research::body", "goesHome", true,
                "description", "Start research.").isRefused());
        assertTrue(run("edit_flow", "action", "set_activity", "name", "daily", "body", "Research::body").isRefused(), "a sub-flow card");

        Flow.Step step = flow().steps().getFirst();
        assertEquals("Research::body", FlowTypes.sourceOf(step.body()));
        assertTrue(step.goHome());
        assertEquals("Start research.", step.description());

        // What is left out stays as it was.
        assertFalse(run("edit_flow", "action", "set_activity", "name", "Research", "switchable", true).isRefused());
        step = flow().steps().getFirst();
        assertEquals("Research::body", FlowTypes.sourceOf(step.body()));
        assertTrue(step.goHome() && step.runnerSwitch());
        assertEquals("Start research.", step.description());
        assertTrue(text(run("read_flow")).contains("• Research — Research::body, goes home first, the runner may"),
                text(run("read_flow")));
        assertFalse(run("edit_flow", "action", "set_activity", "name", "Research", "description", "", "goesHome", false).isRefused());
        step = flow().steps().getFirst();
        assertEquals("", step.description(), "an empty line is none");
        assertFalse(step.goHome());
    }

    @Test
    void theFlowsSetupNamesOnlyAMethodTheBotHas() {
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        assertFalse(run("edit_flow", "action", "add_activity", "name", "Collect", "body", "Collect::body").isRefused());
        String read = text(run("read_flow"));
        assertTrue(read.contains("Home: none.") && read.contains("Setup: none."), read);
        // The fake bot has no public static void method: a name it lacks would not compile.
        AgentReply missing = run("edit_flow", "action", "set_flow_method", "which", "setup", "method", "Gamebot::setup");
        assertTrue(missing.isRefused());
        assertTrue(text(missing).contains("It has: none."), text(missing));
        assertEquals("The bot has no setup now.", text(run("edit_flow", "action", "set_flow_method", "which", "setup")));
    }

    /** Of several flows, MAIN is edited when none is named, and the run is left as it was. */
    @Test
    void mainIsEditedWhenNoFlowIsNamedAndTheRunLeftAlone() {
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        host.set("flows").put("DAILY", TestContexts.row(Flow.class, "").withValue(Flow.NONE));
        assertTrue(text(run("read_flow")).startsWith("Flows: DAILY.\nFlows.DAILY."), "the first, with no MAIN");
        host.set("flows").remove("DAILY");
        assertFalse(run("edit_flow", "action", "add_activity", "name", "Collect", "body", "Collect::body").isRefused());
        host.set("flows").put("DAILY", TestContexts.row(Flow.class, "").withValue(Flow.NONE));
        assertTrue(text(run("read_flow")).startsWith("Flows: MAIN, DAILY.\nFlows.MAIN."));

        assertFalse(run("edit_flow", "action", "add_activity", "name", "Battle").isRefused());
        assertEquals(2, flow().steps().size());
        assertEquals(Flow.NONE, host.set("flows").get("DAILY").value());
        assertEquals(Run.NONE, host.values.get("run").value(), "the flow is no part of the run");
    }

    /** A flow is picked by name; an activity is the bot's, shared by every flow running it. */
    @Test
    void flowsArePickedByNameAndShareTheirActivities() {
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        assertFalse(run("edit_flow", "action", "add_activity", "name", "Collect", "body", "Collect::body").isRefused());
        assertFalse(run("edit_flow", "action", "connect", "from", "Collect", "outcome", "bag full", "to", "Collect").isRefused());

        assertFalse(run("edit_flow", "action", "add_flow", "name", "daily", "copyOf", "MAIN").isRefused());
        assertEquals(flow(), host.set("flows").get("DAILY").value(), "a copy holds the same flow");
        assertFalse(run("edit_flow", "action", "add_flow", "name", "empty").isRefused());
        assertEquals(Flow.NONE, host.set("flows").get("EMPTY").value());
        assertTrue(run("edit_flow", "action", "add_flow", "name", "Daily").isRefused(), "DAILY is taken");
        assertTrue(run("read_flow", "flow", "nightly").isRefused());

        // Added to another flow, Collect keeps its body and its enum's outcomes.
        assertFalse(run("edit_flow", "action", "add_activity", "name", "Collect", "flow", "EMPTY").isRefused());
        Flow empty = (Flow) host.set("flows").get("EMPTY").value();
        assertEquals("Collect::body", FlowValue.bodySource(empty.steps().getFirst()));
        assertTrue(text(run("read_flow", "flow", "empty")).contains("outcomes [Next, Bag full]"));

        // Dropped from one flow, it stays the others'.
        assertFalse(run("edit_flow", "action", "remove_activity", "name", "Collect", "flow", "DAILY").isRefused());
        assertFalse(host.calls.stream().anyMatch(call -> call.startsWith("removeGroup")), host.calls.toString());
        assertTrue(host.set("activities").containsKey("Collect.BAG_FULL"));

        // Another flow's activity has its enum: no activity here may take its name.
        assertFalse(run("edit_flow", "action", "add_activity", "name", "Rest", "flow", "EMPTY").isRefused());
        AgentReply taken = run("edit_flow", "action", "rename_activity", "from", "Collect", "to", "rest");
        assertTrue(taken.isRefused(), "Activities.Rest is EMPTY's");
        assertTrue(text(taken).contains("Activities.Rest is already another activity"), text(taken));

        assertFalse(run("edit_flow", "action", "rename_flow", "from", "empty", "to", "farm").isRefused());
        assertTrue(host.calls.contains("rename flows EMPTY FARM"), host.calls.toString());
        host.uses.put("FARM", List.of(new PluginValues.Use(Path.of("Sdk.java"), 12,
                "public static Flow flow = Flows.FARM;")));
        assertTrue(run("edit_flow", "action", "remove_flow", "name", "farm").isRefused(), "the bot's flow parameter names it");
        assertFalse(run("edit_flow", "action", "remove_flow", "name", "daily").isRefused());
        assertEquals(List.of("MAIN", "FARM"), List.copyOf(host.set("flows").keySet()));
    }

    /** A card running another flow: it moves that flow above, shows its exits, and is wired by them. */
    @Test
    void aSubflowCardIsAddedReadAndWiredByItsExits() {
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        assertFalse(run("edit_flow", "action", "add_activity", "name", "Collect", "body", "Collect::body").isRefused());
        assertFalse(run("edit_flow", "action", "add_flow", "name", "daily").isRefused());
        assertFalse(run("edit_flow", "action", "add_activity", "name", "Claim", "flow", "DAILY").isRefused());

        String added = text(run("edit_flow", "action", "add_subflow", "name", "daily"));
        assertTrue(added.contains("Added ▸ DAILY, which runs Flows.DAILY; its exits, each wired with edit_flow's connect and "
                + "fromFlow DAILY: [Claim · Next]."), added);
        assertTrue(host.calls.contains("moveBefore flows DAILY MAIN"), host.calls.toString());
        assertEquals(List.of("DAILY", "MAIN"), List.copyOf(host.set("flows").keySet()));
        assertTrue(run("edit_flow", "action", "add_subflow", "name", "daily").isRefused(), "MAIN already runs it");
        AgentReply cycle = run("edit_flow", "action", "add_subflow", "name", "main", "flow", "daily");
        assertTrue(cycle.isRefused());
        assertTrue(text(cycle).contains("Flows.MAIN runs Flows.DAILY"), text(cycle));
        assertTrue(run("edit_flow", "action", "add_subflow", "name", "nightly").isRefused());

        assertFalse(run("edit_flow", "action", "connect", "from", "Collect", "outcome", "bag full", "to", "daily").isRefused());
        assertFalse(run("edit_flow", "action", "connect", "fromFlow", "daily", "from", "claim", "to", "Collect").isRefused());
        AgentReply noExit = run("edit_flow", "action", "connect", "fromFlow", "daily", "from", "Claim", "outcome", "done", "to", "Collect");
        assertTrue(noExit.isRefused());
        assertTrue(text(noExit).contains("its exits are [Claim · Next]"), text(noExit));
        assertTrue(text(run("edit_flow", "action", "connect", "from", "daily", "to", "Collect")).contains("is a sub-flow card"));
        String read = text(run("read_flow"));
        assertTrue(read.startsWith("Flows: DAILY (a card in MAIN), MAIN.\nFlows.MAIN.\n"), read);
        assertTrue(read.contains("• ▸ DAILY — runs Flows.DAILY; exits [Claim · Next]"), read);
        assertTrue(read.contains("    on Bag full → ▸ DAILY"), read);
        assertTrue(read.contains("    on Claim · Next → Collect"), read);
        assertEquals(1, flow().edges().stream().filter(e -> e.via() != null).count());

        assertTrue(run("edit_flow", "action", "remove_flow", "name", "daily").isRefused(), "MAIN runs it");
        assertFalse(run("edit_flow", "action", "disconnect", "fromFlow", "daily", "from", "Claim").isRefused());
        assertTrue(flow().edges().stream().noneMatch(e -> e.via() != null));
        assertFalse(run("edit_flow", "action", "set_start", "name", "daily").isRefused());
        assertEquals("▸ DAILY", flow().start().label());
        assertFalse(run("edit_flow", "action", "remove_activity", "name", "▸ DAILY").isRefused());
        assertEquals(List.of("Collect"), flow().steps().stream().map(Flow.Step::label).toList());
    }

    /** A flow moved above its user takes the flows its own cards run with it, each above the card reading it. */
    @Test
    void aSubflowMovesAboveItsUserWithTheFlowsItRuns() {
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        assertFalse(run("edit_flow", "action", "add_activity", "name", "Collect").isRefused());
        assertFalse(run("edit_flow", "action", "add_flow", "name", "x").isRefused());
        assertFalse(run("edit_flow", "action", "add_activity", "name", "Rest", "flow", "X").isRefused());
        assertFalse(run("edit_flow", "action", "add_flow", "name", "daily").isRefused());
        assertFalse(run("edit_flow", "action", "add_subflow", "name", "x", "flow", "daily").isRefused());
        assertEquals(List.of("MAIN", "X", "DAILY"), List.copyOf(host.set("flows").keySet()), "X is above already");

        assertFalse(run("edit_flow", "action", "add_subflow", "name", "daily").isRefused());
        assertEquals(List.of("X", "DAILY", "MAIN"), List.copyOf(host.set("flows").keySet()));
        assertTrue(text(run("read_flow")).contains("X (a card in DAILY), DAILY (a card in MAIN), MAIN."));
    }

    @Test
    void aRunOrFlowWrittenByHandIsLeftAlone() {
        host.values.put("run", TestContexts.row(Run.class, "myRun()"));
        assertTrue(run("read_flow").isRefused());
        assertTrue(run("edit_flow", "action", "set_start", "name", "Collect").isRefused());

        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        host.set("flows").put("MAIN", TestContexts.row(Flow.class, "myFlow()"));
        assertTrue(run("read_flow").isRefused(), "Flows.MAIN written by hand");
        assertTrue(run("edit_flow", "action", "add_activity", "name", "Collect").isRefused());
    }

    // ── source, settings, emulator, launch ──────────────────────────────────────────────────────────────

    @Test
    void theCaptureSourceIsReadAndPointedAtAWindow() {
        host.values.put("capture", TestContexts.row(CaptureSource.class, "")
                .withValue(CaptureSource.window("game.exe", "")));
        assertTrue(text(run("setup", "action", "read")).startsWith("Looks at: Window: game.exe.\nLaunches: "));
        RecordingNativeController open = new RecordingNativeController();
        open.window = new GenericWindow("h", "Other", new java.awt.Rectangle(0, 0, 800, 600), GenericWindow.NO_PID,
                "steam_app_0");
        NativeControllerFactory.setForTesting(open);
        try {
            assertEquals("The bot now looks at Window: Other (steam_app_0).",
                    text(run("setup", "action", "set_capture_source", "window", "Other")));
            assertEquals("Window: Other (steam_app_0)", com.botmaker.sdk.plugin.screen.CaptureLabels.longLabel(
                    (CaptureSource) host.values.get("capture").value()));
            assertTrue(text(run("setup", "action", "set_capture_source", "window", "Missing")).contains("list_windows"));
        } finally {
            NativeControllerFactory.setForTesting(null);
        }
    }

    @Test
    void theOpenWindowsAreListedOncePerTitle() {
        NativeControllerFactory.setForTesting(new RecordingNativeController());
        assertEquals("\"Test Game Window\" class testgame.exe, 800×600 at 100,50", text(run("list_windows")));
    }

    @Test
    void aSettingIsReadAndChangedOneAtATime() {
        host.values.put("settings", TestContexts.row(BotSettings.class, "").withValue(BotSettings.DEFAULTS));
        String all = text(run("setup", "action", "read"));
        assertTrue(all.contains("confidence = 0.8 — "), all);
        assertTrue(all.contains("input_backend = auto"), all);

        assertEquals("confidence is now 0.9 in Sdk.settings().", text(run("setup", "action", "set_setting", "setting", "confidence",
                "value", "0.9")));
        assertEquals(0.9, ((BotSettings) host.values.get("settings").value()).confidence());
        assertFalse(run("setup", "action", "set_setting", "setting", "INPUT_BACKEND", "value", "xtest").isRefused());
        assertEquals(BotSettings.InputBackend.XTEST,
                ((BotSettings) host.values.get("settings").value()).runIn().inputBackend());
        assertEquals(0.9, ((BotSettings) host.values.get("settings").value()).confidence(), "the others stay");
        assertFalse(run("setup", "action", "set_setting", "setting", "run_the_game_in", "value", "my-desktop").isRefused());
        assertEquals(BotSettings.Where.MY_DESKTOP, ((BotSettings) host.values.get("settings").value()).where());
        assertTrue(run("setup", "action", "set_setting", "setting", "run_the_game_in", "value", "cloud").isRefused());

        assertTrue(run("setup", "action", "set_setting", "setting", "confidence", "value", "2").isRefused());
        assertTrue(run("setup", "action", "set_setting", "setting", "max_retry_attempts", "value", "0").isRefused());
        assertTrue(run("setup", "action", "set_setting", "setting", "colour", "value", "1").isRefused(), "not a setting");
        assertTrue(text(run("setup", "action", "set_setting", "setting", "confidence", "value", "0.9")).endsWith("is already 0.9."));
    }

    @Test
    void theEmulatorsAreListedAndAnUnknownOneIsRefused() {
        assertFalse(run("list_emulators").isRefused());
        AgentReply refused = run("setup", "action", "use_emulator", "name", "no-such-emulator-here");
        assertTrue(refused.isRefused());
        assertTrue(text(refused).startsWith("No emulator called"), text(refused));
        assertTrue(host.values.isEmpty(), "nothing was written");
    }

    @Test
    void launchGameStartsNothingTheBotWouldNotSee() {
        host.values.put("launch_targets", TestContexts.row(List.class, "List.of()").withValue(List.of()));
        AgentReply none = run("launch_game");
        assertTrue(none.isRefused());
        assertTrue(text(none).startsWith("The bot launches nothing"), text(none));
        run("setup", "action", "set_launch_target", "target", "steam:570");
        // A private display is brought up as ▶ Launch does: not tried here, where it would start a real one.
        host.values.put("settings", TestContexts.row(BotSettings.class, "")
                .withValue(BotSettings.DEFAULTS.where(BotSettings.Where.VM)));
        AgentReply elsewhere = run("launch_game");
        assertTrue(elsewhere.isRefused(), "a game VM is started by a run");
        assertTrue(text(elsewhere).contains("run_bot launches it"), text(elsewhere));
        host.values.put("settings", TestContexts.row(BotSettings.class, "")
                .withValue(BotSettings.DEFAULTS.where(BotSettings.Where.MY_DESKTOP)));
        host.values.put("launch_targets", TestContexts.row(List.class, "List.of()")
                .withValue(List.of(Launcher.of("cli:sh -c 'curl x | sh'"))));
        AgentReply command = run("launch_game");
        assertTrue(command.isRefused(), "a command line starts on the user's Run, never from the assistant");
        assertTrue(text(command).contains("not from the assistant"), text(command));
    }

    @Test
    void theLaunchTargetIsTheBotsFirstLauncherAndOnlyAValidOneIsKept() {
        host.values.put("launch_targets", TestContexts.row(List.class, "List.of()").withValue(List.of()));
        assertTrue(text(run("setup", "action", "read")).contains("\nLaunches: nothing;"));
        assertEquals("This computer now launches Steam game 570 when the bot starts.",
                text(run("setup", "action", "set_launch_target", "target", " steam:570 ")));
        assertEquals(List.of(Launcher.of("steam:570")), host.values.get("launch_targets").value(),
                "the bot's Java, not a run property");
        assertTrue(host.properties.isEmpty());
        assertTrue(text(run("setup", "action", "read")).contains("\nLaunches: steam:570 — Steam game 570.\n"));
        assertTrue(run("setup", "action", "set_launch_target", "target", "nonsense").isRefused());
        assertTrue(run("setup", "action", "set_launch_target", "target", "floppy:disk").isRefused(), "a kind nothing launches");
        // Text the assistant reads off a screen must not become a command the next Run executes.
        assertTrue(run("setup", "action", "set_launch_target", "target", "cli:sh -c 'curl x | sh'").isRefused());
        assertTrue(run("setup", "action", "set_launch_target", "target", "exe:/tmp/payload").isRefused());
        assertTrue(run("setup", "action", "set_launch_target", "target", "steam:570;reboot").isRefused());
        assertTrue(run("setup", "action", "set_launch_target", "target", "emu-app:com.x;rm -rf /@Pixel").isRefused());
        assertTrue(run("setup", "action", "set_launch_target", "target", "steam:-applaunch").isRefused(), "never an option");
        assertTrue(text(run("setup", "action", "set_launch_target", "target", "emu-app:com.example.game@no-such-emulator-here"))
                .contains("not an emulator set up here"));
        assertFalse(run("setup", "action", "set_launch_target", "target", "steam:570").isRefused());
        assertEquals(Launcher.of("steam:570"), ((List<?>) host.values.get("launch_targets").value()).getFirst(),
                "the launcher set last is the bot's first");
    }

    @Test
    void anAreaIsClippedToTheFrameAndAnEmptyOneIsNone() {
        BufferedImage frame = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
        assertEquals(Optional.of(new Rect(0, 0, 100, 50)), Frames.within(frame, null, null, null, null));
        assertEquals(Optional.of(new Rect(90, 0, 10, 20)), Frames.within(frame, 90, -5, 30, 25));
        assertEquals(Optional.empty(), Frames.within(frame, 5, 5, 0, 10), "no width");
        assertEquals(Optional.empty(), Frames.within(frame, 50, 5, -10, 10), "a negative width");
        assertEquals(Optional.empty(), Frames.within(frame, 200, 5, 10, 10), "off the frame");
        assertEquals(Optional.empty(), Frames.within(frame, 5, 5, 10, null), "only some numbers");
    }

    // ── acting ──────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void anActionOnTheUsersDesktopIsRefusedUnlessTheCallSaysSo() {
        RecordingNativeController controller = new RecordingNativeController();
        NativeControllerFactory.setForTesting(controller);
        agent.frame = withPatchAt(120, 80);
        AgentReply refused = run("click", "x", 10, "y", 20);
        assertTrue(refused.isRefused() && text(refused).contains("desktop: true"), text(refused));
        assertTrue(run("key", "key", "Escape").isRefused());
        assertTrue(controller.events.isEmpty(), "nothing was pressed: " + controller.events);

        assertEquals("Clicked 10,20; nothing changed.",
                text(run("click", "x", 10, "y", 20, "desktop", true, "settleMs", 0)));
        assertTrue(controller.events.contains("mouseMove(1010,520)"), controller.events.toString());
        assertEquals("Clicked ore at 144,104; nothing changed.",
                text(run("click", "picture", "ore", "desktop", true, "settleMs", 0)), "a picture is clicked at its centre");
        assertTrue(controller.events.contains("mouseMove(1144,604)"), controller.events.toString());

        controller.events.clear();
        run("key", "key", "esc", "desktop", true, "settleMs", 0);
        assertEquals(List.of("keyDown(65307)", "keyUp(65307)"), controller.events);
        assertTrue(run("key", "key", "Hyper", "desktop", true).isRefused());
        assertTrue(run("click", "x", 1, "y", 1, "desktop", true, "settleMs", 11000).isRefused());
        controller.events.clear();
        assertTrue(run("click", "x", 400, "y", 10, "desktop", true, "settleMs", 0).isRefused(), "off the frame");
        assertTrue(run("scroll", "x", -1, "y", 10, "amount", 1, "desktop", true, "settleMs", 0).isRefused());
        assertTrue(controller.events.isEmpty(), "nothing lands off the game: " + controller.events);
    }

    @Test
    void textThatAppearedOrWentIsCountedByItsWordsNotItsPlace() {
        Rect at = new Rect(0, 0, 10, 10);
        Rect moved = new Rect(50, 50, 10, 10);
        ActTools.TextDiff diff = ActTools.TextDiff.of(
                List.of(new ActTools.Line("Fight", at), new ActTools.Line("Claim  all", at)),
                List.of(new ActTools.Line("Claim", moved), new ActTools.Line("claim all", at),
                        new ActTools.Line("Quests", at)));
        assertEquals(List.of("Claim", "Quests"), diff.appeared().stream().map(ActTools.Line::text).toList());
        assertEquals(List.of("Fight"), diff.went().stream().map(ActTools.Line::text).toList());
    }

    @Test
    void everyToolHasADistinctNameAndNoneIsCalledWire() {
        List<String> names = SdkAssist.ALL.stream().map(AssistantTool::name).toList();
        assertEquals(names.size(), names.stream().distinct().count(), names.toString());
        assertEquals(36, names.size(), "crop_picture and try_inputs merged into crop_pictures and try_input; the flow, "
                + "picture, tag and setup edits into edit_flow, edit_pictures, edit_tags and setup, 2026-10-15");
        assertTrue(SdkAssist.ALL.stream().noneMatch(t -> t.paramsType().getSimpleName().contains("Wire")));
    }

    /** Studio's own tools the guide names beside the SDK's. */
    private static final java.util.Set<String> STUDIOS = java.util.Set.of("add_method", "run_activity", "edit_code",
            "read_trace");

    @Test
    void theGuideNamesOnlyToolsThereAreAndThePluginGivesIt() {
        for (String word : List.of("describe", "click", "key", "scroll", "setup")) {
            assertTrue(SdkAssist.ALL.stream().anyMatch(t -> t.name().equals(word)), word + " is no tool");
        }
        java.util.Set<String> names = SdkAssist.and(com.botmaker.sdk.plugin.map.MapReport.TOOLS).stream()
                .map(AssistantTool::name).collect(java.util.stream.Collectors.toSet());
        // An action tool's actions are named beside it.
        SdkAssist.ALL.forEach(t -> t.schema().actions().forEach(a -> names.add(a.id())));
        StringBuilder said = new StringBuilder(SdkAssist.GUIDE);
        for (Protocol.Stage stage : Protocol.Stage.values()) said.append('\n').append(stage.section());
        java.util.regex.Matcher named = java.util.regex.Pattern.compile("\\b[a-z]+_[a-z_]+\\b").matcher(said);
        while (named.find()) {
            assertTrue(names.contains(named.group()) || STUDIOS.contains(named.group()),
                    "the guide names " + named.group() + ", which is no tool");
        }
        assertEquals(SdkAssist.GUIDE.strip(), new com.botmaker.sdk.plugin.SdkPlugin().assistantGuide());
    }

    @Test
    void readGuideGivesTheBotsStageAndTheNotesKeepWhereEachScreenStands() {
        assertTrue(run("read_guide").isRefused(), "no run to read the home method from: said, not guessed");
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        String fresh = text(run("read_guide"));
        assertTrue(fresh.startsWith("The bot is at " + Protocol.Stage.SETUP.title()), fresh);
        assertFalse(fresh.contains(Protocol.Stage.BUILD.title()), "only the stage the bot is at: " + fresh);
        assertEquals(Protocol.Stage.BUILD.section(), text(run("read_guide", "stage", "2")));
        assertTrue(run("read_guide", "stage", "Polish").isRefused());

        assertEquals("Kept the game's notes, on the run in Sdk.", text(run("write_notes", "text", "Q opens Quests.")));
        assertTrue(text(run("read_notes")).contains("The game:\nQ opens Quests."), text(run("read_notes")));
        assertTrue(run("read_notes", "screen", "Boss").isRefused(), "no such screen");
        assertTrue(run("write_notes").isRefused(), "nothing given, nothing written");
        assertTrue(run("write_notes", "reached", "built").isRefused(), "reached is a screen's");
    }

    @Test
    void tryInputTakesAPictureOrAHotkeyAndEachScreensInputsBeforeTheScreensTheyOpen() throws IOException {
        Game game = new Game();
        NativeControllerFactory.setForTesting(game);
        agent.frame = withPatchAt(120, 80);
        assertTrue(text(tryOne("screen", "Home")).contains("a picture to click or a hotkey"));
        assertTrue(tryOne("screen", "Home", "picture", "ore", "key", "E").isRefused());
        assertTrue(text(tryOne("screen", "Home", "key", "M")).contains("crop_pictures the first screen's "
                + "marker"), "a hotkey needs no name until it opens a screen");
        assertTrue(text(tryOne("screen", "Home", "picture", "ore", "desktop", true, "settleMs", 0))
                .contains("crop_pictures the first screen's marker"), "the map is started first");

        run("screenshot");
        assertTrue(cropOne("name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE,
                "marker", "set").isRefused(), "a marker without its screen");
        assertTrue(cropOne("name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE,
                "screen", "Guild").isRefused(), "only a marker starts the map");
        assertFalse(TemplateLibrary.exists(resources, "gold"), "a refused note saves nothing");
        String started = text(cropOne("name", "home", "x", 120, "y", 80, "width", SIZE, "height", SIZE,
                "screen", "Home", "marker", "set"));
        assertTrue(started.contains("Added Home to the map as Screens.HOME"), started);
        assertTrue(text(cropOne("name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE,
                "screen", "home")).contains("Noted in Home as untried"));
        assertEquals("home", screen("HOME").firstMarker().id());
        assertEquals(List.of("click gold"), screen("HOME").inputs().stream().map(Input::label).toList());
        String twice = text(cropOne("name", "gold", "x", 10, "y", 10, "width", SIZE, "height", SIZE, "screen", "Home"));
        assertTrue(twice.contains("already exists: give its name with no box"), twice);
        cropOne("name", "bag", "x", 10, "y", 10, "width", SIZE, "height", SIZE);
        String kept = text(run("crop_pictures", "screen", "Home", "boxes", List.of(Map.of("name", "bag"))));
        assertTrue(kept.contains("Kept bag as it is") && kept.contains("Noted in Home as untried"), kept);
        assertEquals(List.of("click gold", "click bag"), screen("HOME").inputs().stream().map(Input::label).toList(),
                "a picture cut earlier, on no screen, is noted as it is");
        assertFalse(run("edit_map", "action", "remove_input", "screen", "Home", "picture", "bag").isRefused());
        assertTrue(cropOne("name", "none_yet", "screen", "Home").isRefused(), "no box names a picture the bot has");
        assertTrue(cropOne("name", "half", "x", 10, "screen", "Home").isRefused(), "a box is all four or none");
        assertEquals(Set.of(), TemplateLibrary.manifest(host).tagsOf("gold"),
                "a screen's pictures are grouped by the map, never tagged");

        // What try_input writes when an input opens a screen: a screen below, reached from Home, owing its marker.
        host.set("screen_map").put("ENGINEER", TestContexts.row(Object.class, "").withValue(GameScreen.named("Engineer")
                .from(Way.of(BotConstants.screen("HOME"), Input.click(screen("HOME").firstMarker())))));
        AgentReply gated = tryOne("screen", "Home", "picture", "gold", "desktop", true, "settleMs", 0);
        assertTrue(gated.isRefused() && text(gated).startsWith("Engineer still owes its marker"), text(gated));
        // Home's shot is this very frame, coin and all: Engineer's marker would show on it, and be put back.
        Files.delete(ScreenShots.file(resources, "HOME"));
        assertTrue(text(cropOne("name", "coin", "x", 300, "y", 200, "width", SIZE, "height", SIZE,
                "screen", "engineer", "marker", "set")).contains("Noted in Engineer as its marker."));
        BufferedImage over = agent.frame;
        // Engineer closed: Home's marker alone shows.
        BufferedImage homeAlone = covered(over, 300, 200, SIZE, SIZE, 5);
        cropOne("name", "gem", "x", 120, "y", 80, "width", SIZE, "height", SIZE, "screen", "Engineer");
        cropOne("name", "list", "x", 120, "y", 80, "width", SIZE, "height", SIZE, "screen", "Engineer");
        AgentReply next = tryOne("screen", "Home", "picture", "gold", "desktop", true, "settleMs", 0);
        assertTrue(next.isRefused() && text(next).startsWith("Finish Engineer first"), text(next));
        assertTrue(tryOne("screen", "Engineer", "picture", "gem", "never", " ").isRefused(),
                "a blank reason never clicks");
        assertTrue(tryOne("screen", "Engineer", "picture", "coin", "never", "spends gems").isRefused(),
                "only an untried input is never clicked");
        assertTrue(tryOne("screen", "Engineer", "picture", "list", "scroll", 0).isRefused());
        assertTrue(tryOne("screen", "Engineer", "key", "Q", "scroll", 3).isRefused(), "a scroll is over a "
                + "picture");
        assertTrue(tryOne("screen", "Engineer", "picture", "gem", "kind", "loot").isRefused(), "no such kind");
        // The list carried its own picture away: Engineer still shows, so the scroll is said, not a screen left.
        agent.frame = covered(over, 120, 80, SIZE, SIZE, 6);
        AgentReply carried = tryOne("screen", "Engineer", "picture", "list", "scroll", -3, "desktop", true,
                "settleMs", 0);
        assertTrue(carried.isRefused() && text(carried).contains("moved off its place"), text(carried));
        agent.frame = over;
        String scrolled = text(tryOne("screen", "Engineer", "picture", "list", "scroll", -3, "desktop",
                true, "settleMs", 0));
        assertTrue(scrolled.contains("tried scroll list -3, no visible effect.") && scrolled.contains("that end is "
                + "reached"), scrolled);
        String last = text(tryOne("screen", "Engineer", "picture", "gem", "never", "spends gems"));
        assertTrue(last.contains("tried click gem, never, spends gems.") && last.contains("scrolled to both ends"),
                last);
        assertEquals(List.of("click gem", "scroll list -3"), screen("ENGINEER").inputs().stream().map(Input::label)
                .toList(), "the list's cut became its scroll");

        // Engineer, opened from Home, covers it: closed first by its way back, Escape with no close button.
        game.then(homeAlone);
        AgentReply still = tryOne("screen", "Home", "picture", "gold", "kind", "chore", "desktop", true,
                "settleMs", 0);
        assertTrue(text(still).contains("Noted in Home: tried click gold, no visible effect. Every input cut on it is "
                + "tried: an activity."), text(still));
        assertEquals(Input.click(screen("HOME").inputs().getFirst().picture()).did(Did.NONE).kind(Kind.CHORE),
                screen("HOME").inputs().getFirst());
        assertTrue(tryOne("screen", "Engineer", "picture", "home").isRefused(),
                "a picture naming a screen the map has asks for opens");

        assertTrue(run("try_input", "screen", "Home", "inputs", List.of()).isRefused(), "no input given");
        AgentReply none = run("try_input", "screen", "Guild", "inputs", List.of(Map.of("picture", "gold"),
                Map.of("picture", "gem")));
        assertTrue(none.isRefused() && text(none).startsWith("No screen Guild"), "a first refusal is the answer");
        AgentReply stopped = run("try_input", "screen", "Home", "inputs", List.of(Map.of("picture", "gold"),
                Map.of("picture", "nosuch"), Map.of("key", "Q")), "desktop", true, "settleMs", 0);
        assertFalse(stopped.isRefused(), "gold was tried");
        assertTrue(text(stopped).contains("Noted in Home: tried click gold") && text(stopped).contains("Stopped "
                + "before nosuch: ") && text(stopped).contains("Not tried: nosuch, Q."), text(stopped));
        assertTrue(run("try_input", "screen", "Home", "inputs", List.of(Map.of("picture", "gold", "kind",
                "loot"))).isRefused(), "a kind is one of the choices");

        assertTrue(run("go_to_screen", "screen", "Guild").isRefused());
        agent.frame = over;
        String walked = text(run("go_to_screen", "screen", "Engineer", "desktop", true));
        assertEquals("On Engineer.", walked.lines().findFirst().orElse(""),
                "both markers show on the still frame: Engineer, opened from Home, is the one over it");
        assertTrue(text(run("which_screen")).startsWith("On Engineer (Screens.ENGINEER): its marker shows."),
                "the last screen seen is looked for first");

        assertTrue(run("place_pictures", "screen", "Engineer", "pictures", List.of("nosuch")).isRefused(),
                "only a picture the bot has");
        assertTrue(run("place_pictures", "screen", "Guild", "pictures", List.of("gold")).isRefused(),
                "an input goes on a screen the map has");
        String placed = text(run("place_pictures", "screen", "Engineer", "back", "gold"));
        assertTrue(placed.startsWith("Noted in Engineer: gold: its way back."), placed);
        assertEquals("gold", screen("ENGINEER").back().picture().id());

        AgentReply partial = run("crop_pictures", "boxes", List.of(box("pick", 120, 80, SIZE, SIZE),
                Map.of("name", "drop", "x", 1, "y", 2, "width", 3)));
        assertTrue(partial.isRefused() && text(partial).contains("drop: give x, y, width and height"), text(partial));
        assertFalse(TemplateLibrary.exists(resources, "pick"), "nothing saved when one box is short");
        assertTrue(run("crop_pictures", "screen", "Engineer", "boxes", List.of(box("pick", 120, 80, 4, 4,
                "marker", "set", "back", true))).isRefused(), "the marker is no way back");
        AgentReply both = run("crop_pictures", "screen", "Engineer", "boxes", List.of(
                box("pick", 120, 80, SIZE, SIZE, "marker", "set"),
                box("spade", 120, 80, SIZE, SIZE, "ignore", Map.of("x", 120, "y", 80, "width", 2, "height", 2)),
                box("hoe", 120, 80, SIZE, SIZE, "back", true), box("far", 5000, 5000, 4, 4),
                box("spade_lit", 120, 80, SIZE, SIZE, "variantOf", "spade", "state", "badge")));
        assertTrue(text(both).contains("Saved spade_lit") && !text(both).contains("Not saved, spade_lit"),
                "another look is cut after the input it is a look of: " + text(both));
        assertTrue(text(both).contains("Noted in Engineer as its marker, in place of coin (marker add adds to the set "
                + "instead).")
                && text(both).contains("Saved spade") && text(both).contains("Not saved, far: ")
                && text(both).contains("Noted in Engineer: hoe: its way back; spade: untried."), text(both));
        assertEquals("pick", screen("ENGINEER").firstMarker().id());
        assertEquals("hoe", screen("ENGINEER").back().picture().id(), "the way back noted with the inputs");
        assertEquals(Set.of(), TemplateLibrary.manifest(host).tagsOf("spade"), "grouped by the map, not tagged");
        assertTrue(screen("ENGINEER").inputs().stream().anyMatch(i -> i.label().equals("click spade")));

        assertTrue(run("not_a_screen", "screen", "Home").isRefused(), "the first screen was opened by nothing");
        String taken = text(run("not_a_screen", "screen", "Engineer"));
        assertTrue(taken.startsWith("Engineer is off the map; ") && taken.endsWith(" is noted in Home as changing it "
                + "in place."), taken);
        assertFalse(host.set("screen_map").containsKey("ENGINEER"));
        assertTrue(screen("HOME").inputs().stream().anyMatch(i -> i.did() == Did.IN_PLACE));
    }

    /**
     * try_input reads where each input left the game on the frame the game shows after it (2026-10-15): a panel
     * closed to the screen under it, whose marker showed under the panel too, is that screen's way back and no new
     * screen; a popup stops try_input's list; a scroll keeps what was said of its input; a hotkey needs a name only once it
     * opened a screen; a button's usual look names no look on the way it opens.
     */
    @Test
    void eachInputIsReadOnTheFrameTheGameShowsAfterIt() {
        Game game = new Game();
        NativeControllerFactory.setForTesting(game);
        BufferedImage home = noise(WATCHED.width(), WATCHED.height(), 21);
        BufferedImage shop = covered(home, 150, 0, 250, 300, 22);
        BufferedImage gift = covered(home, 0, 120, 140, 180, 23);
        agent.frame = home;
        run("screenshot");
        cropOne("name", "home", "x", 10, "y", 10, "width", SIZE, "height", SIZE, "screen", "Home",
                "marker", "set");
        String cut = text(run("crop_pictures", "screen", "Home", "boxes", List.of(box("shop_home", 200, 120, 48, 48),
                box("bag", 80, 20, 48, 48), box("feed", 90, 75, 40, 40, "sometimes", true))));
        assertTrue(cut.contains("feed: comes and goes"), cut);
        cropOne("name", "shop_badge", "x", 250, "y", 120, "width", SIZE, "height", SIZE, "screen",
                "Home", "variantOf", "shop_home", "state", "badge");

        game.then(shop);
        String opened = text(tryOne("screen", "Home", "picture", "shop_home", "desktop", true, "settleMs",
                0));
        assertTrue(opened.contains("Opened Shop, added to the map as Screens.SHOP"), opened);
        assertNull(screen("SHOP").from().state(), "the button's usual look is no look the way needs");
        run("crop_pictures", "screen", "Shop", "boxes", List.of(box("shop", 300, 40, 48, 48, "marker", "set"),
                box("close_shop", 200, 200, 48, 48)));

        game.then(home);
        String closed = text(tryOne("screen", "Shop", "picture", "close_shop", "desktop", true,
                "settleMs", 0));
        assertTrue(closed.contains("tried click close_shop, its way back to Home") && closed.contains("went back up "
                + "to Home"), closed);
        assertEquals(List.of("HOME", "SHOP"), List.copyOf(host.set("screen_map").keySet()), "no screen Close shop");
        assertEquals("close_shop", screen("SHOP").back().picture().id());

        agent.frame = gift;
        run("screenshot");
        cropOne("name", "gift", "x", 30, "y", 200, "width", SIZE, "height", SIZE, "screen", "Gift",
                "marker", "set", "popup", true);
        agent.frame = home;
        game.then(gift);
        String covered = text(run("try_input", "screen", "Home", "inputs", List.of(Map.of("picture", "bag"),
                Map.of("picture", "feed")), "desktop", true, "settleMs", 0));
        assertTrue(covered.contains("tried click bag, shows the popup Gift") && covered.contains("Not tried: feed."),
                covered);
        run("screenshot");
        cropOne("name", "gift_ok", "x", 60, "y", 240, "width", SIZE, "height", SIZE, "screen", "Gift");
        game.then(home);
        String shut = text(tryOne("screen", "Gift", "picture", "gift_ok", "desktop", true, "settleMs", 0));
        assertTrue(shut.contains("tried click gift_ok, its way back to Home"), "a popup's input is tried over the "
                + "screen it covers: " + shut);

        agent.frame = home;
        tryOne("screen", "Home", "picture", "feed", "scroll", -2, "desktop", true, "settleMs", 0);
        Input scrolled = screen("HOME").inputs().stream().filter(i -> i.label().equals("scroll feed -2")).findFirst()
                .orElseThrow();
        assertTrue(scrolled.comesAndGoes(), "the scroll still comes and goes");

        String still = text(tryOne("screen", "Home", "key", "Q", "desktop", true, "settleMs", 0));
        assertTrue(still.contains("tried key Q, no visible effect"), still);
        game.then(noise(WATCHED.width(), WATCHED.height(), 24));
        String away = text(tryOne("screen", "Home", "key", "W", "desktop", true, "settleMs", 0));
        assertTrue(away.contains("Nothing was written: key W opened a screen the map does not have"), away);
    }

    /**
     * An input deep in one branch that opens a screen mapped earlier in another is a way into it: the screens it is
     * reached through move above that screen, so its constant may name them (Squads under Engineer, 2026-10-16).
     */
    @Test
    void aWayIntoAScreenMappedEarlierMovesItsBranchAboveIt() {
        Game game = new Game();
        NativeControllerFactory.setForTesting(game);
        BufferedImage home = noise(WATCHED.width(), WATCHED.height(), 31);
        BufferedImage shop = covered(home, 150, 0, 250, 300, 32);
        BufferedImage bag = covered(home, 0, 120, 140, 180, 33);
        BufferedImage deep = covered(bag, 150, 0, 250, 300, 34);
        agent.frame = home;
        run("screenshot");
        cropOne("name", "home", "x", 10, "y", 10, "width", SIZE, "height", SIZE, "screen", "Home", "marker", "set");
        run("crop_pictures", "screen", "Home", "boxes", List.of(box("shop_home", 200, 120, 48, 48),
                box("bag_home", 80, 20, 48, 48)));
        game.then(shop);
        tryOne("screen", "Home", "picture", "shop_home", "desktop", true, "settleMs", 0);
        run("crop_pictures", "screen", "Shop", "boxes", List.of(box("shop", 300, 40, 48, 48, "marker", "set"),
                box("close_shop", 200, 200, 48, 48, "back", true)));
        game.then(home);
        tryOne("screen", "Shop", "picture", "close_shop", "desktop", true, "settleMs", 0);
        game.then(bag);
        tryOne("screen", "Home", "picture", "bag_home", "desktop", true, "settleMs", 0);
        run("crop_pictures", "screen", "Bag", "boxes", List.of(box("bag_title", 30, 130, 48, 48, "marker", "set"),
                box("deep_in", 60, 240, 48, 48)));
        game.then(deep);
        tryOne("screen", "Bag", "picture", "deep_in", "opens", "Deep", "desktop", true, "settleMs", 0);
        run("crop_pictures", "screen", "Deep", "boxes", List.of(box("deep", 300, 40, 48, 48, "marker", "set"),
                box("go", 200, 200, 48, 48), box("later", 320, 200, 48, 48)));
        assertEquals(List.of("HOME", "SHOP", "BAG", "DEEP"), List.copyOf(host.set("screen_map").keySet()));

        game.then(shop);
        String way = text(tryOne("screen", "Deep", "picture", "go", "desktop", true, "settleMs", 0));
        assertTrue(way.contains("opens Shop, mapped already: a way into it"), way);
        assertEquals(List.of("HOME", "BAG", "DEEP", "SHOP"), List.copyOf(host.set("screen_map").keySet()),
                "Deep and Bag, which it is reached through, are declared above Shop");
        assertEquals(List.of("DEEP"), screen("SHOP").alsoFrom().stream()
                .map(w -> BotConstants.screenName(w.from())).toList());

        // Reached from Deep, whose branch is begun and not finished, Shop is part of that branch.
        agent.frame = shop;
        run("screenshot");
        run("crop_pictures", "screen", "Shop", "boxes", List.of(box("shelf", 160, 250, 48, 48)));
        String shelf = text(tryOne("screen", "Shop", "picture", "shelf", "desktop", true, "settleMs", 0));
        assertTrue(shelf.contains("tried click shelf"), shelf);
    }

    /**
     * A screen opened by an input that comes and goes holds back the next input of the screen it was opened from
     * only while that input shows: gone, the screen cannot be finished now (Engineer, 2026-10-16).
     */
    @Test
    void aScreenWhoseWayInIsGoneHoldsNothingBack() {
        Game game = new Game();
        NativeControllerFactory.setForTesting(game);
        BufferedImage home = noise(WATCHED.width(), WATCHED.height(), 41);
        BufferedImage gift = covered(home, 150, 0, 250, 300, 42);
        BufferedImage giftless = covered(home, 200, 120, 48, 48, 43);
        agent.frame = home;
        run("screenshot");
        cropOne("name", "home", "x", 10, "y", 10, "width", SIZE, "height", SIZE, "screen", "Home", "marker", "set");
        run("crop_pictures", "screen", "Home", "boxes", List.of(box("gift_home", 200, 120, 48, 48, "sometimes", true),
                box("bag", 80, 20, 48, 48)));
        game.then(gift);
        tryOne("screen", "Home", "picture", "gift_home", "opens", "Gift", "desktop", true, "settleMs", 0);
        run("crop_pictures", "screen", "Gift", "boxes", List.of(box("gift", 300, 40, 48, 48, "marker", "set"),
                box("open_box", 200, 200, 48, 48)));

        agent.frame = home;
        String held = text(tryOne("screen", "Home", "picture", "bag", "desktop", true, "settleMs", 0));
        assertTrue(held.contains("Finish Gift first"), "its way in shows, so Gift comes first: " + held);
        agent.frame = giftless;
        BufferedImage bag = covered(giftless, 0, 120, 140, 180, 44);
        game.then(bag);
        String tried = text(tryOne("screen", "Home", "picture", "bag", "opens", "Bag", "desktop", true, "settleMs", 0));
        assertTrue(tried.contains("Opened Bag"), "Gift's way in is gone, so it waits: " + tried);
        run("crop_pictures", "screen", "Bag", "boxes", List.of(box("bag_title", 30, 130, 48, 48, "marker", "set"),
                box("sort", 60, 240, 48, 48)));
        String branch = text(tryOne("screen", "Bag", "picture", "sort", "desktop", true, "settleMs", 0));
        assertTrue(branch.contains("tried click sort"), "Gift's branch, begun, holds no other back: " + branch);
    }

    @Test
    void aPictureGoesOnAScreenOnlyWhereTheGameShowsIt() throws IOException {
        BufferedImage first = withPatchAt(120, 80);
        agent.frame = first;
        run("screenshot");
        cropOne("name", "home", "x", 120, "y", 80, "width", SIZE, "height", SIZE, "screen", "Home", "marker", "set");
        cropOne("name", "bell", "x", 300, "y", 200, "width", SIZE, "height", SIZE);
        cropOne("name", "lamp", "x", 200, "y", 230, "width", SIZE, "height", SIZE);

        // The bell moved on this screen, the lamp is gone: Home's marker still shows.
        BufferedImage moved = covered(covered(first, 300, 200, SIZE, SIZE, 5), 200, 230, SIZE, SIZE, 6);
        moved.getGraphics().drawImage(first.getSubimage(300, 200, SIZE, SIZE), 40, 220, null);
        agent.frame = moved;
        String placed = text(run("place_pictures", "screen", "Home", "pictures", List.of("bell")));
        assertTrue(placed.contains("Used a copy of its own on Home: bell_home (bell sits at 40,220"), placed);
        assertTrue(TemplateLibrary.exists(resources, "bell_home"));
        assertEquals(List.of("click bell_home"), screen("HOME").inputs().stream().map(Input::label).toList(),
                "the copy goes on, the picture keeps its place");
        AgentReply gone = run("place_pictures", "screen", "Home", "pictures", List.of("lamp"));
        assertTrue(gone.isRefused() && text(gone).startsWith("lamp is not on Home now"), text(gone));
        AgentReply kept = run("crop_pictures", "screen", "Home", "boxes", List.of(Map.of("name", "lamp")));
        assertTrue(kept.isRefused() && text(kept).startsWith("lamp is not on Home now"), text(kept));
        String hidden = text(run("place_pictures", "screen", "Home", "sometimes", List.of("lamp")));
        assertTrue(hidden.contains("lamp: comes and goes"), "one that comes and goes may be hidden now: " + hidden);
        assertTrue(text(run("place_pictures", "screen", "Home", "pictures", List.of("bell"))).contains("an input "
                + "already"), "the copy cut before is used again while it sits where the game shows it");

        // Another screen of the map shows: a new box is not noted on Home.
        BufferedImage sign = noise(SIZE, SIZE, 99);
        TemplateLibrary.saveTemplate(resources, sign, "guild");
        host.set("screen_map").put("GUILD", TestContexts.row(Object.class, "").withValue(GameScreen.named("Guild")
                .markers(List.of(new ImageTemplate(TemplateLibrary.pathForName("guild"))))
                .from(Way.of(BotConstants.screen("HOME"), Input.click(screen("HOME").firstMarker())))));
        BufferedImage guild = noise(WATCHED.width(), WATCHED.height(), 23);
        guild.getGraphics().drawImage(sign, 10, 10, null);
        agent.frame = guild;
        run("screenshot");
        AgentReply wrong = cropOne("name", "flag", "x", 200, "y", 100, "width", SIZE, "height", SIZE, "screen", "Home");
        assertTrue(wrong.isRefused() && text(wrong).startsWith("The screenshot shows Guild, not Home"), text(wrong));
        assertFalse(TemplateLibrary.exists(resources, "flag"));
        assertFalse(cropOne("name", "flag", "x", 200, "y", 100, "width", SIZE, "height", SIZE, "screen", "Guild")
                .isRefused(), "noted on the screen it shows");
        String unchecked = text(run("place_pictures", "screen", "Home", "pictures", List.of("lamp")));
        assertTrue(unchecked.contains("Not checked on the game: it does not show Home now."), unchecked);
    }

    /**
     * A screen's shot shows its marker: the frame right after the click that opened it is a draft, replaced by the
     * frame its marker is cut from, and a shot is taken again only while the game shows the screen (Firestone's Arena
     * replay kept its VS splash, 2026-10-16).
     */
    @Test
    void aScreensShotShowsItsMarker() throws IOException {
        Game game = new Game();
        NativeControllerFactory.setForTesting(game);
        BufferedImage home = noise(WATCHED.width(), WATCHED.height(), 51);
        BufferedImage splash = noise(WATCHED.width(), WATCHED.height(), 52);
        BufferedImage bag = covered(home, 0, 120, 400, 180, 53);
        agent.frame = home;
        run("screenshot");
        String started = text(cropOne("name", "home", "x", 10, "y", 10, "width", SIZE, "height", SIZE, "screen",
                "Home", "marker", "set"));
        assertTrue(started.contains("Home's shot is this frame now."), started);
        assertEquals(home.getRGB(5, 5), shot("HOME").getRGB(5, 5));
        run("crop_pictures", "screen", "Home", "boxes", List.of(box("bag", 200, 20, 48, 48)));

        game.then(splash);
        tryOne("screen", "Home", "picture", "bag", "opens", "Bag", "desktop", true, "settleMs", 0);
        assertEquals(splash.getRGB(5, 5), shot("BAG").getRGB(5, 5), "the frame after the click, a draft");
        assertTrue(ScreenEdits.retakeShot(agent, "BAG").startsWith("Bag has no marker yet"),
                "nothing tells the splash from Bag yet: the draft is kept");

        agent.frame = bag;
        run("screenshot");
        String marked = text(cropOne("name", "bag_title", "x", 30, "y", 130, "width", SIZE, "height", SIZE,
                "screen", "Bag", "marker", "set"));
        assertTrue(marked.contains("Bag's shot is this frame now."), marked);
        assertEquals(bag.getRGB(5, 150), shot("BAG").getRGB(5, 150), "the frame its marker is cut from");

        agent.frame = splash;
        String refused = ScreenEdits.retakeShot(agent, "BAG");
        assertTrue(refused.startsWith("The game does not show Bag now"), refused);
        assertEquals(bag.getRGB(5, 150), shot("BAG").getRGB(5, 150), "the shot is kept");
        agent.frame = bag;
        assertEquals("Shot taken.", ScreenEdits.retakeShot(agent, "BAG"));
    }

    /**
     * A screen is told by a set of markers, all shown, and pictures it never shows: a set another screen's shot shows
     * too is put back, naming that screen (Rarity's title scored 0.80 on Upgrade, 2026-10-16).
     */
    @Test
    void aSetAnotherScreensShotShowsIsPutBack() {
        Game game = new Game();
        NativeControllerFactory.setForTesting(game);
        BufferedImage home = noise(WATCHED.width(), WATCHED.height(), 61);
        BufferedImage tab = covered(home, 0, 120, 400, 180, 62);
        agent.frame = home;
        run("screenshot");
        cropOne("name", "home", "x", 10, "y", 10, "width", SIZE, "height", SIZE, "screen", "Home", "marker", "set");
        run("crop_pictures", "screen", "Home", "boxes", List.of(box("tab", 200, 20, 48, 48)));
        game.then(tab);
        tryOne("screen", "Home", "picture", "tab", "opens", "Tab", "desktop", true, "settleMs", 0);

        agent.frame = tab;
        run("screenshot");
        // Home's marker shows on Tab too: by itself it would take Home for Tab.
        String alone = text(run("crop_pictures", "screen", "Tab", "boxes", List.of(Map.of("name", "home",
                "marker", "set"))));
        assertTrue(alone.contains("But home shows on Home's shot too, so a run would take Home for Tab.")
                && alone.contains("Tab keeps no marker; the pictures stay saved."), alone);
        assertFalse(screen("TAB").hasMarker(), "put back");

        String set = text(run("crop_pictures", "screen", "Tab", "boxes", List.of(Map.of("name", "home",
                "marker", "set"), box("tab_title", 30, 130, SIZE, SIZE, "marker", "set"))));
        assertTrue(set.contains("Noted in Tab as one of its markers, told by home + tab_title.")
                && set.contains("Tab's shot is this frame now."), set);
        assertEquals(List.of("home", "tab_title"), screen("TAB").markers().stream().map(ImageTemplate::id).toList());

        agent.frame = home;
        String absent = text(run("crop_pictures", "screen", "Home", "boxes", List.of(Map.of("name", "tab_title",
                "marker", "absent"))));
        assertTrue(absent.contains("Noted in Home as a picture it never shows, told by home, not tab_title."), absent);
        agent.frame = tab;
        AgentReply shown = run("crop_pictures", "screen", "Tab", "boxes", List.of(Map.of("name", "tab",
                "marker", "absent")));
        assertTrue(shown.isRefused() && text(shown).contains("tab shows on Tab now"), text(shown));

        assertEquals("Told by home.", text(run("edit_map", "action", "remove_marker", "screen", "Home",
                "picture", "tab_title")));
        assertTrue(run("edit_map", "action", "remove_marker", "screen", "Home", "picture", "home").isRefused(),
                "its last marker");
        AgentReply lone = run("edit_map", "action", "remove_marker", "screen", "Tab", "picture", "tab_title");
        assertTrue(lone.isRefused() && text(lone).contains("home shows on Home's shot too"), text(lone));
        assertTrue(run("edit_map", "action", "add_marker", "screen", "Home", "picture", "tab_title", "absent", true)
                .isRefused(), "the game shows Tab, which Home's lone marker matches: tab_title shows there");
        agent.frame = home;
        assertEquals("Told by home, not tab_title.", text(run("edit_map", "action", "add_marker", "screen", "Home",
                "picture", "tab_title", "absent", true)));
    }

    /** {@code constant}'s saved shot. */
    private BufferedImage shot(String constant) throws IOException {
        return ImageIO.read(ScreenShots.file(resources, constant).toFile());
    }

    /** {@code frame} with the box at {@code x, y} drawn over by other pixels: a panel opened over it. */
    private static BufferedImage covered(BufferedImage frame, int x, int y, int width, int height, long seed) {
        BufferedImage now = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = now.createGraphics();
        g.drawImage(frame, 0, 0, null);
        g.drawImage(noise(width, height, seed), x, y, null);
        g.dispose();
        return now;
    }

    /** The game: each click, key or scroll shows the next frame it was given, if any. */
    private final class Game extends RecordingNativeController {
        private final Deque<BufferedImage> next = new ArrayDeque<>();

        void then(BufferedImage frame) {
            next.add(frame);
        }

        @Override
        public void mouseButton(int button, boolean press) {
            super.mouseButton(button, press);
            if (!press) answer();
        }

        @Override
        public void keyUp(int nativeKeyCode) {
            super.keyUp(nativeKeyCode);
            answer();
        }

        @Override
        public void scroll(int amount) {
            super.scroll(amount);
            answer();
        }

        private void answer() {
            if (!next.isEmpty()) agent.frame = next.poll();
        }
    }

    /** 🗺 Screens' hand tools set what try_input records, and add and remove screens (2026-10-15). */
    @Test
    void theMapIsEditedByHand() {
        ImageTemplate ore = new ImageTemplate(TemplateLibrary.pathForName("ore"));
        Input click = Input.click(ore);
        host.set("screen_map").put("HOME", TestContexts.row(Object.class, "").withValue(GameScreen.named("Home")
                .markers(List.of(ore)).inputs(List.of(click))));

        assertEquals("Noted: click ore.", ScreenEdits.setInput(host, "HOME", click, click.did(Did.IN_PLACE)));
        assertEquals(Did.IN_PLACE, screen("HOME").inputs().getFirst().did());
        Input inPlace = screen("HOME").inputs().getFirst();
        ScreenEdits.setInput(host, "HOME", inPlace, ScreenEdits.untried(inPlace).never("spends gems"));
        assertEquals("spends gems", screen("HOME").inputs().getFirst().never());
        assertNull(screen("HOME").inputs().getFirst().did(), "never replaces the result");

        assertTrue(ScreenEdits.newScreen(host, "HOME", "Mine", screen("HOME").inputs().getFirst())
                .startsWith("Added Mine as Screens.MINE"));
        assertEquals("Mine", screen("MINE").name());
        assertNull(screen("HOME").inputs().getFirst().never(), "the input that opens it is untried again");
        assertTrue(ScreenEdits.delete(host, "HOME").contains("Mine is reached from it"), "a parent stays");
        assertEquals("Renamed Deep mine.", ScreenEdits.rename(host, "MINE", "Deep mine"));
        assertEquals("Deep mine", screen("MINE").name());
        ScreenEdits.setPopup(host, "MINE", true);
        assertTrue(screen("MINE").isPopup());
        assertEquals("Deleted Deep mine.", ScreenEdits.delete(host, "MINE"));
        assertFalse(host.set("screen_map").containsKey("MINE"));

        ScreenEdits.useAsBack(host, "HOME", screen("HOME").inputs().getFirst());
        assertEquals("ore", screen("HOME").back().picture().id());
        assertTrue(screen("HOME").inputs().isEmpty(), "the way back is no input to try");
    }

    /** A card moved on 🗺 Screens keeps its corner in Screens.java; Tidy takes every corner out (2026-10-15). */
    @Test
    void cardsMovedByHandKeepTheirPlace() {
        host.set("screen_map").put("HOME", TestContexts.row(Object.class, "").withValue(GameScreen.named("Home")));
        host.set("screen_map").put("SHOP", TestContexts.row(Object.class, "").withValue(GameScreen.named("Shop")));

        assertEquals("Moved 2 screens.", ScreenEdits.move(host, Map.of("HOME", new Point(0, 0),
                "SHOP", new Point(320, 40))));
        assertEquals(new Point(320, 40), screen("SHOP").at());
        assertEquals("Moved 1 screen.", ScreenEdits.move(host, Map.of("HOME", new Point(0, 0),
                "SHOP", new Point(300, 60))), "a screen already there is not written again");
        assertEquals("Nothing moved.", ScreenEdits.move(host, Map.of("GONE", new Point(1, 1))));

        assertEquals("Every card is where the layout puts it.", ScreenEdits.tidy(host));
        assertNull(screen("HOME").at());
        assertNull(screen("SHOP").at());
        assertEquals(List.of("Move 2 cards", "Move Shop", "Tidy the map"), host.steps,
                "each drag and Tidy is one step of Studio's undo, however many screens it wrote");
        ScreenEdits.rename(host, "SHOP", "Store");
        assertEquals("Edit Shop", host.steps.getLast(), "a lone edit is named for its screen");
    }

    /** edit_map does what 🗺 Screens' hand tools do, each input named as try_input names it (2026-10-15). */
    @Test
    void editMapFixesTheMapByHand() throws IOException {
        for (String name : List.of("gem", "tab", "coin", "close", "lock")) {
            TemplateLibrary.saveTemplate(resources, patch(), name);
        }
        Input gem = Input.click(picture("gem"));
        Input q = Input.key(Key.named("Q").orElseThrow());
        Input tab = Input.click(picture("tab"));
        host.set("screen_map").put("HOME", TestContexts.row(Object.class, "").withValue(GameScreen.named("Home")
                .markers(marks("ore")).inputs(List.of(gem, q, tab))
                .variants(List.of(ScreenVariant.named("Event").markers(marks("coin"))))));
        host.set("screen_map").put("SHOP", TestContexts.row(Object.class, "").withValue(GameScreen.named("Shop")
                .markers(marks("coin")).from(Way.of(BotConstants.screen("HOME"), q))
                .inputs(List.of(Input.click(picture("close")), Input.click(picture("coin"))))));
        host.set("screen_map").put("MINE", TestContexts.row(Object.class, "").withValue(GameScreen.named("Mine")
                .from(Way.of(BotConstants.screen("HOME"), gem))
                .alsoFrom(List.of(Way.of(BotConstants.screen("SHOP"), Input.click(picture("coin")))))));

        assertTrue(text(run("edit_map", "screen", "Home", "action", "fly")).contains("rename, delete"));
        assertTrue(run("edit_map", "screen", "Nowhere", "action", "delete").isRefused());

        // An input: its result, kind, never, lock, coming and going, and when it is there.
        run("edit_map", "screen", "Home", "action", "set_input", "picture", "tab", "did", "in_place", "kind", "info");
        assertEquals(Did.IN_PLACE, screen("HOME").inputs().get(2).did());
        assertEquals(Kind.INFO, screen("HOME").inputs().get(2).kind());
        AgentReply again = run("edit_map", "screen", "Home", "action", "set_input", "picture", "tab", "did",
                "in_place");
        assertFalse(again.isRefused(), "what the map already says is no error: " + text(again));
        assertTrue(text(again).startsWith("Nothing to change"), text(again));
        run("edit_map", "screen", "Home", "action", "set_input", "picture", "tab", "never", "spends gems");
        assertEquals("spends gems", screen("HOME").inputs().get(2).never());
        assertNull(screen("HOME").inputs().get(2).did(), "never replaces the result");
        assertEquals(Kind.INFO, screen("HOME").inputs().get(2).kind(), "the kind stays");
        assertTrue(run("edit_map", "screen", "Home", "action", "set_input", "picture", "tab", "did", "none",
                "never", "x").isRefused(), "one of did, never and locked");
        assertTrue(run("edit_map", "screen", "Home", "action", "set_input", "picture", "tab").isRefused());
        assertTrue(text(run("edit_map", "screen", "Home", "action", "set_input", "picture", "nope", "did", "none"))
                .contains("its inputs: click gem, key Q, click tab"));
        run("edit_map", "screen", "Home", "action", "set_input", "picture", "tab", "locked", "lock");
        assertEquals("lock", screen("HOME").inputs().get(2).lock().id());
        assertNull(screen("HOME").inputs().get(2).never());
        run("edit_map", "screen", "Home", "action", "reset_input", "picture", "tab");
        assertFalse(screen("HOME").inputs().get(2).settled(), "untried again");
        run("edit_map", "screen", "Home", "action", "set_input", "picture", "tab", "sometimes", true);
        assertTrue(screen("HOME").inputs().get(2).comesAndGoes());
        run("edit_map", "screen", "Home", "action", "input_when", "picture", "tab", "method", "Bot::eventOn");
        assertEquals("Bot::eventOn", ScreenTypes.sourceOf(screen("HOME").inputs().get(2).when()));
        // The method goes into Screens.java as written: anything but a method reference is refused.
        assertTrue(run("edit_map", "screen", "Home", "action", "input_when", "picture", "tab", "method",
                "Bot::eventOn) ; System.exit(0); (").isRefused());
        assertEquals("Bot::eventOn", ScreenTypes.sourceOf(screen("HOME").inputs().get(2).when()));
        run("edit_map", "screen", "Home", "action", "look_when", "look", "event", "method", "Bot::eventOn");
        assertEquals("Bot::eventOn", ScreenTypes.sourceOf(screen("HOME").variants().getFirst().when()));

        // Ways in: a condition, the tree's way taken off (the next one is the tree's), the only one kept.
        AgentReply noWay = run("edit_map", "screen", "Mine", "action", "way_when", "from", "Home", "key", "Q");
        assertTrue(noWay.isRefused() && text(noWay).contains("its ways in: from Home by click gem, from Shop by "
                + "click coin"), text(noWay));
        assertTrue(run("edit_map", "screen", "Home", "action", "look_when", "look", "night").isRefused());
        run("edit_map", "screen", "Mine", "action", "way_when", "from", "Shop", "picture", "coin", "method",
                "Bot::eventOn");
        assertEquals("Bot::eventOn", ScreenTypes.sourceOf(screen("MINE").alsoFrom().getFirst().when()));
        assertTrue(text(run("edit_map", "screen", "Mine", "action", "remove_way", "from", "Home", "picture", "gem"))
                .startsWith("Took Mine's way in from Home by click gem off the map."));
        assertEquals("coin", screen("MINE").from().via().picture().id());
        assertTrue(screen("MINE").alsoFrom().isEmpty());
        AgentReply only = run("edit_map", "screen", "Mine", "action", "remove_way", "from", "Shop", "picture", "coin");
        assertTrue(only.isRefused() && text(only).contains("only way in"), text(only));
        assertEquals("coin", screen("MINE").from().via().picture().id(), "the only way in stays");

        // The way back: an input made it, then taken off, the input untried on the screen again.
        run("edit_map", "screen", "Shop", "action", "use_as_back", "picture", "close");
        assertEquals("close", screen("SHOP").back().picture().id());
        assertEquals(List.of("click coin"), screen("SHOP").inputs().stream().map(Input::label).toList());
        assertTrue(text(run("edit_map", "screen", "Shop", "action", "remove_back")).contains("click close is "
                + "untried on it again"));
        assertNull(screen("SHOP").back());
        assertEquals(List.of("click coin", "click close"), screen("SHOP").inputs().stream().map(Input::label)
                .toList());
        assertTrue(run("edit_map", "screen", "Shop", "action", "remove_back").isRefused(), "it has none now");
        run("edit_map", "screen", "Shop", "action", "use_as_back", "picture", "close");
        run("edit_map", "screen", "Shop", "action", "use_as_back", "picture", "coin");
        assertEquals("coin", screen("SHOP").back().picture().id());
        assertEquals(List.of("click close"), screen("SHOP").inputs().stream().map(Input::label).toList(),
                "the way back it had is an input again");
        assertTrue(run("edit_map", "screen", "Shop", "action", "rename", "name", "home").isRefused(),
                "a name another screen has");
        assertTrue(run("edit_map", "screen", "Home", "action", "delete").isRefused(), "screens are reached from it");

        // A new screen, by an input the parent has or one put on it; renamed, made a popup, deleted.
        assertTrue(text(run("edit_map", "screen", "Home", "action", "new_screen", "name", "Forge", "picture", "tab"))
                .startsWith("Added Forge as Screens.FORGE"));
        assertEquals("tab", screen("FORGE").from().via().picture().id());
        assertEquals(3, screen("HOME").inputs().size(), "tab was on Home already");
        run("edit_map", "screen", "Home", "action", "new_screen", "name", "World", "key", "W");
        assertEquals("key W", screen("HOME").inputs().getLast().label());
        assertTrue(run("edit_map", "screen", "Home", "action", "new_screen", "name", "Cave", "picture", "nothing")
                .isRefused());
        run("edit_map", "screen", "Forge", "action", "rename", "name", "Big forge");
        run("edit_map", "screen", "Big forge", "action", "popup", "popup", true);
        assertTrue(screen("FORGE").isPopup());
        assertEquals("Deleted Big forge.", text(run("edit_map", "screen", "FORGE", "action", "delete")));
        assertFalse(host.set("screen_map").containsKey("FORGE"));
        run("edit_map", "screen", "Home", "action", "remove_input", "picture", "tab");
        assertEquals(List.of("click gem", "key Q", "key W"), screen("HOME").inputs().stream().map(Input::label)
                .toList());
    }

    private static ImageTemplate picture(String name) {
        return new ImageTemplate(TemplateLibrary.pathForName(name));
    }

    /** A screen's markers: the pictures {@code names}. */
    private static List<ImageTemplate> marks(String... names) {
        return Arrays.stream(names).map(SdkAssistTest::picture).toList();
    }

    /** The bot's screen {@code constant}, as try_input and crop_pictures last wrote it. */
    private GameScreen screen(String constant) {
        return (GameScreen) host.set("screen_map").get(constant).value();
    }

    @Test
    void theGuideNamesEveryStageInOrder() {
        int at = -1;
        for (Protocol.Stage stage : Protocol.Stage.values()) {
            String name = stage.title().substring(0, stage.title().indexOf(':'));
            int next = SdkAssist.GUIDE.indexOf("- " + name + ":");
            assertTrue(next > at, name + " is missing or out of order");
            at = next;
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────────

    private AgentReply run(String tool, Object... arguments) {
        Map<String, Object> sent = new LinkedHashMap<>();
        for (int i = 0; i < arguments.length; i += 2) sent.put((String) arguments[i], arguments[i + 1]);
        return SdkAssist.ALL.stream().filter(t -> t.name().equals(tool)).findFirst().orElseThrow()
                .invoke(sent, agent);
    }

    /** try_input with one input: the call's screen and settings beside that input's own fields. */
    private AgentReply tryOne(Object... arguments) {
        return run("try_input", listOfOne(arguments, Set.of("screen", "settleMs", "stay", "desktop"), "inputs"));
    }

    /** crop_pictures with one box: the call's screen and tags beside that box's own fields. */
    private AgentReply cropOne(Object... arguments) {
        return run("crop_pictures", listOfOne(arguments, Set.of("screen", "tags"), "boxes"));
    }

    /** {@code arguments} with every key {@code call} lacks moved into one object, the one element of {@code list}. */
    private static Object[] listOfOne(Object[] arguments, Set<String> call, String list) {
        List<Object> sent = new ArrayList<>();
        Map<String, Object> one = new LinkedHashMap<>();
        for (int i = 0; i < arguments.length; i += 2) {
            if (call.contains((String) arguments[i])) {
                sent.add(arguments[i]);
                sent.add(arguments[i + 1]);
            } else {
                one.put((String) arguments[i], arguments[i + 1]);
            }
        }
        sent.add(list);
        sent.add(List.of(one));
        return sent.toArray();
    }

    /** One crop_pictures box, its fields after its place. */
    private static Map<String, Object> box(String name, int x, int y, int width, int height, Object... more) {
        Map<String, Object> box = new LinkedHashMap<>(Map.of("name", name, "x", x, "y", y, "width", width,
                "height", height));
        for (int i = 0; i < more.length; i += 2) box.put((String) more[i], more[i + 1]);
        return box;
    }

    /** The line a slow tool's reply ends with ({@link ToolTiming}); {@link #text} leaves it out. */
    private static final Pattern TIMING = Pattern.compile("\nTook \\d+\\.\\d s(: .+)?\\.");

    private Flow flow() {
        return (Flow) host.set("flows").get("MAIN").value();
    }

    private static String text(AgentReply reply) {
        return reply.parts().stream().filter(p -> p.kind() == AgentReply.Kind.TEXT).map(AgentReply.Part::text)
                .filter(line -> !TIMING.matcher(line).matches())
                .collect(Collectors.joining("\n"));
    }

    private static java.awt.Dimension size(AgentReply reply) throws IOException {
        byte[] png = reply.parts().stream().filter(p -> p.kind() == AgentReply.Kind.IMAGE).findFirst().orElseThrow()
                .png();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        return new java.awt.Dimension(image.getWidth(), image.getHeight());
    }

    private static BufferedImage withPatchAt(int x, int y) {
        BufferedImage frame = noise(WATCHED.width(), WATCHED.height(), 11);
        frame.getGraphics().drawImage(patch(), x, y, null);
        return frame;
    }

    private static BufferedImage withText(String text, int x, int y) {
        BufferedImage frame = plain(Color.WHITE);
        Graphics2D g = frame.createGraphics();
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 40));
        g.drawString(text, x, y);
        g.dispose();
        return frame;
    }

    private static BufferedImage plain(Color color) {
        BufferedImage frame = new BufferedImage(WATCHED.width(), WATCHED.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = frame.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, frame.getWidth(), frame.getHeight());
        g.dispose();
        return frame;
    }

    private static BufferedImage patch() {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                img.setRGB(x, y, ((x * 5) & 0xFF) << 16 | ((y * 5) & 0xFF) << 8 | ((x * 3 + y * 7) & 0xFF));
            }
        }
        return img;
    }

    private static BufferedImage noise(int width, int height, long seed) {
        BufferedImage bg = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) bg.setRGB(x, y, rnd.nextInt(0xFFFFFF));
        }
        return bg;
    }

    /** The screen the bot watches, as the host hands it to a tool, and the marks it was asked to draw. */
    private static final class Agent implements AgentContext {
        final Host host;
        BufferedImage frame;
        final List<String> marks = new ArrayList<>();

        Agent(Host host) {
            this.host = host;
        }

        @Override public StudioServices services() { return host; }
        @Override public Optional<BufferedImage> frame() { return Optional.ofNullable(frame); }
        @Override public Optional<Area> watchedArea() { return Optional.of(WATCHED); }

        @Override
        public Marks marks() {
            return new Marks() {
                @Override
                public void show(Area area, Kind kind, String label) {
                    marks.add(kind + " " + area.x() + "," + area.y() + " " + area.width() + "×" + area.height()
                            + " " + label);
                }

                @Override
                public void clear() {
                    marks.clear();
                }
            };
        }
    }

    /**
     * A project: its resources folder, its run properties, and its values — each method-shaped value one
     * recording context, each open set a map of constants — writing down every change it was asked for.
     */
    private static final class Host extends SpelledValues implements StudioServices {
        final Path resources;
        final Map<String, TestContexts.Recording> values = new HashMap<>();
        final Map<String, Map<String, TestContexts.Recording>> sets = new HashMap<>();
        final Map<String, List<Use>> uses = new HashMap<>();
        final Map<String, String> properties = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        Host(Path resources) {
            this.resources = resources;
        }

        /** The set under its short name, {@code "pictures"}. */
        Map<String, TestContexts.Recording> set(String name) {
            return sets.computeIfAbsent(name, k -> new LinkedHashMap<>());
        }

        /** An id's short name, the key every map here and every recorded call uses: {@code …$Id.PICTURES} is {@code "pictures"}. */
        private static String name(String id) {
            return id.substring(id.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }

        @Override public Path projectDir() { return resources.resolve("../../..").normalize(); }
        @Override public Path resourcesDir() { return resources; }
        @Override public Theme theme() { return null; }
        @Override public Dialogs dialogs() { return null; }
        @Override public PluginValues pluginValues() { return this; }

        /** Each outermost batch's label, as Studio's undo would name the step. */
        final List<String> steps = new ArrayList<>();
        private int batching;

        @Override
        public History history() {
            return new History() {
                @Override public boolean canUndo() { return false; }
                @Override public boolean canRedo() { return false; }
                @Override public String undoLabel() { return ""; }
                @Override public String redoLabel() { return ""; }
                @Override public void undo() { }
                @Override public void redo() { }
                @Override public AutoCloseable onChanged(Runnable listener) { return () -> { }; }

                @Override
                public void batch(String label, Runnable writes) {
                    if (batching++ == 0) steps.add(label);
                    try {
                        writes.run();
                    } finally {
                        batching--;
                    }
                }
            };
        }

        @Override
        public Runs runs() {
            return new Runs() {
                @Override public void start() { }
                @Override public void stop() { }
                @Override public boolean isRunning() { return false; }
                @Override public java.util.OptionalLong pid() { return java.util.OptionalLong.empty(); }
                @Override public AutoCloseable onStateChanged(java.util.function.Consumer<Boolean> l) { return () -> { }; }
                @Override public AutoCloseable onTelemetry(java.util.function.Consumer<byte[]> l) { return () -> { }; }

                @Override
                public <T> Optional<T> get(RunProperty<T> property) {
                    return property.read(properties.get(property.name()));
                }

                @Override
                public <T> void set(RunProperty<T> property, T value) {
                    String text = property.write(value);
                    if (text == null || text.isBlank()) properties.remove(property.name());
                    else properties.put(property.name(), text);
                }
            };
        }

        @Override
        public List<String> ids() {
            return values.keySet().stream()
                    .map(name -> ManagedValue.idOf(SdkValue.Id.valueOf(name.toUpperCase(Locale.ROOT)))).toList();
        }

        @Override
        public Optional<ValueContext> open(String id) {
            return Optional.ofNullable(values.get(name(id)));
        }

        @Override
        public Optional<String> create(String id) {
            calls.add("create " + name(id));
            set(name(id));
            return Optional.empty();
        }

        @Override public List<String> members(String id) { return List.copyOf(set(name(id)).keySet()); }

        @Override
        public Optional<ValueContext> open(String id, String member) {
            return Optional.ofNullable(set(name(id)).get(member));
        }

        @Override
        public Optional<String> add(String id, String member, Object value) {
            if (set(name(id)).containsKey(member)) return Optional.of(member + " is taken.");
            calls.add("add " + name(id) + " " + member);
            set(name(id)).put(member, TestContexts.row(Object.class, "").withValue(value));
            return Optional.empty();
        }

        @Override
        public List<Use> uses(String id, String member) {
            List<Use> all = new ArrayList<>(uses.getOrDefault(member, List.of()));
            // The map's own, as Studio finds them in Screens.java: each screen naming the picture.
            for (TestContexts.Recording row : set("screen_map").values()) {
                if (row.value() instanceof GameScreen screen
                        && !ScreenGraph.without(screen, member.toLowerCase(Locale.ROOT)).equals(screen)) {
                    all.add(new Use(Path.of("Screens.java"), 1, screen.name()));
                }
            }
            return all;
        }

        @Override
        public Optional<String> rename(String id, String member, String newName) {
            calls.add("rename " + name(id) + " " + member + " " + newName);
            // A grouped member, "Group.NAME", is renamed within its group.
            String renamed = member.contains(".") ? member.substring(0, member.indexOf('.') + 1) + newName : newName;
            set(name(id)).put(renamed, set(name(id)).remove(member));
            // A tag's uses follow it, as the host's rename by binding moves them: each picture naming it.
            if (name(id).equals("tags")) {
                for (TestContexts.Recording picture : set("pictures").values()) {
                    if (!(picture.value() instanceof ImageTemplate held)) continue;
                    picture.set(held.tags(held.tags().stream()
                            .map(tag -> tag.equals(BotConstants.tag(member)) ? BotConstants.tag(newName) : tag)
                            .toList()));
                }
            }
            return Optional.empty();
        }

        // A grouped set's enums: the prefixes of its "Group.NAME" members.

        @Override
        public List<String> groups(String id) {
            return set(name(id)).keySet().stream().filter(m -> m.contains("."))
                    .map(m -> m.substring(0, m.indexOf('.'))).distinct().toList();
        }

        @Override
        public Optional<String> addGroup(String id, String group, List<String> constants) {
            calls.add("addGroup " + name(id) + " " + group + " " + constants);
            for (String constant : constants) {
                set(name(id)).put(group + "." + constant, TestContexts.row(Object.class, "").withValue(null));
            }
            return Optional.empty();
        }

        @Override
        public Optional<String> renameGroup(String id, String group, String newName) {
            calls.add("renameGroup " + name(id) + " " + group + " " + newName);
            for (String member : List.copyOf(set(name(id)).keySet())) {
                if (member.startsWith(group + ".")) {
                    set(name(id)).put(newName + member.substring(group.length()), set(name(id)).remove(member));
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<String> removeGroup(String id, String group) {
            calls.add("removeGroup " + name(id) + " " + group);
            set(name(id)).keySet().removeIf(member -> member.startsWith(group + "."));
            return Optional.empty();
        }

        @Override
        public Optional<String> repoint(String id, String member, String replacement, String note) {
            calls.add("repoint " + name(id) + " " + member + " " + replacement);
            uses.put(replacement, uses.remove(member));
            return Optional.empty();
        }

        @Override
        public Optional<String> moveBefore(String id, String member, String before) {
            calls.add("moveBefore " + name(id) + " " + member + " " + before);
            Map<String, TestContexts.Recording> moved = new LinkedHashMap<>();
            TestContexts.Recording row = set(name(id)).remove(member);
            set(name(id)).forEach((name, value) -> {
                if (name.equals(before)) moved.put(member, row);
                moved.put(name, value);
            });
            set(name(id)).clear();
            set(name(id)).putAll(moved);
            return Optional.empty();
        }

        /** The bot's methods returning each activity's enum, by the enum's name. */
        final Map<String, List<String>> bodies = new HashMap<>();

        @Override
        public List<String> methods(String id, String group) {
            return group == null ? List.of() : bodies.getOrDefault(group, List.of());
        }

        @Override
        public Optional<String> remove(String id, String member) {
            if (!uses(id, member).isEmpty()) return Optional.of(member + " is still used.");
            calls.add("remove " + name(id) + " " + member);
            set(name(id)).remove(member);
            return Optional.empty();
        }
    }
}
