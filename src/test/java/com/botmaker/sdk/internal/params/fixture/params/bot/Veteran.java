package com.botmaker.sdk.internal.params.fixture.params.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.params.GameState;
import com.botmaker.sdk.api.params.ReadOn;
import com.botmaker.sdk.internal.params.fixture.Screens;

/** Game facts a run wrote long ago, as the next run compiles them, for {@code GameFactsTest}. */
@Param
@GameState
public class Veteran {

    @Param.Kept(at = "2020-01-01T00:00:00Z")
    @Param.Shown(changes = Param.Changes.DURING_RUN)
    @ReadOn(screen = Screens.Home.class, field = "level")
    public int read() { return 7; }

    @Param.Kept(at = "2020-01-01T00:00:00Z")
    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public boolean flagged() { return true; }
}
