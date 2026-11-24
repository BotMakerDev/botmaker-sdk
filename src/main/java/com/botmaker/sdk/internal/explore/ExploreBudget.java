// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.plugin.api.assist.Describe;

import java.time.Duration;

/**
 * What <em>Keep exploring</em> takes when the assistant or the shell starts it (web discovery plan, phase 4): how
 * long it goes, on how many screens, how many presses and how many calls of an agent it makes (spec
 * 2026-11-24-explore-agents-design § Coordination rules: a hard run budget). A field left out is no end of that kind;
 * started from Studio, it takes none.
 *
 * @param minutes    the run's time at most
 * @param maxScreens the screens it is on at most, counting each once, before it ends
 * @param maxPresses the presses it makes at most
 * @param maxCalls   the agent sessions the crew starts for it at most
 */
public record ExploreBudget(
        @Describe(value = "minutes it explores at most; until stopped when not given", optional = true)
        Integer minutes,
        @Describe(value = "screens it visits at most, each counted once; no end when not given", optional = true)
        Integer maxScreens,
        @Describe(value = "presses it makes at most; no end when not given", optional = true)
        Integer maxPresses,
        @Describe(value = "agent sessions it starts at most; no end when not given", optional = true)
        Integer maxCalls) {

    public ExploreBudget {
        if (minutes != null && minutes < 1) throw new IllegalArgumentException("minutes is 1 or more");
        if (maxScreens != null && maxScreens < 1) throw new IllegalArgumentException("maxScreens is 1 or more");
        if (maxPresses != null && maxPresses < 1) throw new IllegalArgumentException("maxPresses is 1 or more");
        if (maxCalls != null && maxCalls < 1) throw new IllegalArgumentException("maxCalls is 1 or more");
    }

    /** A budget of time and screens only. */
    public ExploreBudget(Integer minutes, Integer maxScreens) {
        this(minutes, maxScreens, null, null);
    }

    /** No end of any kind. */
    public static final ExploreBudget NONE = new ExploreBudget(null, null);

    /** Whether it gives an end of any kind. */
    public boolean given() {
        return minutes != null || maxScreens != null || maxPresses != null || maxCalls != null;
    }

    /** {@link #minutes} as a time; null without it. */
    public Duration time() {
        return minutes == null ? null : Duration.ofMinutes(minutes);
    }

    /** {@link #maxScreens}; 0 without it. */
    public int screens() {
        return maxScreens == null ? 0 : maxScreens;
    }

    /** {@link #maxPresses}; 0 without it. */
    public int presses() {
        return maxPresses == null ? 0 : maxPresses;
    }

    /** {@link #maxCalls}; 0 without it. */
    public int calls() {
        return maxCalls == null ? 0 : maxCalls;
    }
}
