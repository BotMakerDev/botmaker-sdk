// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.plugin.api.assist.Describe;

import java.time.Duration;

/**
 * What <em>Keep exploring</em> takes when the assistant or the shell starts it (web discovery plan, phase 4): how
 * long it goes and on how many screens. A field left out is no end of that kind; started from Studio, it takes none.
 *
 * @param minutes    the run's time at most
 * @param maxScreens the screens it is on at most, counting each once, before it ends
 */
public record ExploreBudget(
        @Describe(value = "minutes it explores at most; until stopped when not given", optional = true)
        Integer minutes,
        @Describe(value = "screens it visits at most, each counted once; no end when not given", optional = true)
        Integer maxScreens) {

    public ExploreBudget {
        if (minutes != null && minutes < 1) throw new IllegalArgumentException("minutes is 1 or more");
        if (maxScreens != null && maxScreens < 1) throw new IllegalArgumentException("maxScreens is 1 or more");
    }

    /** No end of either kind. */
    public static final ExploreBudget NONE = new ExploreBudget(null, null);

    /** {@link #minutes} as a time; null without it. */
    public Duration time() {
        return minutes == null ? null : Duration.ofMinutes(minutes);
    }

    /** {@link #maxScreens}; 0 without it. */
    public int screens() {
        return maxScreens == null ? 0 : maxScreens;
    }
}
