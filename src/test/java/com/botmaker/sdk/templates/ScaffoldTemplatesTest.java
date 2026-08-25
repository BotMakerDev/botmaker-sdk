package com.botmaker.sdk.templates;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * <h2>What it replaces</h2>
 *
 * <p>The scaffold used to be text blocks inside {@code botmaker-studio}. A text block cannot be asked what it
 * names, so answering "which SDK members does Studio write into generated files?" took a 484-line JDT visitor
 * over the generators' output, a declaration to compare it against, a committed
 * {@code scaffolding-surface.txt} carried across two repositories that cannot read each other, and a rule in
 * {@code ApiPointersTest} to read it back. All of that reconstructed, by parsing, the answer a compiler gives
 * away — and it could only ever be as current as the last person to regenerate the file.
 *
 * <p>Now the question is answered by {@code javac}. The templates are compiled by the build (pass 3, see the
 * pom), so renaming {@code Watchdog#checkpoint} breaks <em>this</em> build on the line that calls it. Nothing
 * below has to check that they compile; it already happened.
 *
 * <h2>What is left for a test</h2>
 *
 * <ol>
 *   <li><b>The manifest describes the templates that are actually shipped.</b> Studio reads the manifest, not
 *       the directory listing, so a template with no record is a file nobody extracts and a record with no
 *       file is a refusal at project creation.
 *   <li><b>Every declared token is a matched pair of fences, exactly once.</b> A token declared and absent is
 *       project data with nowhere to go; a token present twice is data written twice; an unpaired fence is a
 *       fill that runs to the end of the file.
 *   <li><b>The templates call only {@link com.botmaker.sdk.api.meta.Scaffolding} members.</b> This is the one
 *       the compiler cannot answer, because it is not about whether the call resolves — it is about whether
 *       the author of the member <em>knows</em> it is scaffolding. A generated file is regenerated, not
 *       migrated, so a defaulted value inside one is a broken feature rather than a repair, and the
 *       annotation is what tells an SDK author at the declaration that renaming this needs a Studio release
 *       too.
 * </ol>
 *
 * <p>Only that last direction is checked, deliberately: the templates may not call an unannotated member, but
 * an annotated member no template calls is <em>not</em> yet an error. Studio's old generators are still
 * emitting the previous scaffold until they are switched over, so the annotation set is still covering both.
 * The reverse rule belongs with their deletion, not here.
 *
 * <h2>Reading the calls out of bytecode rather than the source</h2>
 *
 * <p>Two things are read: the template <em>text</em> as it will ship (out of {@code target/classes}, so what
 * is checked is what a bot's Studio will extract) and the template <em>classes</em> the build compiled from
 * it. The call list comes from the second, straight out of each class file's constant pool, because that is
 * the resolved truth — a source scan would have to re-implement imports, static imports, wildcards, method
 * references and inherited members, which is the very re-parsing this whole change exists to delete.
 */
class ScaffoldTemplatesTest {

    private static final String SCAFFOLDING = "com.botmaker.sdk.api.meta.Scaffolding";

    /** The package the templates are written in, and the only one Studio rewrites. */
    private static final String TEMPLATE_PACKAGE = "com.botmaker.sdk.templates";

    /** Where the templates ship: this directory inside the jar, alongside its manifest. */
    private static final String TEMPLATE_ROOT = "botmaker-templates";

    /** A fence, name and generation together: {@code /*<STUDIO:FLOW:1>*}{@code /} captures {@code FLOW:1}. */
    private static final Pattern OPEN = Pattern.compile("/\\*<STUDIO:([A-Z_]+:\\d+)>\\*/");
    private static final Pattern CLOSE = Pattern.compile("/\\*</STUDIO:([A-Z_]+:\\d+)>\\*/");

    /** A fence that forgot its generation — matched only so the failure can name it rather than ignore it. */
    private static final Pattern UNGENERATIONED = Pattern.compile("/\\*</?STUDIO:([A-Z_]+)>\\*/");

    /** One {@code template} record of the manifest. */
    private record Template(String role, String kind, String path, String target, List<String> tokens) {}

    private static Path classes;
    private static Path templateClasses;
    private static List<Template> templates;
    private static String declaredPackage;

    /** Types carrying {@code @Scaffolding}; every member of one is scaffolding. */
    private static Set<String> scaffoldingTypes;

    /** Individually annotated members, as {@code fqn#name(arity)} for methods and {@code fqn#name} for fields. */
    private static Set<String> scaffoldingMembers;

    private static ScanResult scan;

    @BeforeAll
    static void readEverything() {
        classes = mainClasses();
        templateClasses = classes.resolveSibling("template-classes");
        templates = manifest();
        scan = new ClassGraph().enableClassInfo().enableMethodInfo().enableFieldInfo().enableAnnotationInfo()
                .overrideClasspath(classes.toString()).scan();
        collectScaffolding();
    }

    @AfterAll
    static void close() {
        if (scan != null) scan.close();
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
    // 2 — every declared token is a matched pair, exactly once
    // ------------------------------------------------------------------

    @Test
    void everyDeclaredTokenIsOneMatchedPairOfFencesAndNothingElseIsFenced() {
        List<String> bad = new ArrayList<>();
        for (Template t : templates) {
            String source = read(templateFile(t));
            List<String> opens = all(OPEN, source);
            List<String> closes = all(CLOSE, source);

            for (String token : t.tokens()) {
                long o = opens.stream().filter(token::equals).count();
                long c = closes.stream().filter(token::equals).count();
                if (o != 1 || c != 1) {
                    bad.add(t.path() + ": " + token + " appears as " + o + " opening and " + c + " closing "
                            + "fence" + (o == 0 && c == 0 ? " — the manifest declares it but the file has none"
                            : "; it must be exactly one of each"));
                }
            }
            for (String token : new TreeSet<>(opens)) {
                if (!t.tokens().contains(token)) {
                    bad.add(t.path() + " is fenced for " + token + ", which its manifest record does not "
                            + "declare — Studio would never fill it, so the default would ship as the bot's");
                }
            }
            for (String token : new TreeSet<>(closes)) {
                if (!opens.contains(token)) bad.add(t.path() + ": " + token + " closes without opening");
            }
            for (String token : new TreeSet<>(all(UNGENERATIONED, source))) {
                bad.add(t.path() + " is fenced for " + token + " with no generation. Studio matches a hole "
                        + "on name AND generation, exactly, so a fence without one is a hole nothing can "
                        + "fill — write /*<STUDIO:" + token + ":1>*/ … /*</STUDIO:" + token + ":1>*/");
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
        assertEmpty(bad, "a template's fences do not match its manifest record",
                """
                A hole is an opening fence, a default that compiles, and a closing fence, with the
                generation of that hole's shape in both:
                    private static final int MAX_STEPS = /*<STUDIO:MAX_STEPS:1>*/ 1000 /*</STUDIO:MAX_STEPS:1>*/;
                and declared on the class as @Template(holes = {"MAX_STEPS:1", …}).""");
    }

    // ------------------------------------------------------------------
    // 3 — the templates call only scaffolding members
    // ------------------------------------------------------------------

    @Test
    void theTemplatesCallOnlyScaffoldingMembers() {
        List<String> bad = new ArrayList<>();
        Map<String, Set<String>> unannotated = new LinkedHashMap<>();
        Set<String> checked = new LinkedHashSet<>();

        for (Path clazz : compiledTemplates()) {
            for (Ref ref : refsOf(clazz)) {
                if (!ref.owner().startsWith("com.botmaker.sdk.")) continue;
                if (ref.owner().startsWith(TEMPLATE_PACKAGE)) continue;      // the templates' own types
                checked.add(ref.key());
                if (scaffoldingTypes.contains(ref.owner())) continue;        // whole type is scaffolding
                if (scaffoldingMembers.contains(ref.key())) continue;
                unannotated.computeIfAbsent(ref.key(), k -> new LinkedHashSet<>())
                        .add(clazz.getFileName().toString());
            }
        }
        unannotated.forEach((ref, from) -> bad.add(ref + "  — called from " + String.join(", ", from)));

        // Before trusting a pass: a constant-pool reader that returned nothing, or a class tree that did not
        // compile, would satisfy every line above without checking one call. The scaffold cannot plausibly
        // touch fewer members than this — the entry point alone reaches four.
        assertTrue(checked.size() >= 10,
                "only " + checked.size() + " SDK members were read out of the templates' constant pools ("
                        + checked + ") — that is too few for the scaffold, so this rule is not actually "
                        + "looking at the calls it claims to check");

        assertEmpty(bad, "a scaffold template calls an SDK member that is not @Scaffolding",
                """
                Studio writes these templates into a bot's source, and a generated file is regenerated rather
                than migrated — so renaming one of these members is not a bot's problem to fix, it is a Studio
                release. @Scaffolding is what says so at the declaration, where the rename gets typed.

                Annotate the member (or its whole type, if every member of it is written into generated code),
                or stop calling it from the template.""");
    }

    /** Sanity: a template tree that silently compiled to nothing would pass every rule above. */
    @Test
    void theTemplatesWereCompiled() {
        List<Path> compiled = compiledTemplates();
        assertFalse(compiled.isEmpty(),
                "no class files under " + templateClasses + " — the compile-templates execution did not run, "
                        + "which would make the call check vacuously true");
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
                    if (!"2".equals(c[1])) {
                        fail("manifest format " + c[1] + " is not what this test reads (2)");
                    }
                }
                case "package" -> declaredPackage = c[1];
                case "template" -> {
                    if (c.length != 6) {
                        fail("a `template` record takes 5 columns (ROLE KIND path target tokens), got "
                                + (c.length - 1) + ": " + line);
                    }
                    List<String> tokens = "-".equals(c[5]) ? List.of() : List.of(c[5].split(","));
                    out.add(new Template(c[1], c[2], c[3], c[4], tokens));
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
    // the @Scaffolding set
    // ------------------------------------------------------------------

    private static void collectScaffolding() {
        scaffoldingTypes = new LinkedHashSet<>();
        scaffoldingMembers = new LinkedHashSet<>();
        for (ClassInfo ci : scan.getAllClasses()) {
            if (ci.hasAnnotation(SCAFFOLDING)) scaffoldingTypes.add(ci.getName());
            for (MethodInfo mi : ci.getDeclaredMethodAndConstructorInfo()) {
                if (mi.hasAnnotation(SCAFFOLDING)) {
                    scaffoldingMembers.add(ci.getName() + "#" + mi.getName()
                            + "(" + mi.getParameterInfo().length + ")");
                }
            }
            for (FieldInfo fi : ci.getDeclaredFieldInfo()) {
                if (fi.hasAnnotation(SCAFFOLDING)) scaffoldingMembers.add(ci.getName() + "#" + fi.getName());
            }
        }
        if (scaffoldingTypes.isEmpty() && scaffoldingMembers.isEmpty()) {
            fail("the scan found no @Scaffolding at all in " + classes + ", which would make the call check "
                    + "fail for every template call rather than checking anything");
        }
    }

    // ------------------------------------------------------------------
    // reading calls out of a class file's constant pool
    // ------------------------------------------------------------------

    /**
     * One member a template refers to. {@code key} is {@code fqn#name(arity)} for a method or constructor and
     * {@code fqn#name} for a field — the same spelling {@link #scaffoldingMembers} is collected in.
     */
    private record Ref(String owner, String key) {}

    /**
     * Every {@code Fieldref}/{@code Methodref}/{@code InterfaceMethodref} in one class file.
     *
     * <p>The constant pool is read directly rather than through a bytecode library, because it is the only
     * thing wanted here and the format is fixed: a handful of tag shapes to skip past. Reading the pool alone
     * also catches what a method-body walk would miss — a method reference like {@code Wire::key} reaches the
     * pool through a {@code MethodHandle} and never appears as a call instruction.
     */
    private static List<Ref> refsOf(Path classFile) {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(classFile))) {
            if (in.readInt() != 0xCAFEBABE) throw new IOException("not a class file: " + classFile);
            in.readUnsignedShort();                     // minor
            in.readUnsignedShort();                     // major
            int count = in.readUnsignedShort();

            String[] utf8 = new String[count];
            int[][] refs = new int[count][];            // [classIndex, nameAndTypeIndex] for member refs
            int[] classNameIndex = new int[count];
            int[][] nameAndType = new int[count][];     // [nameIndex, descriptorIndex]

            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8[i] = readUtf8(in);
                    case 7 -> classNameIndex[i] = in.readUnsignedShort();
                    case 9, 10, 11 -> refs[i] = new int[]{in.readUnsignedShort(), in.readUnsignedShort()};
                    case 12 -> nameAndType[i] = new int[]{in.readUnsignedShort(), in.readUnsignedShort()};
                    case 8, 16, 19, 20 -> in.readUnsignedShort();
                    case 3, 4, 17, 18 -> in.readInt();
                    case 15 -> in.skipBytes(3);
                    case 5, 6 -> {
                        in.readLong();
                        i++;                            // a long or double eats the next slot
                    }
                    default -> throw new IOException("unknown constant pool tag " + tag + " in " + classFile);
                }
            }

            List<Ref> out = new ArrayList<>();
            for (int i = 1; i < count; i++) {
                if (refs[i] == null) continue;
                String owner = utf8[classNameIndex[refs[i][0]]].replace('/', '.');
                int[] nt = nameAndType[refs[i][1]];
                String name = utf8[nt[0]];
                String descriptor = utf8[nt[1]];
                out.add(new Ref(owner, descriptor.startsWith("(")
                        ? owner + "#" + name + "(" + arity(descriptor) + ")"
                        : owner + "#" + name));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + classFile, e);
        }
    }

    private static String readUtf8(DataInputStream in) throws IOException {
        byte[] bytes = new byte[in.readUnsignedShort()];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * The declared parameter count of a method descriptor. A varargs tail is one parameter here — the
     * descriptor spells it as the array it really is — which is the count {@code @Scaffolding} is collected
     * with, so the two agree without either side knowing how many arguments a call passed.
     */
    private static int arity(String descriptor) {
        int n = 0;
        int i = 1;                                      // past '('
        while (descriptor.charAt(i) != ')') {
            while (descriptor.charAt(i) == '[') i++;
            if (descriptor.charAt(i) == 'L') i = descriptor.indexOf(';', i);
            i++;
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------
    // plumbing
    // ------------------------------------------------------------------

    /** Where this module's compiled output is — asked of a class that is unambiguously part of it. */
    private static Path mainClasses() {
        try {
            return Path.of(com.botmaker.sdk.api.meta.Scaffolding.class.getProtectionDomain()
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
