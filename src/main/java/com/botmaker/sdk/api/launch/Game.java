package com.botmaker.sdk.api.launch;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.shared.launch.GameLauncher;

/**
 * Launches a game so a bot can automate it.
 *
 * <p>Three entry points, all exposed as visual blocks:
 * <ul>
 *   <li>{@link #launch(String, String...)} — run any executable/command directly.</li>
 *   <li>{@link #launchSteam(String)} — hand a Steam appId to the local Steam client.</li>
 *   <li>{@link #launchEpic(String)} — hand an Epic app name to the local Epic Games Launcher.</li>
 * </ul>
 *
 * <p>Launching a store game requires that store's client (Steam / Epic Games Launcher) to be installed and
 * signed in — the client owns that session; this SDK never touches store credentials. If the client is not
 * running, invoking the launch starts it and it prompts the user to sign in through its own UI.
 *
 * <p>This class is the bot-facing <em>name</em> for launching; the protocol URLs, CLI ladders and process
 * control themselves live in {@code shared.launch.GameLauncher}, because Studio needs to launch a target too
 * (to verify one without compiling a bot) and cannot depend on the SDK. Only the methods that take a
 * {@link CaptureSource} — the running/wait pairs below — are genuinely SDK-shaped and stay whole here.
 */
public class Game {

    private Game() {}

    /**
     * Starts an executable, optionally with arguments. The process is detached — its input/output is not tied
     * to the bot.
     *
     * @param executablePath path to the program to run (absolute, or resolvable on {@code PATH})
     * @param args           optional command-line arguments
     * @return the started process — the caller's first-hand handle on the target, used by
     *         {@link LaunchTarget#isRunning()} for the variants whose spawned process really is the target
     * @throws IllegalArgumentException if {@code executablePath} is null/blank
     * @throws RuntimeException         if the process could not be started
     */
    public static Process launch(String executablePath, String... args) {
        return GameLauncher.exe(executablePath, args);
    }

    /**
     * Launches a Steam game by its appId (the number in the game's Steam store URL / SteamDB). Opens the
     * cross-platform {@code steam://rungameid/<appId>} URL and falls back to {@code steam -applaunch <appId>}.
     *
     * @param appId the Steam application id, e.g. {@code "570"}
     * @throws IllegalArgumentException if {@code appId} is null/blank
     * @throws RuntimeException         if neither the Steam URL nor the CLI fallback could be invoked
     */
    public static void launchSteam(String appId) {
        GameLauncher.steam(appId);
    }

    /** Convenience overload accepting a numeric appId. */
    public static void launchSteam(int appId) {
        launchSteam(Integer.toString(appId));
    }

    /**
     * Launches an Epic Games game by its <em>app name</em> — the {@code AppName} launch token from the Epic
     * Games Launcher's local manifest (Studio's game picker fills this in for you), not the store-page title.
     * Opens the {@code com.epicgames.launcher://apps/<appName>?action=launch} protocol URL, which the
     * installed Epic Games Launcher handles.
     *
     * <p>Unlike Steam there is no supported command-line fallback, so this relies on the {@code
     * com.epicgames.launcher://} protocol handler that the launcher registers on install.
     *
     * @param appName the Epic application name / launch token, e.g. {@code "Fortnite"}
     * @throws IllegalArgumentException if {@code appName} is null/blank
     * @throws RuntimeException         if the Epic protocol URL could not be invoked (launcher not installed?)
     */
    public static void launchEpic(String appName) {
        GameLauncher.epic(appName);
    }

    /**
     * Launches a game through the <a href="https://heroicgameslauncher.com/">Heroic Games Launcher</a> by its
     * <em>app name</em> — the launch token from Heroic's local library (Studio's Heroic game picker fills this
     * in for you). This is the practical way to launch Epic/GOG games on Linux, where the native store clients
     * don't run.
     *
     * <p>Opens the {@code heroic://launch/<appName>} protocol URL (handled by an installed Heroic, native or
     * Flatpak) and falls back to Heroic's CLI — {@code heroic --no-gui launch <appName>}, then the Flatpak form
     * {@code flatpak run com.heroicgameslauncher.hgl --no-gui launch <appName>}.
     *
     * @param appName the Heroic application name / launch token, e.g. {@code "Firestone"}
     * @throws IllegalArgumentException if {@code appName} is null/blank
     * @throws RuntimeException         if neither the Heroic URL nor a CLI fallback could be invoked
     */
    public static void launchHeroic(String appName) {
        GameLauncher.heroic(appName);
    }

    /**
     * Launches a game through <a href="https://faugus.github.io/">Faugus Launcher</a> by its <em>game id</em> —
     * the {@code gameid} from Faugus's local library (Studio's Faugus game picker fills this in for you). Faugus
     * runs non-Steam Windows games and launchers (Battle.net, the EA App, HoYoPlay, …) through umu/Proton.
     *
     * <p>Faugus registers no protocol handler, so this goes straight to its CLI —
     * {@code faugus-launcher --game <gameId>}, then the Flatpak form
     * {@code flatpak run io.github.Faugus.faugus-launcher --game <gameId>}. Faugus matches {@code gameid}
     * exactly against its {@code games.json}, so the id must be the stored one, not the title.
     *
     * @param gameId the Faugus {@code gameid} launch token, e.g. {@code "battlenet"}
     * @throws IllegalArgumentException if {@code gameId} is null/blank
     * @throws RuntimeException         if neither CLI form could be invoked
     */
    public static void launchFaugus(String gameId) {
        GameLauncher.faugus(gameId);
    }

