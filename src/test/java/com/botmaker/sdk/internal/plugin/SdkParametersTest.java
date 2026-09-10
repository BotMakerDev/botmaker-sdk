package com.botmaker.sdk.internal.plugin;

import com.botmaker.plugin.api.ParameterEdit;
import com.botmaker.plugin.api.ParameterGroup;
import com.botmaker.plugin.api.ParameterRow;
import com.botmaker.plugin.api.value.Range;
import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueChoice;
import com.botmaker.plugin.api.value.ValueShape;
import com.botmaker.plugin.api.value.ValueType;
import com.botmaker.plugin.api.value.Visibility;
import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.ProjectModel;
import com.botmaker.sdk.authoring.SdkVersion;
import com.botmaker.sdk.authoring.VariableModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SDK plugin's half of the Parameters window: the rows it serves, and where a changed value goes.
 *
 * <p>What is worth asserting is not the field-for-field copy — it is the three things the host now relies on
 * a plugin for, and that it did not rely on anybody for while it parsed the file itself: that a row's
 * components survive the crossing, that a group this plugin does not own answers nothing rather than
 * everything, and that an edit reaches the file rather than only the screen.
 */
class SdkParametersTest {

    private static final String OURS = ParameterGroup.DEFAULT_ID;

    private static final ValueType TEXT = ValueType.of(ValueCatalog.TEXT_ID).label("Text").source("String")
            .build();
    private static final ValueType WHOLE = ValueType.of("WHOLE_NUMBER").label("Whole number").source("int")
            .boxed("Integer").primitive().bounded().build();

    private static SdkParameters over(Path resourcesDir) {
        return new SdkParameters(resourcesDir, OURS);
    }

    private static void write(Path dir, VariableModel... variables) throws IOException {
        Authoring.writeModel(SdkVersion.latest(), dir, ProjectModel.of(List.of(), List.of(variables)), 3);
    }

    private static VariableModel retries() {
        return new VariableModel("retries", ValueChoice.of(WHOLE), List.of("2"), "How many tries",
                "Timing", Visibility.EDITOR_ONLY, List.of(), new Range("1", "5"), OURS);
    }

    @Test
    void everyComponentOfAStoredVariableSurvivesTheCrossing(@TempDir Path dir) throws IOException {
        write(dir, retries());

        ParameterRow row = over(dir).rows(OURS).getFirst();

        assertEquals("retries", row.name());
        assertEquals("WHOLE_NUMBER", row.type().type().id());
        assertEquals(List.of("2"), row.value());
        assertEquals("How many tries", row.description());
        // The one rename: a VariableModel's tag is a row's category, which is the word ParameterGroup
        // settled on for the rail inside a section.
        assertEquals("Timing", row.category());
        assertEquals(Visibility.EDITOR_ONLY, row.visibility());
        assertEquals(new Range("1", "5"), row.bounds());
    }

    @Test
    void aGroupThisPluginDoesNotOwnAnswersNothing(@TempDir Path dir) throws IOException {
        write(dir, retries());

        assertEquals(List.of(), over(dir).rows("discord"));
        assertEquals(1, over(dir).rows(OURS).size());
    }

    /** A variable filed under somebody else's group is in the same file and is still not this plugin's row. */
    @Test
    void anotherPluginsVariableInTheSameFileIsNotOurs(@TempDir Path dir) throws IOException {
        write(dir, retries(), VariableModel.of("channel", ValueChoice.of(TEXT), List.of("#general"))
                .withGroup("discord"));

        List<ParameterRow> ours = over(dir).rows(OURS);

        assertEquals(1, ours.size());
        assertEquals("retries", ours.getFirst().name());
    }

    /**
     * A project with no file yet is a project with no parameters — the state a freshly created one is in, and
     * not a reason to refuse to draw the window.
     */
    @Test
    void aProjectWithNoFileHasNoRows(@TempDir Path dir) {
        assertEquals(List.of(), over(dir).rows(OURS));
        assertEquals(Optional.empty(), over(dir).apply(ParameterEdit.of(OURS, "retries", "3")));
    }

    @Test
    void anEditReachesTheFileAndComesBackAsTheStoredRow(@TempDir Path dir) throws IOException {
        write(dir, retries());

        Optional<ParameterRow> stored = over(dir).apply(ParameterEdit.of(OURS, "retries", "4"));

        assertEquals("4", stored.orElseThrow().singleValue());
        assertEquals(List.of("4"),
                Authoring.readModel(SdkVersion.latest(), dir).variables().getFirst().value());
        // Read back through the surface, not only through the model: the window asks again after an edit.
        assertEquals("4", over(dir).rows(OURS).getFirst().singleValue());
    }

    /** Everything the edit did not name is still there — an edit is not a rewrite of the row. */
    @Test
    void anEditChangesNothingButTheValue(@TempDir Path dir) throws IOException {
        write(dir, retries());

        ParameterRow stored = over(dir).apply(ParameterEdit.of(OURS, "retries", "4")).orElseThrow();

        assertEquals("How many tries", stored.description());
        assertEquals("Timing", stored.category());
        assertEquals(Visibility.EDITOR_ONLY, stored.visibility());
        assertEquals(new Range("1", "5"), stored.bounds());
    }

