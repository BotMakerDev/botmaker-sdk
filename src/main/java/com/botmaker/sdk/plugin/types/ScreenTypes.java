package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.TypeHolder;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.Held;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.plugin.api.value.Ref;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Caption;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Grid;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Mark;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Shade;
import com.botmaker.sdk.api.screens.Shades;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Place;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.ButtonState;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.TextButton;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.params.ParamCheck;

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
public final class ScreenTypes implements TypeHolder {

    private ScreenTypes() {}

    @Override
    public List<PluginType<?>> types() {
        return List.of();
    }

    @Override
    public List<ComponentType<?>> components() {
        return List.of(SETUP_SHAPE, WAY_SHAPE, WAY_ON_ITS_OWN_SHAPE, SCREEN_VARIANT_SHAPE, LOOK_SHAPE, SHADES_SHAPE,
                SHADE_SHAPE, CHANNELS_SHAPE, GEOMETRY_SHAPE, BUTTON_SHAPE,
                BUTTON_BESIDE_SHAPE, BUTTON_ELEMENT_SHAPE, GRID_SHAPE, PLACE_ABOVE_SHAPE, PLACE_ABOVE_WORDS_SHAPE,
                PLACE_BELOW_SHAPE, PLACE_BELOW_WORDS_SHAPE, PLACE_LEFT_OF_SHAPE, PLACE_LEFT_OF_WORDS_SHAPE, PLACE_RIGHT_OF_SHAPE,
                PLACE_RIGHT_OF_WORDS_SHAPE, PLACE_NEAREST_SHAPE, PLACE_NEAREST_WORDS_SHAPE, PLACE_BETWEEN_SHAPE,
                PLACE_BETWEEN_WORDS_SHAPE, BUTTON_CYCLING_SHAPE, HOTKEY_KEY_SHAPE, HOTKEY_COMBO_SHAPE, CAPTION_SHAPE,
                READOUT_TEXT_SHAPE, READOUT_DIGITS_SHAPE, READOUT_ELEMENT_SHAPE, MARK_PICTURE_SHAPE, MARK_TEXT_SHAPE,
                MARK_ELEMENT_SHAPE, TEXT_BUTTON_SHAPE, STRIP_SHAPE,
                BESIDE_SHAPE, NODE_SHAPE, TREE_SHAPE, SCROLL_SHAPE, SCROLL_DRAG_SHAPE, PICK_FIRST_SHAPE, PICK_NTH_SHAPE, PICK_EACH_SHAPE,
                PICK_UNTIL_NONE_SHAPE, PICK_UNTIL_SHAPE, PICK_AT_SHAPE, PICK_CELL_SHAPE, WHEN_SHAPE, AT_LEAST_SHAPE,
                AT_MOST_SHAPE, IS_SHAPE, IS_TRUE_SHAPE);
    }

    /**
     * {@code Setup.named(String)} and its links, each written only when the screen has it: {@code .panelOf(…)},
     * {@code .markers(List.of(…))}, {@code .absent(List.of(…))}, {@code .texts(List.of(…))}, {@code .from(…)} (then {@code .fromPick(…)},
     * {@code .fromLooks(…)}, {@code .fromWhen(…)} when its way needs them) or {@code .after(…)} on its own,
     * {@code .alsoFrom(List.of(…))}, {@code .back(…)}, {@code .variants(List.of(…))}, {@code .popup()},
     * {@code .start()}, {@code .loads(…)}, … and {@code .at(new Point(…))} once its card was moved on 🗺 Screens' map.
     */
    public static final DeclaredCall<Setup> SETUP_SHAPE = ComponentType.part(Setup.class)
            .writtenAs(Setup::named)
            .<Input>with(Setup::from, Setup::fromInput)
            .<Screen>with(Setup::after, Setup::fromAfter)
            .<Pick>with(Setup::fromPick, setup -> setup.from() == null ? null : setup.from().pick())
            .<ButtonState>with(Setup::fromLooks, setup -> setup.from() == null ? null : setup.from().state())
            .<BooleanSupplier>with(Setup::fromWhen, Parts.SETUP_FROM_WHEN.part(ScreenTypes::condition));

