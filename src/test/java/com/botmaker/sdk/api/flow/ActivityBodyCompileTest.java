package com.botmaker.sdk.api.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The point of an activity being its outcome enum (2026-10-10): javac, not the flow editor, refuses a body
 * that reports another activity's outcome, and a wire whose outcome is not its activity's is still legal Java
 * that simply leaves that activity.
 */
class ActivityBodyCompileTest {

    private static final String ACTIVITIES = """
            package bot;
            import com.botmaker.sdk.api.bot.Outcome;
            public final class Activities {
                public enum Collect implements Outcome { NEXT, NOTHING_LEFT }
                public enum Battle implements Outcome { NEXT, WON }
            }
            """;

    private static boolean compiles(Path dir, String flowBody) throws IOException {
        Path src = Files.createDirectories(dir.resolve("bot"));
        Files.writeString(src.resolve("Activities.java"), ACTIVITIES);
        Files.writeString(src.resolve("Bodies.java"), """
                package bot;
                import com.botmaker.sdk.api.flow.Flow;
                import java.util.List;
                public final class Bodies {
                    public static Activities.Collect collect() { return Activities.Collect.NOTHING_LEFT; }
                    public static Activities.Battle battle() { return Activities.Battle.WON; }
                    static Object flow() { return %s; }
                }
                """.formatted(flowBody));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjects(src.resolve("Activities.java"),
                    src.resolve("Bodies.java"));
            List<String> options = List.of("-classpath", System.getProperty("java.class.path"),
                    "-d", Files.createDirectories(dir.resolve("out")).toString(), "-proc:none");
            return javac.getTask(null, files, diagnostics, options, null, units).call();
        }
    }

    @Test
    void aBodyReturningItsOwnActivitysEnumCompiles(@TempDir Path dir) throws IOException {
        assertTrue(compiles(dir, "Flow.activity(Activities.Collect.class, Bodies::collect)"));
    }

    @Test
    void aBodyReturningAnotherActivitysEnumDoesNot(@TempDir Path dir) throws IOException {
        assertFalse(compiles(dir, "Flow.activity(Activities.Collect.class, Bodies::battle)"));
    }

    @Test
    void anActivityIsAnOutcomeEnumAndNothingElse(@TempDir Path dir) throws IOException {
        assertFalse(compiles(dir, "Flow.activity(String.class, Bodies::collect)"));
        assertTrue(compiles(dir, "Flow.edge(Activities.Collect.NOTHING_LEFT, Activities.Battle.class)"));
        assertFalse(compiles(dir, "Flow.edge(Activities.Collect.NOTHING_LEFT, String.class)"));
    }
}
