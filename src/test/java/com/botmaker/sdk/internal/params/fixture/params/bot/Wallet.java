package com.botmaker.sdk.internal.params.fixture.params.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.params.Facts;
import com.botmaker.sdk.api.params.ReadOn;
import com.botmaker.sdk.api.params.StaleAfter;
import com.botmaker.sdk.internal.params.fixture.Screens;

/** Facts the map's readouts feed, for {@code RunFactsTest} and {@code ReadoutIntoTest}. */
@Param
@Facts
@SuppressWarnings("removal")
public class Wallet {

    /** What the app shows as a choice. */
    public enum Rank { BRONZE, SILVER_PLUS }

    /** Fed by the shop's readout, which wins over the deprecated mark naming the home screen. */
    @Param.Shown(changes = Param.Changes.DURING_RUN)
    @ReadOn(screen = Screens.Home.class, field = "level")
    public int coins() { return 0; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public String owner() { return ""; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public Rank rank() { return Rank.BRONZE; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public boolean sale() { return false; }

    /** Fed by a readout that says no time, so its deprecated mark still says how long a reading holds. */
    @Param.Shown(changes = Param.Changes.DURING_RUN)
    @StaleAfter(minutes = 5)
    public int tips() { return 0; }
}
