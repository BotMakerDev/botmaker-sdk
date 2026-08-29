package com.botmaker.sdk.authoring;

import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.sdk.internal.authoring.ProjectWriter;
import com.botmaker.sdk.internal.authoring.SdkValueTypes;
import com.botmaker.sdk.internal.authoring.ValueJson;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The one entry point an editor uses to read, write and create a bot project.
 *
 * <h2>The version comes first, always</h2>
 *
 * <p>Every method takes an {@link SdkVersion} as its first argument. That is the inversion's rule made
 * unforgettable at the call site: <em>anything touching bot code takes its answer from the bot's own SDK
 * version</em>, not from whichever SDK the editor happens to bundle. A bot pinning 1.1.0 opened by an editor
 * carrying 1.4.0 gets 1.1.0's answers.
 *
 * <p>An editor gets that argument from the bot's pom, through {@link SdkVersion#ofPin(String)}, and reports
 * {@link AuthoringUnsupported} when it comes back empty. It must not fall back to {@link SdkVersion#latest()}
 * — that would be promising a bot something its own jar cannot deliver.
 *
 * <h2>What this class is not</h2>
 *
 * <p>It does not validate, and it does not repair. A model may name activities that do not exist and values
 * outside their own bounds; reading gives them back as written and writing puts them back. Refusing belongs
 * where a user is watching.
 *
 * <h2>The schema stamp</h2>
 *
 * <p>{@code activities.json} carries a {@code schemaVersion} as its first member. The SDK writes it and
 * reads it back, but the <b>ledger of migration steps still belongs to the caller</b> — which is why
 * {@link #writeModel} takes the number rather than deriving it. That is a deliberate seam and not the final
 * shape: the ledger moves here when the generator does, and until it has, a stamp derived in two places
 * would be two answers to one question.
 */
public final class Authoring {

    /** The field carrying the schema stamp, always written first. */
    public static final String SCHEMA_FIELD = "schemaVersion";

    // There is no SDK_GROUP_ID / SDK_ARTIFACT_ID here, and no pom writer — deliberately, and as a reversal
    // of the decision this class shipped with one day earlier (2026-08-26).
    //
    // The argument for putting them here was that the SDK is the only thing that knows what it publishes
    // itself as. True, and not enough: the pom is not a file *about* the SDK, it is the file that *declares
    // which* SDK — and, in the maintainer's framing, the SDK is the editor's *default plugin*, not the
    // editor. A second plugin would be invisible to it, so a pom the SDK wrote would be missing that
    // plugin's dependency with nobody to notice. Only the thing that knows the whole plugin set can compose
    // that file, and that is the editor.
    //
    // Creation still writes it in one pass: see createProject's `callerFiles`.

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .registerModule(ValueJson.module(SdkValueTypes.CATALOG));

    private Authoring() {
    }

    /**
     * The value types this SDK contributes to a project's vocabulary.
     *
     * <p>The SDK's own registrations and nothing else — an editor loading plugins
     * {@linkplain ValueCatalog#merge merges} this with theirs and holds the result. It is offered rather than
     * assumed for the reason the whole platform exists: the SDK is the editor's default plugin, and a plugin
     * that got to define the vocabulary outright would be the back door every other one is denied.
     *
     * <p>Version-first like everything else here, and the argument is not yet consulted — for the same reason
     * {@code catalog(pin)} on the palette side is not. Narrowing to what an older pin actually shipped is a
     * question the bot's own jar answers, not one a frozen list beside the code can answer truthfully.
     */
    public static ValueCatalog valueTypes(SdkVersion version) {
        requireVersion(version);
        return SdkValueTypes.CATALOG;
    }

    /**
     * Resolves the version a pom pin names, or refuses in the user's words.
     *
     * <p>The refusal is the product here — see {@link AuthoringUnsupported}. Callers that already hold an
     * {@link SdkVersion} do not need this; callers holding a {@code String} out of a pom do, and should let
     * the exception reach the dialog rather than defaulting past it.
     */
    public static SdkVersion require(String sdkPin) throws AuthoringUnsupported {
        return SdkVersion.ofPin(sdkPin).orElseThrow(() -> AuthoringUnsupported.unknownVersion(sdkPin));
    }

    /**
     * Reads {@code activities.json} out of a project's resources directory.
     *
     * <p>A missing file is {@link ProjectModel#empty()} and not an error: an empty project has none, and a
     * game bot that has never had an activity added reads the same as one whose file was deleted — in both
     * cases there is nothing declared, which is a state the editor can show.
     *
     * <p>A file that exists but cannot be parsed <b>is</b> an error, and is thrown. Silently treating
     * corruption as emptiness is how a project gets overwritten with nothing on the next save.
     */
    public static ProjectModel readModel(SdkVersion version, Path resourcesDir) throws IOException {
        requireVersion(version);
        Path file = resourcesDir.resolve(ProjectModel.FILE_NAME);
        if (!Files.exists(file)) return ProjectModel.empty();
        return MAPPER.readValue(file.toFile(), ProjectModel.class);
    }

    /**
     * The schema stamp the stored file carries, or {@code 0} when it has none — which is what every file
     * written before stamping existed reads as, and is the value a migration ledger starts counting from.
     */
    public static int readSchemaVersion(SdkVersion version, Path resourcesDir) throws IOException {
        requireVersion(version);
        Path file = resourcesDir.resolve(ProjectModel.FILE_NAME);
        if (!Files.exists(file)) return 0;
        JsonNode root = MAPPER.readTree(file.toFile());
        JsonNode stamp = root.path(SCHEMA_FIELD);
        return stamp.isInt() ? stamp.asInt() : 0;
    }

    /**
     * Writes {@code activities.json}, stamped with {@code schemaVersion} as its first member.
     *
     * <p>The stamp goes first because a person opening the file to see what version it is should not have to
     * scroll past the whole model to find out. The directory is created if it does not exist.
     */
    public static void writeModel(SdkVersion version, Path resourcesDir, ProjectModel model,
                                  int schemaVersion) throws IOException {
        Files.createDirectories(resourcesDir);
        Files.writeString(resourcesDir.resolve(ProjectModel.FILE_NAME),
                modelJson(version, model, schemaVersion));
    }

    /**
     * The same bytes {@link #writeModel} would write, as text.
     *
     * <p>It is public because <b>creating</b> a project renders every file before committing any of them,
     * and {@code activities.json} is one of those files: a creation that can produce the JSON but not the
     * caller's own files must leave nothing behind. A caller that already has a directory to write into
     * should use {@link #writeModel} instead.
     */
    public static String modelJson(SdkVersion version, ProjectModel model, int schemaVersion)
            throws IOException {
        requireVersion(version);
        ObjectNode body = MAPPER.valueToTree(model);
        ObjectNode stamped = MAPPER.createObjectNode();
        stamped.put(SCHEMA_FIELD, schemaVersion);
        stamped.setAll(body);
        return MAPPER.writeValueAsString(stamped);
    }

    // ---- creation ---------------------------------------------------------------------------------------

    /**
     * Creates a whole bot project at {@code projectDir} — every file the SDK owns, or none of them.
     *
     * <p>What that set is: the four {@code src/} directories, {@code activities.json} (a game bot's; an empty
     * project has no model to store), {@code botmaker-project.properties} carrying the reference resolution,
     * and the placeholder image template. It is the SDK's because every one of those files is <em>about</em>
     * the SDK: the shape of the data it reads back at run time.
     *
     * <p><b>No {@code .java} is in that set, and since 2026-08-29 none ever is.</b> A project's structure
     * belongs to the user, so every source file arrives through {@code callerFiles} — including the entry
     * point, which was the last one the SDK wrote. That extends an argument the pom had already won: the pom
     * <em>declares which</em> SDK, and which other plugins, the project has, and the SDK is one plugin among
     * however many the editor loaded, so it cannot be the author of the file that lists them. The same is
     * true of the file that <em>installs</em> them, and true in a plainer way of every other file: only the
     * user should own the shape of their own project.
     *
     * <p>What this is deliberately <b>not</b>, beyond the source: where projects live, whether the name is
     * one the user may use, the editor's own {@code settings.json}, and version control. Those are the
     * editor's.
     *
     * <p><b>Everything is rendered before anything is committed</b>, and an existing {@code pom.xml} at the
     * target is refused first. So a refusal never leaves a half-created project for someone to find and
     * delete by hand — the rule the editor's own creator enforced before this moved here, preserved exactly.
     *
     * @param callerFiles files the <em>caller</em> composed, keyed by project-relative path, committed in
     *                    this same all-or-none pass. {@code pom.xml} is the one that matters: handing it in
     *                    here rather than writing it before or after is what keeps both properties at once —
     *                    the editor authors the pom, and creation is still all of it or none of it. Keys
     *                    colliding with a file the SDK owns are refused; whole-file ownership, never a merge.
     * @param schemaVersion the stamp {@code activities.json} carries — the caller's ledger, for the same
     *                      reason {@link #writeModel} takes it rather than deriving it
     * @throws IOException              if the target already holds a project, or a file cannot be written
     * @throws IllegalArgumentException if the spec is missing a name, a package or an entry class, or a
     *                                  caller file claims a path the SDK writes
     */
    public static void createProject(SdkVersion version, ProjectSpec spec, Path projectDir,
                                     int schemaVersion, Map<String, String> callerFiles) throws IOException {
        requireVersion(version);
        ProjectWriter.create(version, spec, projectDir, schemaVersion, callerFiles);
    }

    private static void requireVersion(SdkVersion version) {
        if (version == null) {
            throw new IllegalArgumentException("An SdkVersion is required — see Authoring's class javadoc "
                    + "for why every entry point takes one, and SdkVersion.ofPin for where callers get it.");
        }
    }
}
