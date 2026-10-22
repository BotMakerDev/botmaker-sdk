package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.TextButton;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps for the tests, as Studio's tools read them: each screen a setup and its inputs, each input a field named after
 * what it acts on ({@code campaign} for the button of {@code campaign.png}, {@code key_escape}, {@code list_strip}),
 * and an input another screen names a reference to that field ({@link #ref}).
 */
public final class MapFixtures {

    private MapFixtures() {}

    public static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    /** A screen's markers: the pictures {@code ids}. */
    public static List<ImageTemplate> marks(String... ids) {
        return Arrays.stream(ids).map(MapFixtures::picture).toList();
    }

    public static Button click(String id) {
        return Button.of(picture(id));
    }

    /** The field {@code field} of the screen {@code screen}, as a setup names it. */
    public static Button ref(String screen, String field) {
        return BotConstants.input(screen, field);
    }

    /** A screen of {@code setup} holding {@code inputs}, each a field named by {@link #fieldOf}. */
    public static MapScreen screen(Setup setup, Input... inputs) {
        Map<String, Input> fields = new LinkedHashMap<>();
        for (Input input : inputs) fields.put(fieldOf(input), input);
        return new MapScreen(setup, fields);
    }

    /** A screen called {@code name} holding {@code inputs}. */
    public static MapScreen screen(String name, Input... inputs) {
        return screen(Setup.named(name), inputs);
    }

    /** The field an input is named by here: its picture's id, {@code key_<key>}, or its first member's {@code _strip}. */
    public static String fieldOf(Input input) {
        return switch (input) {
            case Button button -> button.picture().id();
            case Hotkey hotkey -> "key_" + hotkey.key().name().toLowerCase(Locale.ROOT);
            case Strip strip -> strip.members().getFirst().id() + "_strip";
            case Tree tree -> tree.members().getFirst().id() + "_tree";
            case TextButton words -> "text_" + words.caption().words().text().toLowerCase(Locale.ROOT);
        };
    }

    /** {@code screen} with {@code inputs} too, or in place of its fields of the same name. */
    public static MapScreen with(MapScreen screen, Input... inputs) {
        MapScreen now = screen;
        for (Input input : inputs) now = now.withInput(fieldOf(input), input);
        return now;
    }

    /** The hotkey {@code key}. */
    public static Hotkey key(Key key) {
        return Hotkey.of(key);
    }
}
