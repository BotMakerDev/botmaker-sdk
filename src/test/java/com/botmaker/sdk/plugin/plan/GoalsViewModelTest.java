package com.botmaker.sdk.plugin.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.plan.Reset;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.bot.BotConstants;
import com.botmaker.sdk.internal.plan.DoneRecords;
import com.botmaker.sdk.internal.plan.PlanModel;
import com.botmaker.sdk.internal.plan.Round;
import com.botmaker.sdk.internal.plan.StudioPlan;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The Goals view's cards, rows and routes, from a plan and a map as Studio reads them: references, no run. */
class GoalsViewModelTest {

    private static final Instant NOON = Instant.parse("2026-11-06T12:00:00Z");

    @TempDir
    Path dir;

    /** A goal nothing meets yet. */
    private static Goal unmet() {
        return Goal.of(new Condition() {
            @Override
            public Answer answer() {
                return Answer.NO;
            }
        });
    }

    /** As Studio reads {@code Do.press(Screens.<screen>.<button>)}. */
    private static Do press(String screen, String button) {
        return Do.press(BotConstants.input(screen, button));
    }

    /**
     * As Studio reads {@code () -> Screens.<screen>.<field>}: the grammar's supplier gives the field's reference, a
     * {@code Button}, wherever a {@code Do} is asked for.
     */
    @SuppressWarnings("unchecked")
    private static Supplier<Do> named(String screen, String field) {
        Supplier<?> reference = () -> BotConstants.input(screen, field);
        return (Supplier<Do>) reference;
    }

