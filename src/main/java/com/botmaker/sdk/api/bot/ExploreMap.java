package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.internal.screens.LiveMoves;
import com.botmaker.sdk.internal.screens.MapWalk;
import com.botmaker.sdk.internal.screens.RunReach;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import com.botmaker.sdk.internal.trace.Say;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Studio's <em>Explore map</em> run: walks the bot's whole screen map, every screen and every edge once, without the
 * bot's {@code main}, and says what did not go as the map says — an edge that missed (a warning), a screen whose
 * pictures no longer match or one never reached (errors) — then one line of counts. The run's problems reach the
 * end-of-run dialog and the Reports tab like any run's.
 *
 * <p>Never stuck for ever: {@link MapWalk.Budgets#DEFAULT} gives each edge 30 s, the walk 30 min and 5 game restarts.
 */
public final class ExploreMap {

    private ExploreMap() {}

    /**
     * The run's main: {@code args} are the binary names of the bot's value holders, as Studio hands a declared run.
     * An arg that does not load is said, and nothing is walked.
     */
    @Hidden("Studio's Explore map run")
    public static void main(String[] args) {
        List<Class<?>> holders = new ArrayList<>();
        for (String name : args) {
            try {
                holders.add(Class.forName(name, false, Thread.currentThread().getContextClassLoader()));
            } catch (ClassNotFoundException | LinkageError e) {
                Debug.error("Explore map cannot load the bot's " + name + ": " + e);
                return;
            }
        }
        Bot.trial(ExploreMap::explore, holders.toArray(Class<?>[]::new));
    }

    /** Starts the game as a run's cold start does, then walks the map and says what it found. */
    private static void explore() {
        Bot.launchConfiguredTarget(Bot.StartMode.COLD);
        ScreenGraph map = ScreenMaps.installed();
        AtomicInteger restarts = new AtomicInteger();
        LiveMoves moves = new LiveMoves(map, new RunReach(), () -> {
            // Restarts in a row wait longer each time, as a run's recoveries do.
            Wait.time(Bot.backOff(restarts.incrementAndGet()));
            Bot.launchConfiguredTarget(Bot.StartMode.RESTART);
        });
        // Each finding said as it is found: a stop keeps the ones found before it.
        MapWalk.Summary summary = MapWalk.walk(map, moves, MapWalk.Budgets.DEFAULT, System::currentTimeMillis,
                finding -> {
                    switch (finding.kind().level()) {
                        case WARN -> Say.warn(finding.said(map), finding.values(map));
                        default -> Say.error(finding.said(map), finding.values(map));
                    }
                });
        Say.info(summary.said(), summary.values());
    }
}