    /**
     * The conditions: a method reference crossing as the text it is written as, {@code Firestone::eventOn}, a name the
     * editor holds and never asks (the user's call, 2026-10-15), or a test of a parameter, a typed value
     * ({@link #WHEN_SHAPE}). An expression is a bot method of its own.
     */
    private static final class Parts {
        static final Held<Way, Object> WAY_WHEN = Held.as(Object.class, way -> held(way.when()));
        static final Held<Setup, Object> SETUP_FROM_WHEN = Held.as(Object.class,
                setup -> setup.from() == null ? "" : held(setup.from().when()));
        static final Held<Input, Object> INPUT_WHEN = Held.as(Object.class, input -> held(input.when()));
        static final Held<ScreenVariant, Object> VARIANT_WHEN = Held.as(Object.class, v -> held(v.when()));
        static final Held<Params.When, String> WHEN_PARAMETER = Held.as(String.class,
                when -> when.parameter() instanceof NamedParameter(String source) ? source : "");
        static final Held<Readout, String> READOUT_FACT = Held.as(String.class,
                readout -> factOf(readout).isEmpty() ? null : factOf(readout));
    }

    /**
     * {@code Way.of(Input)}, {@code .pick(Pick)} through one copy of a repeated button, {@code .whenLooks(ButtonState)}
     * when it needs one look of the button, and {@code .when(…)} when a method says whether it leads here now.
     */
    public static final DeclaredCall<Way> WAY_SHAPE = ComponentType.part(Way.class)
            .writtenAs(Way::of)
            .<ButtonState>with(Way::whenLooks, Way::state)
            .<BooleanSupplier>with(Way::when, Parts.WAY_WHEN.part(ScreenTypes::condition));

    /** {@code Way.onItsOwn(Screen)}: the screen follows that one by itself, and {@code .when(…)} when it needs it. */
    public static final DeclaredCall<Way> WAY_ON_ITS_OWN_SHAPE = ComponentType.part(Way.class)
            .writtenAs(Way::onItsOwn)
            .<BooleanSupplier>with(Way::when, Parts.WAY_WHEN.part(ScreenTypes::condition));

    /**
     * {@code ScreenVariant.named(String)} and its links: {@code .markers(List.of(…))}, {@code .absent(List.of(…))},
     * {@code .inputs(List.of(…))}, {@code .when(…)}.
     */
    public static final DeclaredCall<ScreenVariant> SCREEN_VARIANT_SHAPE = ComponentType.part(ScreenVariant.class)
            .writtenAs(ScreenVariant::named)
            .<BooleanSupplier>with(ScreenVariant::when, Parts.VARIANT_WHEN.part(ScreenTypes::condition))
            .<String>with(ScreenVariant::shot, ScreenVariant::shot);

    /** {@code Look.of(ImageTemplate, ButtonState)}: another look of a button, the SDK's state or the bot's. */
    public static final DeclaredCall<Look> LOOK_SHAPE = ComponentType.part(Look.class).writtenAs(Look::of);

    /** {@code Shades.of(Shade...)}: the {@code SHADES} value, the shades the bot learned. */
    public static final DeclaredCall<Shades> SHADES_SHAPE = ComponentType.part(Shades.class)
            .writtenAsEach(Shades::of, Shades::list);

    /** {@code new Shade(kind, gain, offset, tolerance, cause, state, geometry)}: one learned shade. */
    public static final DeclaredCall<Shade> SHADE_SHAPE = ComponentType.part(Shade.class).writtenAsRecord();

    /** {@code new Shade.Channels(red, green, blue)}: a shade's gain or offset. */
    public static final DeclaredCall<Shade.Channels> CHANNELS_SHAPE = ComponentType.part(Shade.Channels.class)
            .writtenAsRecord();

    /** {@code new Shade.Geometry(ring, smallest, largest, dx, dy)}: how a glow, a scale or a shift moves a button. */
    public static final DeclaredCall<Shade.Geometry> GEOMETRY_SHAPE = ComponentType.part(Shade.Geometry.class)
            .writtenAsRecord();

