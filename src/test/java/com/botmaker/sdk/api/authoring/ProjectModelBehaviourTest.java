package com.botmaker.sdk.api.authoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The derived answers the model owes both of its readers.
 *
 * <p>Every question here used to be answered in the editor, over the editor's own record set. It moved
 * because two things consult it and a disagreement between them is not a wrong pixel — it is a bot that does
 * not compile. Which activities a run reaches decides both what the canvas greys out as an orphan and what
 * the generated registry instantiates; which names are taken decides both what a dialog refuses and what
 * javac refuses.
 */
class ProjectModelBehaviourTest {

    private static ActivityModel activity(String name) {
        return ActivityModel.of(name);
    }

    private static FlowNodeModel node(String name) {
        return new FlowNodeModel(name, 0, 0);
    }

    // ---- reachability -----------------------------------------------------------------------------------

    @Test
    void anUnwiredFlowLeavesEveryActivityInDeclarationOrder() {
        ProjectModel model = ProjectModel.of(List.of(activity("A"), activity("B")), List.of());

        assertEquals(List.of("A", "B"), model.orderedActivities().stream().map(ActivityModel::name).toList());
    }

    @Test
    void anOrphanIsLeftOutOfTheRunOrderAndKeepsItsFlag() {
        FlowModel flow = new FlowModel(List.of(node("A"), node("B"), node("Lonely")),
                List.of(new FlowEdgeModel("A", "B", null)), "A", 0, -1);
        ProjectModel model = ProjectModel.of(List.of(activity("A"), activity("B"), activity("Lonely")), List.of())
                .withFlow(flow);

        assertEquals(List.of("A", "B"), model.orderedActivities().stream().map(ActivityModel::name).toList());
        assertEquals(List.of("A", "B", "Lonely"),
                model.activityFlags().stream().map(VariableModel::name).toList(),
                "an orphan still declares its enable flag — wiring it up is one drag away");
    }

    /** A flow may legitimately loop; the walk has to terminate rather than spin. */
    @Test
    void aCycleTerminates() {
        FlowModel flow = new FlowModel(List.of(node("A"), node("B")),
                List.of(new FlowEdgeModel("A", "B", null), new FlowEdgeModel("B", "A", null)), "A", 0, -1);

        assertEquals(List.of("A", "B"), flow.reachable(List.of("A", "B")));
    }

    /** A wire left behind by a deleted activity must not resurrect it. */
    @Test
    void aStaleWireIsIgnored() {
        FlowModel flow = new FlowModel(List.of(node("A")),
                List.of(new FlowEdgeModel("A", "Deleted", null)), "A", 0, -1);

        assertEquals(List.of("A"), flow.reachable(List.of("A")));
    }

    /** A start naming something that is gone falls back to the first placed activity, never to nothing. */
    @Test
    void aStartThatWasDeletedFallsBackToWhatIsPlaced() {
        FlowModel flow = new FlowModel(List.of(node("A"), node("B")),
                List.of(new FlowEdgeModel("A", "B", null)), "Gone", 0, -1);

        assertEquals("A", flow.resolvedStart(List.of("A", "B")));
        assertEquals(List.of("A", "B"), flow.reachable(List.of("A", "B")));
    }

    // ---- names ------------------------------------------------------------------------------------------

    @Test
    void anActivityAndAVariableShareOneNamespace() {
        ProjectModel model = ProjectModel.of(List.of(activity("Mining")),
                List.of(VariableModel.of("Rest", ValueChoice.of(ValueType.DURATION), List.of("90s"))));

        assertTrue(model.nameClash("Mining", null));
        assertTrue(model.nameClash("Rest", null));
        assertTrue(model.nameClash("mining", null), "a case-insensitive filesystem cannot tell the stubs apart");
        assertFalse(model.nameClash("Fishing", null));
    }

    @Test
    void renamingSomethingToItsOwnNameIsNotAClash() {
        ProjectModel model = ProjectModel.of(List.of(activity("Mining")), List.of());

        assertFalse(model.nameClash("Mining", "Mining"));
        assertFalse(model.nameClash("  mining ", "Mining"));
    }

    @Test
    void aBlankNameIsNeverAClash() {
        assertFalse(ProjectModel.of(List.of(activity("Mining")), List.of()).nameClash("  ", null));
    }

    // ---- flags, presets, grouping -----------------------------------------------------------------------

