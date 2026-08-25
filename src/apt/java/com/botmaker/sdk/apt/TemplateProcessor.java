package com.botmaker.sdk.apt;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads {@code @Template} off the scaffold templates and writes the two things Studio consumes: the template
 * text, with the annotation taken back out, and the generated {@code manifest.txt}.
 *
 * <h2>Why a processor and not a test</h2>
 *
 * <p>The manifest used to be hand-written, and a hand-written manifest is a second place to be right: a
 * template could be added and never listed, or a role misspelled, and none of it was visible until a project
 * was generated. Generated from the annotations there is one source of truth and it is compiled.
 *
 * <h2>It reads the source file off disk</h2>
 *
 * <p>What ships is text, not the compiled class, so the shipped file has to come from the source — read back
 * from {@code -Abotmaker.templates.src}, by the path the type's own qualified name implies.
 *
 * <h2>Why it writes the templates too</h2>
 *
 * <p>The obvious arrangement — the resources plugin copies {@code src/templates/java} verbatim and this only
 * generates the manifest — would ship {@code @Template(…)} inside the text Studio extracts, and from there
 * into a generated bot, which does not compile. Studio could strip it, but that would be a <em>released</em>
 * Studio's job for jars that do not exist yet: every Studio already in the field would write the annotation
 * into somebody's project. Stripping it here means what ships is exactly the plain Java that has always
 * shipped, and no Studio has to learn anything.
 *
 * <h2>Options</h2>
 *
 * <ul>
 *   <li>{@code -Abotmaker.templates.src=<dir>} — the templates' source root. Absent ⇒ nothing is checked and
 *       nothing is written, so an IDE compile of this module does not fail on a path it was never given.</li>
 *   <li>{@code -Abotmaker.templates.out=<dir>} — where to write the templates and the manifest. Absent ⇒
 *       checked but not written.</li>
 * </ul>
 *
 * <p>Like {@link ApiPointerProcessor} this runs before (and over) code it must not reference, so
 * {@code @Template} is matched by name and read out of {@link AnnotationMirror}s by hand.
 */
