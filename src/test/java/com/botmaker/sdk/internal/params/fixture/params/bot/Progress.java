package com.botmaker.sdk.internal.params.fixture.params.bot;

import com.botmaker.plugin.api.params.Param;

import java.time.Duration;

/** A bot's parameter group, for {@code ParamsTest}. */
@Param
public class Progress {

    /** A closed set of values, for writing an enum. */
    public enum Rank { LOW, HIGH }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public int level() { return 1; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public Rank rank() { return Rank.LOW; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public Duration rest() { return Duration.ofMinutes(1); }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public boolean open() { return false; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public Object unspellable() { return null; }

    public int tries() { return 10; }
}