    /**
     * HOME opens SHOP and ARENA, SHOP opens VAULT, VAULT opens CRYPT; each screen holds its buttons and the actions
     * {@code actions} puts there, as {@code Screens.java} declares them.
     */
    private static ScreenGraph map(Map<String, Map<String, Do>> actions) {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("HOME").start(), Map.of("shop", click("shop"), "arena", click("arena"),
                "chest", click("chest")), actions.get("HOME")));
        screens.put("SHOP", screen(Setup.named("SHOP").from(ref("HOME", "shop")), Map.of("vault", click("vault"),
                "sell", click("sell")), actions.get("SHOP")));
        screens.put("ARENA", screen(Setup.named("ARENA").from(ref("HOME", "arena")), Map.of("go", click("go")),
                actions.get("ARENA")));
        screens.put("VAULT", screen(Setup.named("VAULT").from(ref("SHOP", "vault")), Map.of("crypt", click("crypt")),
                actions.get("VAULT")));
        screens.put("CRYPT", screen(Setup.named("CRYPT").from(ref("VAULT", "crypt")), Map.of("dig", click("dig")),
                actions.get("CRYPT")));
        return ScreenGraph.of(screens);
    }

    private static MapScreen screen(Setup setup, Map<String, Input> inputs, Map<String, Do> actions) {
        return new MapScreen(setup, inputs, Map.of(), Map.of(), actions == null ? Map.of() : actions);
    }

    private GoalsViewModel view(Plan plan, Map<String, Goal> goals, Map<String, Map<String, Do>> actions) {
        StudioPlan studio = StudioPlan.of(plan, goals, map(actions));
        DoneRecords records = new DoneRecords(dir.resolve("done.json"), Clock.fixed(NOON, ZoneOffset.UTC));
        return GoalsViewModel.of(new PlanModel(studio.plan(), studio.map(), records, "HOME"), studio);
    }

    @Test
    void rowsFollowThePlanner() {
        Goal gold = unmet();
        Goal guild = unmet();
        Map<String, Goal> goals = new LinkedHashMap<>();
        goals.put("GOLD", gold);
        goals.put("GUILD", guild);
        // SELL is nearest, FIGHT is put first, CHEST is tried before SELL, LOOT is the last resort.
        Map<String, Map<String, Do>> actions = Map.of(
                "SHOP", Map.of("selling", press("SHOP", "sell").serves(BotConstants.goal("GOLD"))),
                "ARENA", Map.of("fighting", press("ARENA", "go").serves(BotConstants.goal("GOLD")).first()),
                "HOME", Map.of("opening", press("HOME", "chest").serves(BotConstants.goal("GOLD"))
                        .over(named("SHOP", "selling"))),
                "CRYPT", Map.of("digging", press("CRYPT", "dig").serves(BotConstants.goal("GOLD"))
                        .serves(BotConstants.goal("GUILD")).last()));
        Plan plan = Plan.of(Plan.tier(BotConstants.goal("GOLD")), Plan.tier(BotConstants.goal("GUILD")));

        GoalsViewModel view = view(plan, goals, actions);

        List<GoalsViewModel.Card> cards = view.cards();
        assertEquals(List.of("GOLD", "GUILD"), cards.stream().map(GoalsViewModel.Card::name).toList());
        assertSame(gold, cards.getFirst().goal(), "a reference is read as the bot's goal");
        List<String> expected = view.round().considered().stream().filter(c -> c.goal() == gold)
                .map(c -> c.step().key()).toList();
        assertEquals(expected, cards.getFirst().rows().stream().map(GoalsViewModel.Row::name).toList(),
                "a card's rows are in the planner's order");
        assertEquals(List.of("ARENA.fighting", "HOME.opening", "SHOP.selling", "CRYPT.digging"), expected,
                "first, then nearest with .over kept, then the last resort");
        GoalsViewModel.Row fight = cards.getFirst().rows().getFirst();
        assertEquals(GoalsViewModel.Mark.FIRST, fight.mark());
        assertEquals("ready", fight.said());
        assertEquals(2, fight.crossing(), "HOME, then ARENA");
        assertEquals(GoalsViewModel.Mark.LAST, cards.getFirst().rows().getLast().mark());
        assertEquals(List.of("CRYPT.digging"), cards.get(1).rows().stream().map(GoalsViewModel.Row::name).toList());
        assertEquals(List.of("ARENA.fighting"), view.round().route().stream().map(Round.Step::key).toList());
    }

    /** A daily's card says its reset and the zone it is read in: its own, else the game's the view's clock is in. */
    @Test
    void aDailyGoalSaysItsResetAndZone() {
        Map<String, Map<String, Do>> actions = Map.of("HOME", Map.of("opening", press("HOME", "chest")));
        GoalsViewModel view = view(Plan.of(Plan.tier(BotConstants.goal("GOLD"))), Map.of("GOLD", unmet()), actions);
        Reset five = Reset.daily(LocalTime.of(5, 0));

        assertEquals("an action done since 05:00 (Europe/Paris)",
                view.fill(Goal.done(() -> null).since(five.in(ZoneId.of("Europe/Paris")))));
        assertEquals("an action done since 05:00 (Z)", view.fill(Goal.done(() -> null).since(five)),
                "no zone of its own: the game's, here the view's UTC clock");
    }

    @Test
    void routeIsOnlyCrossedScreens() {
        Map<String, Goal> goals = Map.of("DEEP", unmet());
        Map<String, Map<String, Do>> actions = Map.of(
                "CRYPT", Map.of("digging", press("CRYPT", "dig").serves(BotConstants.goal("DEEP"))));

        GoalsViewModel view = view(Plan.of(Plan.tier(BotConstants.goal("DEEP"))), goals, actions);

        GoalsViewModel.Row dig = view.cards().getFirst().rows().getFirst();
        GoalsViewModel.Route route = view.route(dig).orElseThrow();
        assertEquals(List.of("HOME", "SHOP", "VAULT", "CRYPT"), route.screens(), "ARENA is not crossed");
        assertEquals(3, route.edges().size());
        assertEquals("digging", route.action());
        assertEquals(4, dig.crossing());
        assertEquals(List.of("CRYPT"), view.round().crossed().subList(3, 4), "the round crosses the same");
    }
}