    /**
     * {@code Button.of(ImageTemplate)} and its links, in the order they are written. A reference to a field is written
     * only as that field.
     */
    public static final DeclaredCall<Button> BUTTON_SHAPE = pressed(ComponentType.part(Button.class)
            .<ImageTemplate, Button>writtenAs(Button::of))
            .<Grid>with(Button::grid, Button::grid)
            .when(button -> BotConstants.inputName(button) == null && !button.cycles() && button.beside() == null
                    && !button.isElement());

    /**
     * {@code Button.of(Element)} and the links an element keeps: an app's input found in the screen's tree. Its
     * picture is the element's shot, so it is never written; nor a spot, a grid or a cycle, which an element has none
     * of.
     */
    public static final DeclaredCall<Button> BUTTON_ELEMENT_SHAPE = pressed(ComponentType.part(Button.class)
            .<Element, Button>writtenAs(Button::of))
            .when(Button::isElement);

    /**
     * {@code Button.beside(ImageTemplate, double, double)} and the same links: a spot with no picture of its own, or a
     * grid's cells, pressed at an offset from an anchor.
     */
    public static final DeclaredCall<Button> BUTTON_BESIDE_SHAPE = pressed(ComponentType.part(Button.class)
            .<ImageTemplate, Double, Double, Button>writtenAs(Button::beside, Button::picture,
                    button -> button.beside().across(), button -> button.beside().down()))
            .<Grid>with(Button::grid, Button::grid)
            .when(button -> BotConstants.inputName(button) == null && !button.cycles() && button.beside() != null);

    /**
     * A button's links past its marked ones ({@link Input}'s traits and {@code .looks(…)}): each read under another
     * name than its own, or written only as a button says it.
     */
    private static DeclaredCall<Button> pressed(DeclaredCall<Button> button) {
        return button
                .<BooleanSupplier>with(Button::when, Parts.INPUT_WHEN.part(ScreenTypes::condition))
                .flag(Button::repeated, ScreenTypes::repeatedWritten)
                .<Gesture>with(Button::gesture, ScreenTypes::gestureWritten)
                .<ImageTemplate>with(Button::dragsOnto, ScreenTypes::dropPicture)
                .<Zone>with(Button::dragsInto, ScreenTypes::dropZone)
                .<Duration>with(Button::dragTime, ScreenTypes::dragTimeWritten);
    }

    /** Whether a button is written repeated: a grid's cells are its copies, which {@code .grid(…)} says alone. */
    private static boolean repeatedWritten(Button button) {
        return button.repeats() && button.grid() == null;
    }

    /** {@code Grid.of(int, int, double, double)}: a grid button's rows and columns, its steps in the anchor's size. */
    public static final DeclaredCall<Grid> GRID_SHAPE = ComponentType.part(Grid.class).writtenAs(Grid::of);

    /*
     * A place's side and whether it is a picture or words are told apart by which factory rebuilds it equal, so
     * none of these says when it is the one.
     */
    public static final DeclaredCall<Place> PLACE_ABOVE_SHAPE = placeShape(Place::above);
    public static final DeclaredCall<Place> PLACE_ABOVE_WORDS_SHAPE = wordsShape(Place::above);
    public static final DeclaredCall<Place> PLACE_BELOW_SHAPE = placeShape(Place::below);
    public static final DeclaredCall<Place> PLACE_BELOW_WORDS_SHAPE = wordsShape(Place::below);
    public static final DeclaredCall<Place> PLACE_LEFT_OF_SHAPE = placeShape(Place::leftOf);
    public static final DeclaredCall<Place> PLACE_LEFT_OF_WORDS_SHAPE = wordsShape(Place::leftOf);
    public static final DeclaredCall<Place> PLACE_RIGHT_OF_SHAPE = placeShape(Place::rightOf);
    public static final DeclaredCall<Place> PLACE_RIGHT_OF_WORDS_SHAPE = wordsShape(Place::rightOf);
    public static final DeclaredCall<Place> PLACE_NEAREST_SHAPE = placeShape(Place::nearest);
    public static final DeclaredCall<Place> PLACE_NEAREST_WORDS_SHAPE = wordsShape(Place::nearest);

