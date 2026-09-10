package com.botmaker.sdk.internal.plugin;

import com.botmaker.plugin.api.ParameterEdit;
import com.botmaker.plugin.api.ParameterGroup;
import com.botmaker.plugin.api.ParameterRow;
import com.botmaker.plugin.api.value.Range;
import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueChoice;
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

    /** The rest of the project is not collateral: a save through this surface keeps the schema stamp. */
    @Test
    void storingAValueKeepsTheSchemaStamp(@TempDir Path dir) throws IOException {
        write(dir, retries());

        over(dir).apply(ParameterEdit.of(OURS, "retries", "4"));

        assertEquals(3, Authoring.readSchemaVersion(SdkVersion.latest(), dir));
    }
}
