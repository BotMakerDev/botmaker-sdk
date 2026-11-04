package com.botmaker.sdk.internal.params.fixture.params.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.params.Assumed;
import com.botmaker.sdk.api.params.GameState;
import com.botmaker.sdk.api.params.ReadOn;
import com.botmaker.sdk.api.params.StaleAfter;
import com.botmaker.sdk.internal.params.fixture.Screens;

/** A bot's game facts, for {@code GameFactsTest}. */
@Param
@GameState
public class Hero {

    /** What the game shows as a choice. */
    public enum Kind { WARRIOR, FIRE_MAGE }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    @ReadOn(screen = Screens.Home.class, field = "level")
    @StaleAfter(minutes = 60)
    public int level() { return 0; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    @ReadOn(screen = Screens.Home.class, field = "player")
    public Kind kind() { return Kind.WARRIOR; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    @ReadOn(screen = Screens.Home.class, field = "player")
    public boolean badge() { return false; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    @ReadOn(screen = Screens.Home.class, field = "gone")
    public boolean gone() { return false; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    @Assumed
    public int gold() { return 3; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public boolean tutorialDone() { return false; }
}
