package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Kind;
import com.botmaker.sdk.api.screens.Reached;
import com.botmaker.sdk.api.screens.ScreenList;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Variant;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A screen taken apart and put back together: {@code build(components(v))} equals {@code v}, as {@link FlowTypesTest}
 * asserts for a flow, over a screen with every link and the three acts.
 */
class ScreenTypesTest {

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    /** A screen's markers: the pictures {@code ids}. */
    private static List<ImageTemplate> marks(String... ids) {
        return Arrays.stream(ids).map(ScreenTypesTest::picture).toList();
    }

    private static <T> T roundTrip(ComponentType<T> shape, T value) {
        return shape.build(shape.components(value));
    }

    private static GameScreen campaign() {
        return GameScreen.named("Campaign").markers(marks("campaign_title"))
                .from(Way.of(BotConstants.screen("HOME"), Input.click(picture("campaign"))))
                .alsoFrom(List.of(Way.of(BotConstants.screen("MAP"), Input.key(Key.ESCAPE))))
                .back(Input.click(picture("close_x")))
                .inputs(List.of(
                        Input.click(picture("claim")).did(Did.IN_PLACE).kind(Kind.CHORE),
                        Input.scroll(picture("list"), -3).did(Did.NONE),
                        Input.click(picture("gems")).never("spends gems"),
                        Input.click(picture("loot")).sometimes(),
                        Input.click(picture("guild")).locked(picture("padlock"))));
    }

    @Test
    void aScreenComesApartAndGoesBackTogetherWhole() {
        assertEquals(campaign(), roundTrip(ScreenTypes.SCREEN_SHAPE, campaign()));
        GameScreen offer = GameScreen.named("Offer").markers(marks("offer")).popup();
        assertEquals(offer, roundTrip(ScreenTypes.SCREEN_SHAPE, offer));
        GameScreen noted = campaign().notes("Loot fills over 8 h.\nIts badge shows on Home.")
                .reached(Reached.SURVEYED).owes(List.of("marker — every icon here animates"));
        assertEquals(noted, roundTrip(ScreenTypes.SCREEN_SHAPE, noted), "its notes, stage and debts go with it");
        GameScreen moved = campaign().at(new Point(420, -36));
        assertEquals(moved, roundTrip(ScreenTypes.SCREEN_SHAPE, moved), "where its card was moved to goes with it");
        assertNull(roundTrip(ScreenTypes.SCREEN_SHAPE, campaign()).at(), "a card never moved has no corner");
        GameScreen told = campaign().markers(marks("window_title", "campaign_tab")).absent(marks("gems"));
        assertEquals(told, roundTrip(ScreenTypes.SCREEN_SHAPE, told), "a set of markers and an absent picture go with it");
    }

    @Test
    void eachInputIsWrittenByItsOwnActsFactory() {
        Input click = Input.click(picture("claim")).did(Did.IN_PLACE).sometimes();
        Input key = Input.key(Key.ESCAPE).kind(Kind.SHORTCUT);
        Input scroll = Input.scroll(picture("list"), 4).locked(picture("padlock"));

        assertEquals(click, roundTrip(ScreenTypes.CLICK_SHAPE, click));
        assertEquals(key, roundTrip(ScreenTypes.KEY_SHAPE, key));
        assertEquals(scroll, roundTrip(ScreenTypes.SCROLL_SHAPE, scroll));
        assertTrue(ScreenTypes.KEY_SHAPE.writes(key));
        assertFalse(ScreenTypes.CLICK_SHAPE.writes(key));
        assertFalse(ScreenTypes.SCROLL_SHAPE.writes(click));
    }

    @Test
    void aListIsWrittenByItsMembersAndGoesWithItsScreen() {
        ScreenList column = ScreenList.of(-5, List.of(picture("tavern_home"), picture("campaign_home")));
        assertEquals(column, roundTrip(ScreenTypes.LIST_SHAPE, column));
        GameScreen home = GameScreen.named("Home").markers(List.of(picture("town")))
                .inputs(List.of(Input.click(picture("tavern_home")), Input.click(picture("campaign_home"))))
                .lists(List.of(column));
        assertEquals(home, roundTrip(ScreenTypes.SCREEN_SHAPE, home), "its lists go with it");
        assertEquals(Optional.of(column), home.listOf(Input.click(picture("campaign_home"))));
        assertEquals(Optional.empty(), home.listOf(Input.click(picture("town"))));
    }

    @Test
    void aReferenceToAScreenIsWrittenOnlyAsItsConstant() {
        assertFalse(ScreenTypes.SCREEN_SHAPE.writes(BotConstants.screen("HOME")));
        assertTrue(ScreenTypes.SCREEN_SHAPE.writes(campaign()));
        assertEquals("HOME", BotConstants.screenName(BotConstants.screen("HOME")));
        Way way = Way.of(BotConstants.screen("HOME"), Input.click(picture("campaign")));
        assertEquals(way, roundTrip(ScreenTypes.WAY_SHAPE, way));
    }

    @Test
    void looksAndConditionsGoBackTogetherAsTheMethodsTheyName() {
        Input shop = Input.click(picture("shop")).variants(List.of(Variant.of(picture("shop_badge"), State.BADGE)))
                .when(ScreenTypes.condition("Firestone::shopOpen"));
        assertEquals(shop, roundTrip(ScreenTypes.CLICK_SHAPE, shop));
        assertEquals("Firestone::shopOpen", ScreenTypes.sourceOf(roundTrip(ScreenTypes.CLICK_SHAPE, shop).when()));

        Way way = Way.of(BotConstants.screen("HOME"), Input.click(picture("event"))).whenLooks(State.BADGE)
                .when(ScreenTypes.condition("Firestone::eventOn"));
        assertEquals(way, roundTrip(ScreenTypes.WAY_SHAPE, way));

        GameScreen event = GameScreen.named("Event").markers(marks("event_title"))
                .variants(List.of(ScreenVariant.named("Halloween").markers(marks("pumpkin"))
                        .inputs(List.of(Input.click(picture("candy")).sometimes()))
                        .when(ScreenTypes.condition("Firestone::halloween"))));
        assertEquals(event, roundTrip(ScreenTypes.SCREEN_SHAPE, event));
        assertEquals("", ScreenTypes.sourceOf(() -> true), "a live method has nothing to be written as");
        assertNull(ScreenTypes.condition(" "));
    }

    @Test
    void theMapsShapesAreHandedToTheHost() {
        assertTrue(SdkTypes.PARTS.containsAll(ScreenTypes.ALL));
    }
}
