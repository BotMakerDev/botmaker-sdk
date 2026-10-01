package com.botmaker.sdk.plugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The standard plugin package tree ({@code docs/refactor/34-plugin-package-tree.md}), held by a source scan in
 * the style of Studio's {@code StudioSourcesTest}.
 *
 * <ul>
 *   <li>{@code com.botmaker.sdk} has three children and no others: {@code api}, {@code internal} and
 *       {@code plugin}. A fourth is how {@code authoring} happened.</li>
 *   <li><b>Rule 1:</b> {@code api} and {@code internal} never name a {@code plugin} class. A bot loads both and
 *       must never link the Studio half.</li>
 *   <li><b>Rule 2:</b> only {@code plugin} names JavaFX or {@code botmaker-plugin-toolkit}. A bot has neither on
 *       its classpath at run time.</li>
 *   <li><b>Rule 3:</b> no two packages under {@code plugin} name each other.</li>
 *   <li><b>Rule 4</b> (2026-10-01): {@code api} is what a bot writes, in packages named by what a bot does, and
 *       no public {@code api} signature names an {@code internal} type — a bot could not write the type down,
 *       so a call that took or returned one would be a block nobody can fill.</li>
 * </ul>
 *
 * <p>Only code is scanned: a comment line may name anything, so a javadoc explaining why {@code api} does not
 * touch JavaFX does not trip the rule it explains.
 */
class PluginLayersTest {

    private static final Path ROOT = Path.of("src/main/java/com/botmaker/sdk");

    /** What the bot half must never name, and why it matters at run time. */
    private static final List<String> STUDIO_ONLY = List.of(
            "com.botmaker.sdk.plugin.", "javafx.", "com.botmaker.plugin.toolkit.");

    @Test
    void theSdkHasExactlyTheThreeLayers() throws IOException {
        Set<String> children = new TreeSet<>();
        try (Stream<Path> list = Files.list(ROOT)) {
            list.filter(Files::isDirectory).forEach(p -> children.add(p.getFileName().toString()));
        }
        assertEquals(Set.of("api", "internal", "plugin"), children);
    }