    /**
     * Launches an Epic game by app name only if {@code source}'s window is not already open.
     *
     * @param appName the Epic application name (see {@link #launchEpic(String)})
     * @param source  the capture source used to detect an existing instance
     * @return true if the game was launched, false if it was already running
     */
    public static boolean launchEpicIfNotRunning(String appName, CaptureSource source) {
        if (isRunning(source)) {
            return false;
        }
        launchEpic(appName);
        return true;
    }

    // --- Running-detection & wait (window-based, via CaptureSource) ---

    /**
     * Whether the game's window is currently open. Pass the same {@link CaptureSource} the bot targets —
     * typically {@code CaptureSource.window("Game Title")} — and this reports whether that window exists
     * right now. A cheap way to tell if a game is already running before deciding to launch it.
     *
     * @param source the capture source identifying the game (usually a {@code CaptureSource.window(...)})
     * @return true if the source's window is currently present
     * @throws IllegalArgumentException if {@code source} is null
     */
    public static boolean isRunning(CaptureSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        return source.isPresent();
    }

    /**
     * Blocks until {@code source}'s window appears, or {@code timeoutMillis} elapses. Poll interval is
     * ~250ms. Use after {@link #launch} to wait for a game to finish starting up.
     *
     * @param source        the capture source identifying the game window
     * @param timeoutMillis the maximum time to wait, in milliseconds
     * @return true if the window appeared within the timeout, false if it timed out
     * @throws IllegalArgumentException if {@code source} is null
     */
    public static boolean waitForLaunch(CaptureSource source, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMillis);
        while (true) {
            if (isRunning(source)) {
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            Wait.milliseconds(250);
        }
    }

    /**
     * Launches {@code executablePath} only if {@code source}'s window is not already open — avoids
     * spawning a duplicate instance of an already-running game.
     *
     * @param executablePath path to the program to run (see {@link #launch})
     * @param source         the capture source used to detect an existing instance
     * @param args           optional command-line arguments
     * @return true if the game was launched, false if it was already running
     */
    public static boolean launchIfNotRunning(String executablePath, CaptureSource source, String... args) {
        if (isRunning(source)) {
            return false;
        }
        launch(executablePath, args);
        return true;
    }

    /**
     * Launches a Steam game by appId only if {@code source}'s window is not already open.
     *
     * @param appId  the Steam application id (see {@link #launchSteam(String)})
     * @param source the capture source used to detect an existing instance
     * @return true if the game was launched, false if it was already running
     */
    public static boolean launchSteamIfNotRunning(String appId, CaptureSource source) {
        if (isRunning(source)) {
            return false;
        }
        launchSteam(appId);
        return true;
    }

    /**
     * Launches {@code executablePath} (unless already running) and then blocks until {@code source}'s
     * window appears or {@code timeoutMillis} elapses.
     *
     * @param executablePath path to the program to run
     * @param source         the capture source identifying the game window
     * @param timeoutMillis  the maximum time to wait for the window, in milliseconds
     * @param args           optional command-line arguments
     * @return true if the game's window was present within the timeout, false if it timed out
     */
    public static boolean launchAndWait(String executablePath, CaptureSource source, long timeoutMillis,
                                        String... args) {
        launchIfNotRunning(executablePath, source, args);
        return waitForLaunch(source, timeoutMillis);
    }

    // --- Process control (by executable name) ---

    /**
     * Force-terminates every process whose executable matches {@code processName} — the "close the game"
     * half of a restart routine. Best-effort and cross-platform: Windows {@code taskkill /F /IM <name>},
     * Linux/macOS {@code pkill -f <name>}. Never throws when there is simply no such process (that is a
     * success for a kill); a genuinely un-runnable killer command is logged, not raised, so a restart loop
     * keeps going.
     *
     * @param processName the executable name, e.g. {@code "Firestone.exe"} (Windows) or {@code "firestone"}
     * @throws IllegalArgumentException if {@code processName} is null/blank
     */
    public static void kill(String processName) {
        GameLauncher.kill(processName);
    }

    /**
     * Whether any process whose executable matches {@code processName} is currently running — the name-based
     * counterpart to {@link #isRunning(CaptureSource)}. Uses {@code tasklist} on Windows, {@code pgrep -f}
     * elsewhere. Returns {@code false} (rather than throwing) if the check itself cannot run.
     *
     * @param processName the executable name to look for
     * @throws IllegalArgumentException if {@code processName} is null/blank
     */
    public static boolean isRunning(String processName) {
        return GameLauncher.isProcessRunning(processName);
    }
}