    /**
     * Empty is <i>not mine</i>, and the file is left alone — the host reads it as "leave the screen alone",
     * so an edit that matched nothing must not be answerable with a row and must not have written.
     */
    @Test
    void anEditNamingNothingWeHoldIsDeclined(@TempDir Path dir) throws IOException {
        write(dir, retries());
        String before = Files.readString(dir.resolve(ProjectModel.FILE_NAME));

        assertEquals(Optional.empty(), over(dir).apply(ParameterEdit.of(OURS, "nobody", "4")));
        assertEquals(Optional.empty(), over(dir).apply(ParameterEdit.of("discord", "retries", "4")));
        assertEquals(before, Files.readString(dir.resolve(ProjectModel.FILE_NAME)));
    }

    /** A list-shaped row crosses one entry per item, and an edit to it replaces the whole list. */
    @Test
    void aListShapedRowCrossesAndIsEditedItemByItem(@TempDir Path dir) throws IOException {
        write(dir, VariableModel.of("hotkeys", ValueChoice.listOf(TEXT), List.of("F1", "F2")));

        assertEquals(List.of("F1", "F2"), over(dir).rows(OURS).getFirst().value());

        ParameterRow stored = over(dir)
                .apply(new ParameterEdit(OURS, "hotkeys", List.of("F1", "F2", "F3"))).orElseThrow();

        assertEquals(List.of("F1", "F2", "F3"), stored.value());
        assertTrue(stored.type().isList());
    }

    // ---- the declaration verbs, and the coercion that came with the editor -----------------------------

    @Test
    void aDeclaredParameterIsSeededWithItsTypesDefault(@TempDir Path dir) throws IOException {
        ParameterRow fresh = over(dir).declare("retries", ValueChoice.of(WHOLE)).orElseThrow();

        assertEquals("0", fresh.singleValue(), "a seeded value has to be one the bot can compile");
        assertEquals(List.of("retries"), List.of(over(dir).rows(OURS).getFirst().name()));
        // A list has no items until the user adds one; seeding one would put a blank row in every new list.
        assertEquals(List.of(),
                over(dir).declare("keys", ValueChoice.listOf(TEXT)).orElseThrow().value());
    }

    /** Declaring is the one verb that has to work on a project whose file does not exist yet. */
    @Test
    void declaringWorksOnAProjectWithNoFile(@TempDir Path dir) {
        assertTrue(over(dir).declare("retries", ValueChoice.of(WHOLE)).isPresent());
        assertEquals(1, over(dir).rows(OURS).size());
    }

    @Test
    void aNameMustBeAJavaIdentifierAndFreeInThisGroup(@TempDir Path dir) throws IOException {
        write(dir, retries());
        SdkParameters ours = over(dir);

        assertTrue(ours.declare("retries", ValueChoice.of(WHOLE)).isEmpty(), "already taken here");
        assertTrue(ours.declare("2fast", ValueChoice.of(TEXT)).isEmpty(), "not an identifier");
        assertTrue(ours.declare("has space", ValueChoice.of(TEXT)).isEmpty());
        assertTrue(ours.declare(" ", ValueChoice.of(TEXT)).isEmpty());
        assertEquals(1, ours.rows(OURS).size(), "a refusal writes nothing");
    }

    @Test
    void removingTakesTheRowOutOfTheFile(@TempDir Path dir) throws IOException {
        write(dir, retries());

        assertTrue(over(dir).remove("retries"));
        assertEquals(List.of(), over(dir).rows(OURS));
        assertFalse(over(dir).remove("retries"), "gone is gone, and saying so is not an error");
    }

    @Test
    void renamingRefusesANameAlreadyTakenHere(@TempDir Path dir) throws IOException {
        write(dir, retries(), VariableModel.of("rest", ValueChoice.of(TEXT), List.of("3s")));

        assertEquals("attempts", over(dir).rename("retries", "attempts").orElseThrow().name());
        assertTrue(over(dir).rename("attempts", "rest").isEmpty());
        assertTrue(over(dir).rename("attempts", "2fast").isEmpty());
        assertEquals("attempts", over(dir).rows(OURS).getFirst().name());
    }

    /**
     * Retyping resets the value and drops the bounds — a date is not a number, and pretending otherwise
     * stores something the editor would have to explain away on the next open.
     */
    @Test
    void retypingResetsTheValueAndDropsTheBounds(@TempDir Path dir) throws IOException {
        write(dir, retries());

        ParameterRow retyped = over(dir).retype("retries", ValueChoice.of(TEXT)).orElseThrow();

        assertEquals(TEXT.id(), retyped.type().type().id());
        assertEquals("", retyped.singleValue());
        assertEquals(Range.NONE, retyped.bounds());
    }

