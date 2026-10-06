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
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.capture.core.RecordingNativeController;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    Path resources;

    private Host host;
    private Agent agent;

    @BeforeEach
    void setUp() throws IOException {
        Frames.forget();
        host = new Host(resources);
        agent = new Agent(host);
        TemplateLibrary.saveTemplate(resources, patch(), "ore", WATCHED.width(), WATCHED.height(), null);
        host.set("pictures").put("ORE", TestContexts.row(Object.class, "").withValue(
                new com.botmaker.sdk.api.vision.ImageTemplate(TemplateLibrary.pathForName("ore"))));
    }

    @AfterEach
    void tearDown() {
        NativeControllerFactory.setForTesting(null);
    }

    // ── seeing ──────────────────────────────────────────────────────────────────────────────────────────

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
        TemplateLibrary.saveTemplate(resources, noise(SIZE, SIZE, 3), "elsewhere", 0, 0, null);
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
    void aPictureIsShownWithItsConstantAndUses() {
        assertEquals("ore: 48×48, Pictures.ORE, unused.", text(run("show_picture", "picture", "ore")));
        host.uses.put("ORE", List.of(new PluginValues.Use(Path.of("/bot/Collect.java"), 4, "click(Pictures.ORE);")));
        assertTrue(text(run("show_picture", "picture", "ore")).endsWith("1 use in 1 file."));
    }

    @Test
    void aRenameMovesTheFileAndTheConstant() {
        assertEquals("Renamed ore to iron.", text(run("rename_picture", "picture", "ore", "to", "iron")));
        assertTrue(TemplateLibrary.exists(resources, "iron"));
        assertFalse(TemplateLibrary.exists(resources, "ore"));
        assertEquals(List.of("rename pictures ORE IRON"), host.calls);
    }

    @Test
    void aPictureInUseIsDeletedOnlyAfterItsUsesMove() throws IOException {
        TemplateLibrary.saveTemplate(resources, patch(), "gold", 0, 0, null);
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
        agent.frame = withPatchAt(120, 80);
        run("screenshot");
        AgentReply replaced = run("replace_picture", "picture", "ore", "x", 0, "y", 0, "width", 30, "height", 20);
        assertFalse(replaced.isRefused(), text(replaced));
        BufferedImage now = ImageIO.read(TemplateLibrary.fileForName(resources, "ore").toFile());
        assertEquals(30, now.getWidth());
        assertEquals(20, now.getHeight());
        assertTrue(host.calls.isEmpty(), "the Java is untouched: " + host.calls);
    }

    @Test
    void aPointIsSavedInTheBotsPixelsAndARegionInTheSources() {
        agent.frame = withPatchAt(120, 80);
        AgentReply point = run("save_point", "name", "claim button", "x", 10, "y", 20);
        assertTrue(text(point).startsWith("Saved Points.CLAIM_BUTTON = 1010,520"), text(point));
        assertEquals(new Point(1010, 520), host.set("points").get("CLAIM_BUTTON").value());
        assertTrue(text(run("save_point", "name", "CLAIM_BUTTON", "x", 30, "y", 40))
                .startsWith("Moved Points.CLAIM_BUTTON = 1030,540"), "the same name moves it");
        assertEquals(new Point(1030, 540), host.set("points").get("CLAIM_BUTTON").value());
        assertTrue(run("save_point", "name", "2nd", "x", 10, "y", 20).isRefused(), "no constant starts with a digit");
        assertTrue(run("save_point", "name", "far", "x", 500, "y", 20).isRefused());

        AgentReply region = run("save_region", "name", "bag", "x", 300, "y", 200, "width", 200, "height", 200);
        assertTrue(text(region).startsWith("Saved Regions.BAG = 300,200 (100×100)"), "clipped: " + text(region));
        assertEquals(new Rect(300, 200, 100, 100), host.set("regions").get("BAG").value());
        assertEquals(List.of("create points", "add points CLAIM_BUTTON", "create regions", "add regions BAG"),
                host.calls);
    }

    // ── the flow ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void theFlowIsBuiltReadAndRewired() {
        host.values.put("flow", TestContexts.row(Flow.class, "").withValue(Flow.NONE));
        assertEquals("The flow has no activities yet; add_activity adds one.", text(run("read_flow")));

        assertFalse(run("add_activity", "name", "Collect", "body", "Collect::body").isRefused());
        assertFalse(run("add_activity", "name", "Battle").isRefused());
        assertFalse(run("connect", "from", "Collect", "outcome", "bag full", "to", "Battle").isRefused());
        assertFalse(run("connect", "from", "Battle", "to", "Collect").isRefused());
        String read = text(run("read_flow"));
        assertTrue(read.startsWith("Starts at Collect;"), read);
        assertTrue(read.contains("• Collect — Collect::body; outcomes [bag full]"), read);
        assertTrue(read.contains("    on bag full → Battle"), read);
        assertTrue(read.contains("• Battle — no body yet"), read);

        assertEquals("Collect on bag full now goes nowhere. Activities: [Collect, Battle].",
                text(run("disconnect", "from", "Collect", "outcome", "bag full")));
        assertTrue(run("disconnect", "from", "Collect", "outcome", "bag full").isRefused());
        assertFalse(run("set_start", "name", "Battle").isRefused());
        assertEquals(Activity.named("Battle"), flow().start());
        assertFalse(run("rename_activity", "from", "Battle", "to", "Fight").isRefused());
        assertFalse(run("remove_activity", "name", "Collect").isRefused());
        assertEquals(List.of("Fight"), flow().steps().stream().map(Flow.Step::label).toList());
        assertTrue(host.set("activities").containsKey("FIGHT"), host.calls.toString());
    }

    @Test
    void aFlowWrittenByHandIsLeftAlone() {
        host.values.put("flow", TestContexts.row(Flow.class, "myFlow()"));
        assertTrue(run("read_flow").isRefused());
        assertTrue(run("set_start", "name", "Collect").isRefused());
    }

    // ── source, settings, emulator, launch ──────────────────────────────────────────────────────────────

    @Test
    void theCaptureSourceIsReadAndPointedAtAWindow() {
        host.values.put("capture", TestContexts.row(CaptureSource.class, "").withValue(CaptureSource.window("Game")));
        assertEquals("Window: Game.", text(run("get_capture_source")));
        assertEquals("The bot now looks at the window \"Other\".", text(run("set_capture_source", "window", "Other")));
        assertEquals("Window: Other", com.botmaker.sdk.plugin.screen.CaptureLabels.longLabel(
                (CaptureSource) host.values.get("capture").value()));
    }

    @Test
    void theOpenWindowsAreListedOncePerTitle() {
        NativeControllerFactory.setForTesting(new RecordingNativeController());
        assertEquals("\"Test Game Window\" 800×600 at 100,50", text(run("list_windows")));
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
                ((BotSettings) host.values.get("settings").value()).input().linuxBackend());
        assertEquals(0.9, ((BotSettings) host.values.get("settings").value()).confidence(), "the others stay");

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
    void theLaunchTargetIsThisMachinesAndOnlyAValidOneIsKept() {
        assertTrue(text(run("get_launch_target")).startsWith("This computer launches nothing"));
        assertEquals("This computer now launches Steam game 570 when the bot starts.",
                text(run("set_launch_target", "target", " steam:570 ")));
        assertEquals("steam:570", host.properties.get("botmaker.launch.target"));
        assertEquals("steam:570 — Steam game 570.", text(run("get_launch_target")));
        assertTrue(run("set_launch_target", "target", "nonsense").isRefused());
        assertTrue(run("set_launch_target", "target", "floppy:disk").isRefused(), "a kind nothing launches");
        assertEquals("steam:570", host.properties.get("botmaker.launch.target"));
    }

    @Test
    void everyToolHasADistinctNameAndNoneIsCalledWire() {
        List<String> names = SdkAssist.ALL.stream().map(AssistantTool::name).toList();
        assertEquals(names.size(), names.stream().distinct().count(), names.toString());
        assertEquals(32, names.size());
        assertTrue(SdkAssist.ALL.stream().noneMatch(t -> t.paramsType().getSimpleName().contains("Wire")));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────────

    private AgentReply run(String tool, Object... arguments) {
        Map<String, Object> sent = new LinkedHashMap<>();
        for (int i = 0; i < arguments.length; i += 2) sent.put((String) arguments[i], arguments[i + 1]);
        return SdkAssist.ALL.stream().filter(t -> t.name().equals(tool)).findFirst().orElseThrow()
                .invoke(sent, agent);
    }

    private Flow flow() {
        return (Flow) host.values.get("flow").value();
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
    private static final class Host implements StudioServices, PluginValues {
        final Path resources;
        final Map<String, TestContexts.Recording> values = new HashMap<>();
        final Map<String, Map<String, TestContexts.Recording>> sets = new HashMap<>();
        final Map<String, List<Use>> uses = new HashMap<>();
        final Map<String, String> properties = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        Host(Path resources) {
            this.resources = resources;
        }

        Map<String, TestContexts.Recording> set(String id) {
            return sets.computeIfAbsent(id, k -> new LinkedHashMap<>());
        }

        @Override public Path projectDir() { return resources.getParent(); }
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
                public String property(String name) {
                    return properties.get(name);
                }

                @Override
                public void setProperty(String name, String value) {
                    if (value == null || value.isBlank()) properties.remove(name);
                    else properties.put(name, value);
                }
            };
        }

        @Override public List<String> ids() { return List.copyOf(values.keySet()); }
        @Override public Optional<ValueContext> open(String id) { return Optional.ofNullable(values.get(id)); }

        @Override
        public Optional<String> create(String id) {
            calls.add("create " + id);
            set(id);
            return Optional.empty();
        }

        @Override public List<String> members(String id) { return List.copyOf(set(id).keySet()); }

        @Override
        public Optional<ValueContext> open(String id, String member) {
            return Optional.ofNullable(set(id).get(member));
        }

        @Override
        public Optional<String> add(String id, String member, Object value) {
            if (set(id).containsKey(member)) return Optional.of(member + " is taken.");
            calls.add("add " + id + " " + member);
            set(id).put(member, TestContexts.row(Object.class, "").withValue(value));
            return Optional.empty();
        }

        @Override public List<Use> uses(String id, String member) { return uses.getOrDefault(member, List.of()); }

        @Override
        public Optional<String> rename(String id, String member, String newName) {
            calls.add("rename " + id + " " + member + " " + newName);
            set(id).put(newName, set(id).remove(member));
            return Optional.empty();
        }

        @Override
        public Optional<String> repoint(String id, String member, String replacement, String note) {
            calls.add("repoint " + id + " " + member + " " + replacement);
            uses.put(replacement, uses.remove(member));
            return Optional.empty();
        }

        @Override
        public Optional<String> remove(String id, String member) {
            if (!uses(id, member).isEmpty()) return Optional.of(member + " is still used.");
            calls.add("remove " + id + " " + member);
            set(id).remove(member);
            return Optional.empty();
        }
    }
}
