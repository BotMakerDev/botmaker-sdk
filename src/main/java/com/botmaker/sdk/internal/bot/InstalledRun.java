package com.botmaker.sdk.internal.bot;

import com.botmaker.sdk.api.bot.Run;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;

/**
 * The run a bot does around its plan: its home and setup.
 *
 * <p>{@code Bot.run(Sdk.class)} installs the value your {@code @SdkValue(SdkValue.Id.RUN)} method returns
 * ({@link #use}), then reads its home and setup from here.
 *
 * <p>Kept out of the insert menus: a dropped {@code use} would be written {@code InstalledRun.use(null)} and clear
 * the run, and {@code installed} hands back a {@code Run} nothing in Studio takes. Its members stay catalogued so the
 * name resolves.
 */
public final class InstalledRun {

    private static volatile Run current = Run.NONE;

    private InstalledRun() {}

    /**
     * Uses {@code run} from now on. {@code null} clears it back to {@link Run#NONE}, which has no home and no setup.
     *
     * <p>Installing twice keeps the later run, which is what a reader would expect of a call named like this one.
     */
    public static void use(Run run) {
        current = run == null ? Run.NONE : run;
    }

    /** The installed run, or {@link Run#NONE} when nothing has been installed. Never {@code null}. */
    public static Run installed() {
        return current;
    }

    /**
     * What gets the game home: the run's {@link Run#home()}, else a {@code Navigate.to} the map's home screen
     * (2026-10-16; past a start screen, the one its loading ends on), else {@code null} for nothing. Read at each
     * call, so a map installed later counts. A walk home that does not arrive throws, so the run's recovery restarts
     * the game rather than taking it as home.
     */
    public static Runnable home() {
        if (current.home() != null) return current.home();
        ScreenGraph map = ScreenMaps.installed();
        return map.home().flatMap(root -> map.instance(root).<Runnable>map(screen -> () -> {
            if (!Navigate.to(screen)) {
                throw new BotStuckException("The run could not walk home to " + map.shown(root) + ".");
            }
        })).orElse(null);
    }

    /**
     * Whether {@code e} is the bot's own stop ({@code Bot.stop()}), which a catch that recovers from everything else
     * must let through. Read by class name: the exception is private to {@code Bot}.
     */
    public static boolean isStop(Throwable e) {
        return e.getClass().getName().equals(STOP);
    }

    private static final String STOP = "com.botmaker.sdk.api.bot.Bot$BotStoppedException";
}
