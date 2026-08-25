package com.botmaker.sdk.api.authoring;

import com.botmaker.sdk.api.meta.Since;
import com.botmaker.sdk.internal.authoring.PomWriter;
import com.botmaker.sdk.internal.authoring.ProjectWriter;
import com.botmaker.sdk.internal.authoring.SourceEmitter;
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
 * The one entry point an editor uses to read, write and generate a bot project.
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
@Since("1.2.0")
public final class Authoring {

    /** The field carrying the schema stamp, always written first. */
    public static final String SCHEMA_FIELD = "schemaVersion";

    /**
     * The Maven coordinate a generated project pins the SDK at.
     *
     * <p>Here rather than in the editor because the SDK is the only thing that knows what it publishes
     * itself as — an editor with its own copy is an editor that keeps resolving the old coordinate the day
     * this one changes.
     */
    public static final String SDK_GROUP_ID = PomWriter.SDK_GROUP_ID;
    /** @see #SDK_GROUP_ID */
    public static final String SDK_ARTIFACT_ID = PomWriter.SDK_ARTIFACT_ID;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private Authoring() {
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
     * <p>It is public because <b>creating</b> a project renders every file it owns before committing any of
     * them, and {@code activities.json} is one of those files: a creation that can produce the JSON but not
     * the sources must leave nothing behind. A caller that already has a directory to write into should use
     * {@link #writeModel} instead.
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
     * <p>What that set is: {@code pom.xml}, the four {@code src/} directories, every generated {@code .java},
     * {@code activities.json} (a game bot's; an empty project has no model to store),
     * {@code botmaker-project.properties} carrying the reference resolution, and the placeholder image
     * template. It is the SDK's because every one of those files is <em>about</em> the SDK: which version the
     * bot compiles against, what its sources call, and what shape the data it stores is in. An editor holding
     * its own copy of that list is an editor deciding what an SDK it did not write requires.
     *
     * <p>What it deliberately is <b>not</b>: where projects live, whether the name is one the user may use,
     * the editor's own {@code settings.json}, and version control. Those are the editor's, and it does them
     * around this call.
     *
     * <p><b>Everything is rendered before anything is committed</b>, and an existing {@code pom.xml} at the
     * target is refused first. So a refusal never leaves a half-created project for someone to find and
     * delete by hand — the rule the editor's own creator enforced before this moved here, preserved exactly.
     *
     * @param schemaVersion the stamp {@code activities.json} carries — the caller's ledger, for the same
     *                      reason {@link #writeModel} takes it rather than deriving it
     * @throws IOException              if the target already holds a project, or a file cannot be written
     * @throws IllegalArgumentException if the spec is missing a name, a package or an entry class
     */
    public static void createProject(SdkVersion version, ProjectSpec spec, Path projectDir,
                                     int schemaVersion) throws IOException {
        requireVersion(version);
        ProjectWriter.create(version, spec, projectDir, schemaVersion);
    }

    /**
     * The project's {@code pom.xml} as text, without writing anything.
     *
     * <p>Creation writes this itself; this exists for the one other caller that legitimately needs the pom
     * alone — an editor <em>restoring</em> a build file somebody deleted out of an otherwise intact project,
     * where {@link #createProject} would refuse (correctly: the project is there) and re-creating around it
     * would overwrite the user's code.
     */
    public static String pomXml(SdkVersion version, ProjectSpec spec) {
        requireVersion(version);
        return PomWriter.pom(spec, version);
    }

    /**
     * The project-relative path of every {@code .java} file {@link #createProject} writes for this spec.
     *
     * <p>This is how an editor knows a generated file has gone missing: the set is not a list somebody
     * maintained beside the generator, it is <em>the generator's own answer</em>, so a file that stops being
     * emitted stops being looked for on the same day.
     */
    public static List<String> generatedFileNames(SdkVersion version, ProjectSpec spec) {
        requireVersion(version);
        return ProjectWriter.generatedFileNames(spec);
    }

    /**
     * The repositories a generated project's {@code pom.xml} declares, {@code id -> url}, in the order they
     * are tried — the same list an editor needs when it resolves that project's classpath itself, which it
     * must not re-derive from the pom it happens to find on disk.
     */
    public static Map<String, String> defaultRepositories(SdkVersion version) {
        requireVersion(version);
        return ProjectWriter.repositories();
    }

    /**
     * Whether {@code groupId:artifactId} is one of the dependencies every generated project is born with —
     * which is how an editor tells a library the <em>user</em> added from one it did not, and so which ones
     * it may offer to remove.
     */
    public static boolean isDefaultDependency(SdkVersion version, String groupId, String artifactId) {
        requireVersion(version);
        return ProjectWriter.isDefaultDependency(groupId, artifactId);
    }

    // ---- generation -------------------------------------------------------------------------------------

    /**
     * Every {@code .java} file this project is made of, keyed by its path relative to the project root.
     *
     * <p>The whole set, which is what <b>creating</b> a project needs: the entry point, {@code GoHome},
     * {@code Popups}, an editable stub per activity, and the five regenerated files
     * {@link #regenerate} also emits. An {@link ProjectSpec.Kind#EMPTY} project has none of them — its entry
     * point is the user's from the first character — so the map comes back empty.
     *
     * <p>Nothing is written to disk here. The caller gets the whole set in memory and commits it, which is
     * what lets a creation that cannot produce every file it owns produce none of them.
     *
     * @param imageBaseNames the file names (no extension) of the project's image templates, which the
     *                       generated {@code Templates} class is built from — the one input that is not in
     *                       the model, because it lives in the images folder rather than in the file
     */
    public static Map<String, String> sources(SdkVersion version, ProjectSpec spec, ProjectModel model,
                                              List<String> imageBaseNames) {
        requireVersion(version);
        return SourceEmitter.sources(spec, model, imageBaseNames);
    }

    /**
     * The subset rewritten wholesale whenever the project's model changes — {@code Activities},
     * {@code Parameters}, {@code Templates}, {@code ActivityRegistry} and {@code FlowDriver}.
     *
     * <p><b>Hand edits inside these files are lost.</b> That was already true, and it now includes editing a
     * <em>value</em>: {@code Parameters} holds literals rather than a parser call, so a duration changed in
     * the Java rather than in the dialog survives exactly until the next save. Each file says so in its own
     * javadoc.
     */
    public static Map<String, String> regenerate(SdkVersion version, ProjectSpec spec, ProjectModel model,
                                                 List<String> imageBaseNames) {
        requireVersion(version);
        return SourceEmitter.regenerated(spec, model, imageBaseNames);
    }

    /**
     * One activity's editable stub, keyed by path like the rest — for an activity that has just been added,
     * or one whose file has gone missing.
     *
     * <p>A SEED file: emitted here in the shape it starts in, and the user's from that moment. Keeping an
     * existing stub's {@code Outcome} enum in step with the canvas is a surgical edit of a file the user
     * owns, and belongs to the editor rather than here.
     */
    public static Map<String, String> activityStub(SdkVersion version, ProjectSpec spec,
                                                   ActivityModel activity) {
        requireVersion(version);
        return SourceEmitter.activityStub(spec, activity);
    }

    private static void requireVersion(SdkVersion version) {
        if (version == null) {
            throw new IllegalArgumentException("An SdkVersion is required — see Authoring's class javadoc "
                    + "for why every entry point takes one, and SdkVersion.ofPin for where callers get it.");
        }
    }
}
