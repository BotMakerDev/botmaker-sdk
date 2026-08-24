package com.botmaker.sdk.api;

import com.botmaker.sdk.api.meta.ReplacedBy;
import com.botmaker.sdk.api.meta.Since;
import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The deprecation window: {@code api-surface.txt} is the previous release's public surface, committed, and
 * this build is diffed against it. An element that vanished without having been {@code @Deprecated} in that
 * file fails here.
 *
 * <h2>Why a committed file, when {@link ApiPointersTest} needs no old jar</h2>
 *
 * <p>The pointer gate checks a link the author <em>declared</em>, and both ends of a declared link live in
 * one build — that is what a deprecation window is for. This gate asks the one question that build cannot
 * answer about itself: <b>what was here before?</b> A deletion is invisible from the inside; the deleted
 * element leaves nothing behind to be scanned. So the previous answer is written down, in the repo, refreshed
 * by {@code release.sh} in the SDK's own release commit, and reviewable as a diff like any other file.
 *
 * <p>It is deliberately <em>not</em> the japicmp gate that was deleted (see
 * {@code docs/refactor/21-api-compat.md} §3). That one enforced <b>coverage</b> — every break had to ship a
 * way across it — and an uncovered break is now a supported outcome. This one enforces only the
 * <b>window</b>: say it is going before it goes, so a bot on the previous release sees a deprecation warning
 * and Studio sees a {@code @ReplacedBy} to follow. What replaces it, or whether anything does, stays the
 * author's call and is checked by the other gate.
 *
 * <h2>The three rules</h2>
 *
 * <ol>
 *   <li><b>Nothing is removed without a window.</b> In the file, gone from the build → the file's line must
 *       have said {@code [deprecated]}.</li>
 *   <li><b>A version is recorded once and never changed.</b> An element present in both keeps the exact
 *       {@code since=} it had — including <em>not having one</em>. Back-filling {@link Since} onto something
 *       that already shipped asserts a fact about a release nobody can re-check.</li>
 *   <li><b>A new element says when it arrived.</b> In the build, absent from the file → it carries a
 *       {@code @Since}, written in the commit that introduces it, which is the only moment the value is
 *       knowable.</li>
 * </ol>
 *
 * <h2>The line</h2>
 *
 * <pre>{@code
 * com.botmaker.sdk.api.vision.ImageFinder:class
 * com.botmaker.sdk.api.vision.ImageFinder#find(com.botmaker.sdk.api.vision.ImageTemplate):…MatchResult
 * com.botmaker.sdk.api.interaction.Mouse#scroll(int):void [deprecated] [since=1.1.0]
 * }</pre>
 *
 * <p><b>Parameters are erased types, not a count.</b> The plan said {@code (argCount)}, and an arity is what
 * a {@link com.botmaker.sdk.api.meta.Replaces} entry carries — but there it disambiguates a name a human
 * wrote, while here it is the identity the diff keys on, and this API is full of same-arity overloads
 * ({@code click(Point)} beside {@code click(Rect)}). Under an arity key, deleting one of two same-arity
 * overloads is a line that never changes, and rule 1 would never see it. Erased types cost a longer line and
 * buy a key that is actually unique.
 *
 * <h2>Regenerating it</h2>
 *
 * <pre>{@code mvn -pl botmaker-sdk test -Dtest=ApiSurfaceTest -Dbotmaker.api.writeSurface=true}</pre>
 *
 * <p>The rules still run, and still run against the file <em>as committed</em> — the snapshot is read before
 * it is overwritten. A regenerate is therefore not a way past them: an undeprecated removal fails the same
 * run that rewrote the file, and the rewrite is visible in {@code git diff} either way.
 *
 * <h2>The escape hatch</h2>
 *
 * <pre>{@code -Dbotmaker.api.allowUndeprecatedRemoval=com.botmaker.sdk.api.X#y,com.botmaker.sdk.api.Z}</pre>
 *
 * <p>A major version is allowed to break things outright, and a rule with no exit is a rule people delete.
 * It is named <b>element by element</b> so it can never be a blanket switch: an entry matches a full key
 * (with its parameter list) or a bare {@code type#member}, and nothing else. An unused entry is itself a
 * failure — a stale exemption is an exemption nobody re-read.
 */
class ApiSurfaceTest {

    private static final String API_PACKAGE = "com.botmaker.sdk.api";
    private static final String SINCE = Since.class.getName();
    private static final String DEPRECATED = Deprecated.class.getName();

    /** Relative to the module — surefire runs with the module directory as the working directory. */
    private static final Path SURFACE = Path.of("api-surface.txt");

    private static final boolean WRITE = Boolean.getBoolean("botmaker.api.writeSurface");
    private static final List<String> ALLOWED_REMOVALS =
            Arrays.stream(System.getProperty("botmaker.api.allowUndeprecatedRemoval", "").split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();

    /** key -> the committed line's payload; empty when the file does not exist yet (the bootstrap). */
    private static Map<String, Entry> committed;
    /** key -> this build's payload, in sorted order. */
    private static Map<String, Entry> current;

    /**
     * One line, split at the only two places anything reads it: the {@code key} the diff matches on, and the
     * flags the rules ask about. {@code returnType} is recorded and never gated — a retype is a break the
     * diff shows the reviewer, but it is not a removal and this gate is about removals.
     */
    private record Entry(String key, String returnType, boolean deprecated, String since) {

        String line() {
            return key + ":" + returnType
                    + (deprecated ? " [deprecated]" : "")
                    + (since == null ? "" : " [since=" + since + "]");
        }

        /** The bare {@code type#member} an escape-hatch entry may name instead of the full key. */
        String withoutParams() {
            int open = key.indexOf('(');
            return open < 0 ? key : key.substring(0, open);
        }
    }

    /**
     * Both surfaces, and — only if the rules already hold — the regenerated file.
     *
     * <p><b>The write is gated on the rules, and that is what makes them a gate at all.</b> Writing first
     * would leave the removed element out of the file, so the run that failed would be followed by a run
     * that passes: two commands and the window is gone, silently. Refusing to write while a rule is broken
     * means the only ways forward are the ones that were meant to exist — restore the element and deprecate
     * it, or name it in {@code allowUndeprecatedRemoval}, which is a deliberate act that shows up in the
     * release invocation.
     */
    @BeforeAll
    static void readBothSurfaces() {
        committed = parse(readCommitted());
        current = scanBuild();
        if (!WRITE) return;
        List<String> blocking = new ArrayList<>();
        if (!committed.isEmpty()) {
            blocking.addAll(removedWithoutAWindow());
            blocking.addAll(rewrittenVersions());
            blocking.addAll(newWithoutAVersion());
        }
        if (blocking.isEmpty()) write();
    }

    // ------------------------------------------------------------------
    // the rules
    // ------------------------------------------------------------------

    /**
     * 1 — an element leaves only from a release that already said it was leaving.
     *
     * <p>The window is one full release: the previous surface had to carry {@code [deprecated]}, which means
     * the previous <em>jar</em> carried {@code @Deprecated} and — by {@link ApiPointersTest}'s rule 1 — a
     * {@link ReplacedBy} beside it. So by the time the element is gone, a bot upgrading from that release has
     * both a compiler warning it could have seen and a pointer Studio can follow, and a bot upgrading from
     * further back at least has the pointer.
     */
    @Test
    void nothingIsRemovedWithoutADeprecationWindow() {
        if (bootstrapping()) return;
        assertEmpty(removedWithoutAWindow(), """
                An element left the API without a deprecation window. Put it back, mark it @Deprecated with
                a @ReplacedBy, release that, and delete it in the release after — or, for a deliberate break
                in a major, name it in -Dbotmaker.api.allowUndeprecatedRemoval=<type#member>,…:""");
    }

    /**
     * The offenders of rule 1, and the stale exemptions with them. An exemption that matched nothing is a
     * failure in its own right: it was written for one specific removal in one specific release, and left
     * behind it silently permits the next removal of anything spelled that way.
     */
    private static List<String> removedWithoutAWindow() {
        List<String> bad = new ArrayList<>();
        List<String> unusedExemptions = new ArrayList<>(ALLOWED_REMOVALS);
        for (Entry was : committed.values()) {
            if (current.containsKey(was.key())) continue;
            boolean exempt = unusedExemptions.removeIf(
                    a -> a.equals(was.key()) || a.equals(was.withoutParams()));
            if (was.deprecated() || exempt) continue;
            bad.add(was.key() + "  (removed, never deprecated)");
        }
        for (String stale : unusedExemptions) {
            bad.add(stale + "  (exempted, but nothing by that name was removed)");
        }
        return bad;
    }

    /**
     * 2 — {@link Since} is a fact about the past, so it is written once and never edited.
     *
     * <p>Both directions are failures and for the same reason. Adding one to an element already in the file
     * back-fills a version that cannot be verified — the release it names has shipped and nothing in it
     * recorded this. Changing or dropping one rewrites an answer Studio has already shown a user.
     */
    @Test
    void aVersionIsRecordedOnceAndNeverChanged() {
        if (bootstrapping()) return;
        assertEmpty(rewrittenVersions(), """
                @Since changed on an element that had already shipped. It records which release first
                contained the element, so after that release it is unrecoverable and unverifiable — restore
                the committed value, or leave it absent as it was:""");
    }

    /** The offenders of rule 2 — an element in both surfaces whose recorded version is not the same one. */
    private static List<String> rewrittenVersions() {
        List<String> bad = new ArrayList<>();
        for (Entry was : committed.values()) {
            Entry is = current.get(was.key());
            if (is == null || Objects.equals(was.since(), is.since())) continue;
            bad.add(was.key() + ": " + describe(was.since()) + " -> " + describe(is.since()));
        }
        return bad;
    }

    /**
     * 3 — a new element carries its version, because this is the only build in which anyone knows it.
     *
     * <p>Absent from the committed surface means "not in the last release", which is exactly the definition
     * of new; and the commit that adds the element is the last moment its introduction version is a fact
     * rather than an archaeology problem.
     *
     * <p>A member with no {@link Since} of its own takes its declaring type's — see {@link #inherited}. That
     * is what makes the rule satisfiable for an {@code enum}, whose {@code values()} and {@code valueOf} no
     * author can annotate.
     */
    @Test
    void everyNewElementSaysWhenItArrived() {
        if (bootstrapping()) return;
        assertEmpty(newWithoutAVersion(), """
                A new public api.* element with no @Since. Add @Since("<the version being cut>") — the
                upgrade dialog groups additions by it, and after this release the value cannot be recovered
                from anything:""");
    }

    /** The offenders of rule 3 — in this build, absent from the last release, and silent about it. */
    private static List<String> newWithoutAVersion() {
        List<String> bad = new ArrayList<>();
        for (Entry is : current.values()) {
            if (committed.containsKey(is.key()) || is.since() != null) continue;
            bad.add(is.key());
        }
        return bad;
    }

    /**
     * 4 — the file is exactly what the generator writes.
     *
     * <p>Not a rule about the API: a rule about the file. It is generated, sorted and deduplicated, so a
     * hand-edit — a line reordered, a flag typed in, a deletion made by hand rather than by deleting the
     * code — is a divergence that would quietly change what rules 1–3 compare against. Sorting also makes
     * the release diff readable, which is the only reason a human ever opens it.
     *
     * <p>The order is <b>by key</b>, not by whole line, which is why {@code Activity:class} leads its own
     * members rather than sorting under them: {@code #} precedes {@code :} in ASCII, so a line sort would
     * bury every type declaration at the bottom of its own block. Comparing the re-rendered entries also
     * checks the round trip — a flag the parser cannot read back is a line the rules would misjudge.
     */
    @Test
    void theCommittedFileIsInCanonicalForm() {
        if (bootstrapping()) return;
        List<String> lines = readCommitted();
        List<String> canonical = new TreeMap<>(committed).values().stream().map(Entry::line).toList();
        assertEquals(String.join("\n", canonical), String.join("\n", lines), """
                api-surface.txt is not in canonical form (sorted, no duplicates). It is generated — do not
                edit it by hand. Regenerate with:
                  mvn -pl botmaker-sdk test -Dtest=ApiSurfaceTest -Dbotmaker.api.writeSurface=true""");
    }

    // ------------------------------------------------------------------
    // reading the two surfaces
    // ------------------------------------------------------------------

    /**
     * True the one time the file does not exist yet. Every element would then read as new and rule 3 would
     * fail the build that is bringing the file into existence, so the rules stand down and the write path is
     * the whole of the run. The next run has a file and is gated normally.
     */
    private static boolean bootstrapping() {
        if (!committed.isEmpty()) return false;
        if (!WRITE) {
            fail("api-surface.txt is missing or empty. Generate it with:\n"
                    + "  mvn -pl botmaker-sdk test -Dtest=ApiSurfaceTest -Dbotmaker.api.writeSurface=true");
        }
        return true;
    }

    private static List<String> readCommitted() {
        if (!Files.exists(SURFACE)) return List.of();
        try {
            return Files.readAllLines(SURFACE).stream()
                    .map(String::strip)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + SURFACE.toAbsolutePath(), e);
        }
    }

    private static void write() {
        try {
            Files.write(SURFACE, current.values().stream().map(Entry::line).toList());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + SURFACE.toAbsolutePath(), e);
        }
    }

    /**
     * A line back into an {@link Entry}. The grammar has no spaces inside the key or the return type, so the
     * flags are simply what follows the first space, and the return type is what follows the key's last
     * {@code :} — a parameter list holds commas, never either.
     */
    private static Map<String, Entry> parse(List<String> lines) {
        Map<String, Entry> out = new LinkedHashMap<>();
        for (String line : lines) {
            int space = line.indexOf(' ');
            String head = space < 0 ? line : line.substring(0, space);
            String flags = space < 0 ? "" : line.substring(space + 1);
            int colon = head.lastIndexOf(':');
            if (colon < 0) throw new IllegalStateException("malformed api-surface.txt line: " + line);
            String since = null;
            int at = flags.indexOf("[since=");
            if (at >= 0) since = flags.substring(at + 7, flags.indexOf(']', at));
            Entry e = new Entry(head.substring(0, colon), head.substring(colon + 1),
                    flags.contains("[deprecated]"), since);
            out.put(e.key(), e);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // scanning this build
    // ------------------------------------------------------------------

    private static ScanResult scan;

    @AfterAll
    static void closeScan() {
        if (scan != null) scan.close();
    }

    /**
     * This build's public {@code api.*} surface, sorted. Same classpath override and same public/synthetic
     * filtering as {@link ApiPointersTest} — the two gates must agree on what "the API" is, or one of them
     * is checking a set the other does not.
     */
    private static Map<String, Entry> scanBuild() {
        scan = new ClassGraph()
                .overrideClasspath(mainClasses())
                .acceptPackages(API_PACKAGE)
                .enableClassInfo()
                .enableMethodInfo()
                .enableFieldInfo()
                .enableAnnotationInfo()
                .scan();

        Map<String, Entry> out = new TreeMap<>();
        for (ClassInfo ci : scan.getAllClasses()) {
            if (!ci.isPublic()) continue;
            String declared = since(ci.getAnnotationInfo(SINCE));
            put(out, new Entry(ci.getName(), kindOf(ci), ci.hasAnnotation(DEPRECATED), declared));

            for (MethodInfo mi : ci.getDeclaredMethodAndConstructorInfo()) {
                if (!mi.isPublic() || mi.isSynthetic() || mi.isBridge()) continue;
                put(out, new Entry(ci.getName() + "#" + mi.getName() + params(mi),
                        mi.getTypeDescriptor().getResultType().toString(),
                        mi.hasAnnotation(DEPRECATED), inherited(since(mi.getAnnotationInfo(SINCE)), declared)));
            }
            for (FieldInfo fi : ci.getDeclaredFieldInfo()) {
                if (!fi.isPublic() || fi.isSynthetic()) continue;
                put(out, new Entry(ci.getName() + "#" + fi.getName(),
                        fi.getTypeDescriptor().toString(),
                        fi.hasAnnotation(DEPRECATED), inherited(since(fi.getAnnotationInfo(SINCE)), declared)));
            }
        }
        return out;
    }

    /**
     * A member's own {@link Since}, or its type's when it has none.
     *
     * <p>Rule 3 asks a new element when it arrived, and there are members no author can answer for: an
     * {@code enum}'s {@code values()} and {@code valueOf(String)} are written by javac and cannot be
     * annotated, so the first enum added to the API would fail a gate with no way to satisfy it. Falling back
     * to the declaring type is the truthful answer anyway — a member declared on a type that arrived in
     * {@code 1.1.0} arrived in {@code 1.1.0} — and it spares every member of a new type an annotation that
     * would only repeat the one above it.
     *
     * <p>It cannot launder a back-fill past rule 2, which is what makes it safe: every type that shipped
     * before this gate existed carries no {@link Since} at all, so what its members inherit is {@code null} —
     * exactly what the committed file already records for them.
     */
    private static String inherited(String own, String declaringType) {
        return own != null ? own : declaringType;
    }

    /**
     * Two elements cannot share a key: the key is the identity everything above rests on, so a collision
     * would silently drop one of them from the surface and hide its removal forever. Erased parameter types
     * make this unreachable in Java — javac refuses the duplicate first — which is exactly why it is worth
     * asserting rather than assuming.
     */
    private static void put(Map<String, Entry> out, Entry e) {
        Entry clash = out.putIfAbsent(e.key(), e);
        if (clash != null) throw new IllegalStateException("two api.* elements share a key: " + e.key());
    }

    private static String params(MethodInfo mi) {
        StringJoiner joiner = new StringJoiner(",", "(", ")");
        for (MethodParameterInfo p : mi.getParameterInfo()) joiner.add(p.getTypeDescriptor().toString());
        return joiner.toString();
    }

    /** What a type <em>is</em>, standing where a member's return type stands. */
    private static String kindOf(ClassInfo ci) {
        if (ci.isAnnotation()) return "@interface";
        if (ci.isEnum()) return "enum";
        if (ci.isRecord()) return "record";
        if (ci.isInterface()) return "interface";
        return "class";
    }

    private static String since(AnnotationInfo ai) {
        if (ai == null) return null;
        Object v = ai.getParameterValues(true).getValue("value");
        return v == null ? null : v.toString().trim();
    }

    private static String mainClasses() {
        try {
            return Path.of(ReplacedBy.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the compiled api.* output", e);
        }
    }

    // ------------------------------------------------------------------
    // small helpers
    // ------------------------------------------------------------------

    private static String describe(String since) {
        return since == null ? "no @Since" : "@Since(\"" + since + "\")";
    }

    /** Offenders first, explanation second — surefire's one-line summary is cut at the first newline. */
    private static void assertEmpty(List<String> bad, String explanation) {
        if (bad.isEmpty()) return;
        List<String> head = bad.size() > 4 ? bad.subList(0, 4) : bad;
        String headline = String.join("; ", head)
                + (bad.size() > head.size() ? "; …and " + (bad.size() - head.size()) + " more" : "");
        fail(headline + "\n" + explanation + "\n  " + String.join("\n  ", bad));
    }
}
