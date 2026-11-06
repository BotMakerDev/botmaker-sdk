package samplebot.params.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.params.Facts;

/** What the game showed, kept between runs: each kind of fact once, read where the map's readouts say. */
@Param
@Facts
public class Progress {

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public int gold() { return 0; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public String player() { return ""; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public boolean soundShown() { return false; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public boolean rested() { return false; }
}
