package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.params.ParamCheck;
import com.botmaker.sdk.internal.screens.MapFixtures;
import com.botmaker.sdk.plugin.assist.ServesProposal.Proposed;
import com.botmaker.sdk.plugin.types.ScreenTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Discovery's guess at the goals a press served, from the facts read before and after it (2026-11-08). */
class ServesProposalTest {

    /** {@code Need.atLeast(Progress::gold, 100)}'s check, as Studio reads it. */
    private static ParamCheck check(String fact, ParamCheck.Test test, Object value) {
        return ScreenTypes.test(fact, test, value);
    }

    private static Map<String, Goal> goals() {
        Map<String, Goal> goals = new LinkedHashMap<>();
        goals.put("GOLD_AT_LEAST_100", Goal.of(check("Progress::gold", ParamCheck.Test.AT_LEAST, 100)));
        goals.put("GUILD_JOINED", Goal.of(check("Progress::guildJoined", ParamCheck.Test.IS_TRUE, null)));
        goals.put("ENERGY_BELOW_5", Goal.of(new Condition.Not(check("Progress::energy", ParamCheck.Test.AT_LEAST, 5))));
        return goals;
    }

    @Test
    void proposesWhenCheckTurnsYes() {
        Map<String, String> before = Map.of("Progress::gold", "40", "Progress::guildJoined", "false",
                "Progress::energy", "3");
        Map<String, String> after = Map.of("Progress::gold", "1,200", "Progress::guildJoined", "false",
                "Progress::energy", "3");

        List<Proposed> proposed = ServesProposal.of(goals(), null, before, after);

        assertEquals(List.of(new Proposed("GOLD_AT_LEAST_100", "gold 40 → 1,200")), proposed,
                "the guild did not move, and energy was below 5 before already");
        assertEquals(List.of(), ServesProposal.of(goals(), null, Map.of(), after), "unknown before: no proposal");
        assertEquals(List.of(new Proposed("GUILD_JOINED", "guildJoined false → true")),
                ServesProposal.of(goals(), null, before, Map.of("Progress::gold", "40",
                        "Progress::guildJoined", "true", "Progress::energy", "3")));
        assertEquals(List.of(new Proposed("ENERGY_BELOW_5", "energy 7 → 3")),
                ServesProposal.of(goals(), null, Map.of("Progress::energy", "7"), Map.of("Progress::energy", "3")),
                "a Need.below is a not over at least");
        String said = ServesProposal.said("SHOP", "sell", null, proposed);
        assertTrue(said.contains("Do.press(sell).serves(Goals.GOLD_AT_LEAST_100) on SHOP"), said);
        assertTrue(said.contains("Proposed, not written"), said);
    }

    @Test
    void neverWritesAlone(@TempDir Path project) {
        Do selling = Do.press(MapFixtures.ref("SHOP", "sell")).serves(BotConstants.goal("GOLD_AT_LEAST_100"));
        Map<String, String> before = Map.of("Progress::gold", "40");
        Map<String, String> after = Map.of("Progress::gold", "400");

        assertEquals(List.of(), ServesProposal.of(goals(), selling, before, after), "it serves it already");
        Do plain = Do.press(MapFixtures.ref("SHOP", "sell"));
        List<Proposed> proposed = ServesProposal.of(goals(), plain, before, after);
        ServesProposal.note(project, "SHOP", "sell", proposed);

        assertEquals(List.of(), plain.serves(), "the action is as it was: a proposal writes nothing");
        assertEquals(List.of("GOLD_AT_LEAST_100"), ServesProposal.proposed(project, "SHOP", "sell", plain));
        assertEquals(List.of(), ServesProposal.proposed(project, "SHOP", "sell", selling),
                "once the author writes .serves, the badge goes");
        assertTrue(ServesProposal.presses(selling, "SHOP", null, "sell"));
        assertTrue(!ServesProposal.presses(selling, "HOME", null, "sell"), "another screen's sell is another input");
        // Nothing here can write the bot: no method of its own takes Studio's services, its values or a value's
        // context (the try's lambdas read the goals and the kept values, as GoalsView does).
        List<String> writers = Arrays.stream(ServesProposal.class.getDeclaredMethods()).filter(m -> !m.isSynthetic())
                .filter(m -> Arrays.stream(m.getParameterTypes()).anyMatch(t -> t.getName().endsWith("StudioServices")
                        || t.getName().endsWith("PluginValues") || t.getName().endsWith("ValueContext")))
                .map(Method::getName).toList();
        assertEquals(List.of(), writers);
    }
}
