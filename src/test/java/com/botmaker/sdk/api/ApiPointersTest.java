package com.botmaker.sdk.api;

import com.botmaker.plugin.api.meta.ReplacedBy;
import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.AnnotationInfoList;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The pointer gate: {@link ReplacedBy} is checked against <em>this</em> build, so a redirect Studio could not
 * follow fails here rather than at a bot's upgrade.
 *
 * <h2>Why this can be checked at all, with no old jar</h2>
 *
 * <p>Nothing here fetches, resolves or diffs a previously published artifact: the whole check is one scan of
 * {@code target/classes} plus the contract's, offline. A pointer is written on an element that is still
 * compilable, naming an element that is also still compilable, so both ends are in the build being checked.
 *
 * <h2>There is no back edge any more, and japicmp is why</h2>
 *
 * <p>{@code @Replaces} — the claim written on the survivor — was deleted on 2026-08-27 with the annotation
 * processor, and rules 3, 4, 5 and 6 went with it. It existed for one case: a bot on 1.0 jumping straight to
 * 3.0 cannot see a pointer that was added in 2.0 on an element 3.0 deleted, so the answer had to survive on
 * the survivor. Under the <b>never-delete</b> rule now enforced by japicmp over
 * {@code com.botmaker.sdk.api.**}, the target jar still carries the deprecated element <em>and</em> its own
 * {@code @ReplacedBy}, so the forward pointer alone answers every upgrade including a skipped one, and
 * pointers compose into a chain ({@code a}→{@code b} in 2.0, {@code b}→{@code c} in 3.0 lands a bot still
 * spelling it {@code a} on {@code c}). The accepted cost is stated plainly in the pom: {@code api} only ever
 * grows.
 *
 * <p>{@code @Since} went the same day, and rule 7 with it. What it recorded — the release an element first
 * shipped in — is answerable from the jar the bot actually resolves, which is the gate-deletion test this
 * repository applies to every check: the question a gate answers must not already be answered by bytecode.
 *
 * <p>Rules 9, 10 and 12 were deleted earlier (2026-08-25) with {@code @Scaffolding} and {@code @Palette}.
 *
 * <h2>What is left</h2>
 *
 * <p>Four rules, each wrong at every version, which is why CI needs no version awareness to run them: a
 * deprecated element says what to use instead (1), every target it names is here (2), a behaviour change is
 * announced in words (8), and a split says when each candidate applies (11).
 */
class ApiPointersTest {

    private static final String API_PACKAGE = "com.botmaker.sdk.api";

    /**
     * The plugin contract, scanned beside {@code api.*} because a pointer may cross into it.
     *
     * <p>A redirect's two ends do not have to live in one module: a type moving from this plugin into the
     * contract is an ordinary rename with a longer name, and the compatibility vocabulary's own move
     * ({@code sdk.api.meta} → {@code plugin.api.meta}, 2026-08-27) is written exactly that way. Rule 2
     * resolves a target, so the target has to be in the scan or every such pointer reads as unresolvable.
     */
    private static final String CONTRACT_PACKAGE = "com.botmaker.plugin.api";

    /**
     * The pointer annotation, read under both spellings for the length of its move.
     *
     * <p>{@code com.botmaker.plugin.api.meta} is where it now lives; {@code com.botmaker.sdk.api.meta} is the
     * deprecated spelling, which under never-delete stays in the jar rather than being removed after a
     * window. An element may carry either.
     */
    private static final List<String> REPLACED_BY =
            List.of(ReplacedBy.class.getName(), "com.botmaker.sdk.api.meta.ReplacedBy");
    private static final String DEPRECATED = Deprecated.class.getName();

    private static ScanResult scan;

    /** Every public element of the scanned packages, in declaration order. */
    private static List<Element> elements;
    /** ref -> the elements sharing it. Overloads share a ref: the annotation carries no arity by design. */
    private static Map<String, List<Element>> byRef;

