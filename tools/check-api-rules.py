#!/usr/bin/env python3
"""Enforce the SDK's API compatibility rules against a japicmp report.

See ../../docs/refactor/21-api-compat.md (umbrella) for the contract. This script answers the one
question an annotation cannot answer on its own:

    A member may only be removed if the PREVIOUSLY RELEASED jar marked it
    @Deprecated(since = "...", forRemoval = true).

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
    1  incompatible changes, ALL of them properly deprecated first.
       Legal, but only in a MAJOR release. The caller knows the version; this script does not.
    2  rule violation: something was removed that was never announced. Wrong at every version.
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

DEPRECATED = "java.lang.Deprecated"

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

    violations, breaking = [], []

    for klass in root.iter("class"):
        owner = klass.get("fullyQualifiedName", "?")
        removed_class = klass.get("changeStatus") == "REMOVED"

        if is_breaking(klass) and removed_class:
            (violations if not was_marked_for_removal(klass) else breaking).append(f"type         {owner}")

        for tag in MEMBER_TAGS:
            for member in klass.iterfind(f"./{tag}s/{tag}"):
                if not is_breaking(member):
                    continue
                line = describe(owner, member)
                if member.get("changeStatus") == "REMOVED" and not was_marked_for_removal(member):
                    violations.append(line)
                else:
                    breaking.append(line)

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
            print(f"    {line}")
        return 1

    print("API check: no incompatible changes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
