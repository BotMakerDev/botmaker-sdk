package com.botmaker.sdk.internal.authoring;

import com.botmaker.sdk.api.authoring.ActivityModel;
import com.botmaker.sdk.api.authoring.Authoring;
import com.botmaker.sdk.api.authoring.FlowEdgeModel;
import com.botmaker.sdk.api.authoring.FlowModel;
import com.botmaker.sdk.api.authoring.FlowNodeModel;
import com.botmaker.sdk.api.authoring.ProjectModel;
import com.botmaker.sdk.api.authoring.ProjectSpec;
import com.botmaker.sdk.api.authoring.Range;
import com.botmaker.sdk.api.authoring.SdkVersion;
import com.botmaker.sdk.api.authoring.ValueChoice;
import com.botmaker.sdk.api.authoring.ValueShape;
import com.botmaker.sdk.api.authoring.ValueType;
import com.botmaker.sdk.api.authoring.VariableModel;
import com.botmaker.sdk.api.authoring.Visibility;
import com.botmaker.sdk.api.geometry.Size;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated project compiles.
 *
 * <p>This is the guarantee the old {@code ScaffoldCompileTest} carried, moved to where it belongs. Over there
 * it lived in the editor's build and compiled against a <em>resolved SDK jar</em>, so it answered "do these
 * files work with some published SDK" — which is a different and weaker question, and one that went stale
 * whenever the jar did. Here the emitted file and the API it calls are the same build: {@code javac} runs
 * against this reactor's own {@code target/classes}, taken from the test classpath.
 *
 * <p>Nothing here asserts the <em>text</em> of a generated file beyond the few lines that carry a decision
 * (the missing {@code final} on a flag, a literal where a parser call used to be). Compiling it is the
 * assertion; a golden-file comparison would fail on every javadoc edit and prove nothing about the file
 * working.
 */
class ScaffoldEmitTest {

    private static final SdkVersion V = SdkVersion.latest();

    private static ProjectSpec spec() {
        return new ProjectSpec("MyBot", "com.mybot", "MyBot", ProjectSpec.Kind.GAME_BOT, "1.2.0",
                new Size(1920, 1080));
    }

    private static ActivityModel activity(String name, String... outcomes) {
        return new ActivityModel(name, true, "", List.of(outcomes), null, null);
    }

    private static FlowNodeModel node(String name) {
        return new FlowNodeModel(name, 0, 0);
    }

    // ---- the corpus -------------------------------------------------------------------------------------

    /** Nothing declared: the shape a game bot is created in before the user adds anything. */
    private static ProjectModel bare() {
        return ProjectModel.empty();
    }

    /** One activity, nothing wired — the flow falls back to declaration order. */
    private static ProjectModel oneActivity() {
        return ProjectModel.of(List.of(activity("Mining")), List.of());
    }

    /** A branch, a loop back to the start, an unrouted outcome that ends the run, and an orphan. */
    private static ProjectModel wired() {
        FlowModel flow = new FlowModel(
                List.of(node("Mining"), node("Selling"), node("Lonely")),
                List.of(new FlowEdgeModel("Mining", "Selling", "BAG_FULL"),
                        new FlowEdgeModel("Mining", "Mining", "NEXT"),
                        new FlowEdgeModel("Selling", "Mining", "NEXT"),
                        new FlowEdgeModel("Mining", "Nowhere", "STALE")),
                "Mining", 50, 0);
        return ProjectModel.of(
                        List.of(activity("Mining", "BAG_FULL", "NO_ORE"),
                                activity("Selling").withGoHome(false).withPopupCheck(false),
                                activity("Lonely")),
                        List.of())
                .withFlow(flow);
    }

    /**
     * Every storable type, scalar and list, with values chosen to be awkward: blank, unparseable, and text
     * carrying the three characters that would break a literal or a comment.
     */
    private static ProjectModel everyType() {
        List<VariableModel> variables = new ArrayList<>();
        for (ValueType type : ValueType.all()) {
            variables.add(new VariableModel("scalar" + type.name(), ValueChoice.of(type),
                    List.of(sample(type)), "a \"quoted\" one */ with a fence", "", Visibility.PUBLIC,
                    List.of(), Range.NONE));
            variables.add(VariableModel.of("blank" + type.name(), ValueChoice.of(type), List.of()));
            variables.add(VariableModel.of("junk" + type.name(), ValueChoice.of(type),
                    List.of("not a " + type.name())));
            variables.add(VariableModel.of("list" + type.name(),
                    new ValueChoice(type, ValueShape.OPEN_LIST), List.of(sample(type), sample(type))));
            variables.add(VariableModel.of("empty" + type.name(),
                    new ValueChoice(type, ValueShape.OPEN_LIST), List.of()));
        }
        return ProjectModel.of(List.of(activity("Mining")), variables);
    }

