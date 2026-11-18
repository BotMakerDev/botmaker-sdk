// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.screens;

import com.botmaker.plugin.api.assist.Describe;

import java.time.Duration;

/**
 * What <em>Explore map</em> takes when the assistant or the shell starts it (web discovery plan, phase 4): how long
 * the walk goes. It walks the map it has, so its screens are the map's: no count of them to give.
 *
 * @param minutes the walk's time at most; {@link MapWalk.Budgets#DEFAULT}'s without it
 */
public record MapCheckBudget(
        @Describe(value = "minutes the walk goes at most; 30 when not given", optional = true) Integer minutes) {

    public MapCheckBudget {
        if (minutes != null && minutes < 1) throw new IllegalArgumentException("minutes is 1 or more");
    }

    /** {@link MapWalk.Budgets#DEFAULT} with this walk's time. */
    public MapWalk.Budgets budgets() {
        MapWalk.Budgets budgets = MapWalk.Budgets.DEFAULT;
        return minutes == null ? budgets
                : new MapWalk.Budgets(budgets.edge(), Duration.ofMinutes(minutes), budgets.restarts());
    }
}