@SupportedAnnotationTypes("com.botmaker.sdk.templates.meta.Template")
@SupportedOptions({TemplateProcessor.SRC_OPTION, TemplateProcessor.OUT_OPTION})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class TemplateProcessor extends AbstractProcessor {

    static final String SRC_OPTION = "botmaker.templates.src";
    static final String OUT_OPTION = "botmaker.templates.out";

    /** The manifest shape this writes. Bumped when the columns change — Studio refuses a format it cannot read. */
    private static final int FORMAT = 3;

    /** The import this processor adds nothing to and takes back out on the way to the jar. */
    private static final String META_IMPORT = "import com.botmaker.sdk.templates.meta.Template;";

    /** One template, as its annotation declares it. Sorted by id so the manifest is byte-stable. */
    private record Declared(String id, String kind, String path, String target, String source) {}

    private final Map<String, Declared> declared = new TreeMap<>();
    private String templatePackage = "";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        String src = processingEnv.getOptions().get(SRC_OPTION);
        if (round.processingOver()) {
            if (src != null) writeOutputs();
            return false;
        }
        for (Element e : round.getElementsAnnotatedWith(
                processingEnv.getElementUtils().getTypeElement("com.botmaker.sdk.templates.meta.Template"))) {
            if (src != null) collect((TypeElement) e, Path.of(src));
        }
        return false;
    }

    // ------------------------------------------------------------------
    // reading one template
    // ------------------------------------------------------------------

    private void collect(TypeElement type, Path srcRoot) {
        String fqn = processingEnv.getElementUtils().getBinaryName(type).toString();
        String relative = fqn.replace('.', '/') + ".java";
        Path file = srcRoot.resolve(relative);
        if (!Files.isRegularFile(file)) {
            error(type, "@Template is on " + fqn + " but " + file + " does not exist. The processor reads the "
                    + "fences out of the source, so the file has to be where its package says it is.");
            return;
        }
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            error(type, "could not read " + file + ": " + e.getMessage());
            return;
        }

        AnnotationMirror mirror = annotation(type);
        String id = string(mirror, "id");
        String kind = enumConstant(mirror, "kind");
        String target = string(mirror, "target");
        if (id == null || kind == null || target == null) return;   // javac has already said what is missing

        Declared previous = declared.putIfAbsent(id, new Declared(id, kind, relative, target, source));
        if (previous != null) {
            error(type, "two templates both declare id \"" + id + "\" (" + previous.path() + " and "
                    + relative + "). Studio asks for a template by that name, so it has to name one file.");
            return;
        }
        int lastDot = fqn.lastIndexOf('.');
        String pkg = lastDot < 0 ? "" : fqn.substring(0, lastDot);
        // The declared package is the templates' ROOT package, which Studio rewrites to the bot's own. A
        // template in a sub-package (activities/) must not narrow it, so the shortest one wins.
        if (templatePackage.isEmpty() || pkg.length() < templatePackage.length()) templatePackage = pkg;
    }

    // ------------------------------------------------------------------
    // writing what ships
    // ------------------------------------------------------------------

    private void writeOutputs() {
        String out = processingEnv.getOptions().get(OUT_OPTION);
        if (out == null || declared.isEmpty()) return;
        Path root = Path.of(out);
        try {
            Files.createDirectories(root);
            for (Declared t : declared.values()) {
                Path file = root.resolve(t.path());
                Files.createDirectories(file.getParent());
                Files.writeString(file, strip(t.source()), StandardCharsets.UTF_8);
            }
            Files.writeString(root.resolve("manifest.txt"), manifest(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the scaffold templates into " + root, e);
        }
    }

    /**
     * The template as it ships: the {@code @Template(…)} annotation and its import taken out, and nothing
     * else touched.
     *
     * <p>The annotation is matched from {@code @Template} to the parenthesis that closes it, counting depth,
     * so a member whose value contains parentheses cannot stop the cut in the middle of one. Everything else
     * about the file — the javadoc, the blank lines, the fences — is left exactly as written, since this text
     * is what a bot's own source is made from.
     */
    static String strip(String source) {
        String withoutImport = source.lines()
                .filter(line -> !line.strip().equals(META_IMPORT))
                .reduce(new StringBuilder(), (sb, line) -> sb.append(line).append('\n'), StringBuilder::append)
                .toString();
        int at = withoutImport.indexOf("@Template(");
        if (at < 0) return withoutImport;
        int depth = 0;
        int i = withoutImport.indexOf('(', at);
        for (; i < withoutImport.length(); i++) {
            char c = withoutImport.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) break;
        }
        int end = i + 1;
        while (end < withoutImport.length() && (withoutImport.charAt(end) == '\n' || withoutImport.charAt(end) == ' ')) {
            end++;
        }
        return withoutImport.substring(0, at) + withoutImport.substring(end);
    }

    private String manifest() {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                # BotMaker scaffold templates — manifest. GENERATED from the @Template annotations on the
                # templates themselves; do not edit. See com.botmaker.sdk.apt.TemplateProcessor.
                #
                # Studio generates a bot's scaffold by extracting these files from the SDK jar it is pinned to
                # and filling in the holes. The SDK owns the frame (the imports, the class shape, the walk, the
                # config reader); Studio owns only what is true about one project. Neither side holds a copy of
                # the other's half.
                #
                # ---------------------------------------------------------------------------------------------
                # A hole is a pair of fenced comments with a compiling default between them:
                #
                #     private static final int MAX_STEPS = /*<STUDIO:MAX_STEPS>*/ 1000 /*</STUDIO:MAX_STEPS>*/;
                #
                # To fill one, replace everything from the opening fence to the closing fence, inclusive. To
                # ignore one, do nothing — the default stands and the file still compiles. A NEWER SDK may add
                # holes an older Studio has never heard of, and an older Studio leaves them at their defaults.
                #
                # Either way the fences themselves are dropped on the way out: what a bot's source carries is the
                # value or the default, never the marker.
                #
                # The defaults are not placeholders. Together they form one small bot that compiles inside the
                # SDK's own build, so every call Studio will write into a generated file is compiled and checked
                # here first.
                #
                # ---------------------------------------------------------------------------------------------
                # Records, one per line:
                #
                #   format   <n>                             this file's shape; bumped when columns change
                #   package  <fqn>                           the templates' own package, which Studio rewrites
                #   template <ROLE> <KIND> <path> <target>   one template
                #
                # KIND is SEED (written once at creation, the user's thereafter) or REGENERATED (rewritten on
                # every model change, never the user's). `target` is the file name in the bot, with ${CLASS} the
                # project's main class and ${ACTIVITY} an activity's name.
                # ---------------------------------------------------------------------------------------------

                """);
        sb.append("format ").append(FORMAT).append('\n');
        sb.append("package ").append(templatePackage).append("\n\n");
        int idWidth = declared.keySet().stream().mapToInt(String::length).max().orElse(1);
        int kindWidth = declared.values().stream().mapToInt(t -> t.kind().length()).max().orElse(1);
        int pathWidth = declared.values().stream().mapToInt(t -> t.path().length()).max().orElse(1);
        for (Declared t : declared.values()) {
            sb.append("template ").append(pad(t.id(), idWidth)).append(' ')
                    .append(pad(t.kind(), kindWidth)).append(' ')
                    .append(pad(t.path(), pathWidth)).append(' ')
                    .append(t.target().strip())
                    .append('\n');
        }
        return sb.toString();
    }

    private static String pad(String text, int width) {
        return text + " ".repeat(width - text.length());
    }

    // ------------------------------------------------------------------
    // reading the mirror
    // ------------------------------------------------------------------

    private AnnotationMirror annotation(Element e) {
        for (AnnotationMirror m : e.getAnnotationMirrors()) {
            if (((TypeElement) m.getAnnotationType().asElement()).getQualifiedName().toString()
                    .equals("com.botmaker.sdk.templates.meta.Template")) {
                return m;
            }
        }
        return null;
    }

    private String string(AnnotationMirror mirror, String member) {
        Object value = value(mirror, member);
        return value == null ? null : value.toString();
    }

    private String enumConstant(AnnotationMirror mirror, String member) {
        Object value = value(mirror, member);
        return value instanceof VariableElement v ? v.getSimpleName().toString() : null;
    }

    /** Explicit values and defaults alike. */
    private Object value(AnnotationMirror mirror, String member) {
        if (mirror == null) return null;
        Map<? extends ExecutableElement, ? extends AnnotationValue> values =
                processingEnv.getElementUtils().getElementValuesWithDefaults(mirror);
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(member)) return entry.getValue().getValue();
        }
        return null;
    }

    private void error(Element e, String message) {
        error(e, null, message);
    }

    private void error(Element e, AnnotationMirror mirror, String message) {
        if (mirror == null) processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, e);
        else processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, e, mirror);
    }
}