    /** {@code Place.between(ImageTemplate, ImageTemplate)}. */
    public static final DeclaredCall<Place> PLACE_BETWEEN_SHAPE = ComponentType.part(Place.class)
            .<ImageTemplate, ImageTemplate, Place>writtenAs(Place::between, Place::picture, Place::otherPicture);

    /** {@code Place.between(Caption, Caption)}. */
    public static final DeclaredCall<Place> PLACE_BETWEEN_WORDS_SHAPE = ComponentType.part(Place.class)
            .<Caption, Caption, Place>writtenAs(Place::between, Place::words, Place::otherWords);

    /** {@code Place.<side>(ImageTemplate)}: a place from a picture. */
    private static DeclaredCall<Place> placeShape(Ref.Of1<ImageTemplate, Place> factory) {
        return ComponentType.part(Place.class).<ImageTemplate, Place>writtenAs(factory, Place::picture);
    }

    /** {@code Place.<side>(Caption)}: a place from words. */
    private static DeclaredCall<Place> wordsShape(Ref.Of1<Caption, Place> factory) {
        return ComponentType.part(Place.class).<Caption, Place>writtenAs(factory, Place::words);
    }

    /** {@code Button.cycling(List<ImageTemplate>)} and its links: a button showing its looks in turn. */
    public static final DeclaredCall<Button> BUTTON_CYCLING_SHAPE = pressed(ComponentType.part(Button.class)
            .<List<ImageTemplate>, Button>writtenAs(Button::cycling, Button::cycle))
            .when(button -> BotConstants.inputName(button) == null && button.cycles());

    /**
     * A button's gesture as written: none for a click, the default, nor for a hold of its own time, which
     * {@code .hold(…)} says alone.
     */
    private static Gesture gestureWritten(Button button) {
        return button.gesture() == Gesture.CLICK || button.hold() != null ? null : button.gesture();
    }

    /** The picture a button is dragged onto; null when it is not dragged, or dragged into a zone. */
    private static ImageTemplate dropPicture(Button button) {
        return button.drag() == null ? null : button.drag().onto();
    }

    /** The zone a button is dragged into; null when it is not dragged, or dragged onto a picture. */
    private static Zone dropZone(Button button) {
        return button.drag() == null ? null : button.drag().into();
    }

    /** A drag's time as written: none for the default. */
    private static Duration dragTimeWritten(Button button) {
        return button.drag() == null ? null : button.drag().time();
    }

    /** {@code Hotkey.of(Key)} and its links: one key, tapped. */
    public static final DeclaredCall<Hotkey> HOTKEY_KEY_SHAPE = ComponentType.part(Hotkey.class)
            .<Key, Hotkey>writtenAs(Hotkey::of)
            .<BooleanSupplier>with(Hotkey::when, Parts.INPUT_WHEN.part(ScreenTypes::condition));

    /** {@code Hotkey.of(Combo)} and the same links: keys pressed together, or held. */
    public static final DeclaredCall<Hotkey> HOTKEY_COMBO_SHAPE = ComponentType.part(Hotkey.class)
            .<Combo, Hotkey>writtenAs(Hotkey::of)
            .<BooleanSupplier>with(Hotkey::when, Parts.INPUT_WHEN.part(ScreenTypes::condition));

    /** {@code Caption.of(Words, Zone)}: words a screen shows, one of the bot's {@code Texts}, read in a {@code Zones}. */
    public static final DeclaredCall<Caption> CAPTION_SHAPE = ComponentType.part(Caption.class).writtenAs(Caption::of);

    /**
     * {@code Readout.text(Zone)}, then {@code .subject()} when it names which one its screen shows, and {@code
     * .into(Progress::gold).staleAfter(…)} when it feeds a fact.
     */
    public static final DeclaredCall<Readout> READOUT_TEXT_SHAPE = feeding(ComponentType.part(Readout.class)
            .writtenAs(Readout::text));

