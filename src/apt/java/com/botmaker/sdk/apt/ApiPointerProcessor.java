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
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The three per-element pointer rules, as {@code javac} errors on the element itself.
 *
 * <h2>Why this exists beside {@code ApiPointersTest}</h2>
 *
 * <p>The test is the gate; this is the ergonomics. A rule that fires in surefire names a <em>class</em> and a
 * rule name, so the author reads a list of refs and then goes looking for the declaration. The same rule as a
 * processor error lands on the line, in the IDE, red, while the annotation is still being typed — which is the
 * moment the answer is actually known. Nothing is checked here that the test does not also check: this build
 * fails twice for one mistake, and that is deliberate. The test is what CI and {@code release.sh} read; if the
 * two ever disagree, the test wins and this one is wrong.
 *
 * <p>Only the rules that are decidable <b>from one element</b> live here — a deprecated element carries a
 * pointer, each target it names exists, each target points back. Everything cross-cutting stays in the test,
 * because a processor sees elements one at a time and never the whole surface at once: rule 4 (no undeclared
 * double claim) is a question about every {@code @Replaces} in the API, and the version rules
 * ({@code -Dbotmaker.api.maxVersion}) are questions only the release caller can pose.
 *
 * <h2>It reads mirrors, not classes</h2>
 *
 * <p>This class is compiled <em>before</em> {@code src/main/java} — it has to be, since it runs over it — so it
 * cannot reference {@code com.botmaker.sdk.api.meta.ReplacedBy} at all. Annotations are matched by their fully
 * qualified name and read out of {@link AnnotationMirror}s by hand. That is not a workaround for the build
 * order; it is what keeps the dependency one-way, and it is why the FQNs below are string constants rather
 * than {@code Class} literals.
 *
 * <p>Explicit values only: {@link AnnotationMirror#getElementValues()} omits defaults, which is exactly what
 * rule 1 needs. {@code @ReplacedBy} with no {@code value} is the deliberate "nothing takes my place", and it
 * has to be distinguishable from an omitted annotation — from the class file both would otherwise read as the
 * empty array.
 *
 * <h2>Refs</h2>
 *
 * <p>A ref is {@code fqn}, {@code fqn#member} or {@code fqn#<init>}, with the binary name for the type half
 * ({@code Outer$Inner}), matching what the test's ClassGraph scan produces and what Studio reads from the jar.
 * A target is looked up through {@link javax.lang.model.util.Elements#getTypeElement} on the source spelling,
 * so it resolves anything in this compilation or on its classpath.
 */
@SupportedAnnotationTypes({
        "java.lang.Deprecated",
        "com.botmaker.sdk.api.meta.ReplacedBy",
        "com.botmaker.sdk.api.meta.Replaces"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class ApiPointerProcessor extends AbstractProcessor {

    private static final String API_PACKAGE = "com.botmaker.sdk.api";
    private static final String REPLACED_BY = "com.botmaker.sdk.api.meta.ReplacedBy";
    private static final String REPLACES = "com.botmaker.sdk.api.meta.Replaces";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (round.processingOver()) return false;
        for (Element e : round.getElementsAnnotatedWith(Deprecated.class)) {
            if (!isPublicApi(e)) continue;
            check(e);
        }
        // Never claim these: @Deprecated in particular is everybody's, and a processor that swallows it
        // silently disables anything else the build might one day run over the same elements.
        return false;
    }

    /** The three rules, in the order an author hits them. Each reports on the element and moves on. */
    private void check(Element e) {
        AnnotationMirror pointer = annotation(e, REPLACED_BY);
        if (pointer == null) {
            error(e, null, "@Deprecated with no @ReplacedBy. Name what takes over — @ReplacedBy(\"fqn#member\") "
                    + "— or write @ReplacedBy with no value to state that nothing does. Studio cannot tell a "
                    + "forgotten pointer from a deliberate dead end, so it has to be said.");
            return;
        }
        List<String> targets = strings(pointer, "value").stream().filter(t -> !t.isBlank()).toList();
        String ref = refOf(e);
        for (String target : targets) {
            if (target.indexOf('@') >= 0) {
                error(e, pointer, "@ReplacedBy(\"" + target + "\") carries an @version. Only @Replaces entries "
                        + "are dated — the forward pointer sits on the element being replaced, whose era is "
                        + "the release it is deprecated in.");
                continue;
            }
            List<Element> resolved = resolve(target);
            if (resolved.isEmpty()) {
                error(e, pointer, "@ReplacedBy(\"" + target + "\") names nothing this build contains. The "
                        + "grammar is fqn, fqn#member or fqn#<init>, with the type spelled in full.");
                continue;
            }
            if (!claimsBack(resolved, ref)) {
                error(e, pointer, target + " does not @Replaces \"" + ref + "\". Write the back-edge now, while "
                        + "both ends still compile: once this element is deleted, the @Replaces on the survivor "
                        + "is the only place the redirect still exists, and a bot upgrading past the deletion "
                        + "reads nothing else.");
            }
        }
    }

    // ------------------------------------------------------------------
    // resolving
    // ------------------------------------------------------------------

    /**
     * The elements a ref names — several when it names an overloaded method, since the pointer annotations
     * carry no arity by design (a {@code @ReplacedBy} already sits on one specific overload; a
     * {@code @Replaces} may name one that no longer exists to be counted). Empty means unresolvable.
     */
    private List<Element> resolve(String ref) {
        int hash = ref.indexOf('#');
        String typeName = (hash < 0 ? ref : ref.substring(0, hash)).replace('$', '.');
        TypeElement type = processingEnv.getElementUtils().getTypeElement(typeName);
        if (type == null) return List.of();
        if (hash < 0) return List.of(type);

        String member = ref.substring(hash + 1);
        List<Element> found = new ArrayList<>();
        for (Element enclosed : type.getEnclosedElements()) {
            String name = enclosed.getSimpleName().toString();
            boolean matches = enclosed.getKind() == ElementKind.CONSTRUCTOR
                    ? "<init>".equals(member)
                    : name.equals(member) && (enclosed instanceof ExecutableElement || enclosed instanceof VariableElement);
            if (matches) found.add(enclosed);
        }
        return found;
    }

    /** Whether any of the resolved targets carries a {@code @Replaces} entry naming {@code ref}. */
    private boolean claimsBack(List<Element> targets, String ref) {
        for (Element target : targets) {
            AnnotationMirror claims = annotation(target, REPLACES);
            if (claims == null) continue;
            for (String entry : strings(claims, "value")) {
                if (nameOf(entry).equals(ref)) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // refs and entries
    // ------------------------------------------------------------------

    /** {@code fqn}, {@code fqn#member} or {@code fqn#<init>} — the binary name for the type half. */
    private String refOf(Element e) {
        if (e instanceof TypeElement type) {
            return processingEnv.getElementUtils().getBinaryName(type).toString();
        }
        Element owner = e.getEnclosingElement();
        String type = owner instanceof TypeElement t
                ? processingEnv.getElementUtils().getBinaryName(t).toString()
                : String.valueOf(owner);
        return type + "#" + (e.getKind() == ElementKind.CONSTRUCTOR ? "<init>" : e.getSimpleName());
    }

    /**
     * The name half of a {@code name[(arity)]@version} entry — the same parse the test does, kept here rather
     * than shared because the two run in different compilations and a shared class would have to ship.
     */
    private static String nameOf(String entry) {
        int at = entry.lastIndexOf('@');
        String name = at <= 0 ? entry : entry.substring(0, at);
        int open = name.lastIndexOf('(');
        return open <= 0 || !name.endsWith(")") ? name : name.substring(0, open);
    }

    // ------------------------------------------------------------------
    // mirrors
    // ------------------------------------------------------------------

    /** The mirror for {@code fqn} on this element, or {@code null} — presence is rule 1's whole subject. */
    private static AnnotationMirror annotation(Element e, String fqn) {
        for (AnnotationMirror m : e.getAnnotationMirrors()) {
            Element declaration = m.getAnnotationType().asElement();
            if (declaration instanceof TypeElement t && t.getQualifiedName().contentEquals(fqn)) return m;
        }
        return null;
    }

    /**
     * A {@code String[]} member, trimmed and in declaration order. Explicit values only, so an unwritten
     * member is the empty list; javac may hand a single-value shorthand back either wrapped in a list or
     * bare, and both spellings mean one entry.
     */
    private static List<String> strings(AnnotationMirror m, String member) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : m.getElementValues().entrySet()) {
            if (!entry.getKey().getSimpleName().contentEquals(member)) continue;
            Object value = entry.getValue().getValue();
            if (value instanceof List<?> values) {
                for (Object v : values) {
                    out.add(String.valueOf(v instanceof AnnotationValue av ? av.getValue() : v).trim());
                }
            } else {
                out.add(String.valueOf(value).trim());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // scope
    // ------------------------------------------------------------------

    /**
     * Whether this is an element the contract covers: public, and public all the way out, inside
     * {@code com.botmaker.sdk.api}. {@code internal} is freely breakable and a non-public element is not part
     * of the surface at all, so deprecating either says nothing to a bot and needs no pointer.
     */
    private boolean isPublicApi(Element e) {
        if (!e.getModifiers().contains(Modifier.PUBLIC)) return false;
        Element cursor = e;
        while (cursor.getEnclosingElement() instanceof TypeElement owner) {
            if (!owner.getModifiers().contains(Modifier.PUBLIC)) return false;
            cursor = owner;
        }
        String pkg = processingEnv.getElementUtils().getPackageOf(e).getQualifiedName().toString();
        return pkg.equals(API_PACKAGE) || pkg.startsWith(API_PACKAGE + ".");
    }

    /** One error, on the element and (when there is one) on the annotation that is wrong. */
    private void error(Element e, AnnotationMirror on, String message) {
        if (on == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, e);
        } else {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, e, on);
        }
    }
}