    @Test
    void theEnableFlagIsAYesNoTaggedWithItsOwnActivityAndHiddenFromTheRunner() {
        VariableModel flag = new ActivityModel("Mining", true, "dig", List.of(), null, null).enabledVariable();

        assertEquals("Mining", flag.name());
        assertEquals(ValueType.YES_NO, flag.type().type());
        assertEquals(List.of("true"), flag.value());
        assertEquals("Mining", flag.tag());
        assertEquals(Visibility.EDITOR_ONLY, flag.visibility());
        assertFalse(flag.isPublic(), "the runner already offers every activity its own switch");
    }

    @Test
    void applyingAPresetTurnsOnExactlyWhatItNames() {
        ProjectModel model = ProjectModel.of(
                List.of(activity("A"), activity("B"), activity("C")), List.of());

        ProjectModel applied = model.applyPreset(new PresetModel("Night", List.of("B")));

        assertEquals(List.of(false, true, false),
                applied.activities().stream().map(ActivityModel::enabled).toList());
    }

    @Test
    void aPresetCapturedFromTheModelNamesTheEnabledOnes() {
        ProjectModel model = ProjectModel.of(
                List.of(activity("A").withEnabled(false), activity("B")), List.of());

        assertEquals(List.of("B"), PresetModel.fromCurrent("Now", model).enabledActivities());
    }

    @Test
    void onlyPublicVariablesAreGroupedForTheRunnerAndAnUntaggedOneLandsUnderGeneral() {
        VariableModel shown = VariableModel.of("Rest", ValueChoice.of(ValueType.DURATION), List.of("90s"));
        VariableModel tagged = shown.withName("Depth").withTag("Mining");
        VariableModel hidden = shown.withName("Retry").withVisibility(Visibility.EDITOR_ONLY);
        ProjectModel model = ProjectModel.of(List.of(), List.of(shown, tagged, hidden));

        var byTag = model.sharedVariables();

        assertEquals(List.of(VariableModel.GENERAL, "Mining"), List.copyOf(byTag.keySet()));
        assertEquals(List.of("Rest"), byTag.get(VariableModel.GENERAL).stream().map(VariableModel::name).toList());
        assertEquals(List.of("Depth"), byTag.get("Mining").stream().map(VariableModel::name).toList());
    }

    /** A copy swaps one component and re-derives nothing — the record stays the file, not the editing of it. */
    @Test
    void aCopyChangesOneThingAndCarriesTheRest() {
        VariableModel original = new VariableModel("Rest", ValueChoice.of(ValueType.WHOLE_NUMBER),
                List.of("5"), "how long", "Mining", Visibility.PUBLIC, List.of(), new Range("0", "10"));

        VariableModel renamed = original.withName("Pause");

        assertEquals("Pause", renamed.name());
        assertEquals(original.value(), renamed.value());
        assertEquals(original.bounds(), renamed.bounds());
        assertEquals("how long", renamed.displayLabel());
        assertEquals("Rest", original.withDescription("").displayLabel(), "no description ⇒ the name");
    }

    @Test
    void anActivityCreatedByAnEditorStartsOff() {
        assertFalse(ActivityModel.create("Mining", "dig").enabled());
        assertTrue(ActivityModel.of("Mining").enabled());
    }

    /**
     * The enum list and the port list differ in exactly one place, and the two methods exist so they can:
     * {@code DISABLED} is a port the flow wires from, never a constant {@code run()} could return.
     */
    @Test
    void disabledIsAPortButNotAnOutcome() {
        ActivityModel mining = ActivityModel.of("Mining").withOutcomes(List.of("BAG_FULL"));

        assertEquals(List.of("NEXT", "BAG_FULL"), mining.allOutcomes());
        assertEquals(List.of("NEXT", "BAG_FULL", "DISABLED"), mining.flowPorts(),
                "DISABLED comes last — every other port is a way the activity finished");
    }

    /** A file naming an outcome the activity already has must not emit it twice, whichever one it names. */
    @Test
    void anImplicitOutcomeDeclaredInTheFileIsNotDuplicated() {
        ActivityModel mining = ActivityModel.of("Mining")
                .withOutcomes(List.of("NEXT", "DISABLED", "BAG_FULL"));

        assertEquals(List.of("NEXT", "BAG_FULL"), mining.allOutcomes());
        assertEquals(List.of("NEXT", "BAG_FULL", "DISABLED"), mining.flowPorts());
    }
}
