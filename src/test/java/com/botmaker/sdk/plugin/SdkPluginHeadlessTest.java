package com.botmaker.sdk.plugin;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SdkPlugin} constructs on a classpath with no JavaFX on it.
 *
 * <h2>The defect this holds shut</h2>
 *
 * <p>Until 2026-09-05 the constructor called {@code Editors.pickWith(new SdkScreenPicks())}. Both of those
 * types are JavaFX-typed, {@code javafx-controls} is {@code optional} in this module's pom, and
 * <b>{@code optional} means not transitive</b> — so a host resolving the published SDK as a dependency got a
 * classpath with no JavaFX, and {@code ServiceLoader} died constructing the only plugin on it:
 *
 * <pre>
 * ServiceConfigurationError: Provider com.botmaker.sdk.plugin.SdkPlugin could not be instantiated
 *   Caused by: NoClassDefFoundError: javafx/scene/Node
 * </pre>
 *
 * <p>{@code PluginLoader} catches that, correctly — a classpath with no loadable plugin is an ordinary state
 * — so nothing crashed and nothing failed to compile. It was found by the plugin registry's gate, on this
 * plugin's own submission.
 *
 * <h2>Why it could not be found here before</h2>
 *
 * <p>An {@code optional} dependency <em>is</em> on this module's own classpath; it is only absent from a
 * consumer's. So every test in this module — and {@code botmaker validate} run against this working copy —
 * resolves JavaFX and cannot see the failure. This test builds the consumer's classpath instead: every entry
 * of {@code java.class.path} except the JavaFX jars, under a loader whose parent is the platform loader, so
 * nothing leaks in from the application loader that would defeat the point.
 *
 * <h2>The rule</h2>
 *
 * <p><b>Constructing a plugin must not link an optional dependency.</b> A headless host — the CLI's
 * {@code validate} and {@code run}, the plugin registry's CI — is a legitimate host, and it is the one that
 * decides whether a plugin may be published at all. Anything JavaFX-shaped belongs behind a {@code build…}
 * hook, which is where the catalog, the value types, the parameters and now the editors are.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SdkPluginHeadlessTest {

    @Test
    void the_plugin_constructs_with_no_javafx_on_the_classpath() throws Exception {
        try (URLClassLoader headless = withoutJavaFx()) {
            Class<?> plugin = Class.forName(SdkPlugin.class.getName(), true, headless);

            Object instance = plugin.getDeclaredConstructor().newInstance();

            assertEquals(SdkPlugin.ID, plugin.getMethod("id").invoke(instance));
            assertEquals("BotMaker SDK", plugin.getMethod("displayName").invoke(instance));
        }
    }

    /**
     * The surfaces a headless host actually asks for, on that same classpath.
     *
     * <p>These are what the registry's gate calls after it has loaded the plugin — the id check, the palette
     * check and the value-type check — so each one linking only JDK and contract types is the difference
     * between a submission that passes and one that reports {@code nothing loaded} four times over.
     */
    @Test
    void the_surfaces_a_headless_host_asks_for_answer_there_too() throws Exception {
        try (URLClassLoader headless = withoutJavaFx()) {
            Class<?> plugin = Class.forName(SdkPlugin.class.getName(), true, headless);
            Object instance = plugin.getDeclaredConstructor().newInstance();

            assertNotNull(plugin.getMethod("catalog", String.class).invoke(instance, "v1.2.0"));
            assertNotNull(plugin.getMethod("valueTypes").invoke(instance));
            assertNotNull(plugin.getMethod("parameters", String.class).invoke(instance, "v1.2.0"));
            assertNotNull(plugin.getMethod("toolbarItems").invoke(instance));
        }
    }

    /**
     * The other half of the assertion: JavaFX really is absent from that loader.
     *
     * <p>Without this the test above would keep passing if the filter ever stopped matching — which is
     * exactly how a test of an absence rots.
     */
    @Test
    void the_headless_classpath_really_has_no_javafx() throws Exception {
        try (URLClassLoader headless = withoutJavaFx()) {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("javafx.scene.Node", false, headless));
        }
    }

    /**
     * Every classpath entry except the JavaFX jars, parented to the platform loader.
     *
     * <p>The platform loader rather than the application loader, because the application loader <em>is</em>
     * this test's classpath: parenting to it would make every filtered jar reachable again through
     * delegation, and the test would pass by loading the very classes it is supposed to have removed.
     */
    private static URLClassLoader withoutJavaFx() throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (!Path.of(entry).getFileName().toString().startsWith("javafx")) {
                urls.add(Path.of(entry).toUri().toURL());
            }
        }
        assertTrue(urls.size() > 1, "the classpath was not split into entries");
        return new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
    }
}
