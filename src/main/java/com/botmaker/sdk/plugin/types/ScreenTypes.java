package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Kind;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;

import java.util.List;

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
     * {@code .popup()}.
     */
    public static final DeclaredCall<GameScreen> SCREEN_SHAPE = ComponentType.part(GameScreen.class)
            .<String, GameScreen>writtenAs(GameScreen::named, GameScreen::name)
            .<ImageTemplate>with(GameScreen::marker, GameScreen::marker)
            .<Way>with(GameScreen::from, GameScreen::from)
            .<List<Way>>with(GameScreen::alsoFrom, GameScreen::alsoFrom)
            .<Input>with(GameScreen::back, GameScreen::back)
            .<List<Input>>with(GameScreen::inputs, GameScreen::inputs)
            .flag(GameScreen::popup, GameScreen::isPopup)
            // A reference to a screen is written only as that constant.
            .when(screen -> BotConstants.screenName(screen) == null);

    /** {@code Way.of(GameScreen, Input)}. */
    public static final DeclaredCall<Way> WAY_SHAPE = ComponentType.part(Way.class)
            .<GameScreen, Input, Way>writtenAs(Way::of, Way::from, Way::via);

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
                .<ImageTemplate>with(Input::locked, Input::lock);
    }

    /** The screen map's shapes, which {@code SdkTypes.PARTS} hands the host. */
    public static final List<ComponentType<?>> ALL = List.of(SCREEN_SHAPE, WAY_SHAPE, CLICK_SHAPE, KEY_SHAPE,
            SCROLL_SHAPE);
}
