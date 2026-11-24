// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.CompanionHolder;
import com.botmaker.plugin.api.run.RunCompanion;

/** The companion of Keep exploring's and Discover's runs: their explore crew's Coordinator ({@link CoordinatorHost}). */
public final class SdkCompanions implements CompanionHolder {

    @Override
    public RunCompanion companion() {
        return new CoordinatorHost();
    }
}
