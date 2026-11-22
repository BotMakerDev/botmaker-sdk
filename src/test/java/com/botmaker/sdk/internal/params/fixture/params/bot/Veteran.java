// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.params.fixture.params.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.params.Facts;

/** Game facts a run wrote long ago, as the next run compiles them, for {@code RunFactsTest}. */
@Param
@Facts
public class Veteran {

    /** Fed by the home screen's readout. */
    @Param.Kept(at = "2020-01-01T00:00:00Z")
    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public int read() { return 7; }

    @Param.Kept(at = "2020-01-01T00:00:00Z")
    @Param.Shown(changes = Param.Changes.DURING_RUN)
    public boolean flagged() { return true; }
}
