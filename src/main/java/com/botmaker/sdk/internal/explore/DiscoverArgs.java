// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.plugin.api.assist.Describe;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * What the SDK's <em>Discover</em> run takes (web discovery phase 6c): the website to map when the bot has no launch
 * target yet, and the budget of the <em>Keep exploring</em> it runs. Every field is optional: on a bot already made,
 * {@code url} is left out and its own launch target is explored.
 *
 * @param url      the website, {@code http} or {@code https}, made the bot's launch target when it has none
 * @param minutes  the run's time at most
 * @param maxPages the screens it is on at most, each counted once
 * @param show     whether the website's browser is a window rather than headless
 * @param goal     what the run explores toward, in plain words (spec 2026-11-24-explore-agents-design); the whole
 *                 map when not given
 * @param goalScreen the screen whose reaching ends the run, a code check
 * @param maxPresses the presses it makes at most
 */
public record DiscoverArgs(
        @Describe(value = "the website to map (https://…), made the bot's launch target when it has none",
                optional = true)
        String url,
        @Describe(value = "minutes it explores at most; until stopped when not given", optional = true)
        Integer minutes,
        @Describe(value = "screens it visits at most, each counted once; no end when not given", optional = true)
        Integer maxPages,
        @Describe(value = "show the website's browser in a window instead of headless", optional = true)
        Boolean show,
        @Describe(value = "what to reach, in plain words (\"the Google sign-in page\"); the whole map when not given",
                optional = true)
        String goal,
        @Describe(value = "the screen's name that ends the run once it is reached", optional = true)
        String goalScreen,
        @Describe(value = "presses it makes at most; no end when not given", optional = true)
        Integer maxPresses) {

    /** Arguments with no goal and no press cap. */
    public DiscoverArgs(String url, Integer minutes, Integer maxPages, Boolean show) {
        this(url, minutes, maxPages, show, null, null, null);
    }

    public DiscoverArgs {
        if (url != null && url.isBlank()) url = null;
        if (url != null) {
            url = url.strip();
            String scheme;
            try {
                URI uri = new URI(url);
                scheme = uri.getScheme();
                if (uri.getHost() == null) throw new IllegalArgumentException("url names no host: " + url);
            } catch (URISyntaxException bad) {
                throw new IllegalArgumentException("url does not read as an address: " + url);
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("url is http or https: " + url);
            }
        }
        // The budget's own checks say what is wrong with each number.
        new ExploreBudget(minutes, maxPages, maxPresses, null);
    }

    /** Whether any field is given. */
    public boolean given() {
        return url != null || minutes != null || maxPages != null || show != null || goal != null
                || goalScreen != null || maxPresses != null;
    }

    /** The budget of the exploring it runs. */
    public ExploreBudget budget() {
        return new ExploreBudget(minutes, maxPages, maxPresses, null);
    }

    /**
     * These arguments toward {@code goal} instead: the website, its browser and its ends kept, the goal's screen kept
     * only while the goal is the same one.
     */
    public DiscoverArgs toward(String goal) {
        return new DiscoverArgs(url, minutes, maxPages, show, goal,
                Objects.equals(goal, this.goal) ? goalScreen : null, maxPresses);
    }

    /** What the exploring it runs goes toward; {@link ExploreGoal#NONE} without a goal. */
    public ExploreGoal explores() {
        return new ExploreGoal(goal, goalScreen);
    }

    /** Whether the browser is shown. */
    public boolean shown() {
        return Boolean.TRUE.equals(show);
    }
}
