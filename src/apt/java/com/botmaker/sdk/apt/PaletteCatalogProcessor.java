package com.botmaker.sdk.apt;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes the palette catalog from the annotations on the facades themselves.
 *
 * <h2>What it replaces</h2>
 *
 * <p>Until 2026-08-26 the catalog was a hand-written class per released SDK version — 620 lines of
 * {@code .<ImageTemplateGroup, CaptureSource, Double>add(ImageFinder::findCompare)}, where the type witnesses
 * existed only to pick an overload and read as noise, and where a method <em>added</em> to a facade stayed out
 * of the menus until somebody remembered to name it. That default is the wrong way round: the silent outcome
 * should be the rare one.
 *
 * <p>So curation moved onto the members it curates. This processor reads
 * {@code com.botmaker.plugin.api.palette.Facade} off the classes and emits one
 * {@code com.botmaker.sdk.internal.plugin.catalog.Catalog}, whose body is
 * {@code .facade(X.class, …).addAll().order(…)} — one block per facade, nothing naming a member. Deleting a
 * method can therefore no longer make the catalog lie: there is nothing in it to go stale.
 *
 * <h2>It reads mirrors, not classes</h2>
 *
 * <p>Same constraint and same convention as {@link ApiPointerProcessor}: this tree is compiled before
 * {@code src/main/java}, so annotations are matched by fully qualified name and read out of
 * {@link AnnotationMirror}s. Defaults matter here — unlike the pointer rules, an omitted {@code icon} is
 * simply an absent icon — so values are read through
 * {@link javax.lang.model.util.Elements#getElementValuesWithDefaults}.
 *
 * <h2>Member order comes from the source, not from reflection</h2>
 *
 * <p>{@code CatalogBuilder.addAll()} runs at class-initialisation time against a {@code Class<?>}, and
 * {@link Class#getDeclaredMethods()} is documented to return members in no particular order. Declaration order
 * — which is the order the facade's author actually chose, and the order a menu should read in — exists only
 * here, in the compiler's view of the source. So the processor emits it as an explicit
 * {@code .order("moveTo", "click", …)} and {@code addAll()} applies it.
 *
 * <h2>Validation</h2>
 *
 * <p>Every string element is checked, because the point of the old method references was that a typo failed
 * the build and that guarantee has to survive: a blank or malformed category, an unknown role, two facades
 * disagreeing about one category's label, a label on a member nothing offers, two overloads of one name
 * labelled differently. All are errors on the annotation, in this build.
 *
 * <p><b>One thing it cannot check</b>, and it is worth knowing: on a partial recompile it would see only the
 * facades in this round and emit a catalog missing the rest. It declines to emit at all when it collected
 * none, which covers the common case; beyond that the guard is that Maven recompiles the whole module
 * whenever anything in it changed. Build with {@code clean} if a catalog ever looks short.
 */
@SupportedAnnotationTypes({
        PaletteCatalogProcessor.FACADE,
        PaletteCatalogProcessor.NOT_IN_PALETTE,
        PaletteCatalogProcessor.PALETTE_DEFAULT,
        PaletteCatalogProcessor.PALETTE_LABEL,
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class PaletteCatalogProcessor extends AbstractProcessor {

    static final String FACADE = "com.botmaker.plugin.api.palette.Facade";
    static final String NOT_IN_PALETTE = "com.botmaker.plugin.api.palette.NotInPalette";
    static final String PALETTE_DEFAULT = "com.botmaker.plugin.api.palette.PaletteDefault";
    static final String PALETTE_LABEL = "com.botmaker.plugin.api.palette.PaletteLabel";

    /** The class this processor writes. Named by {@code SdkPlugin}, which is compiled in the same pass. */
    private static final String GENERATED_PACKAGE = "com.botmaker.sdk.internal.plugin.catalog";
    private static final String GENERATED_CLASS = "Catalog";

    /** The {@code FacadeRole} constants, spelled here because the enum itself is not referenced. */
    private static final Set<String> ROLES = Set.of("MENU", "HIDDEN", "VALUE");

    /** One facade's resolved annotation, gathered across rounds and emitted once at the end. */
    private record Facade(String qualifiedName, String categoryId, String categoryLabel, String icon,
                          String label, String role, int order, List<String> memberOrder) {
    }

    private final List<Facade> facades = new ArrayList<>();
    /** category id → the one non-blank label given for it, and the facade that gave it. */
    private final Map<String, String[]> categoryLabels = new TreeMap<>();
    private boolean emitted;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            String name = annotation.getQualifiedName().toString();
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                switch (name) {
                    case FACADE -> collectFacade(element);
                    case NOT_IN_PALETTE, PALETTE_DEFAULT, PALETTE_LABEL -> checkMemberAnnotation(element, name);
                    default -> { }
                }
            }
        }
        if (roundEnv.processingOver()) {
            checkNames();
            emit();
        }
        // Claim nothing: another processor in the same pass (ApiPointerProcessor) is looking at its own
        // annotations, and these are not among them either way.
        return false;
    }

    // ---------------------------------------------------------------- collection

    private void collectFacade(Element element) {
        if (!(element instanceof TypeElement type)) {
            return;
        }
        AnnotationMirror mirror = mirror(element, FACADE);
        if (mirror == null) {
            return;
        }
        String categoryId = string(mirror, "category");
        if (categoryId.isBlank()) {
            error(element, mirror, "@Facade needs a category id — the menu group this facade is filed under");
            return;
        }
        String role = string(mirror, "role");
        if (!ROLES.contains(role)) {
            error(element, mirror, "@Facade role must be one of " + ROLES + ", not '" + role + "'");
            return;
        }
        String categoryLabel = string(mirror, "categoryLabel");
        if (!categoryLabel.isBlank()) {
            String[] claimed = categoryLabels.get(categoryId);
            if (claimed != null && !claimed[0].equals(categoryLabel)) {
                error(element, mirror, "category '" + categoryId + "' is labelled '" + categoryLabel
                        + "' here and '" + claimed[0] + "' on " + claimed[1]
                        + " — one menu group cannot have two names");
                return;
            }
            categoryLabels.put(categoryId, new String[]{categoryLabel, type.getQualifiedName().toString()});
        }

        facades.add(new Facade(type.getQualifiedName().toString(), categoryId, categoryLabel,
                string(mirror, "icon"), string(mirror, "label"), role, integer(mirror, "order"),
                memberOrder(type)));
    }

    /**
     * The distinct names of the public methods this type declares, in source order — which is the whole reason
     * this runs at compile time rather than being reflected at runtime.
     */
    private List<String> memberOrder(TypeElement type) {
        // A declined name is declined whole, so the marks are gathered before the order is built: an
        // exclusion written on the third overload must still remove the name its first overload introduced.
        Set<String> declined = new LinkedHashSet<>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD && mirror(enclosed, NOT_IN_PALETTE) != null) {
                declined.add(enclosed.getSimpleName().toString());
            }
        }
        Set<String> names = new LinkedHashSet<>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD
                    || !enclosed.getModifiers().contains(Modifier.PUBLIC)
                    || declined.contains(enclosed.getSimpleName().toString())) {
                continue;
            }
            names.add(enclosed.getSimpleName().toString());
        }
        return List.copyOf(names);
    }

    // ---------------------------------------------------------------- validation

    /** A member annotation only means something on a public member of a {@code @Facade} type. */
    private void checkMemberAnnotation(Element element, String annotationName) {
        AnnotationMirror mirror = mirror(element, annotationName);
        String simple = annotationName.substring(annotationName.lastIndexOf('.') + 1);
        Element owner = element.getEnclosingElement();
        if (mirror(owner, FACADE) == null) {
            error(element, mirror, "@" + simple + " curates a palette entry, but "
                    + (owner instanceof TypeElement t ? t.getQualifiedName() : owner)
                    + " is not a @Facade, so it contributes none");
            return;
        }
        if (!element.getModifiers().contains(Modifier.PUBLIC)) {
            error(element, mirror, "@" + simple + " is on a non-public member, which the palette never "
                    + "offers in the first place");
            return;
        }
        if (PALETTE_LABEL.equals(annotationName) && mirror(element, NOT_IN_PALETTE) != null) {
            error(element, mirror, "@PaletteLabel names a menu entry that @NotInPalette removes");
        }
        if (PALETTE_DEFAULT.equals(annotationName) && mirror(element, NOT_IN_PALETTE) != null) {
            error(element, mirror, "@PaletteDefault leads a menu entry that @NotInPalette removes — "
                    + "@NotInPalette hides the whole name, overloads included");
        }
    }

    /**
     * The three checks that are questions about a <em>name</em> rather than about one element, and so can
     * only be asked once every element has been seen.
     *
     * <p>All three exist because the palette's unit is the name: a name's overloads share one menu entry, so
     * they share its heading, they have exactly one lead, and they are hidden together or not at all. Each
     * duplicate mark is refused rather than resolved, since a second mark can only restate the first (noise
     * that will drift) or contradict it (a silent winner nobody chose).
     */
    private void checkNames() {
        for (Facade facade : facades) {
            TypeElement type = processingEnv.getElementUtils().getTypeElement(facade.qualifiedName());
            if (type == null) {
                continue;
            }
            Map<String, List<ExecutableElement>> byName = new LinkedHashMap<>();
            for (Element enclosed : type.getEnclosedElements()) {
                if (enclosed instanceof ExecutableElement method) {
                    byName.computeIfAbsent(method.getSimpleName().toString(), n -> new ArrayList<>()).add(method);
                }
            }
            byName.forEach(this::checkName);
        }
    }

    private void checkName(String name, List<ExecutableElement> overloads) {
        ExecutableElement labelled = null;
        ExecutableElement lead = null;
        ExecutableElement hidden = null;
        for (ExecutableElement method : overloads) {
            AnnotationMirror label = mirror(method, PALETTE_LABEL);
            if (label != null) {
                if (labelled != null && !string(label, "value").equals(string(mirror(labelled, PALETTE_LABEL), "value"))) {
                    error(method, label, "two overloads of " + name + " carry different @PaletteLabel "
                            + "values; they share one menu entry and so must share its heading");
                }
                labelled = method;
            }
            AnnotationMirror leads = mirror(method, PALETTE_DEFAULT);
            if (leads != null) {
                if (lead != null) {
                    error(method, leads, "two overloads of " + name + " are marked @PaletteDefault; a menu "
                            + "entry inserts exactly one shape, so exactly one of them leads");
                }
                lead = method;
            }
            AnnotationMirror hide = mirror(method, NOT_IN_PALETTE);
            if (hide != null) {
                if (hidden != null) {
                    error(method, hide, "@NotInPalette is already on another overload of " + name + ", and it "
                            + "hides the whole name; mark one overload, not each");
                }
                hidden = method;
            }
        }
    }

    // ---------------------------------------------------------------- emission

    private void emit() {
        if (emitted || facades.isEmpty()) {
            // Nothing collected means a round that saw no facades — a partial recompile, or a build of a
            // module that has none. Overwriting a good catalog with an empty one would be worse than not
            // writing, and the reference from SdkPlugin keeps the previous build's class in place.
            return;
        }
        emitted = true;
        List<Facade> ordered = new ArrayList<>(facades);
        ordered.sort(Comparator.comparingInt(Facade::order).thenComparing(Facade::qualifiedName));

        StringBuilder out = new StringBuilder();
        out.append("package ").append(GENERATED_PACKAGE).append(";\n\n")
                .append("import com.botmaker.plugin.api.catalog.Category;\n")
                .append("import com.botmaker.plugin.api.catalog.FacadeRole;\n")
                .append("import com.botmaker.plugin.api.catalog.PaletteCatalog;\n\n")
                .append("/**\n")
                .append(" * The SDK's palette catalog, generated from the {@code @Facade} annotations on the\n")
                .append(" * facades themselves. Do not edit: every line here is a consequence of an annotation,\n")
                .append(" * and the next build overwrites it.\n")
                .append(" *\n")
                .append(" * @see com.botmaker.sdk.apt.PaletteCatalogProcessor\n")
                .append(" */\n")
                .append("public final class ").append(GENERATED_CLASS).append(" {\n\n")
                .append("    private ").append(GENERATED_CLASS).append("() {\n    }\n\n")
                .append("    /** The catalog. Built fresh on each call; {@code SdkPlugin} memoises it. */\n")
                .append("    public static PaletteCatalog build() {\n")
                .append("        return PaletteCatalog.builder()\n");

        for (Facade facade : ordered) {
            String categoryLabel = facade.categoryLabel().isBlank()
                    ? label(facade.categoryId()) : facade.categoryLabel();
            out.append("                .facade(").append(facade.qualifiedName()).append(".class, Category.of(")
                    .append(quote(facade.categoryId())).append(", ").append(quote(categoryLabel))
                    .append("), FacadeRole.").append(facade.role()).append(")\n");
            if (!facade.icon().isBlank()) {
                out.append("                .facadeIcon(").append(quote(facade.icon())).append(")\n");
            }
            if (!facade.label().isBlank()) {
                out.append("                .facadeLabel(").append(quote(facade.label())).append(")\n");
            }
            out.append("                .addAll()\n");
            if (!facade.memberOrder().isEmpty()) {
                out.append("                .order(");
                for (int i = 0; i < facade.memberOrder().size(); i++) {
                    out.append(i == 0 ? "" : ",\n                        ")
                            .append(quote(facade.memberOrder().get(i)));
                }
                out.append(")\n");
            }
        }
        out.append("                .build();\n    }\n}\n");

        try (Writer writer = processingEnv.getFiler()
                .createSourceFile(GENERATED_PACKAGE + "." + GENERATED_CLASS).openWriter()) {
            writer.write(out.toString());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "could not write the palette catalog: " + e);
        }
    }

    /** {@code "vision"} → {@code "Vision"} — the label a category gets when no facade spells one. */
    private static String label(String categoryId) {
        return Character.toUpperCase(categoryId.charAt(0)) + categoryId.substring(1);
    }

    /**
     * A Java string literal, with every non-ASCII character escaped.
     *
     * <p>The escaping is not decoration: facade icons are emoji, the generated file is written through the
     * {@code Filer}'s writer, and the encoding that writer uses is the compiler's rather than anything this
     * build states. {@code \\uXXXX} is encoding-independent, and a surrogate pair survives it intact.
     */
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    // ---------------------------------------------------------------- mirrors

    private AnnotationMirror mirror(Element element, String qualifiedName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (((TypeElement) mirror.getAnnotationType().asElement())
                    .getQualifiedName().contentEquals(qualifiedName)) {
                return mirror;
            }
        }
        return null;
    }

    /** An element's value, defaults included — an omitted {@code icon} is legitimately absent, not an error. */
    private String string(AnnotationMirror mirror, String name) {
        Object value = value(mirror, name);
        return value == null ? "" : value.toString();
    }

    private int integer(AnnotationMirror mirror, String name) {
        Object value = value(mirror, name);
        return value instanceof Integer i ? i : 100;
    }

    private Object value(AnnotationMirror mirror, String name) {
        if (mirror == null) {
            return null;
        }
        for (Map.Entry<? extends javax.lang.model.element.ExecutableElement, ? extends AnnotationValue> entry
                : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) {
                return entry.getValue().getValue();
            }
        }
        return null;
    }

    private void error(Element element, AnnotationMirror mirror, String message) {
        if (mirror != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element, mirror);
        } else {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
        }
    }
}
