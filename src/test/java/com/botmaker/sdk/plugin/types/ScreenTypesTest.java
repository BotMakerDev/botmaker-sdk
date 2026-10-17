package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Reached;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A screen's parts taken apart and put back together: {@code build(components(v))} equals {@code v}, as
 * {@link FlowTypesTest} asserts for a flow, over a setup with every link and each kind of input.
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

    private static Setup campaign() {
        return Setup.named("Campaign").markers(marks("campaign_title"))
                .from(BotConstants.input("HOME", "campaign"))
                .alsoFrom(List.of(Way.of(BotConstants.input("MAP", "keyEscape"))))
                .back(BotConstants.input("CAMPAIGN", "close"));
    }

    @Test
    void aSetupComesApartAndGoesBackTogetherWhole() {
        assertEquals(campaign(), roundTrip(ScreenTypes.SETUP_SHAPE, campaign()));
        Setup offer = Setup.named("Offer").markers(marks("offer")).popup();
        assertEquals(offer, roundTrip(ScreenTypes.SETUP_SHAPE, offer));
        Setup noted = campaign().notes("Loot fills over 8 h.\nIts badge shows on Home.")
                .reached(Reached.SURVEYED).owes(List.of("marker — every icon here animates"));
        assertEquals(noted, roundTrip(ScreenTypes.SETUP_SHAPE, noted), "its notes, stage and debts go with it");
        Setup moved = campaign().at(new Point(420, -36));
        assertEquals(moved, roundTrip(ScreenTypes.SETUP_SHAPE, moved), "where its card was moved to goes with it");
        assertNull(roundTrip(ScreenTypes.SETUP_SHAPE, campaign()).at(), "a card never moved has no corner");
        Setup told = campaign().markers(marks("window_title", "campaign_tab")).absent(marks("gems"));
        assertEquals(told, roundTrip(ScreenTypes.SETUP_SHAPE, told), "a set of markers and an absent picture go with it");
    }

    /** The tree's way in is its input, then the copy, the look and the method it needs, each written after it. */
    @Test
    void theTreesWayInCarriesItsCopyItsLookAndItsMethod() {
        Setup copy = campaign().fromPick(Pick.nth(2, Order.NORTH)).fromLooks(State.BADGE)
                .fromWhen(ScreenTypes.condition("Firestone::eventOn"));
        assertEquals(copy, roundTrip(ScreenTypes.SETUP_SHAPE, copy));
        assertEquals(Pick.nth(2, Order.NORTH), copy.from().pick());
        assertEquals("Firestone::eventOn", ScreenTypes.sourceOf(copy.from().when()));
        assertEquals(Setup.named("Home"), Setup.named("Home").fromPick(Pick.first()), "no way in, nothing to pick");
    }

    @Test
    void eachInputIsWrittenByItsOwnFactory() {
        Button click = Button.of(picture("claim")).tried().sometimes().info();
        Hotkey key = Hotkey.of(Key.ESCAPE).leaves();
        Strip strip = Strip.of(Scroll.wheel(4), marks("list")).locked(picture("padlock")).tried();
        Button never = Button.of(picture("gems")).never("spends gems").repeated();

        assertEquals(click, roundTrip(ScreenTypes.BUTTON_SHAPE, click));
        assertEquals(key, roundTrip(ScreenTypes.HOTKEY_SHAPE, key));
        assertEquals(strip, roundTrip(ScreenTypes.STRIP_SHAPE, strip));
        assertEquals(never, roundTrip(ScreenTypes.BUTTON_SHAPE, never));
        assertTrue(never.repeats(), "a repeated button says so");
    }

    @Test
    void aStripIsWrittenByItsScrollAndItsMembers() {
        Strip column = Strip.of(Scroll.wheel(-5), marks("tavern_home", "campaign_home")).sometimes();
        assertEquals(column, roundTrip(ScreenTypes.STRIP_SHAPE, column));
        assertEquals(Scroll.wheel(-5), roundTrip(ScreenTypes.SCROLL_SHAPE, Scroll.wheel(-5)));
        assertTrue(column.has(picture("campaign_home")));
        assertFalse(column.has(picture("town")));
    }

    /** A wheel or a drag, each by its own factory, with what Discovery measured as links. */
    @Test
    void aScrollIsAWheelOrADragWithItsMeasures() {
        Scroll wheel = Scroll.wheel(-5).step(96).settle(Duration.ofMillis(900)).most(12);
        assertEquals(wheel, roundTrip(ScreenTypes.SCROLL_SHAPE, wheel));
        Scroll drag = Scroll.drag(Order.WEST, 300, Duration.ofMillis(400)).step(280);
        assertEquals(drag, roundTrip(ScreenTypes.SCROLL_DRAG_SHAPE, drag));
        assertTrue(ScreenTypes.SCROLL_SHAPE.writes(wheel));
        assertFalse(ScreenTypes.SCROLL_SHAPE.writes(drag));
        assertTrue(ScreenTypes.SCROLL_DRAG_SHAPE.writes(drag));
        assertEquals(Scroll.drag(Order.EAST, 300, Duration.ofMillis(400)).step(280), drag.back());
        assertEquals(Scroll.SETTLE, Scroll.wheel(-1).settle());
    }

    @Test
    void eachPickIsWrittenByItsOwnFactory() {
        Pick nth = Pick.nth(3, Order.SOUTH_WEST);
        assertEquals(nth, roundTrip(ScreenTypes.PICK_NTH_SHAPE, nth));
        assertEquals(Pick.each(Order.EAST), roundTrip(ScreenTypes.PICK_EACH_SHAPE, Pick.each(Order.EAST)));
        assertEquals(Pick.first(Order.CENTER), roundTrip(ScreenTypes.PICK_FIRST_SHAPE, Pick.first(Order.CENTER)));
        assertEquals(Pick.untilNone(), roundTrip(ScreenTypes.PICK_UNTIL_NONE_SHAPE, Pick.untilNone()));
        assertTrue(ScreenTypes.PICK_NTH_SHAPE.writes(nth));
        assertFalse(ScreenTypes.PICK_EACH_SHAPE.writes(nth));
        Way way = Way.of(BotConstants.input("MISSIONS", "go")).pick(nth);
        assertEquals(way, roundTrip(ScreenTypes.WAY_SHAPE, way));
    }

    @Test
    void aReferenceToAFieldIsWrittenOnlyAsThatField() {
        Button reference = BotConstants.input("HOME", "campaign");
        assertFalse(ScreenTypes.BUTTON_SHAPE.writes(reference));
        assertTrue(ScreenTypes.BUTTON_SHAPE.writes(Button.of(picture("campaign"))));
        assertEquals(new BotConstants.InputName("HOME", "campaign"), BotConstants.inputName(reference));
        assertNull(BotConstants.inputName(Button.of(picture("campaign"))));
        assertNull(BotConstants.inputName(reference.sometimes()), "a reference with traits is a button of its own");
        assertEquals("HOME", BotConstants.screenName(BotConstants.screen("HOME")));
        assertEquals(BotConstants.screen("HOME"), BotConstants.screen("HOME"), "a reference equals one to the same");
    }

    @Test
    void looksAndConditionsGoBackTogetherAsTheMethodsTheyName() {
        Button shop = Button.of(picture("shop")).looks(List.of(Look.of(picture("shop_badge"), State.BADGE)))
                .when(ScreenTypes.condition("Firestone::shopOpen"));
        assertEquals(shop, roundTrip(ScreenTypes.BUTTON_SHAPE, shop));
        assertEquals("Firestone::shopOpen", ScreenTypes.sourceOf(roundTrip(ScreenTypes.BUTTON_SHAPE, shop).when()));

        Way way = Way.of(BotConstants.input("HOME", "event")).whenLooks(State.BADGE)
                .when(ScreenTypes.condition("Firestone::eventOn"));
        assertEquals(way, roundTrip(ScreenTypes.WAY_SHAPE, way));

        Setup event = Setup.named("Event").markers(marks("event_title"))
                .variants(List.of(ScreenVariant.named("Halloween").markers(marks("pumpkin"))
                        .inputs(List.of(BotConstants.input("EVENT", "candy")))
                        .when(ScreenTypes.condition("Firestone::halloween"))));
        assertEquals(event, roundTrip(ScreenTypes.SETUP_SHAPE, event));
        assertEquals("", ScreenTypes.sourceOf(() -> true), "a live method has nothing to be written as");
        assertNull(ScreenTypes.condition(" "));
    }

    @Test
    void theMapsShapesAreHandedToTheHost() {
        assertTrue(SdkTypes.PARTS.containsAll(ScreenTypes.ALL));
    }
}
