// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.run.RunArguments;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.internal.explore.DiscoverArgs;
import com.botmaker.sdk.internal.explore.Generation;
import com.botmaker.sdk.internal.explore.LivePlay;
import com.botmaker.sdk.internal.explore.ResumeFile;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.launch.LaunchTarget;
import com.botmaker.sdk.internal.launch.Target;
import com.botmaker.sdk.internal.trace.Say;
import com.botmaker.sdk.internal.web.WebSource;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The SDK's <em>Discover</em> run (web discovery phase 6c): maps a game or a website into the bot's screen map by
 * <em>Keep exploring</em> within a budget. Given a website's {@code url} on a bot with no launch target, the website
 * becomes the run's target at once and the bot's own with the first stop the run settles, so a blank bot ends with
 * its map and its game. On a bot that has a target, {@code url} is left out.
 */
public final class Discover {

    private Discover() {}

    /** The run's main: {@code args} are the binary names of the bot's value holders, as for {@link KeepExploring}. */
    @Hidden("Studio's Discover run")
    public static void main(String[] args) {
        DiscoverArgs given;
        try {
            given = RunArguments.read(DiscoverArgs.class);
        } catch (IllegalArgumentException e) {
            Debug.error("Discover cannot read its arguments: " + e.getMessage());
            return;
        }
        Generation first;
        try {
            first = Generation.first(List.of(args));
        } catch (RuntimeException | LinkageError e) {
            Debug.error("Discover cannot load the bot's classes: " + e);
            return;
        }
        Bot.trial(() -> {
            first.install();
            Optional<ResumeFile> pending = LivePlay.pending(KeepExploring.project());
            // A restart (a dev reload, a crash) is handed no arguments: it takes those the run kept.
            DiscoverArgs started = ResumeFile.discoverOf(given, pending);
            List<TreeAnswer> settled = target(started);
            if (settled == null) return;
            KeepExploring.explore(first, started.budget(), started, settled, pending);
        }, first.holders());
    }

    /**
     * Points the run at {@code given}'s website when the bot has no launch target: the answer that makes it the bot's
     * own. None when no url is given and the bot has its target, or the url is its target; null, said, when the url
     * is given beside another target, or neither is there.
     */
    private static List<TreeAnswer> target(DiscoverArgs given) {
        if (given.shown()) System.setProperty(WebSource.SHOW, "true");
        List<String> targets = InstalledGame.launchTargets();
        if (given.url() == null) {
            if (!targets.isEmpty()) return List.of();
            Debug.error("Discover needs a url: the bot has no launch target to explore.");
            return null;
        }
        String spec = new LaunchSpec(LaunchKind.WEB, given.url()).spec();
        if (!targets.isEmpty()) {
            if (targets.stream().map(InstalledGame::canonical).anyMatch(spec::equals)) return List.of();
            Debug.error("Discover was given " + given.url() + ", but the bot launches " + String.join(", ", targets)
                    + ": leave the url out, or start from a bot with no launch target.");
            return null;
        }
        Target.set(new LaunchTarget.Web(given.url()));
        Source.set(WebSource.over(given.url()));
        Say.info("Discover maps " + given.url() + ", the bot's launch target from its first settled stop on",
                Map.of());
        return List.of(new TreeAnswer.NewTarget(spec));
    }
}
