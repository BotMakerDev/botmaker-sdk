package com.botmaker.sdk.authoring;

import com.botmaker.plugin.api.ParameterGroup;
import com.botmaker.plugin.api.value.Range;
import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueChoice;
import com.botmaker.plugin.api.value.ValueShape;
import com.botmaker.plugin.api.value.ValueType;
import com.botmaker.plugin.api.value.Visibility;
import com.botmaker.sdk.internal.authoring.SdkValueTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the SDK now guarantees about {@code activities.json}: that it round-trips, that the stamp survives,
 * that the two spellings the file has had over its life still load, and that every parse is total.
 *
 * <p>These are the rules an editor is entitled to rely on now that the file has one owner. A project written
 * by an older editor and opened by a newer one goes through exactly this code, so a regression here is a
 * project that opens with values missing rather than a test that fails somewhere harmless.
 */
class AuthoringModelTest {

    private static final SdkVersion V = SdkVersion.latest();
    private static final ValueCatalog CATALOG = Authoring.valueTypes(V);

    @Test
    void anAbsentFileIsAnEmptyModelRatherThanAnError(@TempDir Path dir) throws IOException {
        assertEquals(ProjectModel.empty(), Authoring.readModel(V, dir));
        assertEquals(0, Authoring.readSchemaVersion(V, dir));
    }

