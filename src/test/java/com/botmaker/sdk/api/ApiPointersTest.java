package com.botmaker.sdk.api;

import io.github.classgraph.AnnotationInfo;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The pointer gate: {@link ReplacedBy} and {@link Replaces} are checked against <em>this</em> build, so a
 * redirect that Studio could not follow fails here rather than at a bot's upgrade.
 *
 * <h2>Why this can be checked at all, with no old jar</h2>
 *
 * <p>A deprecation window puts <b>both ends in the same build</b> — that is what the window is for. The
 * deprecated member is still compilable, and the survivor that takes it over is right there beside it. So the
 * back-edge ({@code @Replaces}) is written and verified while the thing it names still exists, and by the time
 * the member is actually deleted a release later, the entry is already proven. Nothing here fetches, resolves
 * or diffs a previously published artifact: the whole check is one scan of {@code target/classes}, offline.
 *
 * <h2>This is not the gate that was deleted</h2>
 *
 * <p>{@code docs/refactor/21-api-compat.md} §3 records a japicmp gate that was built and removed. It enforced
 * <b>coverage</b> — every break had to ship a way across it — and it went because an uncovered break is now a
 * <em>supported outcome</em>: Studio substitutes a default value of the old return type and marks the enclosing
 * function {@code @NeedsReview}. No coverage rule comes back here, and no version-bump rule either. These
 * checks ask only that something somebody <em>did</em> declare is complete and internally consistent — they
 * are wrong at every version, which is why CI needs no version awareness to run them.
 *
 * <h2>What is checked</h2>
 *
 * <p>Rules 1–5 are the pointer pair: a deprecated element says what to use instead, the target exists, the
 * back-edge is written, no entry is claimed twice, every entry parses. Rules 7–9 cover the three annotations
 * added beside them — {@link Since} is well-formed, a {@code behaviourChanged} move carries its sentence, and
 * a deprecated {@link Scaffolding} element names a real survivor because generated code cannot take a default.
 *
 * <p>The version-aware checks are the exception, and they are opt-in: {@code release.sh} passes
 * {@code -Dbotmaker.api.maxVersion=<the version being cut>} so that neither a {@code @Replaces} entry (rule 6)
 * nor a {@code @Since} (rule 7) can claim an era that has not been released yet. Only the release caller knows
 * that number, so unset means unchecked.
 */
class ApiPointersTest {

    private static final String API_PACKAGE = "com.botmaker.sdk.api";
    private static final String REPLACED_BY = ReplacedBy.class.getName();
    private static final String REPLACES = Replaces.class.getName();
    private static final String SINCE = Since.class.getName();
    private static final String SCAFFOLDING = Scaffolding.class.getName();
    private static final String DEPRECATED = Deprecated.class.getName();

    /** Major.minor.patch, with an optional pre-release/build tail. No leading {@code v} — tags carry it, entries don't. */
    private static final Pattern SEMVER = Pattern.compile("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.\\-]+)?");

    /** The version this release is cutting, when run from {@code release.sh}; empty otherwise. */
    private static final String MAX_VERSION = System.getProperty("botmaker.api.maxVersion", "").trim();

    private static ScanResult scan;

    /** Every public element of {@code api.*}, in declaration order, keyed for the messages below. */
    private static List<Element> elements;
    /** ref -> the elements sharing it. Overloads share a ref: the annotations carry no arity by design. */
    private static Map<String, List<Element>> byRef;

