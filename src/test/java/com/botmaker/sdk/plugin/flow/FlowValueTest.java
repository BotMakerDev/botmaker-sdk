package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.plugin.SpelledValues;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which flow is edited, how a flow is named, and its cards following a rename or a delete. */
class FlowValueTest {

    @Test
    void mainIsTheDefaultElseTheFirst() {
        assertEquals("MAIN", FlowValue.defaultName(List.of()));
        assertEquals("MAIN", FlowValue.defaultName(List.of("DAILY", "MAIN")));
        assertEquals("DAILY", FlowValue.defaultName(List.of("DAILY", "NIGHTLY")));
    }

    @Test
    void aFlowNameIsAConstantNoOtherFlowHas() {
        assertNull(FlowValue.nameProblem("daily", "DAILY", List.of("MAIN")));
        assertTrue(FlowValue.nameProblem("Main", "MAIN", List.of("MAIN")).contains("already has a flow Flows.MAIN"));
        assertTrue(FlowValue.nameProblem("2nd", null, List.of()).contains("makes no constant name"));
    }

    @Test
    void aFlowsCardsFollowItsRenameAndGoWithIt() {
        Map<String, FlowLayout.Cards> flows = new LinkedHashMap<>();
        flows.put("MAIN", FlowLayout.cards(Map.of("Collect", FlowLayout.at(1, 2))));
        flows.put("DAILY", FlowLayout.cards(Map.of("Rest", FlowLayout.at(3, 4))));
        FlowLayout layout = FlowLayout.of(flows, false);

        FlowLayout renamed = FlowValue.moved(layout, "DAILY", "FARM");
        assertEquals(List.of("MAIN", "FARM"), List.copyOf(renamed.flows().keySet()));
        assertEquals(FlowLayout.at(3, 4), renamed.cardsOf("FARM").spot("Rest"));
        assertEquals(false, renamed.goHomeByDefault());

        assertEquals(List.of("MAIN"), List.copyOf(FlowValue.moved(layout, "DAILY", null).flows().keySet()));
        assertSame(layout, FlowValue.moved(layout, "NIGHTLY", "X"), "a flow with no cards moves nothing");
    }

    /** A bot's {@code Flows} class, the constants in file order, each add recorded. */
    private static final class Flows extends SpelledValues {
        final List<String> members = new ArrayList<>();

        @Override
        public List<String> ids() {
            return List.of();
        }

        @Override
        public Optional<ValueContext> open(String id) {
            return Optional.empty();
        }

        @Override
        public List<String> members(String id) {
            return List.copyOf(members);
        }

        @Override
        public Optional<String> add(String id, String member, Object value) {
            members.add(member);
            return Optional.empty();
        }
    }

    private static StudioServices over(PluginValues values) {
        return new StudioServices() {
            @Override public java.nio.file.Path projectDir() { return null; }
            @Override public java.nio.file.Path resourcesDir() { return null; }
            @Override public com.botmaker.plugin.api.Theme theme() { return null; }
            @Override public com.botmaker.plugin.api.Dialogs dialogs() { return null; }
            @Override public PluginValues pluginValues() { return values; }
        };
    }

    @Test
    void aSecondFlowKeepsTheMainFlowTheEditorShowedBeforeAnyWasWritten() {
        Flows flows = new Flows();
        assertNull(FlowValue.create(over(flows), "daily", Flow.NONE));
        assertEquals(List.of("MAIN", "DAILY"), flows.members, "MAIN is written first, so it stays");

        assertNull(FlowValue.create(over(flows), "nightly", Flow.NONE));
        assertEquals(List.of("MAIN", "DAILY", "NIGHTLY"), flows.members);

        Flows fresh = new Flows();
        assertNull(FlowValue.create(over(fresh), "main", Flow.NONE));
        assertEquals(List.of("MAIN"), fresh.members, "MAIN itself is written once");
    }

    @Test
    void aWriteTheHostRefusesIsSaidNotSaved() {
        ValueContext refusing = new ValueContext() {
            @Override
            public com.botmaker.plugin.api.slot.TypeRef type() {
                return null;
            }

            @Override
            public <T> Optional<T> value(Class<T> type) {
                return Optional.empty();
            }

            @Override
            public String source() {
                return "Flow.NONE";
            }

            @Override
            public StudioServices services() {
                return null;
            }

            @Override
            public void set(Object value) {}

            @Override
            public Optional<String> write(Object value) {
                return Optional.of("Activities.Egezg.class cannot be written.");
            }
        };
        FlowValue.Target target = new FlowValue.Target("ZGZG", Optional.of(refusing), Flow.NONE, Run.NONE, null);
        assertEquals("Activities.Egezg.class cannot be written.",
                FlowValue.write(over(new Flows()), target, Flow.NONE));
    }

    @Test
    void aSubflowCardKeepsItsPlaceWhenItsFlowIsRenamed() {
        Map<String, FlowLayout.Cards> flows = new LinkedHashMap<>();
        flows.put("MAIN", FlowLayout.cards(Map.of("Collect", FlowLayout.at(1, 2), "▸ DAILY", FlowLayout.at(5, 6))));
        flows.put("DAILY", FlowLayout.cards(Map.of("Rest", FlowLayout.at(3, 4))));
        FlowLayout renamed = FlowValue.subflowMoved(FlowLayout.of(flows, true), "DAILY", "FARM");
        assertEquals(FlowLayout.at(5, 6), renamed.cardsOf("MAIN").spot("▸ FARM"));
        assertNull(renamed.cardsOf("MAIN").spot("▸ DAILY"));
        assertEquals(FlowLayout.at(1, 2), renamed.cardsOf("MAIN").spot("Collect"));
    }
}
