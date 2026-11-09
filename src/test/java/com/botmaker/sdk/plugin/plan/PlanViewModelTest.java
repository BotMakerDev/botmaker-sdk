package com.botmaker.sdk.plugin.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.bot.BotConstants;
import com.botmaker.sdk.internal.plan.DoneRecords;
import com.botmaker.sdk.internal.plan.PlanModel;
import com.botmaker.sdk.internal.plan.StudioPlan;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.plan.PlanViewModel.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The plan view's tiers of goal chips, read from a plan and a map as Studio reads them. */
class PlanViewModelTest {

    private static final Instant NOON = Instant.parse("2026-11-06T12:00:00Z");

    @TempDir
    Path dir;

    private static Goal answering(Answer answer) {
        return Goal.of(new Condition() {
            @Override
            public Answer answer() {
                return answer;
            }
        });
    }

    @Test
    void waitingShowsDue() {
        Map<String, Goal> goals = new LinkedHashMap<>();
        goals.put("GOLD", answering(Answer.NO));
        goals.put("FULL", answering(Answer.YES));
        goals.put("GUILD", answering(Answer.NO));
        Do selling = Do.press(BotConstants.input("SHOP", "sell")).serves(BotConstants.goal("GOLD"))
                .every(Duration.ofHours(1));
        Do joining = Do.press(BotConstants.input("HOME", "guild")).serves(BotConstants.goal("GUILD"));
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("HOME").start(), Map.of("shop", click("shop"), "guild", click("guild")),
                Map.of("joining", joining)));
        screens.put("SHOP", screen(Setup.named("SHOP").from(ref("HOME", "shop")), Map.of("sell", click("sell")),
                Map.of("selling", selling)));
        Plan plan = Plan.of(Plan.tier(BotConstants.goal("GOLD"), BotConstants.goal("FULL")),
                Plan.tier(BotConstants.goal("GUILD")));
        Path file = dir.resolve("done.json");
        new DoneRecords(file, Clock.fixed(NOON.minus(Duration.ofMinutes(10)), ZoneOffset.UTC))
                .done(DoneRecords.key("SHOP", "selling"), true);

        StudioPlan studio = StudioPlan.of(plan, goals, ScreenGraph.of(screens));
        DoneRecords records = new DoneRecords(file, Clock.fixed(NOON, ZoneOffset.UTC));
        GoalsViewModel goalsView = GoalsViewModel.of(new PlanModel(studio.plan(), studio.map(), records, "HOME"),
                studio);
        PlanViewModel view = PlanViewModel.of(goalsView, ZoneOffset.UTC);

        assertEquals(2, view.tiers().size());
        assertEquals(List.of("GOLD", "FULL"), view.tiers().getFirst().chips().stream()
                .map(PlanViewModel.Chip::name).toList());
        PlanViewModel.Chip gold = view.chip("GOLD").orElseThrow();
        assertEquals(State.WAITING, gold.state());
        assertEquals(Optional.of(NOON.plus(Duration.ofMinutes(50))), gold.due());
        assertEquals("⏳ 12:50", gold.said());
        assertEquals(State.MET, view.chip("FULL").orElseThrow().state());
        assertEquals(State.OPEN, view.chip("GUILD").orElseThrow().state(), "its action is ready");
    }

    private static MapScreen screen(Setup setup, Map<String, Input> inputs, Map<String, Do> actions) {
        return new MapScreen(setup, inputs, Map.of(), Map.of(), actions);
    }
}
