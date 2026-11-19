// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.plugin.api.assist.Describe;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * What the SDK's <em>Discover</em> run takes (web discovery phase 6c): the website to map when the bot has no launch
 * target yet, and the budget of the <em>Keep exploring</em> it runs. Every field is optional: on a bot already made,
 * {@code url} is left out and its own launch target is explored.
 *
 * @param url      the website, {@code http} or {@code https}, made the bot's launch target when it has none
 * @param minutes  the run's time at most
 * @param maxPages the screens it is on at most, each counted once
 * @param show     whether the website's browser is a window rather than headless
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
        Boolean show) {

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
        // The budget's own checks say what is wrong with either number.
        new ExploreBudget(minutes, maxPages);
    }

    /** The budget of the exploring it runs. */
    public ExploreBudget budget() {
        return new ExploreBudget(minutes, maxPages);
    }

    /** Whether the browser is shown. */
    public boolean shown() {
        return Boolean.TRUE.equals(show);
    }
}