    /** Options survive a change of shape over one base type, and never a change of base type. */
    @Test
    void declaredOptionsSurviveAShapeChangeAndNotATypeChange(@TempDir Path dir) throws IOException {
        write(dir, new VariableModel("mode", new ValueChoice(TEXT, ValueShape.ONE_OF), List.of("safe"),
                "", "", Visibility.PUBLIC, List.of("fast", "safe"), Range.NONE, OURS));

        ParameterRow many = over(dir).retype("mode", new ValueChoice(TEXT, ValueShape.ANY_OF)).orElseThrow();
        assertEquals(List.of("fast", "safe"), many.options());

        ParameterRow asNumber = over(dir).retype("mode", ValueChoice.of(WHOLE)).orElseThrow();
        assertEquals(List.of(), asNumber.options(), "they are not values of the new type");
    }

    /** An option the author has just deleted must stop being a stored value. */
    @Test
    void replacingTheOptionsPrunesTheValue(@TempDir Path dir) throws IOException {
        write(dir, new VariableModel("mode", new ValueChoice(TEXT, ValueShape.ONE_OF), List.of("safe"),
                "", "", Visibility.PUBLIC, List.of("fast", "safe"), Range.NONE, OURS));

        ParameterRow pruned = over(dir).setOptions("mode", List.of("fast", "careful")).orElseThrow();

        assertEquals(List.of("fast", "careful"), pruned.options());
        assertEquals("fast", pruned.singleValue(), "the value it held is no longer on offer");
    }

    /** A range is advice and a clamp, never a validation that can fail. */
    @Test
    void declaringARangePullsTheValueIntoIt(@TempDir Path dir) throws IOException {
        write(dir, VariableModel.of("retries", ValueChoice.of(WHOLE), List.of("900")));

        assertEquals("5", over(dir).setBounds("retries", new Range("1", "5")).orElseThrow().singleValue());
        assertEquals("1", over(dir).apply(ParameterEdit.of(OURS, "retries", "-4")).orElseThrow()
                .singleValue(), "an edit is clamped by the same rules");
    }

    /** The editor moved here, so an edit is canonicalised on the way in rather than stored as typed. */
    @Test
    void aValueIsCanonicalisedByItsOwnType(@TempDir Path dir) throws IOException {
        write(dir, VariableModel.of("retries", ValueChoice.of(WHOLE), List.of("2")));

        assertEquals("7", over(dir).apply(ParameterEdit.of(OURS, "retries", " 7 ")).orElseThrow()
                .singleValue());
    }

    @Test
    void theSmallDeclarationsAreStoredAsGiven(@TempDir Path dir) throws IOException {
        write(dir, retries());
        SdkParameters ours = over(dir);

        assertEquals("Vision", ours.setCategory("retries", "Vision").orElseThrow().category());
        assertEquals(Visibility.PUBLIC, ours.setVisibility("retries", Visibility.PUBLIC).orElseThrow()
                .visibility());
        assertEquals("how many", ours.setDescription("retries", "how many").orElseThrow().description());
        assertEquals("how many", ours.rows(OURS).getFirst().displayLabel());
    }

    /** Every verb answers empty for a row this group does not hold, and writes nothing. */
    @Test
    void everyVerbDeclinesARowWeDoNotHold(@TempDir Path dir) throws IOException {
        write(dir, retries());
        SdkParameters ours = over(dir);
        String before = Files.readString(dir.resolve(ProjectModel.FILE_NAME));

        assertTrue(ours.rename("nobody", "somebody").isEmpty());
        assertTrue(ours.retype("nobody", ValueChoice.of(TEXT)).isEmpty());
        assertTrue(ours.setOptions("nobody", List.of("a")).isEmpty());
        assertTrue(ours.setBounds("nobody", new Range("1", "2")).isEmpty());
        assertTrue(ours.setCategory("nobody", "Timing").isEmpty());
        assertTrue(ours.setVisibility("nobody", Visibility.PUBLIC).isEmpty());
        assertTrue(ours.setDescription("nobody", "x").isEmpty());
        assertEquals(before, Files.readString(dir.resolve(ProjectModel.FILE_NAME)));
    }

    /** Another plugin's variable is in the same file and no verb here may touch it. */
    @Test
    void anotherPluginsVariableIsUntouchableFromHere(@TempDir Path dir) throws IOException {
        write(dir, VariableModel.of("channel", ValueChoice.of(TEXT), List.of("#general"))
                .withGroup("discord"));

        assertTrue(over(dir).rename("channel", "room").isEmpty());
        assertFalse(over(dir).remove("channel"));
        assertEquals("#general",
                Authoring.readModel(SdkVersion.latest(), dir).variables().getFirst().singleValue());
    }

    /** The rest of the project is not collateral: a save through this surface keeps the schema stamp. */
    @Test
    void storingAValueKeepsTheSchemaStamp(@TempDir Path dir) throws IOException {
        write(dir, retries());

        over(dir).apply(ParameterEdit.of(OURS, "retries", "4"));

        assertEquals(3, Authoring.readSchemaVersion(SdkVersion.latest(), dir));
    }
}
