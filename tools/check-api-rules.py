#!/usr/bin/env python3
"""Enforce the SDK's API compatibility rules against a japicmp report.

See ../../docs/refactor/21-api-compat.md (umbrella) for the contract. This script answers the two
questions that are wrong at EVERY version — so neither depends on the number being released, and both
can therefore be asked on a pull request, long before anyone decides what to call the result.

RULE 1 — nothing disappears unannounced.

    A member may only be removed if the PREVIOUSLY RELEASED jar marked it
    @Deprecated(since = "...", forRemoval = true).

RULE 2 — nothing breaks without a way through it.

    Every binary-incompatible change must be named either by a recipe in
    src/main/resources/META-INF/rewrite/botmaker-sdk.yml (it can be repaired automatically) or by an
    entry in src/main/resources/META-INF/botmaker/upgrade-notes.json (it cannot, and here is what to
    tell the user). Shipping a break with neither is shipping a bot project nobody can move forward.

An annotation lives *on* the member, so deleting the member deletes the evidence. @Deprecated is a
promise about the future; "this used to exist" is a fact about the past, and only the previous artifact
holds it. japicmp puts that history in its report — a REMOVED method keeps the old jar's annotations —
so the rule is readable from `target/japicmp/japicmp.xml`.

Why not japicmp's own `postAnalysisScript` (Groovy), which is the natural home for this: japicmp 0.26.1
bundles a Groovy that cannot read Java 26 class files ("Unsupported class file major version 70"), and
this project's JDK is 26 while CI runs 21. A rule that passes in CI and explodes on the maintainer's
machine is worse than no rule, and pinning a newer Groovy just moves the same race to the next JDK.
Parsing the XML is version-proof and needs nothing but the standard library.

Exit codes are the interface — `release.sh` and `ci.yml` both branch on them:

    0  no incompatible changes; any version bump is honest.
    1  incompatible changes, ALL of them properly deprecated first AND covered by a migration.
       Legal, but only in a MAJOR release. The caller knows the version; this script does not.
    2  RULE 1 violation: something was removed that was never announced.
    3  RULE 2 violation: something broke with no recipe and no note.

2 and 3 are both wrong at every version, so ci.yml fails the build on either. When both are present the
script prints both reports and returns 2 — a member that should never have been deleted is not made
better by writing a recipe for it.
"""

import fnmatch
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

DEPRECATED = "java.lang.Deprecated"

# Where the two migration files live, relative to this script (tools/ sits beside src/).
RESOURCES = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
RECIPES = RESOURCES / "META-INF" / "rewrite" / "botmaker-sdk.yml"
NOTES = RESOURCES / "META-INF" / "botmaker" / "upgrade-notes.json"

# Recipe option keys that name a piece of the OLD API. Kept as an explicit list rather than "any key
# starting with old" so that adding a recipe type is a deliberate edit here — a silently unrecognised
# key would read as "not covered" and fail a build for the wrong reason.
TYPE_KEYS = ("oldFullyQualifiedTypeName",)
PACKAGE_KEYS = ("oldPackageName",)
PATTERN_KEYS = ("methodPattern",)

# The member kinds japicmp nests under a <class>. Constructors have no meaningful name of their own in
# the report, so they are labelled by their owning class.
MEMBER_TAGS = ("method", "constructor", "field")


def was_marked_for_removal(element):
    """True if this element carried @Deprecated(forRemoval = true) in the OLD jar.

    The value is read from <oldElementValues>, never <newElementValues>: for a removed member there is
    no new side, and for a member that merely changed we still care about what the released jar
    promised, not what this working tree says.
    """
    for annotation in element.iterfind("./annotations/annotation"):
        if annotation.get("fullyQualifiedName") != DEPRECATED:
            continue
        for el in annotation.iterfind("./elements/element"):
            if el.get("name") != "forRemoval":
                continue
            for value in el.iterfind("./oldElementValues/oldElementValue"):
                if value.get("value") == "true":
                    return True
    return False


def is_breaking(element):
    """japicmp's own verdict, not our pattern-matching.

    This matters more than it looks. Recompiling renumbers synthetic lambda methods
    (`lambda$untilFindAll$1`), so they appear as REMOVED on an otherwise untouched class. They are
    binary-compatible, and trusting japicmp to say so is more reliable than guessing every synthetic
    shape javac emits.
    """
    return element.get("binaryCompatible") == "false"


class Coverage:
    """The set of old-API names some migration claims to handle.

    Deliberately coarse: it asks "does anything at all mention this member", not "does the recipe
    repair it correctly" — no static check can answer the second, and the end-to-end test on a real bot
    project is what does. What this catches is the failure that actually happens: breaking something and
    forgetting to write the migration at all.

    The known limit, so nobody is surprised by it: coverage is matched against the WHOLE file, not
    against the recipe for the version being cut, so a member named by an older release's recipe would
    also satisfy this. That needs a member to be broken twice under the same name in two different
    releases, and japicmp only ever reports the diff against the immediately previous release, so the
    window is narrow. The cost of being wrong is a missed reminder, never a false alarm.
    """

    def __init__(self, types, packages, members):
        self.types, self.packages, self.members = types, packages, members

    def covers_type(self, fqcn):
        return (any(fnmatch.fnmatch(fqcn, p) for p in self.types)
                or any(fqcn.startswith(pkg + ".") for pkg in self.packages))

    def covers_member(self, fqcn, name):
        if self.covers_type(fqcn):
            return True
        return any(fnmatch.fnmatch(fqcn, t) and fnmatch.fnmatch(name, m) for t, m in self.members)