    /**
     * One public element as the rules see it.
     *
     * <p>{@code replacedBy} is {@code null} when the annotation is absent and a (possibly empty) list when it
     * is present — the distinction rule 1 rests on.
     */
    private record Element(String ref, String kind, boolean deprecated,
                           List<String> replacedBy, List<String> whens, String note,
                           boolean behaviourChanged) {

        /** The targets that actually name something — a blank entry is the "nothing takes my place" form. */
        List<String> targets() {
            return replacedBy == null ? List.of()
                    : replacedBy.stream().filter(t -> !t.isBlank()).toList();
        }
    }

    @BeforeAll
    static void scanBuild() {
        scan = new ClassGraph()
                // Exactly this module's main output plus the contract, nothing else. The test sources live in
                // the same package, so scanning the plain classpath would mix target/test-classes into the API
                // surface and let a test fixture fail — or accidentally satisfy — a rule below.
                .overrideClasspath(mainClasses(), contractClasses())
                .acceptPackages(API_PACKAGE, CONTRACT_PACKAGE)
                .enableClassInfo()
                .enableMethodInfo()
                .enableFieldInfo()
                .enableAnnotationInfo()
                .scan();
        collect();
    }

    @AfterAll
    static void closeScan() {
        if (scan != null) scan.close();
    }

    // ------------------------------------------------------------------
    // the rules
    // ------------------------------------------------------------------

    /**
     * 1 — a deprecated element says what to use instead, even if the answer is "nothing".
     *
     * <p>The annotation is required rather than optional so the author <em>decides</em>. Studio reads an empty
     * value exactly as it reads a member with no counterpart at all (default value + review mark); what it
     * cannot read is an omission, because an omission and a deliberate dead end look identical from the jar.
     */
    @Test
    void everyDeprecatedElementCarriesAPointer() {
        List<String> bad = new ArrayList<>();
        for (Element e : deprecated()) {
            if (e.replacedBy() == null) bad.add(e.kind() + " " + e.ref());
        }
        assertEmpty(bad, """
                @Deprecated with no @ReplacedBy. Add @ReplacedBy("<target>") beside the @Deprecated, or
                @ReplacedBy with no value to state that nothing takes this over. Without it Studio cannot tell
                a forgotten pointer from a deliberate dead end, and every call site defaults + gets marked:""");
    }

    /**
     * 2 — a pointer that names something names something that is here. <b>Per candidate</b>: a split is
     * only as good as its weakest target, and one unresolvable candidate in a menu of two is a menu entry
     * that cannot be chosen.
     *
     * <p>Under never-delete this rule is stronger than it looks: the target cannot later vanish, so a pointer
     * that resolves in the build that introduced it resolves in every build after it.
     */
    @Test
    void everyPointerTargetResolves() {
        List<String> bad = new ArrayList<>();
        for (Element e : deprecated()) {
            for (String target : e.targets()) {
                if (target.indexOf('@') >= 0) {
                    bad.add(e.ref() + " -> " + target + "  (a @ReplacedBy target carries no @version)");
                } else if (!byRef.containsKey(target)) {
                    bad.add(e.ref() + " -> " + target + "  (no such type/member in this build)");
                }
            }
        }
        assertEmpty(bad, """
                @ReplacedBy names something this build does not contain. The grammar is fqn, fqn#member,
                fqn#<init> — a type must be spelled in full and a member must exist on the type named:""");
    }

    /**
     * 8 — a behaviour change is announced in words, not only as a flag.
     *
     * <p>{@code behaviourChanged = true} exists to force a review mark onto call sites Studio would otherwise
     * redirect silently, because the shapes match and only the meaning moved. A mark with no sentence tells
     * the user their bot now does something different and nothing about what — which is strictly worse than
     * the silent redirect it replaced, since it costs them a hand review that answers nothing.
     */
    @Test
    void aBehaviourChangeCarriesItsSentence() {
        List<String> bad = new ArrayList<>();
        for (Element e : elements) {
            if (e.behaviourChanged() && e.note().isBlank()) {
                bad.add(e.kind() + " " + e.ref());
            }
        }
        assertEmpty(bad, """
                behaviourChanged = true with no note. The flag makes Studio mark every redirected call site for
                review even where the shape did not move; the note is the only thing that tells the user what
                to look for. Add note = "…", in the second person, a sentence or two:""");
    }

