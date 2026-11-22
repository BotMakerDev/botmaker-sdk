// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.params.fixture.params.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.params.Facts;

/** A bot's game facts, the home screen's readouts feeding them, for {@code RunFactsTest} and {@code NeedTest}. */
@Param
@Facts
public class Hero {

    /** What the game shows as a choice. */
    public enum Kind { WARRIOR, FIRE_MAGE }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public int level() { return 0; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public Kind kind() { return Kind.WARRIOR; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public boolean badge() { return false; }

    /** No readout feeds it. */
    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public boolean gone() { return false; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public int gold() { return 3; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public boolean tutorialDone() { return false; }
}
