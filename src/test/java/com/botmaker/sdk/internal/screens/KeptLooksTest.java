package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.internal.bot.BotStuckException;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Pressed;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.picture;
import static com.botmaker.sdk.internal.screens.MapFixtures.screen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The settings a map keeps (2026-11-02): each pressed at the run's start until it shows its look. */
class KeptLooksTest {

    private static final ImageTemplate HIGH = picture("quality_high");
    private static final ImageTemplate SOUND_OFF = picture("sound_off");

    @Test
    void aKeptLookIsOneOfTheButtonsLooks() {
        Button quality = click("quality_low").keeps(HIGH);
        assertEquals(List.of(), quality.looks(), "the Java keeps what its author wrote");
        assertEquals(List.of(Look.of(picture("quality_low"), State.NORMAL), Look.of(HIGH, State.SELECTED)),
                quality.allLooks(), "seen as picked");
        Button already = click("quality_low").looks(List.of(Look.of(HIGH.at(new Rect(1, 2, 3, 4)), State.NORMAL)))
                .keeps(HIGH);
        assertEquals(2, already.allLooks().size(), "its own look, the same picture placed, is kept as it was");
        assertEquals(click("quality_low"), click("quality_low").keeps(HIGH).keeps(null), "none kept: any look will do");
        assertEquals(List.of(picture("x1"), picture("x10")),
                Button.cycling(List.of(picture("x1"), picture("x10"))).keeps(picture("x50")).cycle(),
                "a cycle is written as its author wrote it");
    }

    @Test
    void eachKeptLookIsPressedUntilItShowsScreenByScreen() {
        ScreenGraph map = map();
        assertEquals(List.of("GRAPHICS.quality_low", "SOUND.sound_on"), map(KeptLooks.of(map)),
                "a popup's and a plain button are none");

        List<String> pressed = new ArrayList<>();
        Optional<String> why = KeptLooks.hold(map, (button, pick) -> {
            assertEquals(Pick.Mode.UNTIL, pick.mode());
            pressed.add(pick.look().id());
            return new Pressed(Pressed.Result.DONE, null, 1);
        });
        assertEquals(Optional.empty(), why);
        assertEquals(List.of(HIGH.id(), SOUND_OFF.id()), pressed);
    }

    @Test
    void aLookThatNeverShowsStopsTheRunNamingIt() {
        List<String> pressed = new ArrayList<>();
        Optional<String> why = KeptLooks.hold(map(), (button, pick) -> {
            pressed.add(pick.look().id());
            return new Pressed(Pressed.Result.DISABLED, null, 0);
        });
        assertEquals(List.of(HIGH.id()), pressed, "nothing after the first that failed");
        assertTrue(why.orElseThrow().startsWith("Graphics's quality_low never showed " + HIGH.id() + " (disabled)"),
                why.get());
    }

    @Test
    void aWalkThatGetsStuckIsSaidNotThrown() {
        Optional<String> why = KeptLooks.hold(map(), (button, pick) -> {
            throw new BotStuckException("No way to Graphics in 8 steps.");
        });
        assertEquals("Graphics's quality_low could not be reached: No way to Graphics in 8 steps.", why.orElseThrow());
    }

    private static List<String> map(List<KeptLooks.Kept> kept) {
        return kept.stream().map(k -> k.screen() + "." + k.field()).toList();
    }

    private static ScreenGraph map() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("GRAPHICS", screen("Graphics", click("quality_low").keeps(HIGH), click("close")));
        screens.put("GIFT", screen(Setup.named("Gift").popup(), click("gift_ok").keeps(picture("gift_on"))));
        screens.put("SOUND", screen("Sound", click("sound_on").keeps(SOUND_OFF)));
        return ScreenGraph.of(screens);
    }
}
