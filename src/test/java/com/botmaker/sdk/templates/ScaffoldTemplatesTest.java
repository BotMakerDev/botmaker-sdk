package com.botmaker.sdk.templates;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The scaffold Studio writes into a generated bot lives here, in {@code src/templates/java}, as Java that
 * this module's own compiler reads. This test is what makes that arrangement worth the trouble.
 *
 * <p>The templates are compiled by the build (pass 3, see the pom), so renaming {@code Watchdog#checkpoint}
 * breaks <em>this</em> build on the line that calls it. Nothing below has to check that they compile; it
 * already happened.
 *
 * <h2>What is left for a test</h2>
 *
 * <ol>
 *   <li><b>The manifest describes the templates that are actually shipped.</b> Studio reads the manifest, not
 *       the directory listing, so a template with no record is a file nobody extracts and a record with no
 *       file is a refusal at project creation.
 *   <li><b>Every fence is a matched pair, exactly once.</b> A hole opened twice is data written twice; an
 *       unpaired fence is a fill that runs to the end of the file.
 *   <li><b>What ships is plain Java.</b> {@code @Template} lives in the source and is taken back out on the
 *       way into the jar — a Studio already in the field cannot strip what it has never heard of.
 * </ol>
 *
 * <p>There was a third rule, and it is gone (2026-08-25): the templates used to be checked, out of their own
 * constant pools, against a {@code @Scaffolding} annotation on every SDK member they reached. That annotation
 * existed to make a rename here a declared fact over in {@code botmaker-studio}, which co-authored these
 * files. The SDK becomes the generator instead, so there is no second author to warn.
 */
class ScaffoldTemplatesTest {

    /** The package the templates are written in, and the only one Studio rewrites. */
    private static final String TEMPLATE_PACKAGE = "com.botmaker.sdk.templates";

    /** Where the templates ship: this directory inside the jar, alongside its manifest. */
    private static final String TEMPLATE_ROOT = "botmaker-templates";

    /** The manifest shape this test reads. Must track {@code TemplateProcessor.FORMAT}. */
    private static final String FORMAT = "3";

    /** A fence: {@code /*<STUDIO:FLOW>*}{@code /} captures {@code FLOW}. */
    private static final Pattern OPEN = Pattern.compile("/\\*<STUDIO:([A-Z_]+)>\\*/");
    private static final Pattern CLOSE = Pattern.compile("/\\*</STUDIO:([A-Z_]+)>\\*/");

    /** One {@code template} record of the manifest. */
    private record Template(String role, String kind, String path, String target) {}

    private static Path classes;
    private static Path templateClasses;
    private static List<Template> templates;
    private static String declaredPackage;

    @BeforeAll
    static void readEverything() {
        classes = mainClasses();
        templateClasses = classes.resolveSibling("template-classes");
        templates = manifest();
    }

    // ------------------------------------------------------------------
    // 1 — the manifest and the files agree
    // ------------------------------------------------------------------

    @Test
    void theManifestDescribesEveryTemplateThatShipsAndNoOther() {
        List<String> bad = new ArrayList<>();

        if (!TEMPLATE_PACKAGE.equals(declaredPackage)) {
            bad.add("the manifest's `package` line says " + declaredPackage + ", but the templates are written "
                    + "in " + TEMPLATE_PACKAGE + " — Studio rewrites that prefix, so a wrong one leaves the "
                    + "generated files in the SDK's package");
        }

        Set<String> described = new LinkedHashSet<>();
        for (Template t : templates) {
            described.add(t.path());
            if (!Files.isRegularFile(templateFile(t))) {
                bad.add(t.role() + " names " + t.path() + ", which is not in " + TEMPLATE_ROOT + "/");
            }
            if (!"SEED".equals(t.kind()) && !"REGENERATED".equals(t.kind())) {
                bad.add(t.role() + " has kind " + t.kind() + "; it is SEED or REGENERATED");
            }
        }

        for (Path java : shippedTemplates()) {
            String relative = classes.resolve(TEMPLATE_ROOT).relativize(java).toString().replace('\\', '/');
            if (!described.contains(relative)) {
                bad.add(relative + " ships in the jar but no manifest record names it — Studio reads the "
                        + "manifest, not the directory, so nothing would ever extract it");
            }
        }

        assertEmpty(bad, "the templates and their manifest disagree",
                "The manifest is generated from the @Template annotations — fix the annotation on the "
                        + "template, not a text file.");
    }

    // ------------------------------------------------------------------
    // 2 — every fence is a matched pair, exactly once, and what ships is plain Java
    // ------------------------------------------------------------------

    @Test
    void everyFenceIsOneMatchedPairAndTheShippedTextIsPlainJava() {
        List<String> bad = new ArrayList<>();
        for (Template t : templates) {
            String source = read(templateFile(t));
            List<String> opens = all(OPEN, source);
            List<String> closes = all(CLOSE, source);

            for (String name : new TreeSet<>(opens)) {
                long o = opens.stream().filter(name::equals).count();
                long c = closes.stream().filter(name::equals).count();
                if (o != 1 || c != 1) {
                    bad.add(t.path() + ": " + name + " appears as " + o + " opening and " + c + " closing "
                            + "fence; it must be exactly one of each");
                }
            }
            for (String name : new TreeSet<>(closes)) {
                if (!opens.contains(name)) bad.add(t.path() + ": " + name + " closes without opening");
            }
            // What ships must be plain Java. @Template lives in the SOURCE and is taken back out on the way
            // into the jar, because a Studio already in the field cannot strip what it has never heard of —
            // it would write the annotation straight into somebody's bot.
            if (source.contains("@Template") || source.contains("templates.meta")) {
                bad.add(t.path() + " still carries its @Template declaration in the text that ships. "
                        + "TemplateProcessor strips it; that it is here means the strip missed this file, "
                        + "and every bot generated from it would fail to compile.");
            }
        }
        assertEmpty(bad, "a template's fences are not matched pairs",
                """
                A hole is an opening fence, a default that compiles, and a closing fence:
                    private static final int MAX_STEPS = /*<STUDIO:MAX_STEPS>*/ 1000 /*</STUDIO:MAX_STEPS>*/;
                To fill one, Studio replaces fence to fence; to ignore one, it does nothing and the default
                stands.""");
    }

    /** Sanity: a template tree that silently compiled to nothing would pass every rule above. */
    @Test
    void theTemplatesWereCompiled() {
        List<Path> compiled = compiledTemplates();
        assertFalse(compiled.isEmpty(),
                "no class files under " + templateClasses + " — the compile-templates execution did not run, "
                        + "which would make the checks above vacuously true");
        assertTrue(compiled.size() >= templates.size(),
                "the build compiled " + compiled.size() + " template classes for " + templates.size()
                        + " templates; some template did not compile into " + templateClasses);
    }

    // ------------------------------------------------------------------
    // the manifest
    // ------------------------------------------------------------------

    private static List<Template> manifest() {
        Path file = classes.resolve(TEMPLATE_ROOT).resolve("manifest.txt");
        if (!Files.isRegularFile(file)) {
            fail("no " + file + " — the templates are not reaching the jar. It is generated from the "
                    + "@Template annotations by com.botmaker.sdk.apt.TemplateProcessor, which the "
                    + "compile-templates execution in botmaker-sdk/pom.xml runs.");
        }
        List<Template> out = new ArrayList<>();
        for (String raw : read(file).lines().toList()) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] c = line.split("\\s+");
            switch (c[0]) {
                case "format" -> {
                    if (!FORMAT.equals(c[1])) {
                        fail("manifest format " + c[1] + " is not what this test reads (" + FORMAT + ")");
                    }
                }
                case "package" -> declaredPackage = c[1];
                case "template" -> {
                    if (c.length != 5) {
                        fail("a `template` record takes 4 columns (ROLE KIND path target), got "
                                + (c.length - 1) + ": " + line);
                    }
                    out.add(new Template(c[1], c[2], c[3], c[4]));
                }
                default -> fail("unknown manifest record `" + c[0] + "`: " + line);
            }
        }
        if (out.isEmpty()) fail("the manifest declares no templates");
        return out;
    }

    private static Path templateFile(Template t) {
        return classes.resolve(TEMPLATE_ROOT).resolve(t.path());
    }

    /** Every {@code .java} shipped under {@code botmaker-templates/}, whatever the manifest says about it. */
    private static List<Path> shippedTemplates() {
        return walk(classes.resolve(TEMPLATE_ROOT), ".java");
    }

    private static List<Path> compiledTemplates() {
        return walk(templateClasses, ".class");
    }

    private static List<Path> walk(Path root, String suffix) {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(suffix)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + root, e);
        }
    }

    // ------------------------------------------------------------------
    // plumbing
    // ------------------------------------------------------------------

    /** Where this module's compiled output is — asked of a class that is unambiguously part of it. */
    private static Path mainClasses() {
        try {
            return Path.of(com.botmaker.sdk.api.meta.ReplacedBy.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate target/classes", e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private static List<String> all(Pattern pattern, String text) {
        List<String> out = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static void assertEmpty(List<String> bad, String headline, String explanation) {
        if (bad.isEmpty()) return;
        fail(headline + "\n" + explanation + "\n  " + String.join("\n  ", bad));
    }
}
