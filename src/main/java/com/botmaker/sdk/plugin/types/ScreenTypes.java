package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.Held;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Reached;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;

import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * The shapes of the {@code SCREEN_MAP} value, {@code Screens.java}: a screen's setup, its ways in, and its inputs
 * (each a field of the screen's class), as the calls the host reads and writes. Like a flow's, none is picked on its
 * own ({@link FlowTypes}).
 *
 * <p>An input named inside a setup — the {@code HOME.market} of {@code .from(HOME.market)} — is a reference to that
 * field, held by its screen and field ({@code BotConstants.input}) and written back as the field, never spelled out.
 */
public final class ScreenTypes {

    private ScreenTypes() {}

    /**
     * {@code Setup.named(String)} and its links, each written only when the screen has it: {@code .panelOf(…)},
     * {@code .markers(List.of(…))}, {@code .absent(List.of(…))}, {@code .from(…)} (then {@code .fromPick(…)},
     * {@code .fromLooks(…)}, {@code .fromWhen(…)} when its way needs them) or {@code .after(…)} on its own,
     * {@code .alsoFrom(List.of(…))}, {@code .back(…)}, {@code .variants(List.of(…))}, {@code .popup()},
     * {@code .start()}, {@code .loads(…)}, … and {@code .at(new Point(…))} once its card was moved on 🗺 Screens' map.
     */
    public static final DeclaredCall<Setup> SETUP_SHAPE = ComponentType.part(Setup.class)
            .<String, Setup>writtenAs(Setup::named, Setup::name)
            .<Screen>with(Setup::panelOf, Setup::panelOf)
            .<List<ImageTemplate>>with(Setup::markers, Setup::markers)
            .<List<ImageTemplate>>with(Setup::absent, Setup::absent)
            .<Input>with(Setup::from, Setup::fromInput)
            .<Screen>with(Setup::after, Setup::fromAfter)
            .<Pick>with(Setup::fromPick, setup -> setup.from() == null ? null : setup.from().pick())
            .<State>with(Setup::fromLooks, setup -> setup.from() == null ? null : setup.from().state())
            .<BooleanSupplier>with(Setup::fromWhen, Parts.SETUP_FROM_WHEN.part(ScreenTypes::condition))
            .<List<Way>>with(Setup::alsoFrom, Setup::alsoFrom)
            .<Input>with(Setup::back, Setup::back)
            .<List<ScreenVariant>>with(Setup::variants, Setup::variants)
            .flag(Setup::popup, Setup::isPopup)
            .flag(Setup::start, Setup::isStart)
            .<Duration>with(Setup::loads, Setup::loads)
            .<String>with(Setup::notes, Setup::notes)
            .<Reached>with(Setup::reached, Setup::reached)
            .<List<String>>with(Setup::owes, Setup::owes)
            .<Point>with(Setup::at, Setup::at);

    /**
     * The conditions, each a method reference crossing as the text it is written as, {@code Firestone::eventOn}: a
     * name the editor holds and never asks (the user's call, 2026-10-15). An expression is a bot method of its own.
     */
    private static final class Parts {
        static final Held<Way, String> WAY_WHEN = Held.as(String.class, way -> sourceOf(way.when()));
        static final Held<Setup, String> SETUP_FROM_WHEN = Held.as(String.class,
                setup -> setup.from() == null ? "" : sourceOf(setup.from().when()));
        static final Held<Button, String> BUTTON_WHEN = Held.as(String.class, input -> sourceOf(input.when()));
        static final Held<Hotkey, String> HOTKEY_WHEN = Held.as(String.class, input -> sourceOf(input.when()));
        static final Held<Strip, String> STRIP_WHEN = Held.as(String.class, input -> sourceOf(input.when()));
        static final Held<Tree, String> TREE_WHEN = Held.as(String.class, input -> sourceOf(input.when()));
        static final Held<ScreenVariant, String> VARIANT_WHEN = Held.as(String.class, v -> sourceOf(v.when()));
    }

    /**
     * {@code Way.of(Input)}, {@code .pick(Pick)} through one copy of a repeated button, {@code .whenLooks(State)}
     * when it needs one look of the button, and {@code .when(…)} when a method says whether it leads here now.
     */
    public static final DeclaredCall<Way> WAY_SHAPE = ComponentType.part(Way.class)
            .<Input, Way>writtenAs(Way::of, Way::via)
            .<Pick>with(Way::pick, Way::pick)
            .<State>with(Way::whenLooks, Way::state)
            .<BooleanSupplier>with(Way::when, Parts.WAY_WHEN.part(ScreenTypes::condition))
            .when(way -> !way.isOnItsOwn());

    /** {@code Way.onItsOwn(Screen)}: the screen follows that one by itself, and {@code .when(…)} when it needs it. */
    public static final DeclaredCall<Way> WAY_ON_ITS_OWN_SHAPE = ComponentType.part(Way.class)
            .<Screen, Way>writtenAs(Way::onItsOwn, Way::after)
            .<BooleanSupplier>with(Way::when, Parts.WAY_WHEN.part(ScreenTypes::condition))
            .when(Way::isOnItsOwn);

    /**
     * {@code ScreenVariant.named(String)} and its links: {@code .markers(List.of(…))}, {@code .absent(List.of(…))},
     * {@code .inputs(List.of(…))}, {@code .when(…)}.
     */
    public static final DeclaredCall<ScreenVariant> SCREEN_VARIANT_SHAPE = ComponentType.part(ScreenVariant.class)
            .<String, ScreenVariant>writtenAs(ScreenVariant::named, ScreenVariant::name)
            .<List<ImageTemplate>>with(ScreenVariant::markers, ScreenVariant::markers)
            .<List<ImageTemplate>>with(ScreenVariant::absent, ScreenVariant::absent)
            .<List<Input>>with(ScreenVariant::inputs, ScreenVariant::inputs)
            .<BooleanSupplier>with(ScreenVariant::when, Parts.VARIANT_WHEN.part(ScreenTypes::condition));

    /** {@code Look.of(ImageTemplate, State)}: another look of a button. */
    public static final DeclaredCall<Look> LOOK_SHAPE = ComponentType.part(Look.class)
            .<ImageTemplate, State, Look>writtenAs(Look::of, Look::picture, Look::state);

    /**
     * {@code Button.of(ImageTemplate)} and its links, in the order they are written. A reference to a field is written
     * only as that field.
     */
    public static final DeclaredCall<Button> BUTTON_SHAPE = ComponentType.part(Button.class)
            .<ImageTemplate, Button>writtenAs(Button::of, Button::picture)
            .flag(Button::tried, Button::isTried)
            .flag(Button::leaves, Button::isLeaving)
            .flag(Button::info, Button::isInfo)
            .<String>with(Button::never, Button::never)
            .flag(Button::sometimes, Button::comesAndGoes)
            .<ImageTemplate>with(Button::locked, Button::lock)
            .<List<Look>>with(Button::looks, Button::looks)
            .<BooleanSupplier>with(Button::when, Parts.BUTTON_WHEN.part(ScreenTypes::condition))
            .flag(Button::repeated, Button::repeats)
            .<Gesture>with(Button::gesture, ScreenTypes::gestureWritten)
            .<Duration>with(Button::hold, Button::hold)
            .when(button -> BotConstants.inputName(button) == null && !button.cycles());

    /** {@code Button.cycling(List<ImageTemplate>)} and its links: a button showing its looks in turn. */
    public static final DeclaredCall<Button> BUTTON_CYCLING_SHAPE = ComponentType.part(Button.class)
            .<List<ImageTemplate>, Button>writtenAs(Button::cycling, Button::cycle)
            .flag(Button::tried, Button::isTried)
            .flag(Button::leaves, Button::isLeaving)
            .flag(Button::info, Button::isInfo)
            .<String>with(Button::never, Button::never)
            .flag(Button::sometimes, Button::comesAndGoes)
            .<ImageTemplate>with(Button::locked, Button::lock)
            .<BooleanSupplier>with(Button::when, Parts.BUTTON_WHEN.part(ScreenTypes::condition))
            .flag(Button::repeated, Button::repeats)
            .<Gesture>with(Button::gesture, ScreenTypes::gestureWritten)
            .<Duration>with(Button::hold, Button::hold)
            .when(button -> BotConstants.inputName(button) == null && button.cycles());

    /**
     * A button's gesture as written: none for a click, the default, nor for a hold of its own time, which
     * {@code .hold(…)} says alone.
     */
    private static Gesture gestureWritten(Button button) {
        return button.gesture() == Gesture.CLICK || button.hold() != null ? null : button.gesture();
    }

    /** {@code Hotkey.of(Key)} and its links: one key, tapped. */
    public static final DeclaredCall<Hotkey> HOTKEY_KEY_SHAPE = ComponentType.part(Hotkey.class)
            .<Key, Hotkey>writtenAs(Hotkey::of, Hotkey::key)
            .flag(Hotkey::tried, Hotkey::isTried)
            .flag(Hotkey::leaves, Hotkey::isLeaving)
            .flag(Hotkey::info, Hotkey::isInfo)
            .<String>with(Hotkey::never, Hotkey::never)
            .flag(Hotkey::sometimes, Hotkey::comesAndGoes)
            .<ImageTemplate>with(Hotkey::locked, Hotkey::lock)
            .<BooleanSupplier>with(Hotkey::when, Parts.HOTKEY_WHEN.part(ScreenTypes::condition))
            .when(hotkey -> hotkey.key() != null);

    /** {@code Hotkey.of(Combo)} and the same links: keys pressed together, or held. */
    public static final DeclaredCall<Hotkey> HOTKEY_COMBO_SHAPE = ComponentType.part(Hotkey.class)
            .<Combo, Hotkey>writtenAs(Hotkey::of, Hotkey::combo)
            .flag(Hotkey::tried, Hotkey::isTried)
            .flag(Hotkey::leaves, Hotkey::isLeaving)
            .flag(Hotkey::info, Hotkey::isInfo)
            .<String>with(Hotkey::never, Hotkey::never)
            .flag(Hotkey::sometimes, Hotkey::comesAndGoes)
            .<ImageTemplate>with(Hotkey::locked, Hotkey::lock)
            .<BooleanSupplier>with(Hotkey::when, Parts.HOTKEY_WHEN.part(ScreenTypes::condition))
            .when(hotkey -> hotkey.key() == null);

    /** {@code Strip.of(Scroll, List<ImageTemplate>)} and its links: its members' defaults. */
    public static final DeclaredCall<Strip> STRIP_SHAPE = ComponentType.part(Strip.class)
            .<Scroll, List<ImageTemplate>, Strip>writtenAs(Strip::of, Strip::scroll, Strip::members)
            .flag(Strip::tried, Strip::isTried)
            .flag(Strip::leaves, Strip::isLeaving)
            .flag(Strip::info, Strip::isInfo)
            .<String>with(Strip::never, Strip::never)
            .flag(Strip::sometimes, Strip::comesAndGoes)
            .<ImageTemplate>with(Strip::locked, Strip::lock)
            .<BooleanSupplier>with(Strip::when, Parts.STRIP_WHEN.part(ScreenTypes::condition));

    /** {@code Beside.of(ImageTemplate, double, double)}: a node's offset from a connected one, in that one's size. */
    public static final DeclaredCall<Beside> BESIDE_SHAPE = ComponentType.part(Beside.class)
            .<ImageTemplate, Double, Double, Beside>writtenAs(Beside::of, Beside::other, Beside::across, Beside::down);

    /** {@code Node.of(ImageTemplate)} and its links: the nodes that unlock it, and its offsets. */
    public static final DeclaredCall<Node> NODE_SHAPE = ComponentType.part(Node.class)
            .<ImageTemplate, Node>writtenAs(Node::of, Node::picture)
            .<List<ImageTemplate>>with(Node::after, Node::after)
            .<List<Beside>>with(Node::beside, Node::beside);

    /** {@code Tree.of(List<Node>)} and its links: how it scrolls, and its nodes' defaults. */
    public static final DeclaredCall<Tree> TREE_SHAPE = ComponentType.part(Tree.class)
            .<List<Node>, Tree>writtenAs(Tree::of, Tree::nodes)
            .flag(Tree::dragged, Tree::isDragged)
            .<Integer>with(Tree::wheel, Tree::notches)
            .flag(Tree::tried, Tree::isTried)
            .flag(Tree::leaves, Tree::isLeaving)
            .flag(Tree::info, Tree::isInfo)
            .<String>with(Tree::never, Tree::never)
            .flag(Tree::sometimes, Tree::comesAndGoes)
            .<ImageTemplate>with(Tree::locked, Tree::lock)
            .<BooleanSupplier>with(Tree::when, Parts.TREE_WHEN.part(ScreenTypes::condition));

    /** {@code Scroll.wheel(int)} and its links: how a strip scrolls, and what Discovery measured of it. */
    public static final DeclaredCall<Scroll> SCROLL_SHAPE = ComponentType.part(Scroll.class)
            .<Integer, Scroll>writtenAs(Scroll::wheel, Scroll::notches)
            .<Integer>with(Scroll::step, Scroll::step)
            .<Duration>with(Scroll::settle, Scroll::settle)
            .<Integer>with(Scroll::most, Scroll::most)
            .when(scroll -> scroll.drag() == null);

    /** {@code Scroll.drag(Order, int, Duration)} and the same links. */
    public static final DeclaredCall<Scroll> SCROLL_DRAG_SHAPE = ComponentType.part(Scroll.class)
            .<Order, Integer, Duration, Scroll>writtenAs(Scroll::drag, Scroll::drag, Scroll::px, Scroll::time)
            .<Integer>with(Scroll::step, Scroll::step)
            .<Duration>with(Scroll::settle, Scroll::settle)
            .<Integer>with(Scroll::most, Scroll::most)
            .when(scroll -> scroll.drag() != null);

    /** {@code Pick.first(Order)}. */
    public static final DeclaredCall<Pick> PICK_FIRST_SHAPE = ComponentType.part(Pick.class)
            .<Order, Pick>writtenAs(Pick::first, Pick::order)
            .when(pick -> pick.mode() == Pick.Mode.FIRST);

    /** {@code Pick.nth(int, Order)}. */
    public static final DeclaredCall<Pick> PICK_NTH_SHAPE = ComponentType.part(Pick.class)
            .<Integer, Order, Pick>writtenAs(Pick::nth, Pick::nth, Pick::order)
            .when(pick -> pick.mode() == Pick.Mode.NTH);

    /** {@code Pick.each(Order)}. */
    public static final DeclaredCall<Pick> PICK_EACH_SHAPE = ComponentType.part(Pick.class)
            .<Order, Pick>writtenAs(Pick::each, Pick::order)
            .when(pick -> pick.mode() == Pick.Mode.EACH);

    /** {@code Pick.untilNone()}. */
    public static final DeclaredCall<Pick> PICK_UNTIL_NONE_SHAPE = ComponentType.part(Pick.class)
            .<Pick>writtenAs(Pick::untilNone)
            .when(pick -> pick.mode() == Pick.Mode.UNTIL_NONE);

    /** {@code Pick.until(ImageTemplate)}. */
    public static final DeclaredCall<Pick> PICK_UNTIL_SHAPE = ComponentType.part(Pick.class)
            .<ImageTemplate, Pick>writtenAs(Pick::until, Pick::look)
            .when(pick -> pick.mode() == Pick.Mode.UNTIL);

    /** A condition that only knows what it is written as — {@code Firestone::eventOn} — or {@code null}. */
    public static BooleanSupplier condition(String source) {
        return source == null || source.isBlank() ? null : new NamedCondition(source);
    }

    private static final Pattern METHOD = Pattern.compile("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*::[A-Za-z_$][\\w$]*");

    /**
     * Whether {@code source} is a method reference, {@code Firestone::eventOn}, or blank: what an edit may write as a
     * condition. The source goes into the bot's Java as written, so anything else is refused before it is.
     */
    public static boolean isMethod(String source) {
        return source == null || source.isBlank() || METHOD.matcher(source.strip()).matches();
    }

    /** How {@code condition} is written, or {@code ""} for none or a live one. */
    public static String sourceOf(BooleanSupplier condition) {
        return condition instanceof NamedCondition(String source) ? source : "";
    }

    /**
     * A condition as a name, {@code Firestone::eventOn}: what the editor reads out of {@code Screens.java}, with no
     * classpath to ask it on. Asking it throws, which the walk reads as unknown.
     */
    record NamedCondition(String source) implements BooleanSupplier {

        @Override
        public boolean getAsBoolean() {
            throw new UnsupportedOperationException(
                    "\"" + source + "\" was read out of a file by the editor and is a name, not a method");
        }
    }
}