    /** One plausible stored value per type, in the wire spelling the editor writes. */
    private static String sample(ValueType type) {
        return switch (type) {
            case TEXT -> "a \"quoted\"\ttab\\slash";
            case YES_NO -> "true";
            case WHOLE_NUMBER -> "3";
            case DECIMAL_NUMBER -> "0.75";
            case CHARACTER -> "'";
            case COLOR -> "#3366FF";
            case DATE -> "2026-08-25";
            case TIME_OF_DAY -> "07:30:15";
            case DURATION -> "1h30m";
            case IMAGE_TEMPLATE -> "ore";
            case PRECISION -> "12.5,4,2";
            case POINT -> "3,4";
            case RECT -> "1,2,3,4";
            case SIZE -> "800,600";
            case DIRECTION -> "SOUTH";
            case KEY -> "SPACE";
            case MOUSE_BUTTON -> "LEFT";
        };
    }

    // ---- the guarantee ----------------------------------------------------------------------------------

    @Test
    void everyProjectInTheCorpusCompiles(@TempDir Path dir) throws IOException {
        List<ProjectModel> corpus = List.of(bare(), oneActivity(), wired(), everyType());
        for (int i = 0; i < corpus.size(); i++) {
            Path root = dir.resolve("case" + i);
            Map<String, String> sources = Authoring.sources(V, spec(), corpus.get(i),
                    List.of("ore", "gold_ore", "Mixed-Case"));
            assertFalse(sources.isEmpty(), "a game bot with nothing in it still has files");
            compile(root, sources);
        }
    }

    /**
     * An empty project gets exactly two files, and never regenerates.
     *
     * <p>A hello-world entry point, because a project with no {@code .java} at all is one the editor cannot
     * open — it has no main source file to show; and {@code Templates}, because the images folder exists from
     * the first moment and the first vision block dropped into that entry point has to name a constant that
     * resolves. Neither is a <em>model</em> file: an empty project has no activities and no parameters, which
     * is why {@code regenerate} still answers nothing however much is passed to it.
     */
    @Test
    void anEmptyProjectGetsAnEntryPointAndTemplatesAndNothingElse(@TempDir Path dir) throws IOException {
        ProjectSpec empty = new ProjectSpec("MyBot", "com.mybot", "MyBot", ProjectSpec.Kind.EMPTY, "1.2.0",
                new Size(0, 0));
        Map<String, String> sources = Authoring.sources(V, empty, oneActivity(), List.of("ore"));
        assertEquals(List.of("src/main/java/com/mybot/MyBot.java",
                        "src/main/java/com/mybot/Templates.java"),
                List.copyOf(sources.keySet()));
        assertTrue(Authoring.regenerate(V, empty, oneActivity(), List.of()).isEmpty());
        compile(dir, sources);
    }

    /**
     * The trap this whole design walks into if anyone "tidies" the emitter: a {@code static final boolean}
     * with a constant initialiser is a JLS §4.12.4 constant variable, javac folds it, and a user's
     * {@code while (Activities.Mining) { … }} becomes an <em>unreachable statement</em> — a compile error
     * caused by unticking a box. So the flag is emitted without {@code final}, and the proof is a file that
     * loops on a flag stored as {@code false}.
     */
    @Test
    void aFlagThatIsOffDoesNotMakeTheUsersLoopUnreachable(@TempDir Path dir) throws IOException {
        ProjectModel model = ProjectModel.of(
                List.of(activity("Mining").withEnabled(false)), List.of());
        Map<String, String> sources = new java.util.LinkedHashMap<>(
                Authoring.sources(V, spec(), model, List.of()));

        assertTrue(sources.get("src/main/java/com/mybot/Activities.java")
                .contains("public static boolean Mining = false;"), "the flag must not be final");

        sources.put("src/main/java/com/mybot/UserCode.java", """
                package com.mybot;

                public final class UserCode {
                    public static void loop() {
                        while (Activities.Mining) {
                            Activities.Mining = false;
                        }
                    }

                    private UserCode() {}
                }
                """);
        compile(dir, sources);
    }