    /** {@code Mark.of(ImageTemplate)}: a picture telling its screen, then {@code .absent()} when it never shows there. */
    public static final DeclaredCall<Mark> MARK_PICTURE_SHAPE = ComponentType.part(Mark.class)
            .<ImageTemplate, Mark>writtenAs(Mark::of);

    /** {@code Mark.of(Caption)}: words telling their screen, and the same link. */
    public static final DeclaredCall<Mark> MARK_TEXT_SHAPE = ComponentType.part(Mark.class)
            .<Caption, Mark>writtenAs(Mark::of);

    /** {@code Mark.of(Element)}: an app's element telling its screen, and the same link. */
    public static final DeclaredCall<Mark> MARK_ELEMENT_SHAPE = ComponentType.part(Mark.class)
            .<Element, Mark>writtenAs(Mark::of);

    /** {@code Readout.element(Element)}: an app's text read from the screen's tree, and the same link. */
    public static final DeclaredCall<Readout> READOUT_ELEMENT_SHAPE = feeding(ComponentType.part(Readout.class)
            .<Element, Readout>writtenAs(Readout::element));

    /** {@code Readout.digits(Zone, DigitSet)}: a number read with one of the bot's {@code Digits}, and the same link. */
    public static final DeclaredCall<Readout> READOUT_DIGITS_SHAPE = feeding(ComponentType.part(Readout.class)
            .<Zone, DigitSet, Readout>writtenAs(Readout::digits));

    /**
     * A readout's links to the fact it feeds (2026-11-06): {@code .into(Progress::gold)}, the method reference crossing
     * as its text, then {@code .staleAfter(Duration)} when its reading holds other than an hour.
     */
    private static DeclaredCall<Readout> feeding(DeclaredCall<Readout> readout) {
        return readout
                .<Params.Of>with(Readout::into, Parts.READOUT_FACT.part(ScreenTypes::fact))
                .<Duration>with(Readout::staleAfter, Readout::staleAfter);
    }

    /** {@code TextButton.of(Caption)} and its links: words clicked, pressed by a gesture as a button is. */
    public static final DeclaredCall<TextButton> TEXT_BUTTON_SHAPE = ComponentType.part(TextButton.class)
            .writtenAs(TextButton::of)
            .<BooleanSupplier>with(TextButton::when, Parts.INPUT_WHEN.part(ScreenTypes::condition))
            .<Gesture>with(TextButton::gesture, ScreenTypes::gestureWritten)
            .<Duration>with(TextButton::hold, TextButton::hold);

    /** A text button's gesture as written: none for a click, nor for a hold of its own time, which says it. */
    private static Gesture gestureWritten(TextButton button) {
        return button.gesture() == Gesture.CLICK || button.hold() != null ? null : button.gesture();
    }

    /** {@code Strip.of(Scroll, List<ImageTemplate>)} and its links: its members' defaults. */
    public static final DeclaredCall<Strip> STRIP_SHAPE = ComponentType.part(Strip.class)
            .writtenAs(Strip::of)
            .<BooleanSupplier>with(Strip::when, Parts.INPUT_WHEN.part(ScreenTypes::condition));

    /** {@code Beside.of(ImageTemplate, double, double)}: a node's offset from a connected one, in that one's size. */
    public static final DeclaredCall<Beside> BESIDE_SHAPE = ComponentType.part(Beside.class).writtenAs(Beside::of);

    /** {@code Node.of(ImageTemplate)} and its links: the nodes that unlock it, and its offsets. */
    public static final DeclaredCall<Node> NODE_SHAPE = ComponentType.part(Node.class).writtenAs(Node::of);

    /** {@code Tree.of(List<Node>)} and its links: how it scrolls, and its nodes' defaults. */
    public static final DeclaredCall<Tree> TREE_SHAPE = ComponentType.part(Tree.class)
            .writtenAs(Tree::of)
            .<BooleanSupplier>with(Tree::when, Parts.INPUT_WHEN.part(ScreenTypes::condition));

