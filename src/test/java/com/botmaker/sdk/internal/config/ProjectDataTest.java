package com.botmaker.sdk.internal.config;

import com.botmaker.plugin.api.value.ValueChoice;
import com.botmaker.sdk.authoring.ActivityModel;
import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.ProjectModel;
import com.botmaker.sdk.authoring.SdkVersion;
import com.botmaker.sdk.authoring.VariableModel;
import com.botmaker.sdk.internal.authoring.SdkValueTypes;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The runtime half of {@code activities.json}.
 *
 * <p>The test that matters most is {@link #readsBackWhatTheEditorWrites}. There are two readers of this file
 * and there have to be — the editor's records name {@code com.botmaker.plugin.api} types and
 * {@code botmaker-studio-api} is deliberately off a bot's classpath, so a bot cannot load them. Two readers
 * of one file is a standing risk, and the only honest mitigation is a test that writes with one and reads
 * with the other.
 *
 * <p>Everything else here is the same rule from a different angle: <b>a bot does not fail to start because of
 * its own configuration file</b>. Every lookup below has an answer for a file that is missing, empty,
 * corrupt, or simply does not mention what was asked for.
 */
class ProjectDataTest {

    // ---- the two readers agree --------------------------------------------------------------------------

    @Test
    void readsBackWhatTheEditorWrites() throws Exception {
        ProjectModel model = ProjectModel.of(
                List.of(ActivityModel.create("Mining", "dig").withOutcomes(List.of("BAG_FULL"))
                                .withEnabled(true),
                        ActivityModel.create("Fishing", "").withEnabled(false)),
                List.of(VariableModel.of("minHealth", ValueChoice.of(SdkValueTypes.WHOLE_NUMBER),
                        List.of("20"))));

        ProjectData data = ProjectData.of(Authoring.modelJson(SdkVersion.latest(), model, 2));

        assertEquals(List.of("Mining", "Fishing"), data.activities());
        assertTrue(data.enabled("Mining"));
        assertFalse(data.enabled("Fishing"));
        assertEquals(List.of("BAG_FULL"), data.outcomes("Mining"));
        assertEquals("20", data.value("minHealth"));
        assertEquals(List.of("minHealth"), data.variables());
    }

    @Test
    void readsBackAnEmptyModel() throws Exception {
        ProjectData data = ProjectData.of(
                Authoring.modelJson(SdkVersion.latest(), ProjectModel.empty(), 2));

        assertTrue(data.isEmpty());
        assertEquals(List.of(), data.activities());
    }

    // ---- the classpath ----------------------------------------------------------------------------------

    @Test
    void findsTheModelOnTheClasspath() {
        // src/test/resources/activities.json — the same place a generated bot's own file sits.
        ProjectData data = ProjectData.load(ProjectData.RESOURCE);

        assertFalse(data.isEmpty());
        assertTrue(data.enabled("Mining"));
    }

    @Test
    void aMissingFileIsAnEmptyModelAndNotAnError() {
        // An empty project has no model, and a game bot that has never had an activity added reads the same
        // as one whose file was deleted. Neither is a misconfiguration to complain about.
        ProjectData data = ProjectData.load("/no-such-file.json");

        assertTrue(data.isEmpty());
        assertFalse(data.enabled("Mining"));
        assertEquals("", data.value("minHealth"));
    }

    @Test
    void aFileThatWillNotParseIsAnEmptyModelAndNotAThrow() {
        ProjectData data = ProjectData.of("{ this is not json");

        assertTrue(data.isEmpty());
        assertEquals(List.of(), data.variables());
    }

    // ---- activities -------------------------------------------------------------------------------------

    @Test
    void anActivityNothingKnowsAboutIsOff() {
        // false rather than true: a bot silently running an activity its own configuration has never heard of
        // is worse than one that quietly skips it.
        ProjectData data = ProjectData.load(ProjectData.RESOURCE);

        assertFalse(data.enabled("Smithing"));
        assertEquals(List.of(), data.outcomes("Smithing"));
        assertFalse(data.goHome("Smithing"));
        assertFalse(data.popupCheck("Smithing"));
    }

    @Test
    void readsAnActivitysOwnFlags() {
        ProjectData data = ProjectData.load(ProjectData.RESOURCE);

        assertTrue(data.goHome("Mining"));
        assertTrue(data.popupCheck("Mining"));
        assertFalse(data.goHome("Fishing"));
        assertEquals(List.of("BAG_FULL", "NO_ORE"), data.outcomes("Mining"));
    }

    @Test
    void aNullNameAsksNothingRatherThanThrowing() {
        ProjectData data = ProjectData.load(ProjectData.RESOURCE);

        assertFalse(data.enabled(null));
        assertEquals("", data.value(null));
        assertEquals(List.of(), data.values(null));
        assertFalse(data.declares(null));
    }

    // ---- variables --------------------------------------------------------------------------------------

    @Test
    void readsAStoredValueAsTheTextTheFileHolds() {
        ProjectData data = ProjectData.load(ProjectData.RESOURCE);

        assertEquals("20", data.value("minHealth"));
        assertEquals("1m30s", data.value("restBetween"));
        assertEquals(List.of("ore", "gem"), data.values("targets"));
        assertEquals("ore", data.value("targets"));
    }

    @Test
    void tellsAnUnsetValueApartFromAnUndeclaredOne() {
        // Both read as "" through value(); only one of the two is a mistake, and declares() is the question
        // that separates them.
        ProjectData data = ProjectData.load(ProjectData.RESOURCE);

        assertEquals("", data.value("unset"));
        assertTrue(data.declares("unset"));
        assertEquals("", data.value("nonesuch"));
        assertFalse(data.declares("nonesuch"));
    }

    @Test
    void listsTheNamesInTheOrderTheFileHoldsThem() {
        ProjectData data = ProjectData.load(ProjectData.RESOURCE);

        assertEquals(List.of("Mining", "Fishing"), data.activities());
        assertEquals(List.of("minHealth", "greeting", "restBetween", "anchor", "unset", "targets"),
                data.variables());
    }

    // ---- flow -------------------------------------------------------------------------------------------

    @Test
    void handsTheFlowOverWithoutInterpretingIt() {
        ProjectData data = ProjectData.load(ProjectData.RESOURCE);

        assertEquals("Mining", data.flow().path("start").asText());
        assertEquals(1000, data.flow().path("maxSteps").asInt());
        assertTrue(ProjectData.empty().flow().path("start").asText("").isEmpty());
    }
}