    /**
     * 11 — a split states the condition each candidate applies under.
     *
     * <p>{@link ReplacedBy#whens()} is a sentence per candidate, in {@code value()}'s order. Empty is the
     * normal state of an ordinary one-target pointer and stays legal. What is refused is a <b>split</b> — two
     * or more candidates — whose {@code whens()} is missing or partly blank, because the dialog then asks
     * someone to pick between two method names on no information at all.
     *
     * <p>A mismatched length is refused for both arities: a {@code whens()} that is present but not exactly
     * {@code value()}'s length has lost its correspondence, and the pairing is positional.
     */
    @Test
    void aSplitStatesWhenEachCandidateApplies() {
        List<String> bad = new ArrayList<>();
        for (Element e : elements) {
            List<String> declared = e.replacedBy();
            if (declared == null) continue;
            List<String> targets = e.targets();
            if (!targets.isEmpty() && targets.size() != declared.size()) {
                bad.add(e.ref() + ": a blank target mixed in with " + targets.size() + " real one(s)");
                continue;
            }
            List<String> whens = e.whens();
            if (whens.isEmpty()) {
                if (targets.size() > 1) {
                    bad.add(e.ref() + ": " + targets.size() + " candidates and no whens()");
                }
                continue;
            }
            if (whens.size() != targets.size()) {
                bad.add(e.ref() + ": " + whens.size() + " whens() for " + targets.size() + " candidate(s)");
                continue;
            }
            for (int i = 0; i < whens.size(); i++) {
                if (whens.get(i).isBlank()) {
                    bad.add(e.ref() + ": whens()[" + i + "] is blank, beside " + targets.get(i));
                }
            }
        }
        assertEmpty(bad, """
                A @ReplacedBy split with a missing, mismatched or partly blank whens(), or a blank target
                mixed in with real ones. One sentence per candidate, in value()'s order and the same length —
                "when notches is positive", "when negative". The pairing is positional, and a menu of bare
                member names is not a choice anybody can make. A blank target is the whole-value statement
                "nothing takes my place"; it means nothing beside a candidate that does:""");
    }

    /** Sanity: the scan found the API at all, so a silently empty classpath cannot pass every rule above. */
    @Test
    void theScanSawTheApi() {
        assertTrue(elements.size() > 50,
                "scanned only " + elements.size() + " public elements — target/classes is probably not "
                        + "on the test classpath, which would make every rule above vacuously true");
    }

    // ------------------------------------------------------------------
    // scanning
    // ------------------------------------------------------------------

    /** Where this module's compiled API actually lives — asked of a class that is unambiguously part of it. */
    private static String mainClasses() {
        return codeSourceOf(com.botmaker.sdk.api.flow.FlowGraph.class, "the compiled api.* output");
    }

    /** The contract jar, so that a pointer crossing into {@code com.botmaker.plugin.api} resolves. */
    private static String contractClasses() {
        return codeSourceOf(ReplacedBy.class, "the botmaker-studio-api artifact");
    }

    private static String codeSourceOf(Class<?> anchor, String what) {
        try {
            return Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate " + what, e);
        }
    }

    private static void collect() {
        elements = new ArrayList<>();
        byRef = new LinkedHashMap<>();
        for (ClassInfo ci : scan.getAllClasses()) {
            if (!ci.isPublic()) continue;
            AnnotationInfo typePointer = first(ci.getAnnotationInfo());
            add(new Element(ci.getName(), "type", ci.hasAnnotation(DEPRECATED),
                    pointer(typePointer), whens(typePointer), note(typePointer),
                    behaviourChanged(typePointer)));

            for (MethodInfo mi : ci.getDeclaredMethodAndConstructorInfo()) {
                if (!mi.isPublic() || mi.isSynthetic() || mi.isBridge()) continue;
                AnnotationInfo p = first(mi.getAnnotationInfo());
                add(new Element(ci.getName() + "#" + mi.getName(),
                        mi.isConstructor() ? "constructor" : "method",
                        mi.hasAnnotation(DEPRECATED),
                        pointer(p), whens(p), note(p), behaviourChanged(p)));
            }
            for (FieldInfo fi : ci.getDeclaredFieldInfo()) {
                if (!fi.isPublic() || fi.isSynthetic()) continue;
                AnnotationInfo p = first(fi.getAnnotationInfo());
                add(new Element(ci.getName() + "#" + fi.getName(), "field",
                        fi.hasAnnotation(DEPRECATED),
                        pointer(p), whens(p), note(p), behaviourChanged(p)));
            }
        }
    }

