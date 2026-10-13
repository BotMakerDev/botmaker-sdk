package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.Dialogs;
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
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.api.geometry.Anchor;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.geometry.Spot;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.capture.core.RecordingNativeController;
import com.botmaker.sdk.internal.trace.MissFrames;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.flow.FlowValue;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.pictures.TemplateManifest;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(new java.awt.Dimension(1024, 576), size(look));
        assertTrue(text(look).contains("Shrunk to 1024×576: multiply what you see by 1.8750"), text(look));
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
        assertTrue(Frames.shown().isPresent(), "the whole frame is kept for crop_picture");

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
        assertEquals(new java.awt.Dimension(1024, 576), size(shown));
        assertTrue(run("missed_frames", "name", "../../src/main/resources/ore.png").isRefused());
    }

    // ── pictures and places ─────────────────────────────────────────────────────────────────────────────

    @Test
    void aCropIsCutFromTheLastScreenshotAndDeclared() {
        assertTrue(run("crop_picture", "name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE).isRefused(),
                "no screenshot yet");
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        agent.frame = null;
        AgentReply saved = run("crop_picture", "name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE);
        assertEquals("Saved gold as Pictures.GOLD.", text(saved));
        assertTrue(TemplateLibrary.exists(resources, "gold"));
        assertTrue(host.set("pictures").containsKey("GOLD"));
        assertTrue(text(run("list_pictures")).contains("gold → Pictures.GOLD"));
    }

    @Test
    void aBoxThatChangesOverTimeIsNotCut() {
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        agent.frame = noise(WATCHED.width(), WATCHED.height(), 9);
        AgentReply refused = run("crop_picture", "name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE);
        assertTrue(refused.isRefused() && text(refused).startsWith("That box changes"), text(refused));
        assertFalse(TemplateLibrary.exists(resources, "gold"));
        assertTrue(run("replace_picture", "picture", "ore", "x", 120, "y", 80, "width", SIZE, "height", SIZE)
                .isRefused());

        agent.frame = withPatchAt(120, 80);
        assertFalse(run("crop_picture", "name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE)
                .isRefused(), "the same frame again is still");
    }

    @Test
    void aPictureIsTaggedOnlyWithDeclaredTagsAndEveryTagEditIsATool() {
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        agent.frame = null;
        assertTrue(run("crop_picture", "name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE,
                "tags", List.of("Home")).isRefused(), "Home is not declared");
        assertFalse(TemplateLibrary.exists(resources, "gold"), "a refused tag saves nothing");

        assertEquals("Declared Home; tag_pictures puts it on pictures.", text(run("declare_tag", "tag", " Home ")));
        assertTrue(run("declare_tag", "tag", "home").isRefused(), "already there, in another case");
        assertTrue(run("declare_tag", "tag", TemplateManifest.UNTAGGED).isRefused(), "a built-in group");
        assertEquals("Saved gold as Pictures.GOLD, tagged Home.", text(run("crop_picture", "name", "gold",
                "x", 120, "y", 80, "width", SIZE, "height", SIZE, "tags", List.of("home", "HOME"))),
                "spelled as declared, once");
        assertTrue(text(run("list_pictures")).contains("gold → Pictures.GOLD [Home]"));

        run("declare_tag", "tag", "Popups");
        assertTrue(run("tag_pictures", "tag", "Nope", "pictures", List.of("gold")).isRefused());
        assertTrue(run("tag_pictures", "tag", "Popups", "pictures", List.of("gold", "nothing")).isRefused());
        assertFalse(run("tag_pictures", "tag", "popups", "pictures", List.of("gold", "ore")).isRefused());
        assertEquals(java.util.Set.of("Home", "Popups"), TemplateLibrary.manifest(resources).tagsOf("gold"));
        String listed = text(run("list_tags"));
        assertTrue(listed.contains("Popups: gold, ore"), listed);

        assertEquals("Took Popups off ore.", text(run("untag_pictures", "tag", "Popups", "pictures", List.of("ore"))));
        assertTrue(TemplateLibrary.manifest(resources).tagsOf("ore").isEmpty());
        assertTrue(run("untag_pictures", "tag", "Popups", "pictures", List.of("ore")).isRefused(), "ore has none");
        assertEquals("gold carries Popups.", text(run("set_tags", "picture", "gold", "tags", List.of("Popups"))));

        assertTrue(run("rename_tag", "tag", "Popups", "to", "Home").isRefused(), "taken");
        assertEquals("Renamed Popups to Dialogs.", text(run("rename_tag", "tag", "Popups", "to", "Dialogs")));
        assertEquals(java.util.Set.of("Dialogs"), TemplateLibrary.manifest(resources).tagsOf("gold"));
        assertEquals("Deleted Dialogs; its pictures stay.", text(run("delete_tag", "tag", "Dialogs")));
        assertTrue(TemplateLibrary.manifest(resources).tagsOf("gold").isEmpty());
        assertTrue(TemplateLibrary.exists(resources, "gold"));
        assertTrue(run("delete_tag", "tag", "Dialogs").isRefused());
    }

    @Test
    void theFirstCutSetsTheBotsScreenAndALaterOneFromAnotherFrameNamesItsOwn() {
        host.values.put("screen", TestContexts.row(Size.class, "").withValue(new Size(0, 0)));
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        run("crop_picture", "name", "gold", "x", 120, "y", 80, "width", SIZE, "height", SIZE);
        assertEquals(new Size(WATCHED.width(), WATCHED.height()), host.values.get("screen").value(),
                "the first picture's frame is the bot's size");
        assertEquals(new ImageTemplate(TemplateLibrary.pathForName("gold")).at(new Rect(120, 80, SIZE, SIZE)),
                host.set("pictures").get("GOLD").value(), "cut at the bot's size: no .in");

        agent.frame = noise(800, 600, 5);
        run("screenshot");
        run("crop_picture", "name", "iron", "x", 0, "y", 0, "width", SIZE, "height", SIZE);
        assertEquals(new ImageTemplate(TemplateLibrary.pathForName("iron")).at(new Rect(0, 0, SIZE, SIZE))
                .in(new Size(800, 600)), host.set("pictures").get("IRON").value());
        assertEquals(new Size(WATCHED.width(), WATCHED.height()), host.values.get("screen").value(), "kept");
    }

    @Test
    void aReplacementRewritesWhereThePictureWasCut() {
        host.values.put("screen", TestContexts.row(Size.class, "").withValue(new Size(800, 600)));
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        assertFalse(run("replace_picture", "picture", "ore", "x", 120, "y", 80, "width", SIZE, "height", SIZE)
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
        assertEquals("Renamed ore to iron.", text(run("rename_picture", "picture", "ore", "to", "iron")));
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

        AgentReply refused = run("delete_picture", "picture", "ore");
        assertTrue(refused.isRefused());
        assertTrue(text(refused).contains("Collect.java:4"), text(refused));
        assertTrue(TemplateLibrary.exists(resources, "ore"));

        AgentReply deleted = run("delete_picture", "picture", "ore", "pointUsesAt", "gold");
        assertFalse(deleted.isRefused(), text(deleted));
        assertFalse(TemplateLibrary.exists(resources, "ore"));
        assertTrue(host.calls.contains("add pictures GOLD"), host.calls.toString());
        assertTrue(host.calls.stream().anyMatch(c -> c.startsWith("repoint pictures ORE GOLD")), host.calls.toString());
        assertTrue(host.calls.contains("remove pictures ORE"), host.calls.toString());
    }

    @Test
    void aReplacementKeepsTheNameAndTakesTheNewImage() throws IOException {
        host.values.put("screen", TestContexts.row(Size.class, "").withValue(new Size(WATCHED.width(), WATCHED.height())));
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        AgentReply replaced = run("replace_picture", "picture", "ore", "x", 0, "y", 0, "width", 30, "height", 20);
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
        AgentReply point = run("save_point", "name", "claim button", "x", 10, "y", 20);
        assertTrue(text(point).startsWith("Saved Spots.CLAIM_BUTTON = 10,20 of the bot's 400×300 screen"), text(point));
        assertEquals(new Spot(10, 20), host.set("spots").get("CLAIM_BUTTON").value());
        assertEquals(new Size(400, 300), host.values.get("screen").value(),
                "a bot naming no size takes the screenshot's");
        assertTrue(text(run("save_point", "name", "CLAIM_BUTTON", "x", 30, "y", 40, "anchor", "top_left"))
                .startsWith("Moved Spots.CLAIM_BUTTON = 30,40"), "the same name moves it");
        assertEquals(new Spot(30, 40).anchored(Anchor.TOP_LEFT), host.set("spots").get("CLAIM_BUTTON").value());
        assertTrue(run("save_point", "name", "2nd", "x", 10, "y", 20).isRefused(), "no constant starts with a digit");
        assertTrue(run("save_point", "name", "far", "x", 500, "y", 20).isRefused());

        AgentReply region = run("save_region", "name", "bag", "x", 300, "y", 200, "width", 200, "height", 200);
        assertTrue(text(region).startsWith("Saved Zones.BAG = 300,200 (100×100)"), "clipped: " + text(region));
        assertEquals(new Zone(300, 200, 100, 100), host.set("zones").get("BAG").value());
        assertEquals(List.of("create spots", "add spots CLAIM_BUTTON", "create zones", "add zones BAG"),
                host.calls);
    }

    @Test
    void aPlaceOnAGameShowingAnotherSizeIsWrittenAtTheBotsSize() {
        host.values.put("screen", TestContexts.row(Size.class, "").withValue(new Size(200, 150)));
        agent.frame = withPatchAt(120, 80);
        assertTrue(text(run("save_point", "name", "claim", "x", 100, "y", 60))
                .startsWith("Saved Spots.CLAIM = 50,30 of the bot's 200×150 screen"), "the 400×300 game is twice it");
        assertEquals(new Spot(50, 30), host.set("spots").get("CLAIM").value());
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
        assertTrue(empty.endsWith("The flow has no activities yet; add_activity adds one."), empty);

        // A project with no flow gets Flows.MAIN on its first change.
        assertFalse(run("add_activity", "name", "Collect", "body", "Collect::body").isRefused());
        assertTrue(host.calls.contains("add flows MAIN"), host.calls.toString());
        assertFalse(run("add_activity", "name", "Battle").isRefused());
        assertEquals(1, host.set("flows").size(), "the second change edits the flow the first one wrote");
        assertFalse(run("connect", "from", "Collect", "outcome", "bag full", "to", "Battle").isRefused());
        assertFalse(run("connect", "from", "Battle", "to", "Collect").isRefused());
        String read = text(run("read_flow"));
        assertTrue(read.startsWith("Flows: MAIN.\nFlows.MAIN.\n"), read);
        assertTrue(read.contains("\nStarts at Collect;"), read);
        assertTrue(read.contains("• Collect — Collect::body; outcomes [Next, Bag full]"), read);
        assertTrue(read.contains("    on Bag full → Battle"), read);
        assertTrue(read.contains("• Battle — no body yet"), read);

        assertEquals("Flows.MAIN: Collect on Bag full now goes nowhere. Activities: [Collect, Battle].",
                text(run("disconnect", "from", "Collect", "outcome", "bag full")));
        assertTrue(run("disconnect", "from", "Collect", "outcome", "bag full").isRefused());
        assertFalse(run("set_start", "name", "Battle").isRefused());
        assertEquals(FlowNames.activity("Battle"), flow().start());
        assertFalse(run("rename_activity", "from", "Battle", "to", "Fight").isRefused());
        assertFalse(run("remove_activity", "name", "Collect").isRefused());
        assertEquals(List.of("Fight"), flow().steps().stream().map(Flow.Step::label).toList());
        assertEquals(List.of("Fight.NEXT"), List.copyOf(host.set("activities").keySet()), host.calls.toString());
        assertTrue(host.calls.contains("add activities Collect.BAG_FULL"), host.calls.toString());
        assertTrue(host.calls.contains("renameGroup activities Battle Fight"), host.calls.toString());
    }

    @Test
    void theFlowsSetupNamesOnlyAMethodTheBotHas() {
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        assertFalse(run("add_activity", "name", "Collect", "body", "Collect::body").isRefused());
        String read = text(run("read_flow"));
        assertTrue(read.contains("Home: none.") && read.contains("Setup: none."), read);
        // The fake bot has no public static void method: a name it lacks would not compile.
        AgentReply missing = run("set_flow_method", "which", "setup", "method", "Gamebot::setup");
        assertTrue(missing.isRefused());
        assertTrue(text(missing).contains("It has: none."), text(missing));
        assertEquals("The bot has no setup now.", text(run("set_flow_method", "which", "setup")));
    }

    /** Of several flows, MAIN is edited when none is named, and the run is left as it was. */
    @Test
    void mainIsEditedWhenNoFlowIsNamedAndTheRunLeftAlone() {
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        host.set("flows").put("DAILY", TestContexts.row(Flow.class, "").withValue(Flow.NONE));
        assertTrue(text(run("read_flow")).startsWith("Flows: DAILY.\nFlows.DAILY."), "the first, with no MAIN");
        host.set("flows").remove("DAILY");
        assertFalse(run("add_activity", "name", "Collect", "body", "Collect::body").isRefused());
        host.set("flows").put("DAILY", TestContexts.row(Flow.class, "").withValue(Flow.NONE));
        assertTrue(text(run("read_flow")).startsWith("Flows: MAIN, DAILY.\nFlows.MAIN."));

        assertFalse(run("add_activity", "name", "Battle").isRefused());
        assertEquals(2, flow().steps().size());
        assertEquals(Flow.NONE, host.set("flows").get("DAILY").value());
        assertEquals(Run.NONE, host.values.get("run").value(), "the flow is no part of the run");
    }

    /** A flow is picked by name; an activity is the bot's, shared by every flow running it. */
    @Test
    void flowsArePickedByNameAndShareTheirActivities() {
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        assertFalse(run("add_activity", "name", "Collect", "body", "Collect::body").isRefused());
        assertFalse(run("connect", "from", "Collect", "outcome", "bag full", "to", "Collect").isRefused());

        assertFalse(run("add_flow", "name", "daily", "copyOf", "MAIN").isRefused());
        assertEquals(flow(), host.set("flows").get("DAILY").value(), "a copy holds the same flow");
        assertFalse(run("add_flow", "name", "empty").isRefused());
        assertEquals(Flow.NONE, host.set("flows").get("EMPTY").value());
        assertTrue(run("add_flow", "name", "Daily").isRefused(), "DAILY is taken");
        assertTrue(run("read_flow", "flow", "nightly").isRefused());

        // Added to another flow, Collect keeps its body and its enum's outcomes.
        assertFalse(run("add_activity", "name", "Collect", "flow", "EMPTY").isRefused());
        Flow empty = (Flow) host.set("flows").get("EMPTY").value();
        assertEquals("Collect::body", FlowValue.bodySource(empty.steps().getFirst()));
        assertTrue(text(run("read_flow", "flow", "empty")).contains("outcomes [Next, Bag full]"));

        // Dropped from one flow, it stays the others'.
        assertFalse(run("remove_activity", "name", "Collect", "flow", "DAILY").isRefused());
        assertFalse(host.calls.stream().anyMatch(call -> call.startsWith("removeGroup")), host.calls.toString());
        assertTrue(host.set("activities").containsKey("Collect.BAG_FULL"));

        // Another flow's activity has its enum: no activity here may take its name.
        assertFalse(run("add_activity", "name", "Rest", "flow", "EMPTY").isRefused());
        AgentReply taken = run("rename_activity", "from", "Collect", "to", "rest");
        assertTrue(taken.isRefused(), "Activities.Rest is EMPTY's");
        assertTrue(text(taken).contains("Activities.Rest is already another activity"), text(taken));

        assertFalse(run("rename_flow", "from", "empty", "to", "farm").isRefused());
        assertTrue(host.calls.contains("rename flows EMPTY FARM"), host.calls.toString());
        host.uses.put("FARM", List.of(new PluginValues.Use(Path.of("Sdk.java"), 12,
                "public static Flow flow = Flows.FARM;")));
        assertTrue(run("remove_flow", "name", "farm").isRefused(), "the bot's flow parameter names it");
        assertFalse(run("remove_flow", "name", "daily").isRefused());
        assertEquals(List.of("MAIN", "FARM"), List.copyOf(host.set("flows").keySet()));
    }

    /** A card running another flow: it moves that flow above, shows its exits, and is wired by them. */
    @Test
    void aSubflowCardIsAddedReadAndWiredByItsExits() {
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        assertFalse(run("add_activity", "name", "Collect", "body", "Collect::body").isRefused());
        assertFalse(run("add_flow", "name", "daily").isRefused());
        assertFalse(run("add_activity", "name", "Claim", "flow", "DAILY").isRefused());

        String added = text(run("add_subflow", "name", "daily"));
        assertTrue(added.contains("Added ▸ DAILY, which runs Flows.DAILY; its exits, each wired with connect and "
                + "fromFlow DAILY: [Claim · Next]."), added);
        assertTrue(host.calls.contains("moveBefore flows DAILY MAIN"), host.calls.toString());
        assertEquals(List.of("DAILY", "MAIN"), List.copyOf(host.set("flows").keySet()));
        assertTrue(run("add_subflow", "name", "daily").isRefused(), "MAIN already runs it");
        AgentReply cycle = run("add_subflow", "name", "main", "flow", "daily");
        assertTrue(cycle.isRefused());
        assertTrue(text(cycle).contains("Flows.MAIN runs Flows.DAILY"), text(cycle));
        assertTrue(run("add_subflow", "name", "nightly").isRefused());

        assertFalse(run("connect", "from", "Collect", "outcome", "bag full", "to", "daily").isRefused());
        assertFalse(run("connect", "fromFlow", "daily", "from", "claim", "to", "Collect").isRefused());
        AgentReply noExit = run("connect", "fromFlow", "daily", "from", "Claim", "outcome", "done", "to", "Collect");
        assertTrue(noExit.isRefused());
        assertTrue(text(noExit).contains("its exits are [Claim · Next]"), text(noExit));
        assertTrue(text(run("connect", "from", "daily", "to", "Collect")).contains("is a sub-flow card"));
        String read = text(run("read_flow"));
        assertTrue(read.startsWith("Flows: DAILY (a card in MAIN), MAIN.\nFlows.MAIN.\n"), read);
        assertTrue(read.contains("• ▸ DAILY — runs Flows.DAILY; exits [Claim · Next]"), read);
        assertTrue(read.contains("    on Bag full → ▸ DAILY"), read);
        assertTrue(read.contains("    on Claim · Next → Collect"), read);
        assertEquals(1, flow().edges().stream().filter(e -> e.via() != null).count());

        assertTrue(run("remove_flow", "name", "daily").isRefused(), "MAIN runs it");
        assertFalse(run("disconnect", "fromFlow", "daily", "from", "Claim").isRefused());
        assertTrue(flow().edges().stream().noneMatch(e -> e.via() != null));
        assertFalse(run("set_start", "name", "daily").isRefused());
        assertEquals("▸ DAILY", flow().start().label());
        assertFalse(run("remove_activity", "name", "▸ DAILY").isRefused());
        assertEquals(List.of("Collect"), flow().steps().stream().map(Flow.Step::label).toList());
    }

    /** A flow moved above its user takes the flows its own cards run with it, each above the card reading it. */
    @Test
    void aSubflowMovesAboveItsUserWithTheFlowsItRuns() {
        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        assertFalse(run("add_activity", "name", "Collect").isRefused());
        assertFalse(run("add_flow", "name", "x").isRefused());
        assertFalse(run("add_activity", "name", "Rest", "flow", "X").isRefused());
        assertFalse(run("add_flow", "name", "daily").isRefused());
        assertFalse(run("add_subflow", "name", "x", "flow", "daily").isRefused());
        assertEquals(List.of("MAIN", "X", "DAILY"), List.copyOf(host.set("flows").keySet()), "X is above already");

        assertFalse(run("add_subflow", "name", "daily").isRefused());
        assertEquals(List.of("X", "DAILY", "MAIN"), List.copyOf(host.set("flows").keySet()));
        assertTrue(text(run("read_flow")).contains("X (a card in DAILY), DAILY (a card in MAIN), MAIN."));
    }

    @Test
    void aRunOrFlowWrittenByHandIsLeftAlone() {
        host.values.put("run", TestContexts.row(Run.class, "myRun()"));
        assertTrue(run("read_flow").isRefused());
        assertTrue(run("set_start", "name", "Collect").isRefused());

        host.values.put("run", TestContexts.row(Run.class, "").withValue(Run.NONE));
        host.set("flows").put("MAIN", TestContexts.row(Flow.class, "myFlow()"));
        assertTrue(run("read_flow").isRefused(), "Flows.MAIN written by hand");
        assertTrue(run("add_activity", "name", "Collect").isRefused());
    }

    // ── source, settings, emulator, launch ──────────────────────────────────────────────────────────────

    @Test
    void theCaptureSourceIsReadAndPointedAtAWindow() {
        host.values.put("capture", TestContexts.row(CaptureSource.class, "")
                .withValue(CaptureSource.window("game.exe", "")));
        assertEquals("Window: game.exe.", text(run("get_capture_source")));
        RecordingNativeController open = new RecordingNativeController();
        open.window = new GenericWindow("h", "Other", new java.awt.Rectangle(0, 0, 800, 600), GenericWindow.NO_PID,
                "steam_app_0");
        NativeControllerFactory.setForTesting(open);
        try {
            assertEquals("The bot now looks at Window: Other (steam_app_0).",
                    text(run("set_capture_source", "window", "Other")));
            assertEquals("Window: Other (steam_app_0)", com.botmaker.sdk.plugin.screen.CaptureLabels.longLabel(
                    (CaptureSource) host.values.get("capture").value()));
            assertTrue(text(run("set_capture_source", "window", "Missing")).contains("list_windows"));
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
        String all = text(run("get_settings"));
        assertTrue(all.contains("confidence = 0.8 — "), all);
        assertTrue(all.contains("input_backend = auto"), all);

        assertEquals("confidence is now 0.9 in Sdk.settings().", text(run("set_setting", "setting", "confidence",
                "value", "0.9")));
        assertEquals(0.9, ((BotSettings) host.values.get("settings").value()).confidence());
        assertFalse(run("set_setting", "setting", "INPUT_BACKEND", "value", "xtest").isRefused());
        assertEquals(BotSettings.InputBackend.XTEST,
                ((BotSettings) host.values.get("settings").value()).runIn().inputBackend());
        assertEquals(0.9, ((BotSettings) host.values.get("settings").value()).confidence(), "the others stay");
        assertFalse(run("set_setting", "setting", "run_the_game_in", "value", "my-desktop").isRefused());
        assertEquals(BotSettings.Where.MY_DESKTOP, ((BotSettings) host.values.get("settings").value()).where());
        assertTrue(run("set_setting", "setting", "run_the_game_in", "value", "cloud").isRefused());

        assertTrue(run("set_setting", "setting", "confidence", "value", "2").isRefused());
        assertTrue(run("set_setting", "setting", "max_retry_attempts", "value", "0").isRefused());
        assertTrue(run("set_setting", "setting", "colour", "value", "1").isRefused(), "not a setting");
        assertTrue(text(run("set_setting", "setting", "confidence", "value", "0.9")).endsWith("is already 0.9."));
    }

    @Test
    void theEmulatorsAreListedAndAnUnknownOneIsRefused() {
        assertFalse(run("list_emulators").isRefused());
        AgentReply refused = run("use_emulator", "name", "no-such-emulator-here");
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
        run("set_launch_target", "target", "steam:570");
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
        assertTrue(text(run("get_launch_target")).startsWith("This computer launches nothing"));
        assertEquals("This computer now launches Steam game 570 when the bot starts.",
                text(run("set_launch_target", "target", " steam:570 ")));
        assertEquals(List.of(Launcher.of("steam:570")), host.values.get("launch_targets").value(),
                "the bot's Java, not a run property");
        assertTrue(host.properties.isEmpty());
        assertEquals("steam:570 — Steam game 570.", text(run("get_launch_target")));
        assertTrue(run("set_launch_target", "target", "nonsense").isRefused());
        assertTrue(run("set_launch_target", "target", "floppy:disk").isRefused(), "a kind nothing launches");
        // Text the assistant reads off a screen must not become a command the next Run executes.
        assertTrue(run("set_launch_target", "target", "cli:sh -c 'curl x | sh'").isRefused());
        assertTrue(run("set_launch_target", "target", "exe:/tmp/payload").isRefused());
        assertTrue(run("set_launch_target", "target", "steam:570;reboot").isRefused());
        assertTrue(run("set_launch_target", "target", "emu-app:com.x;rm -rf /@Pixel").isRefused());
        assertTrue(run("set_launch_target", "target", "steam:-applaunch").isRefused(), "never an option");
        assertTrue(text(run("set_launch_target", "target", "emu-app:com.example.game@no-such-emulator-here"))
                .contains("not an emulator set up here"));
        assertFalse(run("set_launch_target", "target", "steam:570").isRefused());
        assertEquals(Launcher.of("steam:570"), ((List<?>) host.values.get("launch_targets").value()).getFirst(),
                "the launcher set last is the bot's first");
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
        assertTrue(run("click", "x", 1, "y", 1, "desktop", true, "settleMs", 9000).isRefused());
        controller.events.clear();
        assertTrue(run("click", "x", 400, "y", 10, "desktop", true, "settleMs", 0).isRefused(), "off the frame");
        assertTrue(run("scroll", "x", -1, "y", 10, "amount", 1, "desktop", true, "settleMs", 0).isRefused());
        assertTrue(controller.events.isEmpty(), "nothing lands off the game: " + controller.events);
    }

    @Test
    void aKeyIsNamedAsPrintedOrAsKeyNamesIt() {
        assertEquals(Optional.of(Key.ESCAPE), ActTools.key("Escape"));
        assertEquals(Optional.of(Key.ESCAPE), ActTools.key("esc"));
        assertEquals(Optional.of(Key.Q), ActTools.key("q"));
        assertEquals(Optional.of(Key.NUM5), ActTools.key("5"));
        assertEquals(Optional.of(Key.PAGE_UP), ActTools.key("Page Up"));
        assertEquals(Optional.of(Key.PAGE_UP), ActTools.key("PAGE_UP"));
        assertEquals(Optional.of(Key.F5), ActTools.key("f5"));
        assertEquals(Optional.of(Key.LEFT_BRACKET), ActTools.key("["));
        assertEquals(Optional.empty(), ActTools.key("Hyper"));
        assertEquals(Optional.empty(), ActTools.key(" "));
    }

    @Test
    void theChangedBoxHoldsEveryMovedPixelAndNothingElse() {
        BufferedImage before = noise(200, 100, 5);
        BufferedImage after = copy(before);
        assertEquals(Optional.empty(), ActTools.changed(before, after));
        after.setRGB(40, 30, after.getRGB(40, 30) ^ 0x808080);
        after.setRGB(120, 60, after.getRGB(120, 60) ^ 0x808080);
        assertEquals(Optional.of(new Rect(40, 30, 82, 32)), ActTools.changed(before, after));
        assertEquals(Optional.of(new Rect(0, 0, 50, 50)), ActTools.changed(before, noise(50, 50, 5)),
                "another size is all changed");
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

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        copy.getGraphics().drawImage(image, 0, 0, null);
        return copy;
    }

    @Test
    void everyToolHasADistinctNameAndNoneIsCalledWire() {
        List<String> names = SdkAssist.ALL.stream().map(AssistantTool::name).toList();
        assertEquals(names.size(), names.stream().distinct().count(), names.toString());
        assertEquals(52, names.size());
        assertTrue(SdkAssist.ALL.stream().noneMatch(t -> t.paramsType().getSimpleName().contains("Wire")));
    }

    /** Studio's own tools the guide names beside the SDK's. */
    private static final java.util.Set<String> STUDIOS = java.util.Set.of("add_method", "run_activity", "set_slot");

    @Test
    void theGuideNamesOnlyToolsThereAreAndThePluginGivesIt() {
        for (String word : List.of("describe", "click", "key", "scroll", "connect")) {
            assertTrue(SdkAssist.ALL.stream().anyMatch(t -> t.name().equals(word)), word + " is no tool");
        }
        java.util.Set<String> names = SdkAssist.ALL.stream().map(AssistantTool::name)
                .collect(java.util.stream.Collectors.toSet());
        java.util.regex.Matcher named = java.util.regex.Pattern.compile("\\b[a-z]+_[a-z_]+\\b").matcher(SdkAssist.GUIDE);
        while (named.find()) {
            assertTrue(names.contains(named.group()) || STUDIOS.contains(named.group()),
                    "the guide names " + named.group() + ", which is no tool");
        }
        assertEquals(SdkAssist.GUIDE.strip(), new com.botmaker.sdk.plugin.SdkPlugin().assistantGuide());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────────

    private AgentReply run(String tool, Object... arguments) {
        Map<String, Object> sent = new LinkedHashMap<>();
        for (int i = 0; i < arguments.length; i += 2) sent.put((String) arguments[i], arguments[i + 1]);
        return SdkAssist.ALL.stream().filter(t -> t.name().equals(tool)).findFirst().orElseThrow()
                .invoke(sent, agent);
    }

    private Flow flow() {
        return (Flow) host.set("flows").get("MAIN").value();
    }

    private static String text(AgentReply reply) {
        return reply.parts().stream().filter(p -> p.kind() == AgentReply.Kind.TEXT).map(AgentReply.Part::text)
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

        @Override public List<Use> uses(String id, String member) { return uses.getOrDefault(member, List.of()); }

        @Override
        public Optional<String> rename(String id, String member, String newName) {
            calls.add("rename " + name(id) + " " + member + " " + newName);
            // A grouped member, "Group.NAME", is renamed within its group.
            String renamed = member.contains(".") ? member.substring(0, member.indexOf('.') + 1) + newName : newName;
            set(name(id)).put(renamed, set(name(id)).remove(member));
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

        @Override
        public Optional<String> remove(String id, String member) {
            if (!uses(id, member).isEmpty()) return Optional.of(member + " is still used.");
            calls.add("remove " + name(id) + " " + member);
            set(name(id)).remove(member);
            return Optional.empty();
        }
    }
}
