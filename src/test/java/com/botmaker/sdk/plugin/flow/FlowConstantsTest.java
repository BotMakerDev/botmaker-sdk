package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.plugin.types.FlowTypes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a save asks of the bot's {@code Activities} class — one enum per activity — around writing the flow. */
class FlowConstantsTest {

    /** A project's {@code Activities} class, held as enum → constants, recording each change asked of it. */
    private static final class Project implements PluginValues {
        final Map<String, List<String>> enums = new LinkedHashMap<>();
        final List<String> asked = new ArrayList<>();
        /** Members a remove is refused for: code still uses them. */
        final Set<String> used;

        Project(Set<String> used, String... enumThenConstants) {
            this.used = used;
            for (String declaration : enumThenConstants) {
                String[] parts = declaration.split(" ");
                enums.put(parts[0], new ArrayList<>(List.of(parts).subList(1, parts.length)));
            }
        }

        @Override
        public List<String> ids() {
            return List.of();
        }

        @Override
        public Optional<ValueContext> open(String id) {
            return Optional.empty();
        }

        @Override
        public List<String> groups(String id) {
            return List.copyOf(enums.keySet());
        }

        @Override
        public List<String> members(String id) {
            List<String> members = new ArrayList<>();
            enums.forEach((group, constants) -> constants.forEach(c -> members.add(group + "." + c)));
            return members;
        }

        @Override
        public Optional<String> addGroup(String id, String group, List<String> constants) {
            asked.add("addGroup " + group + " " + constants);
            enums.put(group, new ArrayList<>(constants));
            return Optional.empty();
        }

        @Override
        public Optional<String> renameGroup(String id, String group, String newName) {
            asked.add("renameGroup " + group + " " + newName);
            enums.put(newName, enums.remove(group));
            return Optional.empty();
        }

        @Override
        public Optional<String> removeGroup(String id, String group) {
            asked.add("removeGroup " + group);
            if (used.contains(group)) return Optional.of("used in Gamebot.java");
            enums.remove(group);
            return Optional.empty();
        }

        @Override
        public Optional<String> add(String id, String member, Object value) {
            asked.add("add " + member);
            String[] parts = member.split("\\.");
            enums.get(parts[0]).add(parts[1]);
            return Optional.empty();
        }

        @Override
        public Optional<String> rename(String id, String member, String newName) {
            asked.add("rename " + member + " " + newName);
            String[] parts = member.split("\\.");
            List<String> constants = enums.get(parts[0]);
            constants.set(constants.indexOf(parts[1]), newName);
            return Optional.empty();
        }

        @Override
        public Optional<String> remove(String id, String member) {
            asked.add("remove " + member);
            if (used.contains(member)) return Optional.of("returned by Collect.java");
            String[] parts = member.split("\\.");
            enums.get(parts[0]).remove(parts[1]);
            return Optional.empty();
        }

        /** Each enum's methods: {@code Collect::body} returns {@code Collect}, whether a flow links it or not. */
        @Override
        public List<String> methods(String id, String group) {
            return group == null ? List.of("Gamebot::goHome") : List.of(group + "::body");
        }

        @Override
        public MethodWrite createMethod(String id, String group, String className, String method) {
            asked.add("createMethod " + group + " " + className + "::" + method);
            return group != null && !enums.containsKey(group) ? new MethodWrite(null, "no enum " + group)
                    : new MethodWrite(className + "::" + method, null);
        }
    }

    /** A body is one of the methods returning its enum; home one returning nothing; either can be written. */
    @Test
    void the_methods_a_card_can_run_and_the_way_home() {
        Project project = new Project(Set.of(), "Collect NEXT");
        assertEquals(List.of("Collect::body"), FlowConstants.bodies(project, "Collect"));
        assertEquals(List.of("Gamebot::goHome"), FlowConstants.homes(project));
        assertEquals("Collect::gather", FlowConstants.createMethod(project, "Collect", "Collect", "gather").reference());
        assertEquals("Home::goHome", FlowConstants.createMethod(project, null, "Home", "goHome").reference());
        assertFalse(FlowConstants.createMethod(project, "Battle", "Battle", "body").written());
        assertEquals(List.of("createMethod Collect Collect::gather", "createMethod null Home::goHome",
                "createMethod Battle Battle::body"), project.asked);
        assertEquals(List.of(MethodPicker.NONE, "Collect::body", "Old::gone"),
                MethodPicker.items(List.of("Collect::body"), "Old::gone"), "what the bot holds stays listed");
    }