    @Test
    void aCorruptFileThrowsRatherThanReadingAsEmpty(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(ProjectModel.FILE_NAME), "{ not json");
        assertThrows(IOException.class, () -> Authoring.readModel(V, dir));
    }

    @Test
    void aModelRoundTripsWithItsStamp(@TempDir Path dir) throws IOException {
        ProjectModel written = new ProjectModel(
                List.of(new ActivityModel("Mining", true, "dig", List.of("FULL"), null, Boolean.FALSE)),
                List.of(new VariableModel("REST", ValueChoice.of(SdkValueTypes.DURATION), List.of("90s"),
                                "How long to rest", "Mining", Visibility.PUBLIC, List.of(),
                                new Range("30s", null), ParameterGroup.DEFAULT_ID),
                        new VariableModel("HOTKEYS", ValueChoice.listOf(SdkValueTypes.KEY),
                                List.of("SPACE", "ESCAPE"), "", "", Visibility.EDITOR_ONLY, List.of(),
                                Range.NONE, ParameterGroup.DEFAULT_ID)),
                new FlowModel(List.of(new FlowNodeModel("Mining", 12, 34)),
                        List.of(new FlowEdgeModel("Mining", "Mining", "FULL")),
                        "Mining", 500, 0),
                List.of(new PresetModel("Night", List.of("Mining"))),
                Boolean.FALSE);

        Authoring.writeModel(V, dir, written, 7);

        assertEquals(written, Authoring.readModel(V, dir));
        assertEquals(7, Authoring.readSchemaVersion(V, dir));
    }

    @Test
    void theStampIsTheFirstMemberOfTheFile(@TempDir Path dir) throws IOException {
        Authoring.writeModel(V, dir, ProjectModel.empty(), 3);
        String text = Files.readString(dir.resolve(ProjectModel.FILE_NAME));
        assertTrue(text.indexOf(Authoring.SCHEMA_FIELD) < text.indexOf("activities"),
                "the stamp must lead the file so a reader does not have to scroll past the model to find it");
    }

    /**
     * An explicit {@code 0} step delay is a setting a user asked for; an <em>absent</em> one is a file older
     * than the field. Conflating them turns every pre-existing flow into a zero-delay one — the runaway the
     * field exists to prevent.
     */
    @Test
    void anAbsentStepDelayTakesTheDefaultAndAnExplicitZeroSurvives(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(ProjectModel.FILE_NAME),
                "{\"flow\":{\"nodes\":[],\"edges\":[],\"start\":\"\",\"maxSteps\":10}}");
        assertEquals(FlowModel.DEFAULT_STEP_DELAY_MS, Authoring.readModel(V, dir).flow().stepDelayMs());

        Files.writeString(dir.resolve(ProjectModel.FILE_NAME),
                "{\"flow\":{\"nodes\":[],\"edges\":[],\"start\":\"\",\"maxSteps\":10,\"stepDelayMs\":0}}");
        assertEquals(0, Authoring.readModel(V, dir).flow().stepDelayMs());
    }

    /** The pseudo-type that predates the shape axis: {@code CHOICE} was text out of a written-down set. */
    @Test
    void theLegacyChoicePseudoTypeLoadsAsTextOutOfASet() {
        ValueChoice c = ValueChoice.fromWire(CATALOG, "CHOICE", null, Boolean.FALSE);
        assertEquals(SdkValueTypes.TEXT, c.type());
        assertEquals(ValueShape.ONE_OF, c.shape());
    }

    /** The boolean that predates the shape axis: {@code list:true} was both list shapes at once. */
    @Test
    void theLegacyListBooleanBecomesAListShape() {
        assertEquals(ValueShape.ANY_OF, ValueChoice.fromWire(CATALOG, "TEXT", null, Boolean.TRUE).shape());
        assertEquals(ValueShape.ONE, ValueChoice.fromWire(CATALOG, "TEXT", null, Boolean.FALSE).shape());
    }

    /**
     * The question {@link ValueChoice#fromWire} cannot answer, because the options are a sibling field: a
     * stored {@code ANY_OF} is tick boxes when there is a set behind it and a free list when there is not.
     */
    @Test
    void aStoredAnyOfWithNoSetBehindItReadsAsAnOpenList() {
        ValueChoice anyOfText = new ValueChoice(SdkValueTypes.TEXT, ValueShape.ANY_OF);
        assertEquals(ValueShape.OPEN_LIST, VariableModel.listShapeOf(anyOfText, List.of()).shape());
        assertEquals(ValueShape.ANY_OF, VariableModel.listShapeOf(anyOfText, List.of("a")).shape());

        // A closed set is its own set of options — nobody has to write them down.
        ValueChoice anyOfKey = new ValueChoice(SdkValueTypes.KEY, ValueShape.ANY_OF);
        assertEquals(ValueShape.ANY_OF, VariableModel.listShapeOf(anyOfKey, List.of()).shape());
    }

    @Test
    void everyParseIsTotal() {
        assertEquals(ValueShape.ONE, ValueShape.fromWire("SOME_NEW_SHAPE"));
        assertEquals(Visibility.EDITOR_ONLY, Visibility.fromId("something-else"));
        assertEquals(Visibility.EDITOR_ONLY, Visibility.fromId(null));
    }

    /**
     * Total, but no longer by pretending: an id nothing registered used to read as {@code TEXT}, which was a
     * defensible answer for a closed set of seventeen and is the wrong one for an open vocabulary. A file
     * naming {@code discord.Channel} is not a file whose author meant text — it is a file whose plugin is not
     * installed, and quietly retyping it is how a user's value is destroyed by a missing jar.
     */
    @Test
    void anIdNothingRegisteredIsUnknownRatherThanText() {
        ValueType invented = CATALOG.type("A_TYPE_SOME_PLUGIN_OWNS");
        assertFalse(invented.known());
        assertEquals("A_TYPE_SOME_PLUGIN_OWNS", invented.id(), "and it keeps the id, so a save round-trips");
        assertFalse(CATALOG.knows("A_TYPE_SOME_PLUGIN_OWNS"));
        assertTrue(CATALOG.initializer(ValueChoice.of(invented), List.of("whatever")).isEmpty(),
                "an unknown type declines to emit rather than guessing a literal");

        // Null is the one case that is still text: it is an absent field, not a name nobody claimed.
        assertEquals(SdkValueTypes.TEXT, CATALOG.type(null));
    }

    /** The unknown value survives the round trip, which is the whole of the guarantee. */
    @Test
    void aValueOfAnUnknownTypeIsStillThereAfterASave(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(ProjectModel.FILE_NAME), """
                {"variables":[{"name":"CHANNEL",
                               "type":{"type":"discord.Channel","shape":"ONE"},
                               "value":["#general"]}]}""");

        ProjectModel read = Authoring.readModel(V, dir);
        VariableModel v = read.variables().getFirst();
        assertEquals("discord.Channel", v.type().type().id());
        assertEquals(List.of("#general"), v.value());

        Authoring.writeModel(V, dir, read, 1);
        assertEquals(read, Authoring.readModel(V, dir));
        assertTrue(Files.readString(dir.resolve(ProjectModel.FILE_NAME)).contains("discord.Channel"));
    }

    /** "One of yes and no" is a boolean, said twice and worse — the shape is corrected, not stored. */
    @Test
    void aClosedSetCannotCarryAnAuthorWrittenSubset() {
        assertEquals(ValueShape.ONE, new ValueChoice(SdkValueTypes.YES_NO, ValueShape.ONE_OF).shape());
        assertEquals(ValueShape.ONE_OF, new ValueChoice(SdkValueTypes.TEXT, ValueShape.ONE_OF).shape());
    }

    @Test
    void aVersionThisBuildDoesNotKnowIsRefusedInTheUsersWords() {
        AuthoringUnsupported refusal =
                assertThrows(AuthoringUnsupported.class, () -> Authoring.require("9.9.9"));
        assertTrue(refusal.getMessage().contains("9.9.9"), "the refusal must name the pin");
        assertTrue(refusal.getMessage().contains(SdkVersion.latest().id()),
                "and the newest version this build does know, so the user can act on it");
    }

    /**
     * A snapshot pin is this very jar. Sending it through the unknown-version path would refuse creation in
     * every development build — a refusal about a version that is, by construction, the one refusing.
     */
    @Test
    void aSnapshotPinResolvesToThisBuild() throws AuthoringUnsupported {
        assertEquals(SdkVersion.latest(), Authoring.require("0.0.0-SNAPSHOT"));
        assertEquals(SdkVersion.latest(), Authoring.require(""));
        assertTrue(SdkVersion.of("0.0.0-SNAPSHOT").isEmpty(), "but of() must still say it does not know it");
    }

    @Test
    void aTagIsToleratedOnTheWayInButTheWireFormCarriesNoV() {
        assertEquals(SdkVersion.V1_1_0, SdkVersion.of("v1.1.0").orElseThrow());
        assertEquals("1.1.0", SdkVersion.V1_1_0.id());
        assertFalse(SdkVersion.latest().id().startsWith("v"));
        assertTrue(SdkVersion.latest().atLeast(SdkVersion.V1_1_0));
    }

    /** The emitter's spellings — qualified where a fixed import block could otherwise forget them. */
    @Test
    void theSourceSpellingsAreTheOnesTheGeneratorWrites() {
        assertEquals("java.time.Duration", ValueChoice.of(SdkValueTypes.DURATION).sourceName());
        assertEquals("java.util.List<Key>", ValueChoice.listOf(SdkValueTypes.KEY).sourceName());
        assertEquals("int", ValueChoice.of(SdkValueTypes.WHOLE_NUMBER).sourceName());
        assertEquals("java.util.List<Integer>",
                ValueChoice.listOf(SdkValueTypes.WHOLE_NUMBER).sourceName());
    }
}
