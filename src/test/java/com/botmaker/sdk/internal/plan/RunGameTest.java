package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.screens.Custom;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.plan.Round.Step;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What a plan's step presses on the game. */
class RunGameTest {

    /** Public, as a bot's are: the map reads a screen's fields by reflection. */
    public static final class Title extends Screen {
        public final Custom idle = Custom.of(() -> {}).free();
        public final Do waitOnTitle = Do.press(idle);

        @Override
        public Setup setup() {
            return Setup.named("Title");
        }
    }

    @Test
    void aStepPressesItsActionsInputNotTheActionsOwnField() {
        Title title = new Title();
        ScreenGraph map = ScreenGraph.ofScreens(Map.of("TITLE", title));

        assertEquals("idle", RunGame.inputField(map, new Step(title.waitOnTitle, "TITLE", "waitOnTitle")));
        assertEquals("waitOnTitle", RunGame.inputField(map, new Step(Do.press(Custom.of(() -> {})), "TITLE",
                "waitOnTitle")), "an input the screen does not hold: the step's own field, which the walk says is absent");
    }
}