    /**
     * The first accepted spelling of {@code @ReplacedBy} that is <em>directly</em> present.
     *
     * <p>{@code directOnly()} is load-bearing rather than tidy. ClassGraph folds meta-annotations into a
     * class's annotation list, and the pointer annotations annotate <em>each other</em> — so without this
     * filter every element that merely uses one reads as carrying whatever that annotation's own declaration
     * carries. A redirect is a statement about the element it is written on; nothing here ever wanted an
     * inherited one.
     */
    private static AnnotationInfo first(AnnotationInfoList on) {
        if (on == null) return null;
        AnnotationInfoList direct = on.directOnly();
        for (String name : REPLACED_BY) {
            AnnotationInfo found = direct.get(name);
            if (found != null) return found;
        }
        return null;
    }

    private static void add(Element e) {
        elements.add(e);
        byRef.computeIfAbsent(e.ref(), k -> new ArrayList<>()).add(e);
    }

    /**
     * The {@code @ReplacedBy} targets, in declared preference order: {@code null} when the annotation is
     * absent, an empty list when it is present with no value. That distinction is rule 1's whole subject.
     */
    private static List<String> pointer(AnnotationInfo ai) {
        return ai == null ? null : strings(ai, "value");
    }

    /** The per-candidate sentences on a {@code @ReplacedBy}; empty when absent or unwritten. */
    private static List<String> whens(AnnotationInfo ai) {
        return ai == null ? List.of() : strings(ai, "whens");
    }

    /**
     * A {@code String[]} annotation element as a list, trimmed and in declaration order. ClassGraph hands an
     * array element back as {@code Object[]}; a single-value shorthand ({@code @ReplacedBy("x")}) is already
     * an array in the class file, so nothing here has to special-case it.
     */
    private static List<String> strings(AnnotationInfo ai, String name) {
        Object v = ai.getParameterValues(true).getValue(name);
        if (!(v instanceof Object[] arr)) return v == null ? List.of() : List.of(v.toString().trim());
        List<String> out = new ArrayList<>(arr.length);
        for (Object o : arr) out.add(String.valueOf(o).trim());
        return out;
    }

    /** The author's sentence; {@code ""} both when the annotation is absent and when it carries no note. */
    private static String note(AnnotationInfo ai) {
        if (ai == null) return "";
        Object v = ai.getParameterValues(true).getValue("note");
        return v == null ? "" : v.toString().trim();
    }

    /** The {@code behaviourChanged} flag; false when absent, as the default is. */
    private static boolean behaviourChanged(AnnotationInfo ai) {
        if (ai == null) return false;
        Object v = ai.getParameterValues(true).getValue("behaviourChanged");
        return v instanceof Boolean b && b;
    }

    // ------------------------------------------------------------------
    // small helpers
    // ------------------------------------------------------------------

    private static List<Element> deprecated() {
        return elements.stream().filter(Element::deprecated).toList();
    }

    /**
     * Fails with the offenders on the <b>first line</b> and the explanation under it. The order matters:
     * surefire's one-line summary — which is all {@code release.sh} and the CI log show — is truncated at the
     * first newline, so a message that opens with prose names no element and helps nobody.
     */
    private static void assertEmpty(List<String> bad, String explanation) {
        if (bad.isEmpty()) return;
        List<String> head = bad.size() > 4 ? bad.subList(0, 4) : bad;
        String headline = String.join("; ", head)
                + (bad.size() > head.size() ? "; …and " + (bad.size() - head.size()) + " more" : "");
        fail(headline + "\n" + explanation + "\n  " + String.join("\n  ", bad));
    }
}
