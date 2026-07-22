package com.botmaker.sdk.api.launch;
import com.botmaker.sdk.api.Debug;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.internal.launch.UriLauncher;

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
 */
public class Game {

    private Game() {}

    /**
     * Starts an executable, optionally with arguments. The process is detached — its input/output is not tied
     * to the bot.
     *
     * @param executablePath path to the program to run (absolute, or resolvable on {@code PATH})
     * @param args           optional command-line arguments
     * @throws IllegalArgumentException if {@code executablePath} is null/blank
     * @throws RuntimeException         if the process could not be started
     */
    public static void launch(String executablePath, String... args) {
        if (executablePath == null || executablePath.isBlank()) {
            throw new IllegalArgumentException("executablePath must not be empty");
        }
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(executablePath);
        if (args != null) {
            for (String arg : args) {
                if (arg != null) command.add(arg);
            }
        }
        try {
            // Log the command before running it: a launch that "does nothing" is otherwise invisible, since a
            // detached process gives no feedback. This line makes the attempt show up in the Studio console.
            Debug.log("[Game] launch: " + String.join(" ", command));
            new ProcessBuilder(command).start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to launch '" + executablePath + "': " + e.getMessage(), e);
        }
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
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId must not be empty");
        }
        String id = appId.trim();
        String uri = "steam://rungameid/" + id;
        // Log before invoking: if Steam doesn't come up, the console still shows the URI/CLI we tried, so a
        // silent failure (e.g. no registered steam:// handler) is diagnosable instead of "nothing happened".
        Debug.log("[Game] launchSteam " + id + " → " + uri);
        if (UriLauncher.open(uri)) {
            Debug.log("[Game] launchSteam: opener invoked for " + uri);
            return;
        }
        // Fallback: the Steam CLI (requires `steam` on PATH).
        Debug.log("[Game] launchSteam: opener declined " + uri + ", falling back to `steam -applaunch " + id + "`");
        try {
            new ProcessBuilder("steam", "-applaunch", id).start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to launch Steam game '" + id
                    + "'. Is Steam installed? " + e.getMessage(), e);
        }
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
        if (appName == null || appName.isBlank()) {
            throw new IllegalArgumentException("appName must not be empty");
        }
        String id = appName.trim();
        String uri = "com.epicgames.launcher://apps/" + id + "?action=launch&silent=true";
        // Log before invoking: if the launcher doesn't come up, the console still shows the URI we tried, so a
        // silent failure (e.g. no registered com.epicgames.launcher:// handler) is diagnosable.
        Debug.log("[Game] launchEpic " + id + " → " + uri);
        if (UriLauncher.open(uri)) {
            Debug.log("[Game] launchEpic: opener invoked for " + uri);
            return;
        }
        throw new RuntimeException("Failed to launch Epic game '" + id
                + "'. Is the Epic Games Launcher installed?");
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
        if (appName == null || appName.isBlank()) {
            throw new IllegalArgumentException("appName must not be empty");
        }
        String id = appName.trim();
        String uri = "heroic://launch/" + id;
        // Log before invoking: if Heroic doesn't come up, the console still shows the URI/CLI we tried, so a
        // silent failure (e.g. no registered heroic:// handler) is diagnosable instead of "nothing happened".
        Debug.log("[Game] launchHeroic " + id + " → " + uri);
        if (UriLauncher.open(uri)) {
            Debug.log("[Game] launchHeroic: opener invoked for " + uri);
            return;
        }
        // Fallbacks: Heroic's CLI, first on PATH, then as a Flatpak (its most common Linux install form).
        Debug.log("[Game] launchHeroic: opener declined " + uri + ", falling back to the Heroic CLI");
        if (tryStart("heroic", "--no-gui", "launch", id)) {
            return;
        }
        if (tryStart("flatpak", "run", "com.heroicgameslauncher.hgl", "--no-gui", "launch", id)) {
            return;
        }
        throw new RuntimeException("Failed to launch Heroic game '" + id
                + "'. Is the Heroic Games Launcher installed?");
    }

    /** Best-effort {@link ProcessBuilder#start()}; logs and returns false rather than throwing on failure. */
    private static boolean tryStart(String... command) {
        try {
            new ProcessBuilder(command).start();
            Debug.log("[Game] ran: " + String.join(" ", command));
            return true;
        } catch (Exception e) {
            Debug.log("[Game] command failed (" + command[0] + "): " + e.getMessage());
            return false;
        }
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
        if (processName == null || processName.isBlank()) {
            throw new IllegalArgumentException("processName must not be empty");
        }
        String name = processName.trim();
        String[] command = isWindows()
                ? new String[]{"taskkill", "/F", "/IM", name}
                : new String[]{"pkill", "-f", name};
        Debug.log("[Game] kill " + name + " → " + String.join(" ", command));
        try {
            int code = new ProcessBuilder(command).inheritIO().start().waitFor();
            // taskkill=128 / pkill=1 both mean "no matching process" — expected, not a failure.
            Debug.log("[Game] kill " + name + ": exit " + code
                    + (code == 0 ? " (terminated)" : " (nothing to kill / already gone)"));
        } catch (Exception e) {
            Debug.log("[Game] kill " + name + " failed to invoke: " + e.getMessage());
        }
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
        if (processName == null || processName.isBlank()) {
            throw new IllegalArgumentException("processName must not be empty");
        }
        String name = processName.trim();
        try {
            if (isWindows()) {
                Process p = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq " + name).start();
                String out = new String(p.getInputStream().readAllBytes());
                p.waitFor();
                return out.toLowerCase().contains(name.toLowerCase());
            }
            // `--` so a name starting with '-' isn't read as a flag. pgrep -f matches whole command lines,
            // which includes the bot's own JVM when the name appears in its arguments — so read the pids and
            // discard our own rather than trusting the exit code.
            Process p = new ProcessBuilder("pgrep", "-f", "--", name).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            long self = ProcessHandle.current().pid();
            for (String line : out.split("\\R")) {
                String pid = line.trim();
                if (!pid.isEmpty() && Long.parseLong(pid) != self) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Debug.log("[Game] isRunning(" + name + ") check failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