    /**
     * One annotatable API element — a type, a method, a constructor or a field — reduced to what the gate
     * reads. {@code replacedBy} is {@code null} when the annotation is absent and {@code ""} when it is
     * present with no target (the explicit "nothing takes its place"); the difference is the whole point of
     * check 1, and the empty case reaches us as a <em>missing</em> value element in the class file, since
     * {@code ""} is the annotation's default and javac does not emit defaults.
     *
     * <p>{@code deprecated} is per <em>element</em> and not per ref: overloads share a ref, and deprecating
     * one of them says nothing about the others. So are {@code since} and {@code scaffolding}: one overload
     * may be older, or written by a generator, while its siblings are not.
     *
     * <p>{@code since} is {@code null} when the element carries no {@link Since} — which the whole
     * pre-1.1.0 surface deliberately does not, so absence is never an error here.
     */
    private record Element(String ref, String kind, boolean deprecated, String replacedBy, String note,
                           boolean behaviourChanged, List<String> replaces, String since, boolean scaffolding) {
    }

    @BeforeAll
    static void scanBuild() {
        scan = new ClassGraph()
                // Exactly this module's main output, nothing else. The test sources live in the same
                // package, so scanning the plain classpath would mix target/test-classes into the API
                // surface and let a test fixture fail — or accidentally satisfy — a rule below.
                .overrideClasspath(mainClasses())
                .acceptPackages(API_PACKAGE)
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

    /** 2 — a pointer that names something names something that is here. */
    @Test
    void everyPointerTargetResolves() {
        List<String> bad = new ArrayList<>();
        for (Element e : deprecated()) {
            String target = e.replacedBy();
            if (target == null || target.isEmpty()) continue;
            if (target.indexOf('@') >= 0) {
                bad.add(e.ref() + " -> " + target + "  (a @ReplacedBy target carries no @version; only @Replaces entries do)");
            } else if (!byRef.containsKey(target)) {
                bad.add(e.ref() + " -> " + target + "  (no such type/member in this build)");
            }
        }
        assertEmpty(bad, """
                @ReplacedBy names something this build does not contain. The grammar is fqn, fqn#member,
                fqn#<init> — a type must be spelled in full and a member must exist on the type named:""");
    }

    /**
     * 3 — the back-edge exists. This is the rule that makes the old jar unnecessary.
     *
     * <p>{@code @ReplacedBy} lives on the element that is about to disappear, so it is readable only from a
     * jar that still has it — the bot's own, older one. Once the member is deleted, {@code @Replaces} on the
     * survivor is the only surviving record, and it has to have been written while both ends were here.
     */
    @Test
    void everyPointerHasItsBackEdge() {
        List<String> bad = new ArrayList<>();
        for (Element e : deprecated()) {
            String target = e.replacedBy();
            if (target == null || target.isEmpty() || !byRef.containsKey(target)) continue; // checks 1/2 own these
            boolean claimed = byRef.get(target).stream()
                    .flatMap(t -> t.replaces().stream())
                    .map(ApiPointersTest::nameOf)
                    .anyMatch(e.ref()::equals);
            if (!claimed) bad.add(target + " does not @Replaces " + e.ref());
        }
        assertEmpty(bad, """
                A @ReplacedBy with no matching @Replaces on the other end. Add @Replaces("<old>@<version>") to
                the target, naming the version this deprecation ships in. The forward pointer is readable only
                while the deprecated element still exists; the back-edge is what a bot upgrading past its
                deletion reads instead:""");
    }

    /** 4 — one old spelling, at one era, belongs to one survivor. An ambiguous claim is no claim. */
    @Test
    void noEntryIsClaimedTwice() {
        Map<String, Set<String>> claimants = new TreeMap<>();
        for (Element e : elements) {
            for (String entry : e.replaces()) {
                claimants.computeIfAbsent(entry, k -> new LinkedHashSet<>()).add(e.ref());
            }
        }
        List<String> bad = new ArrayList<>();
        claimants.forEach((entry, refs) -> {
            if (refs.size() > 1) bad.add(entry + " claimed by " + String.join(" and ", refs));
        });
        assertEmpty(bad, """
                Two different elements @Replaces the same name@version. Studio treats a contested entry as
                unpaired (default value + review mark, plus a line in the report's problems), so this silently
                loses the redirect for both. Keep one claim, or move one to a different version:""");
    }

    /**
     * 5 — every entry parses, carries a real version, and does not quietly shadow something that still works.
     *
     * <p>The last clause is the interesting one: an entry naming a <em>live</em> element is fine during a
     * deprecation window — the old member is still here, that is the window — but only if that member is
     * actually deprecated and pointing back. An entry naming a healthy, undeprecated element is a claim to
     * take over something nobody is giving up.
     */
    @Test
    void everyEntryIsWellFormed() {
        List<String> bad = new ArrayList<>();
        for (Element e : elements) {
            for (String entry : e.replaces()) {
                int at = entry.lastIndexOf('@');
                if (at <= 0 || at == entry.length() - 1) {
                    bad.add(e.ref() + ": \"" + entry + "\" — expected <fqn[#member]>@<version>");
                    continue;
                }
                String name = entry.substring(0, at);
                String version = entry.substring(at + 1);
                if (!SEMVER.matcher(version).matches()) {
                    bad.add(e.ref() + ": \"" + entry + "\" — \"" + version + "\" is not a semver");
                }
                List<Element> live = byRef.get(name);
                if (live != null) {
                    boolean handedOver = live.stream()
                            .anyMatch(l -> l.deprecated() && e.ref().equals(l.replacedBy()));
                    if (!handedOver) {
                        bad.add(e.ref() + ": \"" + entry + "\" names " + name + ", which is still live and is "
                                + "not @Deprecated + @ReplacedBy(\"" + e.ref() + "\")");
                    }
                }
            }
        }
        assertEmpty(bad, """
                A malformed or over-reaching @Replaces entry. Every entry is <fqn[#member]>@<version>, where
                the version is the last release that spelling existed in; an entry may name an element of this
                build only while that element is the deprecated one pointing back at the claimant:""");
    }

    /**
     * 6 — release-time only: no entry claims an era that has not shipped.
     *
     * <p>Skipped unless {@code -Dbotmaker.api.maxVersion} is set, which only {@code release.sh}'s decide pass
     * does. An entry dated ahead of the release being cut resolves for nobody: Studio consults an entry only
     * when the bot's pinned version is at or below it, and no bot can pin a version that does not exist.
     */
    @Test
    void noEntryIsDatedAfterTheReleaseBeingCut() {
        if (MAX_VERSION.isEmpty()) return;
        List<String> bad = new ArrayList<>();
        for (Element e : elements) {
            for (String entry : e.replaces()) {
                int at = entry.lastIndexOf('@');
                if (at <= 0) continue; // check 5 owns the parse
                String version = entry.substring(at + 1);
                if (SEMVER.matcher(version).matches() && compare(version, MAX_VERSION) > 0) {
                    bad.add(e.ref() + ": \"" + entry + "\" is dated after " + MAX_VERSION);
                }
            }
        }
        assertEmpty(bad, "A @Replaces entry names a version newer than the " + MAX_VERSION
                + " being released. No bot can pin it, so Studio would never consult the entry:");
    }

    /**
     * 7 — every {@code @Since} is a semver, and at release time none is dated after the version being cut.
     *
     * <p>Only the shape is checkable here, and deliberately: <b>absence is not an error</b>. The surface that
     * predates the contract carries no {@code @Since} and never will, because the value is a fact about a
     * release nobody recorded — see {@link Since}. What can be wrong is a value that is present and untrue in
     * a way a machine can see: a {@code v} prefix, a two-segment version, or (during a release) a version
     * that has not shipped, which would make the dialog announce an element as "new in" a release the user's
     * jar cannot contain.
     */
    @Test
    void everySinceIsWellFormedAndNotInTheFuture() {
        List<String> bad = new ArrayList<>();
        for (Element e : elements) {
            String since = e.since();
            if (since == null) continue;
            if (!SEMVER.matcher(since).matches()) {
                bad.add(e.ref() + ": @Since(\"" + since + "\") is not a semver (no leading v, three segments)");
            } else if (!MAX_VERSION.isEmpty() && compare(since, MAX_VERSION) > 0) {
                bad.add(e.ref() + ": @Since(\"" + since + "\") is dated after the " + MAX_VERSION + " being released");
            }
        }
        assertEmpty(bad, """
                A malformed or future-dated @Since. The value is major.minor.patch with no leading "v" — the
                git tag carries that, nothing inside the API does — and it names the release the element first
                shipped in, which can never be one that has not been cut:""");
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
            if (e.behaviourChanged() && e.note().isBlank()) bad.add(e.kind() + " " + e.ref());
        }
        assertEmpty(bad, """
                @ReplacedBy(behaviourChanged = true) with no note. The flag makes Studio mark every redirected
                call site for review even where the shape did not move; the note is the only thing that tells
                the user what to look for. Add note = "…", in the second person, a sentence or two:""");
    }