    /** The activity a body belongs to is the enum it returns, which the host reads off its declaration. */
    @Test
    void a_method_belongs_to_the_enum_it_returns() {
        Project project = new Project(Set.of(), "Collect NEXT", "Battle NEXT");
        assertEquals(Optional.of("Battle"), FlowConstants.returnedBy(project, "Battle::body"));
        assertEquals(Optional.empty(), FlowConstants.returnedBy(project, "Gamebot::goHome"));
        assertEquals(Optional.empty(), FlowConstants.returnedBy(project, null));
    }

    private static Board board(Map<String, List<String>> exits) {
        List<Flow.Step> steps = new ArrayList<>();
        for (String activity : exits.keySet()) {
            steps.add(FlowTypes.step(FlowNames.activity(activity), ActivityBody.none()));
        }
        return new Board(new Flow(steps, List.of(), steps.isEmpty() ? null : steps.getFirst().activity(),
                Flow.Limits.DEFAULT), exits);
    }

    private static <K, V> Map<K, V> ordered(K k1, V v1, K k2, V v2) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    @Test
    void the_project_is_read_as_enums_of_constants() {
        Project project = new Project(Set.of(), "Collect NEXT NOTHING_LEFT", "Rest NEXT");
        assertEquals(Map.of("Collect", List.of("NEXT", "NOTHING_LEFT"), "Rest", List.of("NEXT")),
                FlowConstants.declared(project));
    }

    @Test
    void a_new_activity_is_an_enum_seeded_with_its_outcomes() {
        Project project = new Project(Set.of(), "Collect NEXT");
        String refused = FlowConstants.prepare(project, board(ordered("Collect", List.of("Next"),
                "Daily reward", List.of("Next", "Done"))), new LinkedHashMap<>(), new LinkedHashMap<>());
        assertNull(refused);
        assertEquals(List.of("addGroup DailyReward [NEXT, DONE]"), project.asked);
    }

    @Test
    void a_new_outcome_is_a_constant_of_its_activitys_enum() {
        Project project = new Project(Set.of(), "Collect NEXT", "Battle NEXT");
        assertNull(FlowConstants.prepare(project, board(ordered("Collect", List.of("Next", "Bag full"),
                "Battle", List.of("Next"))), new LinkedHashMap<>(), new LinkedHashMap<>()));
        assertEquals(List.of("add Collect.BAG_FULL"), project.asked);
    }

    /** A rename renames the enum or the constant that held the old label, rather than adding one beside it. */
    @Test
    void a_canvas_rename_renames_the_enum_and_the_constant() {
        Project project = new Project(Set.of(), "Battle NEXT WON");
        Map<String, String> activityRenames = new LinkedHashMap<>(Map.of("Battle", "Fight"));
        Map<String, Map<String, String>> outcomeRenames = new LinkedHashMap<>();
        outcomeRenames.put("Fight", new LinkedHashMap<>(Map.of("Won", "Victory")));
        assertNull(FlowConstants.prepare(project, board(Map.of("Fight", List.of("Next", "Victory"))),
                activityRenames, outcomeRenames));
        assertEquals(List.of("renameGroup Battle Fight", "rename Fight.WON VICTORY"), project.asked);
        assertTrue(activityRenames.isEmpty(), "a rename that landed is spent");
        assertTrue(outcomeRenames.get("Fight").isEmpty());
    }

