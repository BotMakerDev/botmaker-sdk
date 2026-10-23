package com.botmaker.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bot shaped as Studio writes one ({@code src/test/resources/samplebot}) still compiles against this SDK: an
 * {@code api.*} change that breaks a bot fails here, in the SDK's own tests (2026-10-23, in place of the
 * templates build). A break that is meant fixes the sample in the same change.
 */
class SampleBotCompileTest {

    @Test
    void theSampleBotCompiles(@TempDir Path out) throws IOException, URISyntaxException {
        Path root = Path.of(SampleBotCompileTest.class.getResource("/samplebot").toURI());
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(root)) {
            sources = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        assertFalse(sources.isEmpty(), "no sample bot sources under " + root);

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean compiled;
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of("-classpath", System.getProperty("java.class.path"),
                    "-d", out.toString(), "-proc:none");
            compiled = javac.getTask(null, files, diagnostics, options, null,
                    files.getJavaFileObjectsFromPaths(sources)).call();
        }
        String errors = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> (d.getSource() == null ? "" : root.relativize(Path.of(d.getSource().toUri())) + ":"
                        + d.getLineNumber() + ": ") + d.getMessage(Locale.ROOT))
                .collect(Collectors.joining("\n"));
        assertTrue(compiled, "the sample bot no longer compiles against this SDK:\n" + errors);
    }
}