    @Test
    void theBotHalfNamesNothingOfTheStudioHalf() {
        List<String> offences = new ArrayList<>();
        for (String layer : List.of("api", "internal")) {
            for (Path file : sources(ROOT.resolve(layer))) {
                List<String> lines = read(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).strip();
                    if (isComment(line)) continue;
                    for (String name : STUDIO_ONLY) {
                        if (line.contains(name)) {
                            offences.add(ROOT.relativize(file) + ":" + (i + 1) + " names " + name + "*");
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), offences, "api/ and internal/ run inside a bot; only plugin/ may name these");
    }

    /**
     * Rule 3: no two packages under {@code plugin} name each other. A package is the first segment below
     * {@code plugin} ({@code pilot.ui} is {@code pilot}); a pair that depends both ways is one feature in two
     * places, which is how {@code screen} and {@code source} were until 2026-09-23.
     */
    @Test
    void noTwoPluginPackagesNameEachOther() {
        Path plugin = ROOT.resolve("plugin");
        Map<String, Set<String>> uses = new TreeMap<>();
        for (Path file : sources(plugin)) {
            Path relative = plugin.relativize(file);
            if (relative.getNameCount() < 2) continue;
            String from = relative.getName(0).toString();
            for (String line : read(file)) {
                String code = line.strip();
                if (isComment(code)) continue;
                Matcher named = PLUGIN_PACKAGE.matcher(code);
                while (named.find()) {
                    if (!named.group(1).equals(from)) uses.computeIfAbsent(from, k -> new TreeSet<>()).add(named.group(1));
                }
            }
        }
        List<String> cycles = new ArrayList<>();
        uses.forEach((from, targets) -> targets.forEach(to -> {
            if (from.compareTo(to) < 0 && uses.getOrDefault(to, Set.of()).contains(from)) {
                cycles.add(from + " <-> " + to);
            }
        }));
        assertEquals(List.of(), cycles, "a package under plugin/ depends one way or not at all");
    }

    /** Rule 4: the packages a bot's Java imports from, each named by what a bot does with it. */
    @Test
    void apiIsNamedByWhatABotDoes() throws IOException {
        Set<String> packages = new TreeSet<>();
        try (Stream<Path> list = Files.list(ROOT.resolve("api"))) {
            list.filter(Files::isDirectory).forEach(p -> packages.add(p.getFileName().toString()));
        }
        assertEquals(new TreeSet<>(Set.of("bot", "capture", "console", "flow", "geometry", "input", "random",
                "sound", "text", "time", "vision")), packages);
    }

    /** Rule 4: no public {@code api} member takes, returns or exposes an {@code internal} type. */
    @Test
    void noApiSignatureNamesAnInternalType() throws ClassNotFoundException {
        List<String> offences = new ArrayList<>();
        for (Path file : sources(ROOT.resolve("api"))) {
            String name = "com.botmaker.sdk." + ROOT.relativize(file).toString()
                    .replace(java.io.File.separatorChar, '.').replaceAll("\\.java$", "");
            for (Class<?> type : withNested(Class.forName(name))) {
                if (!java.lang.reflect.Modifier.isPublic(type.getModifiers())) continue;
                for (java.lang.reflect.Method m : type.getDeclaredMethods()) {
                    if (exposed(m.getModifiers()) && !m.isSynthetic()) {
                        check(offences, type, m.getName(), m.getGenericReturnType());
                        for (java.lang.reflect.Type p : m.getGenericParameterTypes()) check(offences, type, m.getName(), p);
                    }
                }
                for (java.lang.reflect.Constructor<?> c : type.getDeclaredConstructors()) {
                    if (exposed(c.getModifiers())) {
                        for (java.lang.reflect.Type p : c.getGenericParameterTypes()) check(offences, type, "<init>", p);
                    }
                }
                for (java.lang.reflect.Field f : type.getDeclaredFields()) {
                    if (exposed(f.getModifiers())) check(offences, type, f.getName(), f.getGenericType());
                }
                if (type.getGenericSuperclass() != null) check(offences, type, "extends", type.getGenericSuperclass());
                for (java.lang.reflect.Type i : type.getGenericInterfaces()) check(offences, type, "implements", i);
            }
        }
        assertEquals(List.of(), offences, "an api signature a bot cannot write down");
    }

    private static List<Class<?>> withNested(Class<?> type) {
        List<Class<?>> all = new ArrayList<>(List.of(type));
        for (Class<?> nested : type.getDeclaredClasses()) all.addAll(withNested(nested));
        return all;
    }

    private static boolean exposed(int modifiers) {
        return java.lang.reflect.Modifier.isPublic(modifiers) || java.lang.reflect.Modifier.isProtected(modifiers);
    }

    private static void check(List<String> offences, Class<?> owner, String member, java.lang.reflect.Type type) {
        if (type instanceof Class<?> c) {
            if (c.isArray()) check(offences, owner, member, c.getComponentType());
            else if (c.getName().startsWith("com.botmaker.sdk.internal.")) {
                offences.add(owner.getName() + "." + member + " names " + c.getName());
            }
        } else if (type instanceof java.lang.reflect.ParameterizedType p) {
            check(offences, owner, member, p.getRawType());
            for (java.lang.reflect.Type arg : p.getActualTypeArguments()) check(offences, owner, member, arg);
        } else if (type instanceof java.lang.reflect.GenericArrayType a) {
            check(offences, owner, member, a.getGenericComponentType());
        } else if (type instanceof java.lang.reflect.WildcardType w) {
            for (java.lang.reflect.Type bound : w.getUpperBounds()) check(offences, owner, member, bound);
            for (java.lang.reflect.Type bound : w.getLowerBounds()) check(offences, owner, member, bound);
        }
    }

    private static final Pattern PLUGIN_PACKAGE =Pattern.compile("com\\.botmaker\\.sdk\\.plugin\\.([a-z][a-z0-9]*)\\.");

    private static boolean isComment(String line) {
        return line.startsWith("//") || line.startsWith("*") || line.startsWith("/*");
    }

    private static List<Path> sources(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> read(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
