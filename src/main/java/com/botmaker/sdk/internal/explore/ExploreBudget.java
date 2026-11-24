// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.plugin.api.assist.Describe;

import java.time.Duration;

/**
 * What <em>Keep exploring</em> takes when the assistant, the shell or the Explore page's goal field starts it (web
 * discovery plan, phase 4): how long it goes, on how many screens, how many presses and how many calls of an agent it
 * makes (spec 2026-11-24-explore-agents-design § Coordination rules: a hard run budget), and what it explores toward.
 * A field left out is no end of that kind; started from the page's button with an empty field, it takes none.
 *
 * @param minutes    the run's time at most
 * @param maxScreens the screens it is on at most, counting each once, before it ends
 * @param maxPresses the presses it makes at most
 * @param maxCalls   the agent sessions the crew starts for it at most
 * @param goal       what the run explores toward, in plain words, which gives it its crew; blank is none
 */
public record ExploreBudget(
        @Describe(value = "minutes it explores at most; until stopped when not given", optional = true)
        Integer minutes,
        @Describe(value = "screens it visits at most, each counted once; no end when not given", optional = true)
        Integer maxScreens,
        @Describe(value = "presses it makes at most; no end when not given", optional = true)
        Integer maxPresses,
        @Describe(value = "agent sessions it starts at most; no end when not given", optional = true)
        Integer maxCalls,
        @Describe(value = "what to reach, in plain words (\"the Google sign-in page\"); the whole map when not given",
                optional = true)
        String goal) {

    public ExploreBudget {
        if (minutes != null && minutes < 1) throw new IllegalArgumentException("minutes is 1 or more");
        if (maxScreens != null && maxScreens < 1) throw new IllegalArgumentException("maxScreens is 1 or more");
        if (maxPresses != null && maxPresses < 1) throw new IllegalArgumentException("maxPresses is 1 or more");
        if (maxCalls != null && maxCalls < 1) throw new IllegalArgumentException("maxCalls is 1 or more");
        goal = goal == null || goal.isBlank() ? null : goal.strip();
    }

    /** A budget with no goal. */
    public ExploreBudget(Integer minutes, Integer maxScreens, Integer maxPresses, Integer maxCalls) {
        this(minutes, maxScreens, maxPresses, maxCalls, null);
    }

    /** A budget of time and screens only. */
    public ExploreBudget(Integer minutes, Integer maxScreens) {
        this(minutes, maxScreens, null, null);
    }

    /** No end of any kind. */
    public static final ExploreBudget NONE = new ExploreBudget(null, null);

    /** Whether it gives anything: an end of any kind, or a goal. */
    public boolean given() {
        return minutes != null || maxScreens != null || maxPresses != null || maxCalls != null || goal != null;
    }

    /**
     * What the run carries as a <em>Discover</em>'s arguments, {@code kept} those a restart's memory kept (null:
     * none): without a goal, {@code kept} as it is; with one, {@code kept} turned toward it, its website and browser
     * kept, or, with none kept, this goal and these ends, so a restart keeps both.
     */
    public DiscoverArgs toward(DiscoverArgs kept) {
        if (goal == null) return kept;
        return kept != null ? kept.toward(goal) : new DiscoverArgs(null, minutes, maxScreens, null, goal, null,
                maxPresses);
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
