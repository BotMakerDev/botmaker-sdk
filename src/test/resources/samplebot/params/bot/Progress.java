package samplebot.params.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.params.Assumed;
import com.botmaker.sdk.api.params.GameState;
import com.botmaker.sdk.api.params.ReadOn;
import com.botmaker.sdk.api.params.StaleAfter;
import samplebot.plugins.com_github_botmakerdev.botmaker_sdk.Screens;

/** What the game showed, kept between runs: each kind of fact once. */
@Param
@GameState
public class Progress {

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    @ReadOn(screen = Screens.Home.class, field = "gold")
    @StaleAfter(minutes = 30)
    public int gold() { return 0; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    @ReadOn(screen = Screens.Home.class, field = "title")
    public String player() { return ""; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    @ReadOn(screen = Screens.Settings.class, field = "volume")
    public boolean soundShown() { return false; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    @Assumed
    public boolean rested() { return false; }
}
