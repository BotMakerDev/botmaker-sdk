package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Reached;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.plugin.SdkPlugin;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.text.Words;
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

    /** The start screen and its load time, and a way on its own naming the screen it follows, go back together. */
    @Test
    void aStartScreenAndAWayOnItsOwnComeApartAndGoBackTogether() {
        Setup loading = Setup.named("Loading").markers(marks("loading")).start().loads(Duration.ofSeconds(90));
        assertEquals(loading, roundTrip(ScreenTypes.SETUP_SHAPE, loading));
        Setup home = Setup.named("Home").after(BotConstants.screen("LOADING"))
                .alsoFrom(List.of(Way.onItsOwn(BotConstants.screen("SPLASH"))));
        assertEquals(home, roundTrip(ScreenTypes.SETUP_SHAPE, home));
        Way own = Way.onItsOwn(BotConstants.screen("LOADING")).when(ScreenTypes.condition("Firestone::eventOn"));
        assertEquals(own, roundTrip(ScreenTypes.WAY_ON_ITS_OWN_SHAPE, own));
        assertFalse(ScreenTypes.WAY_SHAPE.writes(own));
        assertTrue(ScreenTypes.WAY_ON_ITS_OWN_SHAPE.writes(own));
    }

    /** A panel names the screen it is in, before its way in through that screen's input. */
    @Test
    void aPanelNamesTheScreenItIsIn() {
        Setup upgrade = Setup.named("Upgrade").panelOf(BotConstants.screen("HOME"))
                .from(BotConstants.input("HOME", "upgrade"));
        assertEquals(upgrade, roundTrip(ScreenTypes.SETUP_SHAPE, upgrade));
        assertFalse(upgrade.popup().isPanel(), "a popup is in no screen");
        assertFalse(upgrade.panelOf(BotConstants.screen("HOME")).start().isPanel());
        assertTrue(Setup.named("Offer").popup().panelOf(null).isPopup(), "taking no panel off keeps the popup");
    }

    @Test
    void eachInputIsWrittenByItsOwnFactory() {
        Button click = Button.of(picture("claim")).tried().sometimes().info();
        Hotkey key = Hotkey.of(Key.ESCAPE).leaves();
        Strip strip = Strip.of(Scroll.wheel(4), marks("list")).locked(picture("padlock")).tried();
        Button never = Button.of(picture("gems")).never("spends gems").repeated();

        assertEquals(click, roundTrip(ScreenTypes.BUTTON_SHAPE, click));
        assertEquals(key, roundTrip(ScreenTypes.HOTKEY_KEY_SHAPE, key));
        assertEquals(strip, roundTrip(ScreenTypes.STRIP_SHAPE, strip));
        assertEquals(never, roundTrip(ScreenTypes.BUTTON_SHAPE, never));
        assertTrue(never.repeats(), "a repeated button says so");
    }

    /**
     * A map written before hotkeys took keys together, {@code Hotkey.of(Key.Q)}, still reads as one key, and keys
     * together or held are written by {@code Hotkey.of(Combo)}.
     */
    @Test
    void anOldHotkeyOfOneKeyStillReads() {
        Hotkey q = Hotkey.of(Key.Q).tried();
        assertTrue(ScreenTypes.HOTKEY_KEY_SHAPE.writes(q));
        assertFalse(ScreenTypes.HOTKEY_COMBO_SHAPE.writes(q));
        assertEquals(q, roundTrip(ScreenTypes.HOTKEY_KEY_SHAPE, q));
        assertEquals(Hotkey.of(Combo.of(Key.Q)), Hotkey.of(Key.Q), "one key is a combo of it");

        Hotkey save = Hotkey.of(Combo.of(Key.CTRL, Key.S)).info();
        Hotkey held = Hotkey.of(Combo.of(Key.W).held(Duration.ofMillis(500)));
        assertFalse(ScreenTypes.HOTKEY_KEY_SHAPE.writes(save));
        assertFalse(ScreenTypes.HOTKEY_KEY_SHAPE.writes(held), "a held key is no tap");
        assertEquals(save, roundTrip(ScreenTypes.HOTKEY_COMBO_SHAPE, save));
        assertEquals(held, roundTrip(ScreenTypes.HOTKEY_COMBO_SHAPE, held));
    }

    /** A click, every button's gesture until said, writes no {@code .gesture(…)}; a hold writes its time alone. */
    @Test
    void aButtonWithNoGestureWritesNone() {
        Button plain = Button.of(picture("claim"));
        assertEquals(Gesture.CLICK, plain.gesture());
        // Its gesture and hold links, before the text field's four.
        List<Object> parts = ScreenTypes.BUTTON_SHAPE.components(plain);
        assertNull(parts.get(parts.size() - 6));
        assertNull(parts.get(parts.size() - 5));

        Button twice = Button.of(picture("hero")).gesture(Gesture.DOUBLE).repeated();
        Button held = Button.of(picture("charge")).hold(Duration.ofSeconds(2));
        Button heldLong = Button.of(picture("charge")).gesture(Gesture.HOLD);
        assertEquals(twice, roundTrip(ScreenTypes.BUTTON_SHAPE, twice));
        assertEquals(held, roundTrip(ScreenTypes.BUTTON_SHAPE, held));
        assertEquals(heldLong, roundTrip(ScreenTypes.BUTTON_SHAPE, heldLong));
        List<Object> heldParts = ScreenTypes.BUTTON_SHAPE.components(held);
        assertNull(heldParts.get(heldParts.size() - 6), "a hold of its own time says it alone");
        assertEquals(Duration.ofSeconds(2), heldParts.get(heldParts.size() - 5));
        List<Object> twiceParts = ScreenTypes.BUTTON_SHAPE.components(twice);
        assertEquals(Gesture.DOUBLE, twiceParts.get(twiceParts.size() - 6));
        Button cycling = Button.cycling(List.of(picture("x1"), picture("x10"))).gesture(Gesture.RIGHT);
        assertEquals(cycling, roundTrip(ScreenTypes.BUTTON_CYCLING_SHAPE, cycling));
    }

    /**
     * A text field is a button and its typing links: a text of the bot's, or {@code .typed()} for the body's,
     * cleared and submitted when it says. A button of a map written before fields types nothing.
     */
    @Test
    void aTextFieldIsWrittenByItsTextAndItsTraits() {
        Button search = Button.of(picture("search")).types(Words.of("Dragons")).clears().submits();
        Button byBody = Button.of(picture("search")).typed().submits();
        assertEquals(search, roundTrip(ScreenTypes.BUTTON_SHAPE, search));
        assertEquals(byBody, roundTrip(ScreenTypes.BUTTON_SHAPE, byBody));
        assertEquals(Words.of("Dragons"), ScreenTypes.BUTTON_SHAPE.components(search)
                .get(ScreenTypes.BUTTON_SHAPE.components(search).size() - 4));
        assertTrue(byBody.typedByBody());
        assertFalse(search.typedByBody(), "a text of its own is walked through");
        Button old = Button.of(picture("claim")).tried();
        assertEquals(old, roundTrip(ScreenTypes.BUTTON_SHAPE, old));
        assertFalse(old.isField());
        Button cycling = Button.cycling(List.of(picture("x1"), picture("x10"))).types(Words.of("a"));
        assertEquals(cycling, roundTrip(ScreenTypes.BUTTON_CYCLING_SHAPE, cycling), "no link is lost to a cycle");
    }

    @Test
    void aStripIsWrittenByItsScrollAndItsMembers() {
        Strip column = Strip.of(Scroll.wheel(-5), marks("tavern_home", "campaign_home")).sometimes();
        assertEquals(column, roundTrip(ScreenTypes.STRIP_SHAPE, column));
        assertEquals(Scroll.wheel(-5), roundTrip(ScreenTypes.SCROLL_SHAPE, Scroll.wheel(-5)));
        assertTrue(column.has(picture("campaign_home")));
        assertFalse(column.has(picture("town")));
    }

    /** A tree is written by its nodes, each by its picture, its unlock lines and its offsets, and how it scrolls. */
    @Test
    void aTreeIsWrittenByItsNodesAndHowItScrolls() {
        Node health = Node.of(picture("health")).after(marks("damage"))
                .beside(List.of(Beside.of(picture("damage"), 1.2, -0.6)));
        Tree tree = Tree.of(List.of(Node.of(picture("damage")), health)).dragged().sometimes();
        assertEquals(tree, roundTrip(ScreenTypes.TREE_SHAPE, tree));
        assertEquals(health, roundTrip(ScreenTypes.NODE_SHAPE, health));
        assertEquals(Node.of(picture("damage")), roundTrip(ScreenTypes.NODE_SHAPE, Node.of(picture("damage"))));
        Tree wheel = Tree.of(List.of(Node.of(picture("damage")))).wheel(-3);
        assertEquals(wheel, roundTrip(ScreenTypes.TREE_SHAPE, wheel));
        assertEquals(Beside.of(picture("damage"), -2, 0.5),
                roundTrip(ScreenTypes.BESIDE_SHAPE, Beside.of(picture("damage"), -2, 0.5)));
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
        assertTrue(new SdkPlugin().componentTypes().containsAll(List.of(ScreenTypes.SETUP_SHAPE,
                ScreenTypes.WAY_SHAPE, ScreenTypes.BUTTON_SHAPE, ScreenTypes.TREE_SHAPE, ScreenTypes.PICK_UNTIL_SHAPE)));
    }
}
