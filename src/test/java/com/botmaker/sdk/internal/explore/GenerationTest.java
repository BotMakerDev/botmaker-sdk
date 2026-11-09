package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bot's classes loaded again in the run's JVM: a new generation sees the fix, a broken one changes nothing. */
class GenerationTest {

    private static final String SHOP = """
            package demo;

            import com.botmaker.sdk.api.screens.Screen;
            import com.botmaker.sdk.api.screens.Setup;

            public final class Shop extends Screen {
                %s
                @Override
                public Setup setup() {
                    return Setup.named("Shop");
                }
            }
            """;
    private static final String ADDED = "public final com.botmaker.sdk.api.screens.Button ok = "
            + "com.botmaker.sdk.api.screens.Button.of(new com.botmaker.sdk.api.vision.ImageTemplate(\"images/ok.png\")).free();";

    @TempDir
    Path project;
    private Generation first;

    @BeforeEach
    void compileTheBot() throws IOException {
        write("demo/Sdk.java", "package demo;\n\npublic final class Sdk {}\n");
        write("demo/Shop.java", SHOP.formatted(""));
        write("demo/Gone.java", "package demo;\n\npublic final class Gone {}\n");
        Path classes = Files.createDirectories(project.resolve("target/classes"));
        String[] args = {"-d", classes.toString(), "-classpath", Generation.classPath(project, null),
                project.resolve("src/main/java/demo/Sdk.java").toString(),
                project.resolve("src/main/java/demo/Shop.java").toString(),
                project.resolve("src/main/java/demo/Gone.java").toString()};
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args));
        ClassLoader app = new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
        first = Generation.first(List.of("demo.Sdk"), app);
    }

    private void write(String file, String source) throws IOException {
        Path path = project.resolve("src/main/java").resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
    }

    @Test
    void nextSeesAFieldAddedToAScreen() throws Exception {
        write("demo/Shop.java", SHOP.formatted(ADDED));
        Generation next = assertInstanceOf(Generation.Ok.class, first.next(project, 1)).generation();

        Class<?> shop = next.loader().loadClass("demo.Shop");
        assertTrue(Arrays.stream(shop.getFields()).anyMatch(field -> field.getName().equals("ok")));
        assertFalse(Arrays.stream(first.loader().loadClass("demo.Shop").getFields())
                .anyMatch(field -> field.getName().equals("ok")), "the old generation keeps its own classes");
        assertEquals(project.resolve("target/botmaker-explore/gen-1"), next.classes());
        assertEquals(1, next.number());
        assertEquals("demo.Sdk", next.holders()[0].getName());
        assertSame(next.loader(), next.holders()[0].getClassLoader());
    }

    @Test
    void compileErrorIsRefusedAndOldGenerationStays() throws Exception {
        write("demo/Shop.java", SHOP.formatted("public final int broken = ;"));
        Generation.Refused refused = assertInstanceOf(Generation.Refused.class, first.next(project, 1));

        assertTrue(refused.errors().getFirst().startsWith("Shop.java:"), refused.errors().toString());
        assertEquals("demo.Shop", first.loader().loadClass("demo.Shop").getName(), "the old one still loads");
        assertEquals(0, first.number());
    }

    @Test
    void aDeletedClassIsGone() throws Exception {
        Files.delete(project.resolve("src/main/java/demo/Gone.java"));
        Generation next = assertInstanceOf(Generation.Ok.class, first.next(project, 1)).generation();

        assertThrows(ClassNotFoundException.class, () -> next.loader().loadClass("demo.Gone"),
                "the run's old class folder is still on the parent; it never serves the bot's classes");
        assertEquals("demo.Gone", first.loader().loadClass("demo.Gone").getName());
        assertNull(next.loader().getResource("demo/Gone.class"));
    }

    @Test
    void aNumberInUseIsRefused() {
        Generation next = assertInstanceOf(Generation.Ok.class, first.next(project, 1)).generation();

        assertInstanceOf(Generation.Refused.class, next.next(project, 1), "its folder serves the classes in use");
        assertInstanceOf(Generation.Refused.class, next.next(project, 0));
    }

    @Test
    void sdkStaticsAreShared() throws Exception {
        ScreenGraph held = ScreenGraph.of(Map.of());
        ScreenMaps.use(held);
        try {
            Generation next = assertInstanceOf(Generation.Ok.class, first.next(project, 2)).generation();
            Class<?> shop = next.loader().loadClass("demo.Shop");

            assertSame(Screen.class, shop.getSuperclass(), "the SDK comes from the parent");
            assertSame(ScreenMaps.class, next.loader().loadClass(ScreenMaps.class.getName()));
            assertSame(held, ScreenMaps.installed());
            assertNotSame(first.loader().loadClass("demo.Shop"), shop);
        } finally {
            ScreenMaps.use(null);
        }
    }
}