    /**
     * 9 — scaffolding is never deprecated into a dead end.
     *
     * <p>An empty {@code @ReplacedBy} is a supported answer everywhere else: Studio substitutes a default
     * value at each call site and marks the enclosing function for review, and the bot compiles. Generated
     * files cannot take that repair — they hold no user code to review, and a defaulted value inside one is a
     * broken feature rather than a repair — so Studio's only remaining answer is to refuse the upgrade, and
     * the edit, until Studio itself is updated. Declaring the dead end here is declaring that; the gate makes
     * the author say what takes over instead.
     */
    @Test
    void deprecatedScaffoldingNamesItsReplacement() {
        List<String> bad = new ArrayList<>();
        for (Element e : deprecated()) {
            if (!e.scaffolding()) continue;
            if (e.replacedBy() == null || e.replacedBy().isEmpty()) bad.add(e.kind() + " " + e.ref());
        }
        assertEmpty(bad, """
                A @Deprecated @Scaffolding element with no replacement. Studio writes this element into the
                files it generates, which are regenerated rather than migrated — so "nothing takes my place"
                is not a repair here, it is a refusal to upgrade or to edit the Activity Flow. Name the
                survivor, or drop @Scaffolding once no generator emits it:""");
    }

    /** Sanity: the scan found the API at all, so a silently empty classpath cannot pass every rule above. */
    @Test
    void theScanSawTheApi() {
        assertTrue(elements.size() > 50,
                "scanned only " + elements.size() + " public api.* elements — target/classes is probably not "
                        + "on the test classpath, which would make every rule above vacuously true");
    }

