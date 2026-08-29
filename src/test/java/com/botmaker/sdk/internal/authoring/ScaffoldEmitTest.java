package com.botmaker.sdk.internal.authoring;

import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.authoring.ActivityModel;
import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.FlowEdgeModel;
import com.botmaker.sdk.authoring.FlowModel;
import com.botmaker.sdk.authoring.FlowNodeModel;
import com.botmaker.sdk.authoring.ProjectModel;
import com.botmaker.sdk.authoring.ProjectSpec;
import com.botmaker.sdk.authoring.SdkVersion;
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
 * <h2>What this test lost, and why that is the point</h2>
 *
 * <p>It used to carry a corpus of every storable type with awkward values, because the emitter wrote
 * <em>literals</em> into {@code Parameters} and a quote, a backslash or a comment fence in a user's text
 * would have produced a file that did not compile. It also held the trap about {@code Activities}' missing
 * {@code final}, and two assertions about the shape of the generated {@code FlowDriver} table.
 *
 * <p>None of those files exist. A project's values, flags, pictures and flow are read at run time now, so
 * there is no longer any user data anywhere in this output — which means the corpus that mattered has
 * collapsed to "with activities and without". <b>A generator that cannot be handed unsafe input is the
 * outcome the whole change was for</b>, and this test getting smaller is what that looks like.
 *
 * <p>Nothing here asserts the <em>text</em> of a file beyond the two lines that carry a decision. Compiling
 * it is the assertion; a golden-file comparison would fail on every javadoc edit and prove nothing about the
 * file working.
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

    /** One activity, nothing wired. */
    private static ProjectModel oneActivity() {
        return ProjectModel.of(List.of(activity("Mining")), List.of());
    }

    /**
     * A branch, a loop back to the start, an unrouted outcome that ends the run, and an orphan.
     *
     * <p>The wiring no longer reaches the emitted source at all — it is read from {@code activities.json} at
     * run time — so what this case still checks is that it <em>does not</em>: a stub whose activity has three
     * outcomes and two wires is the same file as one with none, plus two enum constants.
     */
    private static ProjectModel wired() {
        FlowModel flow = new FlowModel(
                List.of(node("Mining"), node("Selling"), node("Lonely")),
                List.of(new FlowEdgeModel("Mining", "Selling", "BAG_FULL"),
                        new FlowEdgeModel("Mining", "Mining", "NEXT"),
                        new FlowEdgeModel("Mining", "Selling", FlowEdgeModel.DISABLED_OUTCOME),
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
     * A name and a description carrying the characters that would end a javadoc comment or a string literal.
     *
     * <p>The description is the last piece of user text that still reaches emitted source — it becomes the
     * stub's class javadoc — so it is the last thing that can produce a file that does not compile.
     */
    private static ProjectModel awkwardText() {
        return ProjectModel.of(
                List.of(activity("Mining").withDescription("a \"quoted\" one */ with a fence \\ and a slash")),
                List.of());
    }

    // ---- the guarantee ----------------------------------------------------------------------------------

    @Test
    void everyProjectInTheCorpusCompiles(@TempDir Path dir) throws IOException {
        List<ProjectModel> corpus = List.of(bare(), oneActivity(), wired(), awkwardText());
        for (int i = 0; i < corpus.size(); i++) {
            Map<String, String> sources = Authoring.sources(V, spec(), corpus.get(i));
            assertFalse(sources.isEmpty(), "a game bot with nothing in it still has files");
            compile(dir.resolve("case" + i), sources);
        }
    }

    /**
     * An empty project gets exactly one file.
     *
     * <p>A hello-world entry point, because a project with no {@code .java} at all is one the editor cannot
     * open — it has no main source file to show. It used to get {@code Templates} as well, so that the first
     * vision block dropped into that entry point named a constant that resolved; a picture is named by its
     * file now ({@code Wire.image("ore")}), so there is no constant to resolve and nothing to keep in step.
     */
    @Test
    void anEmptyProjectGetsAnEntryPointAndNothingElse(@TempDir Path dir) throws IOException {
        ProjectSpec empty = new ProjectSpec("MyBot", "com.mybot", "MyBot", ProjectSpec.Kind.EMPTY, "1.2.0",
                new Size(0, 0));

        Map<String, String> sources = Authoring.sources(V, empty, oneActivity());

        assertEquals(List.of("src/main/java/com/mybot/MyBot.java"), List.copyOf(sources.keySet()));
        compile(dir, sources);
    }

    /** A game bot's whole output: three seeds plus one stub per activity, and nothing derived. */
    @Test
    void aGameBotGetsItsSeedsAndOneStubPerActivity() {
        assertEquals(List.of("src/main/java/com/mybot/MyBot.java",
                        "src/main/java/com/mybot/GoHome.java",
                        "src/main/java/com/mybot/Popups.java",
                        "src/main/java/com/mybot/activities/Mining.java",
                        "src/main/java/com/mybot/activities/Selling.java",
                        "src/main/java/com/mybot/activities/Lonely.java"),
                List.copyOf(Authoring.sources(V, spec(), wired()).keySet()));
    }

    /**
     * The activity's tick is read, not compiled in.
     *
     * <p>This is the line that replaced the whole {@code Activities} class, and with it the trap that class
     * carried: {@code public static final boolean MINING = false;} is a JLS §4.12.4 constant variable, javac
     * folds it into every use site, and a user's own {@code while (Activities.MINING) { … }} then became an
     * <em>unreachable statement</em> — a compile error caused by unticking a box. A method call cannot fold,
     * so the trap is gone by construction rather than by remembering to leave off a {@code final}.
     */
    @Test
    void anActivityReadsItsOwnTickRatherThanAGeneratedField() {
        String mining = Authoring.sources(V, spec(), wired())
                .get("src/main/java/com/mybot/activities/Mining.java");

        assertTrue(mining.contains("return Wire.enabled(\"Mining\");"), mining);
        assertFalse(mining.contains("Activities"), "there is no generated flags class to import any more");
    }

    /**
     * The entry point hands the loader its own class, and nothing else about the flow.
     *
     * <p>{@code MyBot.class} is the only thing in a generated project that says where the project's package
     * is, which is what lets an activity be found at {@code <package>.activities.<Name>} with no manifest to
     * keep in step. Everything else the old {@code FlowDriver} held — the start node, the routes, the step
     * budget, the pause — is in {@code activities.json}.
     */
    @Test
    void theEntryPointNamesItselfAndLetsTheSdkReadTheRest() {
        String main = Authoring.sources(V, spec(), wired()).get("src/main/java/com/mybot/MyBot.java");

        assertTrue(main.contains("FlowGraph.run(MyBot.class, GoHome.INSTANCE::execute)"), main);
        assertFalse(main.contains("FlowDriver"), "the generated driver is gone");
    }

    /**
     * {@code DISABLED} is still not an enum constant.
     *
     * <p>An activity can never <em>report</em> being disabled — it did not run — so it is one slot on the
     * node rather than an outcome, and that was true when the table was generated and stays true now that it
     * is read. The stub is where it would show up if anyone confused the two.
     */
    @Test
    void disabledIsNeverSomethingAnActivityCanReturn() {
        assertFalse(Authoring.sources(V, spec(), wired())
                        .get("src/main/java/com/mybot/activities/Mining.java").contains("DISABLED"),
                "the Outcome enum must not gain a constant no run() could ever return");
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