    /** A value is a literal in the source, not a string the bot parses when it starts. */
    @Test
    void aValueIsBakedIntoTheSourceRatherThanParsedAtStartup() {
        ProjectModel model = ProjectModel.of(List.of(), List.of(
                VariableModel.of("REST", ValueChoice.of(ValueType.DURATION), List.of("1h30m")),
                VariableModel.of("HOTKEYS", ValueChoice.listOf(ValueType.KEY), List.of("SPACE", "ESCAPE"))));

        String parameters = Authoring.regenerate(V, spec(), model, List.of())
                .get("src/main/java/com/mybot/Parameters.java");

        assertNotNull(parameters);
        assertTrue(parameters.contains(
                        "public static final java.time.Duration REST = java.time.Duration.ofMillis(5400000L);"),
                parameters);
        assertTrue(parameters.contains(
                        "public static final java.util.List<Key> HOTKEYS = "
                                + "java.util.List.of(Key.SPACE, Key.ESCAPE);"),
                parameters);
        assertTrue(parameters.contains("import com.botmaker.sdk.api.interaction.Key;"),
                "an SDK type used in a field has to be imported");
        assertFalse(parameters.contains("Wire."), "the parser call is gone, and so is Wire");
    }

    /** Regeneration touches five files and none of the ones a user owns. */
    @Test
    void regenerationTouchesOnlyTheFilesItOwns() {
        Map<String, String> regenerated = Authoring.regenerate(V, spec(), wired(), List.of());

        assertEquals(List.of("src/main/java/com/mybot/Activities.java",
                        "src/main/java/com/mybot/Parameters.java",
                        "src/main/java/com/mybot/Templates.java",
                        "src/main/java/com/mybot/ActivityRegistry.java",
                        "src/main/java/com/mybot/FlowDriver.java"),
                List.copyOf(regenerated.keySet()));
    }

    /** An orphan is not instantiated, and still has a stub and a flag so the project keeps compiling. */
    @Test
    void anOrphanKeepsItsStubAndItsFlagButIsNotRegistered() {
        Map<String, String> sources = Authoring.sources(V, spec(), wired(), List.of());

        assertTrue(sources.containsKey("src/main/java/com/mybot/activities/Lonely.java"));
        assertTrue(sources.get("src/main/java/com/mybot/Activities.java").contains("boolean Lonely"));
        assertFalse(sources.get("src/main/java/com/mybot/ActivityRegistry.java").contains("LONELY"));
    }

    // ---- compiling --------------------------------------------------------------------------------------

    /**
     * Writes the sources under {@code root} and compiles them against this build's own classes.
     *
     * <p>The classpath is the test's own, which is what makes this test worth having: {@code target/classes}
     * is on it, so the generated file is checked against the API in this working tree rather than against
     * whatever was last published.
     */
    private static void compile(Path root, Map<String, String> sources) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Map.Entry<String, String> file : sources.entrySet()) {
            Path target = root.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            if (file.getKey().endsWith(".java")) files.add(target);
        }

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "this test needs a JDK, not a JRE");
        DiagnosticCollector<JavaFileObject> problems = new DiagnosticCollector<>();
        Path classes = Files.createDirectories(root.resolve("classes"));
        try (StandardJavaFileManager files1 = javac.getStandardFileManager(problems, null,
                StandardCharsets.UTF_8)) {
            boolean ok = javac.getTask(null, files1, problems,
                    List.of("-classpath", System.getProperty("java.class.path"),
                            "-d", classes.toString(), "-Xlint:all"),
                    null, files1.getJavaFileObjectsFromPaths(files)).call();

            StringBuilder report = new StringBuilder();
            for (var problem : problems.getDiagnostics()) report.append(problem).append('\n');
            assertTrue(ok, () -> "the generated project did not compile:\n" + report);
            assertEquals("", report.toString().strip(),
                    "a generated file must be -Xlint:all clean — a warning in code nobody wrote is noise "
                            + "the user cannot act on");
        }
    }
}