    // ------------------------------------------------------------------
    // scanning
    // ------------------------------------------------------------------

    /** Where this module's compiled API actually lives — asked of a class that is unambiguously part of it. */
    private static String mainClasses() {
        try {
            return Path.of(ReplacedBy.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the compiled api.* output", e);
        }
    }

    private static void collect() {
        elements = new ArrayList<>();
        byRef = new LinkedHashMap<>();
        for (ClassInfo ci : scan.getAllClasses()) {
            if (!ci.isPublic()) continue;
            AnnotationInfo typePointer = ci.getAnnotationInfo(REPLACED_BY);
            add(new Element(ci.getName(), "type", ci.hasAnnotation(DEPRECATED),
                    pointer(typePointer), note(typePointer), behaviourChanged(typePointer),
                    entries(ci.getAnnotationInfo(REPLACES)),
                    since(ci.getAnnotationInfo(SINCE)), ci.hasAnnotation(SCAFFOLDING)));

            for (MethodInfo mi : ci.getDeclaredMethodAndConstructorInfo()) {
                if (!mi.isPublic() || mi.isSynthetic() || mi.isBridge()) continue;
                AnnotationInfo p = mi.getAnnotationInfo(REPLACED_BY);
                add(new Element(ci.getName() + "#" + mi.getName(),
                        mi.isConstructor() ? "constructor" : "method",
                        mi.hasAnnotation(DEPRECATED),
                        pointer(p), note(p), behaviourChanged(p),
                        entries(mi.getAnnotationInfo(REPLACES)),
                        since(mi.getAnnotationInfo(SINCE)), mi.hasAnnotation(SCAFFOLDING)));
            }
            for (FieldInfo fi : ci.getDeclaredFieldInfo()) {
                if (!fi.isPublic() || fi.isSynthetic()) continue;
                AnnotationInfo p = fi.getAnnotationInfo(REPLACED_BY);
                add(new Element(ci.getName() + "#" + fi.getName(), "field",
                        fi.hasAnnotation(DEPRECATED),
                        pointer(p), note(p), behaviourChanged(p),
                        entries(fi.getAnnotationInfo(REPLACES)),
                        since(fi.getAnnotationInfo(SINCE)), fi.hasAnnotation(SCAFFOLDING)));
            }
        }
    }

    private static void add(Element e) {
        elements.add(e);
        byRef.computeIfAbsent(e.ref(), k -> new ArrayList<>()).add(e);
    }

    /**
     * The {@code @ReplacedBy} target: {@code null} when the annotation is absent, {@code ""} when it is
     * present with no value. javac writes no value element for {@code @ReplacedBy} — {@code ""} is the
     * declared default — so a missing parameter here means the empty form, never a missing annotation.
     */
    private static String pointer(AnnotationInfo ai) {
        if (ai == null) return null;
        Object v = ai.getParameterValues(true).getValue("value");
        return v == null ? "" : v.toString().trim();
    }

    /**
     * The author's sentence on a {@code @ReplacedBy}; {@code ""} both when the annotation is absent and when
     * it carries no note. The two are the same thing to every rule below — there is nothing to check about a
     * sentence nobody wrote — so unlike {@link #pointer} this one does not distinguish them.
     */
    private static String note(AnnotationInfo ai) {
        if (ai == null) return "";
        Object v = ai.getParameterValues(true).getValue("note");
        return v == null ? "" : v.toString().trim();
    }

    /** The {@code behaviourChanged} flag on a {@code @ReplacedBy}; false when absent, as the default is. */
    private static boolean behaviourChanged(AnnotationInfo ai) {
        if (ai == null) return false;
        Object v = ai.getParameterValues(true).getValue("behaviourChanged");
        return v instanceof Boolean b && b;
    }

    /**
     * The {@code @Since} value, or {@code null} when the element carries none. {@link Since} declares no
     * default, so a present annotation always has a value; {@code null} means absent, which is the normal
     * state of the whole pre-contract surface.
     */
    private static String since(AnnotationInfo ai) {
        if (ai == null) return null;
        Object v = ai.getParameterValues(true).getValue("value");
        return v == null ? "" : v.toString().trim();
    }

    /** The {@code @Replaces} entries, in declaration order; empty when the annotation is absent. */
    private static List<String> entries(AnnotationInfo ai) {
        if (ai == null) return List.of();
        Object v = ai.getParameterValues(true).getValue("value");
        if (!(v instanceof Object[] arr)) return List.of();
        List<String> out = new ArrayList<>(arr.length);
        for (Object o : arr) out.add(String.valueOf(o).trim());
        return out;
    }

    // ------------------------------------------------------------------
    // small helpers
    // ------------------------------------------------------------------

    private static List<Element> deprecated() {
        return elements.stream().filter(Element::deprecated).toList();
    }

    /** The name half of a {@code name@version} entry; the whole string when it carries no version. */
    private static String nameOf(String entry) {
        int at = entry.lastIndexOf('@');
        return at <= 0 ? entry : entry.substring(0, at);
    }

    /** Numeric-segment comparison; a pre-release tail is ignored, which is close enough to order releases. */
    private static int compare(String a, String b) {
        String[] x = a.split("[-+]")[0].split("\\.");
        String[] y = b.split("[-+]")[0].split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? Integer.parseInt(x[i]) : 0;
            int yi = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (xi != yi) return Integer.compare(xi, yi);
        }
        return 0;
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
