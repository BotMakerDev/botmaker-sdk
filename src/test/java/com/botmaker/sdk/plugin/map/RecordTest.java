package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Argument;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.api.value.Ref;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.plugin.flow.ActivityScreens;
import com.botmaker.sdk.plugin.flow.ActivityScreens.Target;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🗺's Record into: each action in Live becomes one {@code PluginValues.appendCall} of the map's own call, its
 * arguments the map's fields by name — and which activity works where, as the flows say.
 */
class RecordTest {

    private static final Target TAVERN = new Target("Tavern", "Tavern::body", "TAVERN");

    /** One appendCall as the host received it. */
    private record Call(String method, Executable call, List<Argument> arguments) {}

    /** A host that keeps every appendCall and writes nothing. */
    private static final class Host implements PluginValues {
        final List<Call> calls = new ArrayList<>();

        @Override public List<String> ids() { return List.of(); }
        @Override public Optional<ValueContext> open(ManagedValue<?> value) { return Optional.empty(); }

        @Override
        public Optional<String> appendCall(String method, Ref call, Argument... arguments) {
            calls.add(new Call(method, Ref.resolve(call), List.of(arguments)));
            return Optional.empty();
        }
    }

    @Test
    void aPressCallsAppendCallWithNavigatePressAndTheFieldsMember() throws Exception {
        Host host = new Host();

        Optional<String> refused = BodyRecorder.press(host, TAVERN, "TAVERN", "beerBuy", false);

        assertTrue(refused.isEmpty());
        Call call = host.calls.getFirst();
        assertEquals("Tavern::body", call.method());
        assertEquals(Navigate.class.getMethod("press", com.botmaker.sdk.api.screens.Button.class), call.call());
        assertEquals(Member.of("TAVERN", "beerBuy"), call.arguments().getFirst().member());
        assertEquals(SdkValues.SCREEN_MAP, call.arguments().getFirst().set());
    }

    @Test
    void aHotkeyATypedFieldAndAWalkEachWriteTheirOwnCall() throws Exception {
        Host host = new Host();

        BodyRecorder.press(host, TAVERN, "TAVERN", "close", true);
        BodyRecorder.type(host, TAVERN, "TAVERN", "search", "ale");
        BodyRecorder.walk(host, TAVERN, "TAVERN");

        assertEquals(Navigate.class.getMethod("press", com.botmaker.sdk.api.screens.Hotkey.class),
                host.calls.get(0).call());
        assertEquals(Navigate.class.getMethod("type", com.botmaker.sdk.api.screens.Button.class, String.class),
                host.calls.get(1).call());
        assertEquals("ale", host.calls.get(1).arguments().get(1).value());
        assertEquals(Navigate.class.getMethod("to", com.botmaker.sdk.api.screens.Screen.class),
                host.calls.get(2).call());
        assertEquals(Member.of("TAVERN"), host.calls.get(2).arguments().getFirst().member());
    }

    @Test
    void whatTheLineSaysNamesTheActivityOrWhyNothingWentIn() {
        assertEquals("Recorded into Tavern: press beerBuy", BodyRecorder.said(Optional.empty(), TAVERN, "press beerBuy"));
        assertEquals("Not recorded: no such method", BodyRecorder.said(Optional.of("no such method"), TAVERN, "x"));
    }

    @Test
    void eachScreenListsTheActivitiesThatSayTheyWorkThere() {
        List<Target> targets = List.of(TAVERN, new Target("Mine", "Mine::body", null),
                new Target("Brew", "Brew::body", "TAVERN"));

        assertEquals(Map.of("TAVERN", List.of("Tavern", "Brew")), ActivityScreens.byScreen(targets));
        assertEquals(Optional.of("TAVERN"), ActivityScreens.screenOf(targets, "Brew::body"));
        assertEquals(Optional.empty(), ActivityScreens.screenOf(targets, "Mine::body"));
    }
}