    /** {@code Scroll.wheel(int)} and its links: how a strip scrolls, and what Discovery measured of it. */
    public static final DeclaredCall<Scroll> SCROLL_SHAPE = ComponentType.part(Scroll.class)
            .writtenAs(Scroll::wheel);

    /** {@code Scroll.drag(Order, int, Duration)} and the same links. */
    public static final DeclaredCall<Scroll> SCROLL_DRAG_SHAPE = ComponentType.part(Scroll.class)
            .<Order, Integer, Duration, Scroll>writtenAs(Scroll::drag);

    /*
     * A pick's mode is told apart by which factory rebuilds it equal. The type arguments pick a factory among the
     * accessors of the same name (Pick::nth is also the reader nth()); a cell's row is read by nth(), so it is given.
     */

    /** {@code Pick.first(Order)}. */
    public static final DeclaredCall<Pick> PICK_FIRST_SHAPE = ComponentType.part(Pick.class)
            .<Order, Pick>writtenAs(Pick::first);

    /** {@code Pick.nth(int, Order)}. */
    public static final DeclaredCall<Pick> PICK_NTH_SHAPE = ComponentType.part(Pick.class)
            .<Integer, Order, Pick>writtenAs(Pick::nth);

    /** {@code Pick.each(Order)}. */
    public static final DeclaredCall<Pick> PICK_EACH_SHAPE = ComponentType.part(Pick.class)
            .<Order, Pick>writtenAs(Pick::each);

    /** {@code Pick.untilNone()}. */
    public static final DeclaredCall<Pick> PICK_UNTIL_NONE_SHAPE = ComponentType.part(Pick.class)
            .<Pick>writtenAs(Pick::untilNone);

    /** {@code Pick.until(ImageTemplate)}. */
    public static final DeclaredCall<Pick> PICK_UNTIL_SHAPE = ComponentType.part(Pick.class)
            .<ImageTemplate, Pick>writtenAs(Pick::until);

    /** {@code Pick.at(Place)}. */
    public static final DeclaredCall<Pick> PICK_AT_SHAPE = ComponentType.part(Pick.class)
            .<Place, Pick>writtenAs(Pick::at);

    /** {@code Pick.cell(int, int)}. */
    public static final DeclaredCall<Pick> PICK_CELL_SHAPE = ComponentType.part(Pick.class)
            .<Integer, Integer, Pick>writtenAs(Pick::cell, Pick::nth, Pick::column);

    /*
     * A test of a during-a-run parameter, Params.when(Progress::gold).atLeast(5), is a typed chain (2026-11-04): the
     * When is Params::when of the parameter's method reference, and each link of it a ParamCheck of its own shape,
     * written on the When it holds.
     */

    /** {@code Params.when(Progress::gold)}: the parameter, as the method reference it is written as. */
    public static final DeclaredCall<Params.When> WHEN_SHAPE = ComponentType.part(Params.When.class)
            .<Params.Of, Params.When>writtenAs(Params::when, Parts.WHEN_PARAMETER.part(ScreenTypes::parameter));

    /** {@code <when>.atLeast(5)}. */
    public static final DeclaredCall<ParamCheck> AT_LEAST_SHAPE = ComponentType.part(ParamCheck.class)
            .<Params.When, Object, Condition>writtenAs(Params.When::atLeast, ParamCheck::when, ParamCheck::value);

    /** {@code <when>.atMost(5)}. */
    public static final DeclaredCall<ParamCheck> AT_MOST_SHAPE = ComponentType.part(ParamCheck.class)
            .<Params.When, Object, Condition>writtenAs(Params.When::atMost, ParamCheck::when, ParamCheck::value);

    /** {@code <when>.is("gold")}. */
    public static final DeclaredCall<ParamCheck> IS_SHAPE = ComponentType.part(ParamCheck.class)
            .<Params.When, Object, Condition>writtenAs(Params.When::is, ParamCheck::when, ParamCheck::value);

    /** {@code <when>.isTrue()}. */
    public static final DeclaredCall<ParamCheck> IS_TRUE_SHAPE = ComponentType.part(ParamCheck.class)
            .<Params.When, Condition>writtenAs(Params.When::isTrue, ParamCheck::when);

