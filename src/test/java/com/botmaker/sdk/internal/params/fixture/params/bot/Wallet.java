// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.params.fixture.params.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.params.Facts;

/** Facts the map's readouts feed, for {@code RunFactsTest} and {@code ReadoutIntoTest}. */
@Param
@Facts
public class Wallet {

    /** What the app shows as a choice. */
    public enum Rank { BRONZE, SILVER_PLUS }

    /** Fed by the shop's readout, which holds ten minutes. */
    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public int coins() { return 0; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public String owner() { return ""; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public Rank rank() { return Rank.BRONZE; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public boolean sale() { return false; }

    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public int tips() { return 0; }
}
