package com.botmaker.sdk.apicheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enforces the SDK's API compatibility rules against a japicmp report.
 *
 * <p>See {@code ../docs/refactor/21-api-compat.md} (umbrella) for the contract. This answers the two questions
 * that are wrong at <em>every</em> version — so neither depends on the number being released, and both can
 * therefore be asked on a pull request, long before anyone decides what to call the result.
 *
 * <h2>Rule 1 — nothing disappears unannounced</h2>
 *
 * <p>A member may only be removed if the <b>previously released jar</b> marked it
 * {@code @Deprecated(since = "...", forRemoval = true)}. An annotation lives <em>on</em> the member, so
 * deleting the member deletes the evidence: {@code @Deprecated} is a promise about the future, while "this
 * used to exist" is a fact about the past that only the previous artifact holds. japicmp puts that history in
 * its report — a REMOVED member keeps the old jar's annotations — so the rule is readable from
 * {@code target/japicmp/japicmp.xml}.
 *
 * <h2>Rule 2 — nothing breaks without a way through it</h2>
 *
 * <p>Every binary-incompatible change must be named by an entry in
 * {@code src/main/resources/META-INF/botmaker/migrations.json}, carrying either a {@code fix} (Studio repairs
 * the call sites) or a {@code manual} sentence (it cannot, and here is what to tell the user). Shipping a
 * break with neither is shipping a bot project nobody can move forward.
 *
 * <h2>Why the coverage window is "newer than the old version", not "the whole file"</h2>
 *
 * <p>The predecessor of this class matched coverage against every entry in the file, and documented the hole
 * that leaves: an older release's migration would satisfy a brand-new break of the same member. Here the
 * comparison baseline is known ({@code oldVersion}, the tag japicmp was pointed at), and japicmp only ever
 * reports the diff against it — so a break introduced by <em>this</em> build must be covered by an entry filed
 * under a version <b>strictly newer</b> than that tag. Nothing else can legitimately claim it.
 *
 * <h2>The verdict is a file, not an exit code</h2>
 *
 * <p>{@code target/japicmp/api-verdict.json} carries the code and the offending members, and the build passes
 * regardless unless {@code -Dbotmaker.api.failOnViolation=true}. That is what keeps "the API broke" separate
 * from "the build broke" — a distinction {@code release.sh} depends on, and one a plugin that simply failed
 * would collapse. CI switches the flag on; {@code release.sh} leaves it off and reads the file.
 *
 * <p>Codes, unchanged from the Python script this replaces, because {@code release.sh} branches on them:
 * <b>0</b> clean · <b>1</b> incompatible but every change deprecated first <em>and</em> migratable (legal, but
 * only in a major release — the caller knows the version; this does not) · <b>2</b> Rule 1 violation ·
 * <b>3</b> Rule 2 violation · <b>4</b> the migrations file itself is unreadable or invalid, which always fails
 * the build since it is a broken input rather than a verdict about the API.
 *
 * <p>When 2 and 3 are both present the report prints both and returns 2: a member that should never have been
 * deleted is not made better by writing a migration for it.
 *
 * <h2>Why this is Java, and why it lives in src/api-check</h2>
 *
 * <p>It was Python until the OpenRewrite YAML went away — reading that file needed PyYAML, the script's one
 * non-stdlib import. With one JSON file left, and Jackson already a direct SDK dependency, the rule can be
 * compiler-checked against the format's own shape instead.
 *
 * <p>It is <b>not</b> in {@code src/main}: that would put build tooling in the jar on every bot's classpath,
 * the same reason the {@code internal/} harnesses were deleted. It is <b>not</b> in {@code src/test} either:
 * {@code release.sh} passes {@code -Dmaven.test.skip=true} deliberately (a test that no longer builds is CI's
 * problem, not this gate's), so test sources are not compiled when the gate runs. It gets its own source root,
 * compiled only under the {@code api-check} profile — which {@code flattenMode=oss} already strips from the
 * published pom.
 *
 * <p>Nor japicmp's own {@code postAnalysisScript}, which is the natural home for all this: japicmp 0.26.1
 * bundles a Groovy that cannot read Java 26 class files ("Unsupported class file major version 70"), and this
 * project's JDK is 26 while CI runs 21. A rule that passes in CI and explodes on the maintainer's machine is
 * worse than no rule.
 */
public final class ApiRulesCheck {

    /** The highest {@code schema} this checker understands. See the format's own `_readme`. */
    private static final int MAX_SCHEMA = 1;

    private static final String DEPRECATED = "java.lang.Deprecated";

    /** A constructor has no name of its own; this is how japicmp and the migrations file both spell one. */
    private static final String CTOR = "<init>";

    /**
     * Every {@code fix.kind} this SDK's tooling knows. Checked here so a typo is a red build in this repo,
     * rather than an entry that silently repairs nothing on a user's machine months later. Adding a kind means
     * adding it here <em>and</em> to Studio's applier — but never bumping {@code schema}, because an older
     * Studio meeting an unknown kind degrades it to manual rather than misreading it.
     */
    private static final Set<String> FIX_KINDS = Set.of(
            "renameMethod", "renameType", "renameField", "moveMember",
            "dropArgument", "reorderArguments", "insertArgument");

    /** The member kinds japicmp nests under a {@code <class>}. */
    private static final String[] MEMBER_TAGS = {"method", "constructor", "field"};

    private ApiRulesCheck() {}

    // =============================================================================================
    // ENTRY POINT
    // =============================================================================================

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: ApiRulesCheck <japicmp.xml> <migrations.json> <verdict.json> "
                    + "<oldVersion> <failOnViolation>");
            System.exit(4);
            return;
        }
        Path report = Path.of(args[0]);
        Path migrations = Path.of(args[1]);
        Path verdict = Path.of(args[2]);
        String oldVersion = args[3];
        boolean failOnViolation = Boolean.parseBoolean(args[4]);

        Result result = run(report, migrations, oldVersion);
        write(verdict, result, oldVersion);
        result.print();

        // 4 is a broken input rather than a verdict, so it fails whatever the flag says: carrying on would
        // mean reporting "no migration path" for members whose migration we simply failed to read.
        if (result.code == 4 || (failOnViolation && result.code >= 2)) {
            System.exit(result.code);
        }
    }

    static Result run(Path report, Path migrations, String oldVersion) throws Exception {
        if (!Files.isRegularFile(report)) {
            return Result.invalid("no japicmp report at " + report
                    + "\n  run: mvn -pl botmaker-sdk -Papi-check verify -Dbotmaker.api.oldVersion=<tag>");
        }

        Coverage coverage;
        try {
            coverage = Coverage.read(migrations, oldVersion);
        } catch (InvalidMigrationsException e) {
            return Result.invalid(e.getMessage());
        }

        Result result = new Result();
        for (Element klass : childElements(parse(report), "class")) {
            String owner = attr(klass, "fullyQualifiedName", "?");

            if (isBreaking(klass) && "REMOVED".equals(attr(klass, "changeStatus", ""))) {
                record(result, coverage, "type         " + owner, klass, owner, null);
            }
            for (String tag : MEMBER_TAGS) {
                for (Element member : childElements(klass, tag)) {
                    if (!isBreaking(member)) continue;
                    // japicmp names a constructor after its class; the migrations file spells that <init>,
                    // matching the JVM, so an entry can single a constructor out from its methods.
                    String name = "constructor".equals(tag) ? CTOR : attr(member, "name", "");
                    record(result, coverage, describe(owner, tag, name), member, owner, name);
                }
            }
        }
        return result;
    }

    /**
     * Sorts one breaking change into the two rules.
     *
     * <p>Order matters: a member removed without being announced is reported under Rule 1 <em>only</em>.
     * Telling someone to also write a migration for a deletion they should not have made is noise on top of
     * the real problem.
     */
    private static void record(Result result, Coverage coverage, String line, Element element,
                               String owner, String name) {
        if ("REMOVED".equals(attr(element, "changeStatus", "")) && !wasMarkedForRemoval(element)) {
            result.violations.add(line);
            return;
        }
        result.breaking.add(line);
        boolean covered = name == null ? coverage.coversType(owner) : coverage.coversMember(owner, name);
        if (!covered) result.uncovered.add(line);
    }

    private static String describe(String owner, String tag, String name) {
        if ("constructor".equals(tag)) return "constructor  " + owner + "(…)";
        return String.format("%-12s %s#%s", tag, owner, name);
    }

    // =============================================================================================
    // THE JAPICMP REPORT
    // =============================================================================================

    private static Element parse(Path report) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // The report is our own build output, but a parser that resolves external entities on a file it was
        // merely handed is a habit worth not having.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(report.toFile());
        return document.getDocumentElement();
    }

    /**
     * japicmp's own verdict, not our pattern-matching.
     *
     * <p>This matters more than it looks. Recompiling renumbers synthetic lambda methods
     * ({@code lambda$untilFindAll$1}), so they show up as REMOVED on an otherwise untouched class. They are
     * binary-compatible, and trusting japicmp to say so is more reliable than guessing every synthetic shape
     * javac emits.
     */
    private static boolean isBreaking(Element element) {
        return "false".equals(attr(element, "binaryCompatible", ""));
    }

    /**
     * True if this element carried {@code @Deprecated(forRemoval = true)} in the OLD jar.
     *
     * <p>Read from {@code <oldElementValues>}, never {@code <newElementValues>}: for a removed member there is
     * no new side, and for a member that merely changed we still care about what the released jar promised,
     * not what this working tree says.
     */
    private static boolean wasMarkedForRemoval(Element element) {
        for (Element annotation : descendants(element, "annotation")) {
            if (!DEPRECATED.equals(attr(annotation, "fullyQualifiedName", ""))) continue;
            for (Element el : descendants(annotation, "element")) {
                if (!"forRemoval".equals(attr(el, "name", ""))) continue;
                for (Element value : descendants(el, "oldElementValue")) {
                    if ("true".equals(attr(value, "value", ""))) return true;
                }
            }
        }
        return false;
    }

    // =============================================================================================
    // WHAT THE MIGRATIONS FILE CLAIMS
    // =============================================================================================

    /**
     * The set of old-API names some migration claims to handle, restricted to versions newer than the
     * comparison baseline.
     *
     * <p>Deliberately coarse: it asks "does anything at all name this member", not "does the migration repair
     * it correctly". No static check can answer the second — the end-to-end run against a real bot project is
     * what does. What this catches is the failure that actually happens: breaking something and forgetting to
     * write the migration at all.
     */
    private static final class Coverage {
        private final Set<String> types = new LinkedHashSet<>();
        private final Set<String[]> members = new LinkedHashSet<>();

        static Coverage read(Path file, String oldVersion) throws InvalidMigrationsException {
            Coverage coverage = new Coverage();
            if (!Files.isRegularFile(file)) {
                throw new InvalidMigrationsException("no migrations file at " + file
                        + "\n  every break needs one; see META-INF/botmaker/migrations.json");
            }

            JsonNode root;
            try {
                root = new ObjectMapper().readTree(Files.readString(file, StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new InvalidMigrationsException(file + " is not readable JSON: " + e.getMessage());
            }

            if (!root.hasNonNull("schema")) {
                throw new InvalidMigrationsException(file + " has no \"schema\" field (expected " + MAX_SCHEMA + ").");
            }
            int schema = root.path("schema").asInt(-1);
            if (schema < 1 || schema > MAX_SCHEMA) {
                throw new InvalidMigrationsException(file + " declares schema " + schema
                        + ", but this checker understands 1.." + MAX_SCHEMA + ".");
            }

            JsonNode versions = root.path("versions");
            List<String> problems = new ArrayList<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = versions.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                String version = entry.getKey();
                if (!entry.getValue().isArray()) {
                    problems.add("versions[\"" + version + "\"] is not a list of entries.");
                    continue;
                }
                boolean inWindow = isNewerThan(version, oldVersion);
                int index = 0;
                for (JsonNode node : entry.getValue()) {
                    validate(version, index++, node, problems);
                    // Validation covers the WHOLE file; coverage only the window. A malformed entry filed
                    // under an old release is still a bug worth failing on — it will be read by Studio.
                    if (inWindow) coverage.add(node.path("member").asText(""));
                }
            }
            if (!problems.isEmpty()) {
                throw new InvalidMigrationsException(file + " is invalid:\n    "
                        + String.join("\n    ", problems));
            }
            return coverage;
        }

        private static void validate(String version, int index, JsonNode node, List<String> problems) {
            String where = "versions[\"" + version + "\"][" + index + "]";
            if (!node.isObject()) {
                problems.add(where + " is not an object.");
                return;
            }
            if (node.path("member").asText("").isBlank()) problems.add(where + " has no \"member\".");
            if (node.path("summary").asText("").isBlank()) problems.add(where + " has no \"summary\".");

            boolean hasFix = node.hasNonNull("fix");
            boolean hasManual = !node.path("manual").asText("").isBlank();
            if (hasFix == hasManual) {
                problems.add(where + " must have exactly one of \"fix\" or \"manual\""
                        + (hasFix ? " — it has both." : " — it has neither."));
                return;
            }
            if (!hasFix) return;

            String kind = node.path("fix").path("kind").asText("");
            if (!FIX_KINDS.contains(kind)) {
                problems.add(where + " has fix.kind \"" + kind + "\", which is not one of "
                        + String.join(", ", FIX_KINDS.stream().sorted().toList()) + ".");
            }
        }

        private void add(String member) {
            if (member.isBlank()) return;
            int hash = member.indexOf('#');
            if (hash < 0) {
                types.add(member);
            } else {
                members.add(new String[]{member.substring(0, hash), member.substring(hash + 1)});
            }
        }

        boolean coversType(String fqcn) {
            return types.stream().anyMatch(pattern -> glob(pattern, fqcn));
        }

        boolean coversMember(String fqcn, String name) {
            if (coversType(fqcn)) return true;
            return members.stream().anyMatch(m -> glob(m[0], fqcn) && glob(m[1], name));
        }

        /** {@code *} is the only wildcard, so a whole package can move in one entry. */
        private static boolean glob(String pattern, String value) {
            if (pattern.indexOf('*') < 0) return pattern.equals(value);
            StringBuilder regex = new StringBuilder();
            for (String literal : pattern.split("\\*", -1)) {
                if (regex.length() > 0) regex.append(".*");
                regex.append(Pattern.quote(literal));
            }
            return value.matches(regex.toString());
        }
    }

    private static final class InvalidMigrationsException extends Exception {
        InvalidMigrationsException(String message) {
            super(message);
        }
    }

    // =============================================================================================
    // THE VERDICT
    // =============================================================================================

    static final class Result {
        final List<String> violations = new ArrayList<>();
        final List<String> breaking = new ArrayList<>();
        final List<String> uncovered = new ArrayList<>();
        String invalid;
        int code;

        static Result invalid(String message) {
            Result result = new Result();
            result.invalid = message;
            result.code = 4;
            return result;
        }

        int code() {
            if (invalid != null) return 4;
            if (!violations.isEmpty()) return 2;
            if (!uncovered.isEmpty()) return 3;
            return breaking.isEmpty() ? 0 : 1;
        }

        void print() {
            code = code();
            if (invalid != null) {
                System.out.println("\nAPI check could not run: " + invalid);
                return;
            }
            if (!violations.isEmpty()) {
                System.out.println("\nRemoved without a deprecation cycle — " + violations.size() + ":");
                violations.forEach(line -> System.out.println("    " + line));
                System.out.println("\nEach one is gone from this build but was NOT marked "
                        + "@Deprecated(since = \"...\", forRemoval = true) in the previous release.");
                System.out.println("Deprecate them in a MINOR release first, naming the replacement in a "
                        + "Javadoc @deprecated");
                System.out.println("line, then remove them in the next MAJOR one.  "
                        + "See docs/refactor/21-api-compat.md.");
                return;
            }
            if (!breaking.isEmpty()) {
                System.out.println("\nBreaking changes — " + breaking.size()
                        + " (each was deprecated first, so this is legal in a MAJOR release):");
                for (String line : breaking) {
                    System.out.println("    " + line + (uncovered.contains(line) ? "   <-- no migration" : ""));
                }
            }
            if (!uncovered.isEmpty()) {
                System.out.println("\nNo migration path — " + uncovered.size() + ":");
                uncovered.forEach(line -> System.out.println("    " + line));
                System.out.println("\nEach one breaks a bot that compiles today, and nothing tells its author "
                        + "what to do.");
                System.out.println("Add an entry naming it to "
                        + "src/main/resources/META-INF/botmaker/migrations.json, under a");
                System.out.println("version newer than the one being compared against — with a \"fix\" if "
                        + "Studio can repair the");
                System.out.println("call sites, or a \"manual\" sentence if the repair needs a human decision.");
                System.out.println("See docs/refactor/21-api-compat.md §4.");
                return;
            }
            if (breaking.isEmpty()) System.out.println("API check: no incompatible changes.");
        }
    }

    private static void write(Path verdict, Result result, String oldVersion) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("code", result.code());
        root.put("oldVersion", oldVersion);
        if (result.invalid != null) root.put("invalid", result.invalid);
        put(root.putArray("removedWithoutCycle"), result.violations);
        put(root.putArray("breaking"), result.breaking);
        put(root.putArray("uncovered"), result.uncovered);

        Files.createDirectories(verdict.getParent());
        Files.writeString(verdict, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n",
                StandardCharsets.UTF_8);
    }

    private static void put(ArrayNode array, List<String> lines) {
        lines.forEach(line -> array.add(line.replaceAll("\\s+", " ").trim()));
    }

    // =============================================================================================
    // DOM HELPERS
    // =============================================================================================

    private static String attr(Element element, String name, String fallback) {
        String value = element.getAttribute(name);
        return value == null || value.isEmpty() ? fallback : value;
    }

    /** Descendant elements of {@code tag}, which for japicmp's shallow shape is what a caller ever wants. */
    private static List<Element> descendants(Element parent, String tag) {
        NodeList found = parent.getElementsByTagName(tag);
        List<Element> out = new ArrayList<>(found.getLength());
        for (int i = 0; i < found.getLength(); i++) {
            Node node = found.item(i);
            if (node instanceof Element element) out.add(element);
        }
        return out;
    }

    private static List<Element> childElements(Element parent, String tag) {
        return descendants(parent, tag);
    }

    // =============================================================================================
    // VERSIONS
    // =============================================================================================

    /**
     * True when {@code version} is strictly newer than {@code baseline} — the window a migration must be filed
     * in to count as coverage.
     *
     * <p>An unparseable baseline (there is no previous tag, or it is spelled in some way {@code sort -V} would
     * understand and this does not) opens the window to everything. A gate that silently narrows to nothing is
     * how "no migration path" gets reported for a member whose migration is right there.
     */
    private static boolean isNewerThan(String version, String baseline) {
        int[] a = semver(version);
        int[] b = semver(baseline);
        if (a == null || b == null) return true;
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return a[i] > b[i];
        }
        return false;
    }

    /** Release tags are cut as {@code v1.0.26}; some older ones are bare. Null when it is neither. */
    private static int[] semver(String version) {
        if (version == null) return null;
        String text = version.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("v")) text = text.substring(1);
        String[] parts = text.split("\\.");
        if (parts.length < 3) return null;
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9].*$", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }
}
