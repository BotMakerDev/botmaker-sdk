package com.botmaker.sdk.api;

import com.botmaker.sdk.api.meta.Palette;
import com.botmaker.sdk.api.meta.ReplacedBy;
import com.botmaker.sdk.api.meta.Replaces;
import com.botmaker.sdk.api.meta.Scaffolding;
import com.botmaker.sdk.api.meta.Since;
import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
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
 * <p>Rules 1–5 are the pointer pair: a deprecated element says what to use instead, every target exists, every
 * back-edge is written, no entry is claimed twice <em>undeclared</em>, every entry parses. Rules 7–9 cover the
 * three annotations added beside them — {@link Since} is well-formed, a {@code behaviourChanged} move carries
 * its sentence at whichever end asserts it, and a deprecated {@link Scaffolding} element names a real survivor
 * because generated code cannot take a default. Rule 10 covers {@link Palette}: curation is per type, so
 * annotating methods inside a type that is not itself annotated is a no-op nobody would notice. Rule 11 covers
 * the split — a {@code @ReplacedBy} naming several candidates has to say when each one applies. Rule 12 is the
 * only one that reads a file: {@code @Scaffolding} is a claim about what <em>Studio</em> generates, so the two
 * copies are compared through a committed {@code scaffolding-surface.txt} that Studio's own test writes.
 *
 * <h2>A pointer is a set, not a value</h2>
 *
 * <p>{@link ReplacedBy#value()} is a {@code String[]}, so rules 2, 3 and 9 run <b>per candidate</b> and rule 4
 * is no longer flat: two survivors claiming one old spelling is precisely what a split looks like from the
 * back edge, and it is legal exactly when the old element declares those two. The ordinary one-target pointer
 * is the degenerate case of all of it and is checked exactly as it was.
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
    private static final String PALETTE = Palette.class.getName();
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
    /**
     * One public {@code api.*} element as the rules see it.
     *
     * <p>{@code replacedBy} is {@code null} when the annotation is absent and a (possibly empty) list when it
     * is present — the distinction rule 1 rests on. {@code note}/{@code behaviourChanged} come from
     * {@code @ReplacedBy} and {@code replacesNote}/{@code replacesBehaviourChanged} from {@code @Replaces};
     * they are kept apart because the two are read out of different jars and a rule that checks one is not
     * checking the other. {@code params} is the parameter count of a method or constructor and {@code -1} for
     * a type or field — it is what lets rule 5 verify an entry's optional arity.
     */
    private record Element(String ref, String kind, boolean deprecated,
                           List<String> replacedBy, List<String> whens, String note, boolean behaviourChanged,
                           List<String> replaces, String replacesNote, boolean replacesBehaviourChanged,
                           String since, boolean scaffolding, boolean palette, int params) {

        /** The targets that actually name something — a blank entry is the "nothing takes my place" form. */
        List<String> targets() {
            return replacedBy == null ? List.of()
                    : replacedBy.stream().filter(t -> !t.isBlank()).toList();
        }
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

    /**
     * 2 — a pointer that names something names something that is here. <b>Per candidate</b>: a split is
     * only as good as its weakest target, and one unresolvable candidate in a menu of two is a menu entry
     * that cannot be chosen.
     */
    @Test
    void everyPointerTargetResolves() {
        List<String> bad = new ArrayList<>();
        for (Element e : deprecated()) {
            for (String target : e.targets()) {
                if (target.indexOf('@') >= 0) {
                    bad.add(e.ref() + " -> " + target + "  (a @ReplacedBy target carries no @version; only @Replaces entries do)");
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
            for (String target : e.targets()) {
                if (!byRef.containsKey(target)) continue; // check 2 owns this
                boolean claimed = byRef.get(target).stream()
                        .flatMap(t -> t.replaces().stream())
                        .map(ApiPointersTest::nameOf)
                        .anyMatch(e.ref()::equals);
                if (!claimed) bad.add(target + " does not @Replaces " + e.ref());
            }
        }
        assertEmpty(bad, """
                A @ReplacedBy with no matching @Replaces on the other end. Add @Replaces("<old>@<version>") to
                the target, naming the version this deprecation ships in. The forward pointer is readable only
                while the deprecated element still exists; the back-edge is what a bot upgrading past its
                deletion reads instead:""");
    }

    /**
     * 4 — one old spelling, at one era, belongs to one survivor <em>unless the old element says otherwise</em>.
     *
     * <p>This rule used to be flat: two claims on one {@code name@version} was an error, full stop. That
     * refused the one shape the back edge exists to carry. A <b>split</b> — {@code @ReplacedBy} naming two
     * targets — <em>is</em> two survivors claiming one old spelling, and once the old member is finally
     * deleted the pair of claims is the only place the split still exists. Refusing it would mean a split
     * readable during the deprecation window and unreadable forever after.
     *
     * <p>So a double claim is legal exactly when the claimed element is still in this build and its own
     * {@code @ReplacedBy} lists <b>precisely</b> those claimants — no more, no fewer. That is checkable here,
     * while both ends are compilable, which is the whole design of this gate. Every other double claim is
     * still an error: Studio reads an undeclared contested entry as unpaired, so it silently loses the
     * redirect for both claimants.
     */
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
            if (refs.size() <= 1) return;
            List<Element> old = byRef.get(nameOf(entry));
            Set<String> declared = new LinkedHashSet<>();
            if (old != null) old.forEach(o -> declared.addAll(o.targets()));
            if (declared.equals(refs)) return; // a declared split — the back edge of the fan-out
            bad.add(entry + " claimed by " + String.join(" and ", refs)
                    + (old == null ? "  (and " + nameOf(entry) + " is not in this build to declare the split)"
                                   : "  (whose @ReplacedBy names " + (declared.isEmpty() ? "nothing" : String.join(" and ", declared)) + ")"));
        });
        assertEmpty(bad, """
                Two different elements @Replaces the same name@version without that element declaring the
                split. A double claim is legal only when the claimed element's own @ReplacedBy lists exactly
                those claimants; otherwise Studio treats the entry as contested, which is unpaired (default
                value + review mark). Declare the split at the other end, keep one claim, or move one to a
                different version:""");
    }

    /**
     * 5 — every entry parses, carries a real version, and does not quietly shadow something that still works.
     *
     * <p>The last clause is the interesting one: an entry naming a <em>live</em> element is fine during a
     * deprecation window — the old member is still here, that is the window — but only if that member is
     * actually deprecated and pointing back. An entry naming a healthy, undeprecated element is a claim to
     * take over something nobody is giving up.
     *
     * <p>The optional <b>arity</b> — {@code fqn#member(2)@1.2.0} — is checked the same way and only while it
     * can be: it exists precisely for the case where the overload it names is already deleted, and then there
     * is nothing to compare it against. While the old member <em>is</em> still in this build, an arity that
     * matches none of its overloads is a typo the gate can see, so it does.
     */
    @Test
    void everyEntryIsWellFormed() {
        List<String> bad = new ArrayList<>();
        for (Element e : elements) {
            for (String entry : e.replaces()) {
                int at = entry.lastIndexOf('@');
                if (at <= 0 || at == entry.length() - 1) {
                    bad.add(e.ref() + ": \"" + entry + "\" — expected <fqn[#member][(arity)]>@<version>");
                    continue;
                }
                String version = entry.substring(at + 1);
                if (!SEMVER.matcher(version).matches()) {
                    bad.add(e.ref() + ": \"" + entry + "\" — \"" + version + "\" is not a semver");
                }
                String name = nameOf(entry);
                int arity = arityOf(entry);
                if (arity == BAD_ARITY) {
                    bad.add(e.ref() + ": \"" + entry + "\" — the arity between the parentheses is not a number");
                    continue;
                }
                List<Element> live = byRef.get(name);
                if (live == null) continue;
                boolean handedOver = live.stream()
                        .anyMatch(l -> l.deprecated() && l.targets().contains(e.ref()));
                if (!handedOver) {
                    bad.add(e.ref() + ": \"" + entry + "\" names " + name + ", which is still live and is "
                            + "not @Deprecated + @ReplacedBy(\"" + e.ref() + "\")");
                }
                if (arity >= 0 && live.stream().noneMatch(l -> l.params() == arity)) {
                    bad.add(e.ref() + ": \"" + entry + "\" names arity " + arity + ", which " + name
                            + " has no overload of in this build");
                }
            }
        }
        assertEmpty(bad, """
                A malformed or over-reaching @Replaces entry. Every entry is <fqn[#member][(arity)]>@<version>,
                where the version is the last release that spelling existed in; an entry may name an element of
                this build only while that element is the deprecated one pointing back at the claimant, and an
                arity written while that element is still here must match one of its overloads:""");
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
     *
     * <p><b>Both ends carry the flag and the note</b>, because the two are read out of different jars and only
     * one of them survives the deletion of the deprecated element. So both are checked, separately: a
     * {@code @Replaces(behaviourChanged = true)} with a blank {@code @Replaces} note is exactly as useless to
     * the bot that arrives late as the forward version is to the bot that arrives on time, and the forward
     * note cannot rescue it — that jar is gone by then.
     */
    @Test
    void aBehaviourChangeCarriesItsSentence() {
        List<String> bad = new ArrayList<>();
        for (Element e : elements) {
            if (e.behaviourChanged() && e.note().isBlank()) {
                bad.add(e.kind() + " " + e.ref() + " (@ReplacedBy)");
            }
            if (e.replacesBehaviourChanged() && e.replacesNote().isBlank()) {
                bad.add(e.kind() + " " + e.ref() + " (@Replaces)");
            }
        }
        assertEmpty(bad, """
                behaviourChanged = true with no note, on the annotation named in brackets. The flag makes
                Studio mark every redirected call site for review even where the shape did not move; the note
                is the only thing that tells the user what to look for. Each end needs its own — they are read
                from different jars and the forward one does not outlive the element it sits on. Add
                note = "…", in the second person, a sentence or two:""");
    }

    /**
     * 11 — a split states the condition each candidate applies under.
     *
     * <p>{@link ReplacedBy#whens()} is a sentence per candidate, in {@code value()}'s order. Empty is the
     * normal state of an ordinary one-target pointer and stays legal. What is refused is a <b>split</b> — two
     * or more candidates — whose {@code whens()} is missing or partly blank, because the dialog then asks
     * someone to pick between two method names on no information at all. The names say what each candidate is
     * called; only this says <em>when</em> it is the right one, which is the entire question being asked.
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
            if (e.targets().isEmpty()) bad.add(e.kind() + " " + e.ref());
        }
        assertEmpty(bad, """
                A @Deprecated @Scaffolding element with no replacement. Studio writes this element into the
                files it generates, which are regenerated rather than migrated — so "nothing takes my place"
                is not a repair here, it is a refusal to upgrade or to edit the Activity Flow. Name the
                survivor, or drop @Scaffolding once no generator emits it:""");
    }

    /**
     * 10 — a curated method lives in a curated type.
     *
     * <p>{@link Palette} is read strictly, but only per <em>type</em>: a facade that does not carry it is
     * uncurated, and Studio offers every one of its public static methods exactly as it did before the
     * annotation existed. That is deliberate — it is what lets the sweep proceed one facade at a time — but it
     * makes one mistake completely silent. Annotating a handful of overloads inside a facade whose type
     * declaration was never annotated hides nothing and shows nothing: the menu is unchanged, and the author
     * who wrote twelve {@code @Palette}s is looking at all fifty-four methods wondering why.
     *
     * <p>So the half-finished state is the error, not the unstarted one. A type with no {@code @Palette}
     * anywhere is fine; a type whose methods carry it while the type does not is a facade someone began
     * curating and did not finish.
     */
    @Test
    void curatedMethodsLiveInACuratedType() {
        Set<String> curatedTypes = new LinkedHashSet<>();
        for (Element e : elements) {
            if ("type".equals(e.kind()) && e.palette()) curatedTypes.add(e.ref());
        }
        List<String> bad = new ArrayList<>();
        for (Element e : elements) {
            if (!e.palette() || "type".equals(e.kind())) continue;
            String owner = e.ref().substring(0, e.ref().indexOf('#'));
            if (!curatedTypes.contains(owner)) bad.add(e.kind() + " " + e.ref());
        }
        assertEmpty(bad, """
                @Palette on a method whose declaring type is not @Palette. An uncurated type offers every
                public static method it has, so these annotations change nothing and no menu will reflect
                them. Annotate the type to switch it into strict mode — at which point ONLY the annotated
                overloads are offered — or remove these:""");
    }

    /**
     * 12 — the {@code @Scaffolding} set is exactly what Studio's generators emit.
     *
     * <p>{@code @Scaffolding} is a claim about a repository this one cannot see: it says <em>Studio writes
     * this element into the files it generates</em>, and the dependency runs the other way — Studio compiles
     * against the SDK, never the reverse. So the annotation is a second copy of a fact that lives in Studio's
     * text blocks, and until this rule existed nothing kept the two in step. A member that stopped being
     * generated kept its annotation and went on blocking upgrades for a reason that had ceased to be true; a
     * member a new generator started writing carried none, so rule 9 never asked its author for a survivor and
     * the upgrade broke a generated file mid-apply.
     *
     * <p>Neither side can read the other, and comparing the annotations with themselves proves nothing — so
     * the expectation is a file. {@code botmaker-studio}'s {@code ScaffoldSurfaceTest} holds the truth (it
     * parses the generators' actual output with JDT and asserts its own declaration matches), and writes it to
     * {@code botmaker-sdk/scaffolding-surface.txt}. This rule reads that file back. A change to the scaffold
     * is therefore one commit touching both repositories, which is what it always was — the file just makes
     * the second half fail loudly instead of silently.
     *
     * <p>A line is {@code fqn} for a type and {@code fqn#member(params)} for a method or constructor, the
     * count being the <b>declared</b> parameter count — what {@link MethodInfo#getParameterInfo()} yields
     * here, and what a varargs member has regardless of how few arguments a generator passes it. Studio
     * resolves each of its call sites to that number before writing the file, precisely because this end has
     * no call site to count.
     */
    @Test
    void theScaffoldingSetMatchesStudiosDeclaration() {
        List<String> expected = scaffoldingSurfaceFile();
        List<String> annotated = new ArrayList<>();
        for (Element e : elements) {
            if (!e.scaffolding()) continue;
            annotated.add(e.params() >= 0 ? e.ref() + "(" + e.params() + ")" : e.ref());
        }
        annotated.sort(String::compareTo);

        List<String> bad = new ArrayList<>();
        for (String line : annotated) {
            if (!expected.contains(line)) bad.add("@Scaffolding but no generator emits it: " + line);
        }
        for (String line : expected) {
            if (!annotated.contains(line)) bad.add("emitted by a generator but not @Scaffolding: " + line);
        }
        assertEmpty(bad, """
                @Scaffolding disagrees with botmaker-studio's ScaffoldSurface. The two are copies of one
                fact — which SDK elements Studio writes into generated files — and this file is how they are
                compared across two repositories that cannot read each other. Add or remove the annotation
                here, or (if the generator really changed) update ScaffoldSurface in Studio and regenerate:
                mvn -pl botmaker-studio test -Dtest=ScaffoldSurfaceTest -Dbotmaker.scaffold.writeSurface=true""");
    }

    /**
     * The committed expectation, one line per element. Unlike Studio's side — which skips the comparison in a
     * standalone checkout where no sibling SDK exists — a missing file here is a failure: it lives in this
     * repository, so it is either present or somebody deleted it.
     */
    private static List<String> scaffoldingSurfaceFile() {
        Path file = Path.of("scaffolding-surface.txt");
        if (!Files.exists(file)) {
            fail("scaffolding-surface.txt is missing from the module root. It is committed here and written by "
                    + "botmaker-studio: mvn -pl botmaker-studio test -Dtest=ScaffoldSurfaceTest "
                    + "-Dbotmaker.scaffold.writeSurface=true");
        }
        try {
            return Files.readAllLines(file).stream().map(String::trim).filter(l -> !l.isEmpty()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file.toAbsolutePath(), e);
        }
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
            AnnotationInfo typeClaims = ci.getAnnotationInfo(REPLACES);
            add(new Element(ci.getName(), "type", ci.hasAnnotation(DEPRECATED),
                    pointer(typePointer), whens(typePointer), note(typePointer), behaviourChanged(typePointer),
                    entries(typeClaims), note(typeClaims), behaviourChanged(typeClaims),
                    since(ci.getAnnotationInfo(SINCE)), ci.hasAnnotation(SCAFFOLDING),
                    ci.hasAnnotation(PALETTE), -1));

            for (MethodInfo mi : ci.getDeclaredMethodAndConstructorInfo()) {
                if (!mi.isPublic() || mi.isSynthetic() || mi.isBridge()) continue;
                AnnotationInfo p = mi.getAnnotationInfo(REPLACED_BY);
                AnnotationInfo c = mi.getAnnotationInfo(REPLACES);
                add(new Element(ci.getName() + "#" + mi.getName(),
                        mi.isConstructor() ? "constructor" : "method",
                        mi.hasAnnotation(DEPRECATED),
                        pointer(p), whens(p), note(p), behaviourChanged(p),
                        entries(c), note(c), behaviourChanged(c),
                        since(mi.getAnnotationInfo(SINCE)), mi.hasAnnotation(SCAFFOLDING),
                        mi.hasAnnotation(PALETTE), mi.getParameterInfo().length));
            }
            for (FieldInfo fi : ci.getDeclaredFieldInfo()) {
                if (!fi.isPublic() || fi.isSynthetic()) continue;
                AnnotationInfo p = fi.getAnnotationInfo(REPLACED_BY);
                AnnotationInfo c = fi.getAnnotationInfo(REPLACES);
                add(new Element(ci.getName() + "#" + fi.getName(), "field",
                        fi.hasAnnotation(DEPRECATED),
                        pointer(p), whens(p), note(p), behaviourChanged(p),
                        entries(c), note(c), behaviourChanged(c),
                        since(fi.getAnnotationInfo(SINCE)), fi.hasAnnotation(SCAFFOLDING),
                        false, -1)); // @Palette does not target fields — a constant is never a menu entry.
            }
        }
    }

    private static void add(Element e) {
        elements.add(e);
        byRef.computeIfAbsent(e.ref(), k -> new ArrayList<>()).add(e);
    }

    /**
     * The {@code @ReplacedBy} targets, in declared preference order: {@code null} when the annotation is
     * absent, an empty list when it is present with no value. That distinction is rule 1's whole subject — an
     * omission and a deliberate dead end have to be told apart — so unlike {@link #note} this one keeps it.
     *
     * <p>The value is an array as of the split widening, and a single-target {@code @ReplacedBy("…")} reaches
     * the bytecode as a one-element array, so both forms arrive here identically.
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

    /**
     * The author's sentence on a {@code @ReplacedBy} <em>or</em> a {@code @Replaces} — both declare
     * {@code note()}, and for the same purpose at opposite ends of the move. {@code ""} both when the
     * annotation is absent and when it carries no note: the two are the same thing to every rule below, since
     * there is nothing to check about a sentence nobody wrote.
     */
    private static String note(AnnotationInfo ai) {
        if (ai == null) return "";
        Object v = ai.getParameterValues(true).getValue("note");
        return v == null ? "" : v.toString().trim();
    }

    /** The {@code behaviourChanged} flag on either annotation; false when absent, as the default is. */
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
        return ai == null ? List.of() : strings(ai, "value");
    }

    // ------------------------------------------------------------------
    // small helpers
    // ------------------------------------------------------------------

    private static List<Element> deprecated() {
        return elements.stream().filter(Element::deprecated).toList();
    }

    /**
     * The name half of a {@code name[(arity)]@version} entry — the version <em>and</em> the optional arity
     * stripped, so it can be looked up in {@link #byRef}, which keys on the bare {@code fqn[#member]}.
     */
    private static String nameOf(String entry) {
        int at = entry.lastIndexOf('@');
        String name = at <= 0 ? entry : entry.substring(0, at);
        int open = name.lastIndexOf('(');
        return open <= 0 || !name.endsWith(")") ? name : name.substring(0, open);
    }

    /** {@link #arityOf} for an entry that carries no arity — the ordinary case, and not an error. */
    private static final int NO_ARITY = -1;
    /** {@link #arityOf} for parentheses holding something that is not a number. Rule 5 reports it. */
    private static final int BAD_ARITY = -2;

    /**
     * The optional parameter count in a {@code fqn#member(2)@1.2.0} entry. It exists because this end may
     * name an overload that no longer exists to be counted — see {@link Replaces} — so it is written by hand
     * and therefore worth parsing strictly.
     */
    private static int arityOf(String entry) {
        int at = entry.lastIndexOf('@');
        String name = at <= 0 ? entry : entry.substring(0, at);
        int open = name.lastIndexOf('(');
        if (open <= 0 || !name.endsWith(")")) return NO_ARITY;
        try {
            return Integer.parseInt(name.substring(open + 1, name.length() - 1).trim());
        } catch (NumberFormatException e) {
            return BAD_ARITY;
        }
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
