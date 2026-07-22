package com.botmaker.sdk.api.launch;

import com.botmaker.sdk.api.Debug;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.emulator.WindowsRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The observations behind {@link LaunchTarget#isRunning()} — each one a thing the OS actually reports, never a
 * timer or a cooldown. They are kept out of {@link LaunchTarget} itself because they are plumbing (process
 * tables, a registry key, a VDF file) rather than part of the launch contract.
 *
 * <p>Every method is best-effort and total: an unreadable process table, a missing registry key or an absent
 * window backend answers "no evidence" rather than throwing, because the caller's fallback — launching — is
 * always safe.
 */
final class RunningProbe {

    /**
     * Processes {@link LaunchTarget#start()} actually spawned, keyed by {@link LaunchTarget#spec()}. Only
     * meaningful for the variants whose spawned process <em>is</em> the target ({@code exe:}/{@code cli:}); a
     * {@code steam://} opener or {@code faugus-launcher} hands off and exits within a second, so those
     * deliberately never record.
     */
    private static final Map<String, ProcessHandle> SPAWNED = new ConcurrentHashMap<>();

    /** Steam writes the app id it is currently running here, as a DWORD (so {@code reg query} prints hex). */
    private static final String STEAM_KEY = "HKCU\\Software\\Valve\\Steam";
    private static final String RUNNING_APP_ID = "RunningAppID";
    private static final Pattern VDF_RUNNING_APP_ID =
            Pattern.compile("\"RunningAppID\"\\s+\"(\\d+)\"");

    private RunningProbe() {}

    /** Remembers {@code process} as the live incarnation of {@code spec}. A null process clears the entry. */
    static void record(String spec, Process process) {
        if (spec == null) return;
        if (process == null) {
            SPAWNED.remove(spec);
            return;
        }
        SPAWNED.put(spec, process.toHandle());
    }

    /** True when a process this bot started for {@code spec} is still alive. */
    static boolean spawnedAlive(String spec) {
        ProcessHandle handle = spec == null ? null : SPAWNED.get(spec);
        if (handle == null) {
            return false;
        }
        if (handle.isAlive()) {
            return true;
        }
        SPAWNED.remove(spec);
        return false;
    }

    /**
     * True when any live process other than this JVM mentions {@code token} in its command line.
     *
     * <p>This is the primary layer precisely <em>because</em> it matches wrappers: a Steam game runs under
     * {@code reaper SteamLaunch AppId=<id> -- … proton …}, a Heroic one under {@code legendary launch <appName>},
     * a Faugus one under {@code umu-run} with the game id in its environment-carrying argv. The token is the
     * target's own launch identity, so whichever layer of wrapping is on top, one of them still carries it.
     *
     * <p>Caveat: the JDK exposes {@link ProcessHandle.Info#commandLine()} only for processes the current user can
     * inspect — on Windows that means same-user processes, which is the case for a game the user launched.
     */
    static boolean commandLineMentions(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String needle = token.trim().toLowerCase();
        long self = ProcessHandle.current().pid();
        try {
            return ProcessHandle.allProcesses()
                    .filter(p -> p.pid() != self)
                    .map(p -> p.info().commandLine())
                    .anyMatch(cmd -> cmd.map(c -> c.toLowerCase().contains(needle)).orElse(false));
        } catch (Exception e) {
            Debug.log("[Target] process scan for '" + token + "' failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * True when a window titled after {@code token} is open — asked of the OS through
     * {@code NativeController.getAllWindows()}, <em>not</em> of the ambient capture source, so it answers whatever
     * the project happens to capture (the desktop, a monitor, an emulator).
     */
    static boolean windowTitled(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String needle = token.trim().toLowerCase();
        try {
            for (GenericWindow w : NativeControllerFactory.get().getAllWindows()) {
                String title = w.getTitle();
                if (title != null && title.toLowerCase().contains(needle)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Includes the UnsupportedOperationException macOS's absent backend throws.
            Debug.log("[Target] window scan for '" + token + "' failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * True when Steam itself reports {@code appId} as the app it is running — the one authority that is observed
     * rather than inferred. Absent/unreadable, or a different app, both answer {@code false} so the caller falls
     * through to the other layers (the key is not written for every launch path, e.g. non-Steam shortcuts).
     */
    static boolean steamReportsRunning(String appId) {
        if (appId == null || appId.isBlank()) {
            return false;
        }
        String running = readSteamRunningAppId();
        return running != null && running.equals(appId.trim());
    }

    /** Steam's currently-running app id as a decimal string, or {@code null} when it can't be read. */
    private static String readSteamRunningAppId() {
        String fromRegistry = WindowsRegistry.read(STEAM_KEY, RUNNING_APP_ID);
        if (fromRegistry != null && !fromRegistry.isBlank()) {
            return decimal(fromRegistry);
        }
        return fromSteamVdf();
    }

    /** Linux: Steam mirrors the same value into {@code ~/.steam/registry.vdf}. */
    private static String fromSteamVdf() {
        for (String candidate : new String[]{".steam/registry.vdf", ".steam/steam/registry.vdf"}) {
            Path vdf = Path.of(System.getProperty("user.home", ""), candidate);
            try {
                if (!Files.isReadable(vdf)) {
                    continue;
                }
                Matcher m = VDF_RUNNING_APP_ID.matcher(Files.readString(vdf, StandardCharsets.UTF_8));
                if (m.find()) {
                    return m.group(1);
                }
            } catch (Exception e) {
                Debug.log("[Target] reading " + vdf + " failed: " + e.getMessage());
            }
        }
        return null;
    }

    /** {@code reg query} prints a DWORD as {@code 0x23a}; normalise both forms to a decimal string. */
    private static String decimal(String raw) {
        String v = raw.trim();
        try {
            return v.toLowerCase().startsWith("0x")
                    ? Long.toString(Long.parseLong(v.substring(2), 16))
                    : Long.toString(Long.parseLong(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Test seam: forgets every recorded spawn. */
    static void clearSpawned() {
        SPAWNED.clear();
    }

    /** The live handle recorded for {@code spec}, if any — exposed for tests. */
    static Optional<ProcessHandle> spawned(String spec) {
        return Optional.ofNullable(SPAWNED.get(spec));
    }
}
