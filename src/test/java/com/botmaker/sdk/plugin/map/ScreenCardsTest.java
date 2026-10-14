package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Kind;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Link;
import com.botmaker.sdk.plugin.map.ScreenCards.RowState;
import com.botmaker.sdk.plugin.map.ScreenCards.State;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What 🗺 Screens draws of a map: each card's state, progress and badges, each edge, each input's row. */
class ScreenCardsTest {

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    private static Input click(String id) {
        return Input.click(picture(id));
    }

    /**
     * Home opens Campaign (finished, a chore and a never) and Engineer (owing its marker); Campaign's map opens Map,
     * which Home also reaches by a hotkey; Arena is behind a locked input; Reward is a popup.
     */
    private static ScreenGraph map() {
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", GameScreen.named("Home").marker(picture("home"))
                .inputs(List.of(click("campaign"), click("engineer"), click("gold"), Input.key(Key.M),
                        click("arena").locked(picture("padlock")), click("event").sometimes())));
        screens.put("CAMPAIGN", GameScreen.named("Campaign").marker(picture("campaign_title"))
                .from(Way.of(BotConstants.screen("HOME"), click("campaign")))
                .inputs(List.of(click("claim").did(Did.IN_PLACE).kind(Kind.CHORE), click("map"),
                        click("gems").never("spends gems"))));
        screens.put("MAP", GameScreen.named("Map").marker(picture("map_title"))
                .from(Way.of(BotConstants.screen("CAMPAIGN"), click("map")))
                .alsoFrom(List.of(Way.of(BotConstants.screen("HOME"), Input.key(Key.M))))
                .inputs(List.of(Input.key(Key.ESCAPE).did(Did.NONE))));
        screens.put("ENGINEER", GameScreen.named("Engineer").from(Way.of(BotConstants.screen("HOME"), click("engineer"))));
        screens.put("ARENA", GameScreen.named("Arena").marker(picture("arena_title"))
                .from(Way.of(BotConstants.screen("HOME"), click("arena").locked(picture("padlock")))));
        screens.put("REWARD", GameScreen.named("Reward").marker(picture("reward")).back(click("close_x")).popup());
        return ScreenGraph.of(screens);
    }

    private static Card card(String constant) {
        return ScreenCards.cards(map(), Map.of("CAMPAIGN", "surveyed")).stream()
                .filter(c -> c.constant().equals(constant)).findFirst().orElseThrow();
    }

    @Test
    void aCardSaysHowFarDiscoveryGotByColourAndByWord() {
        Card home = card("HOME");
        assertEquals(State.UNTRIED, home.state());
        assertEquals("1 untried", home.said(), "gold only: a key that opened a screen is tried, a lock settled, an "
                + "input that comes and goes waits");
        assertEquals(4, home.tried());
        assertEquals(6, home.inputs());
        assertEquals("", home.shape(), "no shape before it is finished");

        Card campaign = card("CAMPAIGN");
        assertEquals(State.FINISHED, campaign.state());
        assertEquals("activity", campaign.shape(), "its claim opened nothing");
        assertEquals(1, campaign.chores());
        assertEquals("surveyed", campaign.stage());
        assertEquals("campaign_title", campaign.marker());

        assertEquals(State.OWES, card("ENGINEER").state());
        assertEquals("owes its marker", card("ENGINEER").said());
        assertEquals(State.FINISHED, card("REWARD").state(), "a popup needs no input");
        assertTrue(card("REWARD").popup());
    }

    @Test
    void aScreenBehindALockOrAnInputThatComesAndGoesIsDrawnDashed() {
        assertTrue(card("ARENA").conditional());
        assertEquals(ScreenGraph.Presence.LOCKED, card("ARENA").presence());
        assertFalse(card("MAP").conditional(), "one way in that is always there is enough");
        assertFalse(card("REWARD").conditional());
    }

    @Test
    void eachEntranceIsSolidAndOneOnlyAnotherWayInAndAWayBackAreDashed() {
        List<Link> links = ScreenCards.links(map());
        List<Link> entrances = links.stream().filter(Link::entrance).toList();
        assertEquals(List.of("HOME>CAMPAIGN", "CAMPAIGN>MAP", "HOME>ENGINEER", "HOME>ARENA"),
                entrances.stream().map(l -> l.from() + ">" + l.to()).toList());
        assertTrue(links.stream().anyMatch(l -> !l.entrance() && !l.back() && l.from().equals("HOME")
                && l.to().equals("MAP")), "the hotkey is another way in");
        assertTrue(links.stream().anyMatch(l -> l.back() && l.from().equals("MAP") && l.to().equals("CAMPAIGN")));
        assertTrue(links.stream().noneMatch(l -> l.from().equals("REWARD") || l.to().equals("REWARD")),
                "a popup is in no tree");
    }

    @Test
    void theTreeStartsAtTheFirstScreenAndAPathReadsFromIt() {
        assertEquals(Optional.of("HOME"), ScreenCards.root(map()));
        assertEquals(List.of("Home", "Campaign", "Map"), ScreenCards.path(map(), "MAP"));
        assertEquals(List.of("Reward"), ScreenCards.path(map(), "REWARD"));
        assertEquals(Optional.empty(), ScreenCards.root(ScreenGraph.of(Map.of())));
    }

    @Test
    void eachInputsRowSaysWhatItDid() {
        List<ScreenCards.Row> home = ScreenCards.rows(map(), "HOME");
        assertEquals(List.of(RowState.TRIED, RowState.TRIED, RowState.UNTRIED, RowState.TRIED, RowState.LOCKED,
                RowState.UNSEEN), home.stream().map(ScreenCards.Row::state).toList());
        assertEquals(List.of("→ Campaign", "→ Engineer", "untried", "→ Map", "locked", "not seen yet"),
                home.stream().map(ScreenCards.Row::result).toList());
        assertEquals("comes and goes", home.getLast().kind());
        assertEquals("event", home.getLast().picture());

        List<ScreenCards.Row> campaign = ScreenCards.rows(map(), "CAMPAIGN");
        assertEquals("in place", campaign.getFirst().result());
        assertEquals("chore", campaign.getFirst().kind());
        assertEquals(RowState.NEVER, campaign.getLast().state());
        assertEquals("never: spends gems", campaign.getLast().result());
        assertEquals(List.of(), ScreenCards.rows(map(), "NOWHERE"));
    }

    @Test
    void anUnchangedMapReadsAsEqualCardsSoNothingIsRedrawn() {
        assertEquals(ScreenCards.cards(map(), Map.of()), ScreenCards.cards(map(), Map.of()));
        assertEquals(ScreenCards.links(map()), ScreenCards.links(map()));
    }
}
