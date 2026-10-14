package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Kind;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A screen taken apart and put back together: {@code build(components(v))} equals {@code v}, as {@link FlowTypesTest}
 * asserts for a flow, over a screen with every link and the three acts.
 */
class ScreenTypesTest {

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    private static <T> T roundTrip(ComponentType<T> shape, T value) {
        return shape.build(shape.components(value));
    }

    private static GameScreen campaign() {
        return GameScreen.named("Campaign").marker(picture("campaign_title"))
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
        GameScreen offer = GameScreen.named("Offer").marker(picture("offer")).popup();
        assertEquals(offer, roundTrip(ScreenTypes.SCREEN_SHAPE, offer));
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
    void aReferenceToAScreenIsWrittenOnlyAsItsConstant() {
        assertFalse(ScreenTypes.SCREEN_SHAPE.writes(BotConstants.screen("HOME")));
        assertTrue(ScreenTypes.SCREEN_SHAPE.writes(campaign()));
        assertEquals("HOME", BotConstants.screenName(BotConstants.screen("HOME")));
        Way way = Way.of(BotConstants.screen("HOME"), Input.click(picture("campaign")));
        assertEquals(way, roundTrip(ScreenTypes.WAY_SHAPE, way));
    }

    @Test
    void theMapsShapesAreHandedToTheHost() {
        assertTrue(SdkTypes.PARTS.containsAll(ScreenTypes.ALL));
    }
}