def parse_method_pattern(pattern):
    """`com.botmaker.sdk.api.interaction.Wait seconds(..)` -> ('com...Wait', 'seconds').

    OpenRewrite's grammar is `<type> <name>(<args>)`, both halves allowing `*` wildcards, which is why
    the caller matches with fnmatch rather than equality. Argument types are irrelevant here: japicmp
    reports a member by name, and a recipe that names the method at all is claiming the method.
    """
    head, _, tail = pattern.strip().partition(" ")
    name = tail.partition("(")[0].strip()
    return (head.strip(), name) if head and name else None


def load_coverage(recipes=RECIPES, notes=NOTES):
    """Read both migration files into one Coverage. Missing files mean 'nothing is covered'."""
    types, packages, members = set(), set(), set()

    if recipes.is_file():
        try:
            import yaml
        except ImportError:  # the one non-stdlib import; say so rather than dying on a traceback.
            raise SystemExit(
                "error: PyYAML is required to read META-INF/rewrite/botmaker-sdk.yml.\n"
                "       install it with:  python3 -m pip install pyyaml\n"
                "       (ci.yml installs it; release.sh reports this and stops.)")

        for doc in yaml.safe_load_all(recipes.read_text(encoding="utf-8")):
            if not isinstance(doc, dict):
                continue
            for entry in doc.get("recipeList") or []:
                if not isinstance(entry, dict):
                    continue  # a bare recipe name takes no options, so it names no API member.
                for options in entry.values():
                    if not isinstance(options, dict):
                        continue
                    for key in TYPE_KEYS:
                        if options.get(key):
                            types.add(str(options[key]))
                    for key in PACKAGE_KEYS:
                        if options.get(key):
                            packages.add(str(options[key]).rstrip("."))
                    for key in PATTERN_KEYS:
                        parsed = options.get(key) and parse_method_pattern(str(options[key]))
                        if parsed:
                            members.add(parsed)

    if notes.is_file():
        data = json.loads(notes.read_text(encoding="utf-8"))
        for entries in (data.get("versions") or {}).values():
            for entry in entries or []:
                member = (entry or {}).get("member", "")
                owner, sep, name = member.partition("#")
                if not owner:
                    continue
                (members.add((owner, name)) if sep else types.add(owner))

    return Coverage(types, packages, members)


def describe(owner, element):
    tag = element.tag
    if tag == "constructor":
        return f"constructor  {owner}(…)"
    return f"{tag:<12} {owner}#{element.get('name')}"


def main():
    report = Path(sys.argv[1] if len(sys.argv) > 1 else "target/japicmp/japicmp.xml")
    if not report.is_file():
        print(f"error: no japicmp report at {report}", file=sys.stderr)
        print("       run: mvn -pl botmaker-sdk -Papi-check verify -Dbotmaker.api.oldVersion=<tag>",
              file=sys.stderr)
        return 2

    root = ET.parse(report).getroot()
    old_jar = root.get("oldVersion") or root.get("oldJar") or "the previous release"

    coverage = load_coverage()
    violations, breaking, uncovered = [], [], []

    def record(line, element, owner, name):
        """Sort one breaking change into the two rules. Order matters: a member that was removed without
        being announced is reported under RULE 1 only — telling someone to also write a recipe for a
        deletion they should not have made is noise on top of the real problem."""
        if element.get("changeStatus") == "REMOVED" and not was_marked_for_removal(element):
            violations.append(line)
            return
        breaking.append(line)
        covered = coverage.covers_type(owner) if name is None else coverage.covers_member(owner, name)
        if not covered:
            uncovered.append(line)

    for klass in root.iter("class"):
        owner = klass.get("fullyQualifiedName", "?")

        if is_breaking(klass) and klass.get("changeStatus") == "REMOVED":
            record(f"type         {owner}", klass, owner, None)

        for tag in MEMBER_TAGS:
            for member in klass.iterfind(f"./{tag}s/{tag}"):
                if not is_breaking(member):
                    continue
                # japicmp names a constructor after its class; upgrade-notes.json spells that `<init>`,
                # matching the JVM, so that a note can single out a constructor from its methods.
                name = "<init>" if tag == "constructor" else member.get("name")
                record(describe(owner, member), member, owner, name)

    if violations:
        print(f"\nRemoved without a deprecation cycle — {len(violations)}:")
        for line in violations:
            print(f"    {line}")
        print(f"\nEach one is gone from this build but was NOT marked "
              f"@Deprecated(since = \"...\", forRemoval = true) in {old_jar}.")
        print("Deprecate them in a MINOR release first, naming the replacement in a Javadoc @deprecated")
        print("line, then remove them in the next MAJOR one.  See docs/refactor/21-api-compat.md.")
        return 2

    if breaking:
        print(f"\nBreaking changes — {len(breaking)} (each was deprecated first, so this is legal in a "
              f"MAJOR release):")
        for line in breaking:
            print(f"    {line}" + ("" if line not in uncovered else "   <-- no migration"))

    if uncovered:
        print(f"\nNo migration path — {len(uncovered)}:")
        for line in uncovered:
            print(f"    {line}")
        print("\nEach one breaks a bot that compiles today, and nothing tells its author what to do.")
        print("Add a recipe naming it to src/main/resources/META-INF/rewrite/botmaker-sdk.yml so")
        print("`mvn rewrite:run` repairs the call sites, or — if the repair needs a human decision —")
        print("describe it in src/main/resources/META-INF/botmaker/upgrade-notes.json.")
        print("See docs/refactor/21-api-compat.md §4.")
        return 3

    if breaking:
        return 1

    print("API check: no incompatible changes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