    /**
     * The condition a {@code .when(…)} part holds: a method reference, {@code Firestone::eventOn}, as the text it is
     * written as, or {@code null} when blank; a test of a parameter as the value it is.
     */
    public static BooleanSupplier condition(Object held) {
        return switch (held) {
            case BooleanSupplier typed -> typed;
            case String source when !source.isBlank() -> new NamedCondition(source.strip());
            case null, default -> null;
        };
    }

    /** What a {@code .when(…)} part gives to be written: a method reference's text, a test as itself, or {@code ""}. */
    private static Object held(BooleanSupplier condition) {
        return condition instanceof NamedCondition(String source) ? source
                : condition instanceof ParamCheck check ? check : "";
    }

    private static final Pattern METHOD = Pattern.compile("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*::[A-Za-z_$][\\w$]*");

    /**
     * Whether {@code name} is a method reference, {@code Firestone::eventOn}: what a condition or a tested parameter
     * is named by. A reference crosses as its text, so anything else is refused before it is written.
     */
    public static boolean isMethod(String name) {
        return name != null && METHOD.matcher(name.strip()).matches();
    }

    /** {@code fact}, a parameter's method reference ({@code Progress::gold}), tested by {@code test} against {@code value}. */
    public static ParamCheck test(String fact, ParamCheck.Test test, Object value) {
        return new ParamCheck(Params.when(parameter(fact)), test, test == ParamCheck.Test.IS_TRUE ? null : value);
    }

    /** The parameter a {@code Params.when(…)} names, as its method reference's text. */
    private static Params.Of<?, ?> parameter(String reference) {
        return new NamedParameter(reference.strip());
    }

    /**
     * {@code reference}, a fact's method reference ({@code Progress::gold}), as the editor holds it in a readout's
     * {@code .into(…)}; null when blank, a readout feeding no fact.
     */
    public static Params.Of<?, ?> fact(String reference) {
        return reference == null || reference.isBlank() ? null : parameter(reference);
    }

    /** The method reference of the fact {@code readout} is read into, {@code Progress::gold}, or {@code ""}. */
    public static String factOf(Readout readout) {
        return readout.fact() instanceof NamedParameter(String source) ? source : "";
    }

    /** The method reference of the parameter {@code check} tests, {@code Progress::gold}, or {@code ""}. */
    public static String factOf(BooleanSupplier check) {
        return check instanceof ParamCheck(Params.When<?> when, var test, var value)
                && when.parameter() instanceof NamedParameter(String source) ? source : "";
    }

    /**
     * How {@code condition} reads to a person: a method reference as written, {@code Firestone::eventOn}, a test as
     * {@code Progress::gold at least 5}; {@code ""} for none or a live one.
     */
    public static String sourceOf(BooleanSupplier condition) {
        if (condition instanceof NamedCondition(String source)) return source;
        if (!(condition instanceof ParamCheck(var when, ParamCheck.Test test, Object value))) return "";
        String fact = factOf(condition);
        return switch (test) {
            case AT_LEAST -> fact + " at least " + value;
            case AT_MOST -> fact + " at most " + value;
            case IS -> fact + " is " + (value instanceof String text ? "\"" + text + "\"" : value);
            case IS_TRUE -> fact + " is true";
        };
    }

    /**
     * A condition as a name, {@code Firestone::eventOn}: what the editor reads out of {@code Screens.java}, with no
     * classpath to ask it on, so it cannot tell: unknown, which never closes a way.
     */
    record NamedCondition(String source) implements Condition {

        @Override
        public Answer answer() {
            return Answer.UNKNOWN;
        }
    }

    /**
     * A parameter as the method reference it is written as, {@code Progress::gold}: what the editor holds of a
     * {@code Params.when(…)}, with no classpath to call it on.
     */
    record NamedParameter(String source) implements Params.Of<Object, Object> {

        @Override
        public Object apply(Object group) {
            throw new UnsupportedOperationException(source + " is only its name here");
        }
    }
}