    /** "Won" to "WON!" is still WON: nothing is renamed, and the canvas's rename is spent. */
    @Test
    void a_rename_to_the_same_constant_renames_nothing() {
        Project project = new Project(Set.of(), "Battle NEXT WON");
        Map<String, Map<String, String>> outcomeRenames = new LinkedHashMap<>();
        outcomeRenames.put("Battle", new LinkedHashMap<>(Map.of("Won", "WON!")));
        assertNull(FlowConstants.prepare(project, board(Map.of("Battle", List.of("Next", "WON!"))),
                new LinkedHashMap<>(), outcomeRenames));
        assertEquals(List.of(), project.asked);
    }

    /** What the canvas dropped goes after the write; what code still uses stays, said. */
    @Test
    void forgetting_removes_what_the_canvas_dropped_and_keeps_what_is_used() {
        Project project = new Project(Set.of("Collect.WON", "Rest"), "Collect NEXT WON LOST", "Rest NEXT", "Old NEXT");
        List<String> notes = new ArrayList<>();
        FlowConstants.forget(project, board(Map.of("Collect", List.of("Next"))), List.of("Collect", "Rest", "Old"),
                Set::of, notes);
        assertEquals(List.of("remove Collect.WON", "remove Collect.LOST", "removeGroup Rest", "removeGroup Old"),
                project.asked);
        assertEquals(Map.of("Collect", List.of("NEXT", "WON"), "Rest", List.of("NEXT")), project.enums);
        assertEquals(2, notes.size(), notes.toString());
        assertTrue(notes.getFirst().contains("Activities.Collect.WON stays"), notes.getFirst());
    }

    /** An enum the last save did not have is not the canvas's to remove. */
    @Test
    void forgetting_leaves_an_enum_the_canvas_never_drew() {
        Project project = new Project(Set.of(), "Collect NEXT", "Mine NEXT");
        FlowConstants.forget(project, board(Map.of("Collect", List.of("Next"))), Set.of("Collect"), Set::of,
                new ArrayList<>());
        assertEquals(List.of(), project.asked);
    }

    /** An activity one flow drops is the bot's still while another flow draws it. */
    @Test
    void forgetting_keeps_an_enum_another_flow_draws() {
        Project project = new Project(Set.of(), "Collect NEXT", "Rest NEXT");
        FlowConstants.forget(project, board(Map.of("Collect", List.of("Next"))), Set.of("Collect", "Rest"),
                () -> Set.of("Rest"), new ArrayList<>());
        assertEquals(List.of(), project.asked);
        assertEquals(List.of("Next"), FlowConstants.outcomesOf(project, "Rest"));
        assertEquals(List.of(), FlowConstants.outcomesOf(project, "Battle"));
    }

    /** + New outcome… in a body's return slot declares it in that activity's enum, once. */
    @Test
    void a_new_outcome_is_declared_in_one_enum_once() {
        Project project = new Project(Set.of(), "Battle NEXT");
        assertEquals(Optional.empty(), FlowConstants.declare(project, "Battle", "Timeout"));
        assertEquals(Optional.empty(), FlowConstants.declare(project, "Battle", "timeout"));
        assertEquals(List.of("add Battle.TIMEOUT"), project.asked);
        assertTrue(FlowConstants.declare(project, "Battle", "!!").isPresent());
    }

    /**
     * A name written by hand in another case ({@code Collect.won}) is shown, but the card names it back as
     * {@code WON}: the save refuses rather than write a value nothing holds.
     */
    @Test
    void a_flow_naming_what_the_enums_lack_is_not_written() {
        Flow flow = new Flow(List.of(FlowTypes.step(FlowNames.activity("Collect"), ActivityBody.none())),
                List.of(new Flow.Edge(FlowNames.outcome("Collect", "Won"), FlowNames.activity("Collect"))),
                FlowNames.activity("Collect"), Flow.Limits.DEFAULT);

        assertNull(FlowConstants.unheld(new Project(Set.of(), "Collect NEXT WON"), flow));
        String refused = FlowConstants.unheld(new Project(Set.of(), "Collect NEXT won"), flow);
        assertNotNull(refused);
        assertTrue(refused.contains("Activities.Collect.WON"), refused);
        assertNotNull(FlowConstants.unheld(new Project(Set.of()), flow));
    }
}
