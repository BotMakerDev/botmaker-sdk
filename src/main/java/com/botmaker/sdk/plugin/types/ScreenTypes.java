package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.Held;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Kind;
import com.botmaker.sdk.api.screens.Reached;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Variant;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * The shapes of the {@code SCREEN_MAP} value, {@code Screens.java}: a screen, its ways in and its inputs, as the
 * calls the host reads and writes. Like a flow's, none is picked on its own ({@link FlowTypes}).
 *
 * <p>A screen named inside another — the {@code HOME} of {@code Way.of(HOME, …)} — is a reference to that constant,
 * held by its name ({@code BotConstants.screen}) and written back as the constant, never spelled out.
 */
public final class ScreenTypes {

    private ScreenTypes() {}

    /**
     * {@code GameScreen.named(String)} and its links, each written only when the screen has it: {@code .marker(…)},
     * {@code .from(…)}, {@code .alsoFrom(List.of(…))}, {@code .back(…)}, {@code .inputs(List.of(…))},
     * {@code .popup()}, … and {@code .at(new Point(…))} once its card was moved on 🗺 Screens' map.
     */
    public static final DeclaredCall<GameScreen> SCREEN_SHAPE = ComponentType.part(GameScreen.class)
            .<String, GameScreen>writtenAs(GameScreen::named, GameScreen::name)
            .<ImageTemplate>with(GameScreen::marker, GameScreen::marker)
            .<Way>with(GameScreen::from, GameScreen::from)
            .<List<Way>>with(GameScreen::alsoFrom, GameScreen::alsoFrom)
            .<Input>with(GameScreen::back, GameScreen::back)
            .<List<Input>>with(GameScreen::inputs, GameScreen::inputs)
            .<List<ScreenVariant>>with(GameScreen::variants, GameScreen::variants)
            .flag(GameScreen::popup, GameScreen::isPopup)
            .<String>with(GameScreen::notes, GameScreen::notes)
            .<Reached>with(GameScreen::reached, GameScreen::reached)
            .<List<String>>with(GameScreen::owes, GameScreen::owes)
            .<Point>with(GameScreen::at, GameScreen::at)
            // A reference to a screen is written only as that constant.
            .when(screen -> BotConstants.screenName(screen) == null);

    /**
     * The conditions, each a method reference crossing as the text it is written as, {@code Firestone::eventOn}: a
     * name the editor holds and never asks (the user's call, 2026-10-15). An expression is a bot method of its own.
     */
    private static final class Parts {
        static final Held<Way, String> WAY_WHEN = Held.as(String.class, way -> sourceOf(way.when()));
        static final Held<Input, String> INPUT_WHEN = Held.as(String.class, input -> sourceOf(input.when()));
        static final Held<ScreenVariant, String> VARIANT_WHEN = Held.as(String.class, v -> sourceOf(v.when()));
    }

    /**
     * {@code Way.of(GameScreen, Input)}, {@code .whenLooks(State)} when it needs one look of the button, and
     * {@code .when(…)} when a method says whether it leads here now.
     */
    public static final DeclaredCall<Way> WAY_SHAPE = ComponentType.part(Way.class)
            .<GameScreen, Input, Way>writtenAs(Way::of, Way::from, Way::via)
            .<State>with(Way::whenLooks, Way::state)
            .<BooleanSupplier>with(Way::when, Parts.WAY_WHEN.part(ScreenTypes::condition));

    /**
     * {@code ScreenVariant.named(String)} and its links: {@code .marker(…)}, {@code .inputs(List.of(…))},
     * {@code .when(…)}.
     */
    public static final DeclaredCall<ScreenVariant> SCREEN_VARIANT_SHAPE = ComponentType.part(ScreenVariant.class)
            .<String, ScreenVariant>writtenAs(ScreenVariant::named, ScreenVariant::name)
            .<ImageTemplate>with(ScreenVariant::marker, ScreenVariant::marker)
            .<List<Input>>with(ScreenVariant::inputs, ScreenVariant::inputs)
            .<BooleanSupplier>with(ScreenVariant::when, Parts.VARIANT_WHEN.part(ScreenTypes::condition));

    /** {@code Variant.of(ImageTemplate, State)}: another look of an input's button. */
    public static final DeclaredCall<Variant> VARIANT_SHAPE = ComponentType.part(Variant.class)
            .<ImageTemplate, State, Variant>writtenAs(Variant::of, Variant::picture, Variant::state);

    /** {@code Input.click(ImageTemplate)} and the input's links. */
    public static final DeclaredCall<Input> CLICK_SHAPE = inputLinks(ComponentType.part(Input.class)
            .<ImageTemplate, Input>writtenAs(Input::click, Input::picture))
            .when(input -> input.act() == Input.Act.CLICK);

    /** {@code Input.key(Key)} and the input's links. */
    public static final DeclaredCall<Input> KEY_SHAPE = inputLinks(ComponentType.part(Input.class)
            .<Key, Input>writtenAs(Input::key, Input::key))
            .when(input -> input.act() == Input.Act.KEY);

    /** {@code Input.scroll(ImageTemplate, int)} and the input's links. */
    public static final DeclaredCall<Input> SCROLL_SHAPE = inputLinks(ComponentType.part(Input.class)
            .<ImageTemplate, Integer, Input>writtenAs(Input::scroll, Input::picture, Input::notches))
            .when(input -> input.act() == Input.Act.SCROLL);

    /** {@code call} with every input's links, in the order they are written: one list for the three acts. */
    private static DeclaredCall<Input> inputLinks(DeclaredCall<Input> call) {
        return call
                .<Did>with(Input::did, Input::did)
                .<Kind>with(Input::kind, Input::kind)
                .<String>with(Input::never, Input::never)
                .flag(Input::sometimes, Input::comesAndGoes)
                .<ImageTemplate>with(Input::locked, Input::lock)
                .<List<Variant>>with(Input::variants, Input::variants)
                .<BooleanSupplier>with(Input::when, Parts.INPUT_WHEN.part(ScreenTypes::condition));
    }

    /** The screen map's shapes, which {@code SdkTypes.PARTS} hands the host. */
    public static final List<ComponentType<?>> ALL = List.of(SCREEN_SHAPE, WAY_SHAPE, SCREEN_VARIANT_SHAPE,
            VARIANT_SHAPE, CLICK_SHAPE, KEY_SHAPE, SCROLL_SHAPE);

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
