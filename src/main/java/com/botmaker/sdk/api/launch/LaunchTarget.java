package com.botmaker.sdk.api.launch;
import com.botmaker.sdk.api.Debug;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.emulator.Emulator;
import com.botmaker.sdk.api.emulator.EmulatorRef;
import com.botmaker.sdk.api.emulator.Emulators;
import com.botmaker.sdk.api.interaction.Wait;

import java.util.Optional;

/**
 * A parsed, launchable description of <em>what the bot automates</em> — the value behind the ambient
 * {@link Target} holder. One of the things a Studio game picker can produce: a game in a launcher's library
 * (Steam, Epic, Heroic, Faugus), a plain executable or command line, or an app running inside a named Android
 * emulator.
 *
 * <p>Persisted as a single {@code launch.target} string in {@code botmaker-project.properties} (see
 * {@code ProjectDefaults}) using the {@link #spec()} form:
 * <pre>
 *   steam:&lt;appId&gt;
 *   epic:&lt;appName&gt;
 *   heroic:&lt;appName&gt;
 *   faugus:&lt;gameId&gt;
 *   cli:&lt;command line&gt;
 *   exe:&lt;path&gt;
 *   emu-app:&lt;package&gt;@&lt;instanceName&gt;
 * </pre>
 *
 * <p>Each variant knows how to {@link #start()} itself by delegating to {@link Game} / {@link Emulators}; the
 * generated {@code Startup.run()} of a game-bot simply calls {@code Target.start()}.
 */
public sealed interface LaunchTarget {

    /** Brings the target up (launches the game/app). Best-effort — logs rather than throwing on failure. */
    void start();

    /**
     * Brings the target up only if it isn't already running — the cold-start path, so a game the user already
     * opened by hand isn't relaunched.
     */
    default void startIfNotRunning() {
        if (isRunning()) {
            return;
        }
        start();
    }

    /**
     * Whether the target is up right now, decided by layered <em>observation</em> — no timers, no cooldown, no
     * "we launched it recently so it must be running". Each layer answers from something the OS actually
     * reports, and the first "yes" wins:
     *
     * <ol>
     *   <li>the ambient {@link Source#current() capture source}'s window, when it has a window identity at all —
     *       the cheapest answer, and the one that is by definition the surface the bot automates;</li>
     *   <li>a process this bot itself spawned for this {@link #spec()} still being alive (only ever recorded by
     *       {@link Exe}/{@link Cli}, whose spawned process really is the target);</li>
     *   <li>any live process whose <em>command line</em> mentions this target's {@link #runningToken() token} —
     *       deliberately matching the wrapper (Steam's {@code reaper}, {@code proton}, {@code umu-run},
     *       {@code legendary}), since that is what a launcher-started game actually runs as;</li>
     *   <li>a window titled after the token, enumerated from the OS rather than from the capture source.</li>
     * </ol>
     *
     * <p>This replaced a probe that asked only the capture source: when the project captures the desktop or a
     * monitor, {@link CaptureSource#hasWindowIdentity()} is false, so the answer was an unconditional "not
     * running" and every Steam/Epic/Heroic/Faugus target relaunched on every run.
     *
     * <p>Known gap, accepted knowingly: for the ~second between {@link #start()} handing off to a launcher and
     * the wrapper process appearing, every layer is legitimately false, so a bot calling this in a tight loop
     * could launch twice. {@link Game#launchAndWait} — which blocks on the window appearing — is the answer for
     * that, and the {@code [Target]} traces make it visible when it happens.
     */
    default boolean isRunning() {
        if (targetWindowOpen(spec())) {
            return true;
        }
        if (RunningProbe.spawnedAlive(spec())) {
            Debug.log("[Target] " + spec() + ": the process we launched is still alive");
            return true;
        }
        String token = runningToken();
        if (token == null || token.isBlank()) {
            return false;
        }
        if (RunningProbe.commandLineMentions(token)) {
            Debug.log("[Target] " + spec() + ": a live process mentions '" + token + "'");
            return true;
        }
        if (RunningProbe.windowTitled(token)) {
            Debug.log("[Target] " + spec() + ": a window is titled after '" + token + "'");
            return true;
        }
        return false;
    }

    /**
     * The distinctive string a live incarnation of this target carries — in a process command line, and often in
     * a window title. Not the {@link #spec()}: that is our own encoding, whereas this is the launcher's own
     * launch identity ({@code AppId=570}, an Epic/Heroic {@code AppName}, an executable's file name).
     * {@code null} when the variant has no such token and answers {@link #isRunning()} another way.
     */
    String runningToken();

    /**
     * True when the ambient capture source is a window source that is open right now — i.e. the target is
     * already up. False when it is absent <em>or</em> when the source can't answer the question at all.
     */
    private static boolean targetWindowOpen(String spec) {
        CaptureSource source = Source.current();
        if (source == null || !source.hasWindowIdentity()) {
            return false;
        }
        if (!source.isPresent()) {
            return false;
        }
        Debug.log("[Target] " + spec + ": target window already open — skipping cold launch");
        return true;
    }

    /** Restarts the target from a clean state. Defaults to {@link #start()}; only some variants can force-stop. */
    default void restart() {
        start();
    }

    /** The canonical {@code launch.target} string this target round-trips to. */
    String spec();

    /**
     * Parses a {@code launch.target} spec (see the class javadoc) into a target, or {@code null} when the spec
     * is null/blank or its kind is unrecognised. Never throws — an unparseable target simply yields {@code null}
     * so the holder falls back to "no target".
     */
    static LaunchTarget parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String trimmed = spec.trim();
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) {
            return null;
        }
        String kind = trimmed.substring(0, colon).trim().toLowerCase();
        String rest = trimmed.substring(colon + 1).trim();
        if (rest.isEmpty()) {
            return null;
        }
        return switch (kind) {
            case "steam" -> new Steam(rest);
            case "epic" -> new Epic(rest);
            case "heroic" -> new Heroic(rest);
            case "faugus" -> new Faugus(rest);
            case "cli" -> new Cli(rest);
            case "exe" -> new Exe(rest);
            case "emu-app" -> parseEmulatorApp(rest);
            default -> null;
        };
    }

    /** {@code <package>@<instance>} — split on the <em>last</em> {@code @} so a package's dots are preserved. */
    private static LaunchTarget parseEmulatorApp(String rest) {
        int at = rest.lastIndexOf('@');
        if (at <= 0 || at == rest.length() - 1) {
            return null;
        }
        return new EmulatorApp(rest.substring(0, at).trim(), rest.substring(at + 1).trim());
    }

    /** A Steam game, launched by its numeric appId (see {@link Game#launchSteam(String)}). */
    record Steam(String appId) implements LaunchTarget {
        @Override
        public void start() {
            Game.launchSteam(appId);
        }

        /**
         * Steam publishes the app id it is currently running, so ask it before guessing — then fall through to
         * the shared layers, since that key is not written for every launch path (non-Steam shortcuts).
         */
        @Override
        public boolean isRunning() {
            if (RunningProbe.steamReportsRunning(appId)) {
                Debug.log("[Target] steam:" + appId + ": Steam reports it as the running app");
                return true;
            }
            return LaunchTarget.super.isRunning();
        }

        /**
         * Not the bare id — a number that short would match any command line by accident. Steam's own launch
         * wrapper spells it {@code reaper SteamLaunch AppId=<id> --}, which is unambiguous.
         */
        @Override
        public String runningToken() {
            return "AppId=" + appId;
        }

        @Override
        public String spec() {
            return "steam:" + appId;
        }
    }

    /** An Epic Games title, launched by its Epic {@code AppName} (see {@link Game#launchEpic(String)}). */
    record Epic(String appName) implements LaunchTarget {
        @Override
        public void start() {
            Game.launchEpic(appName);
        }

        /** The Epic {@code AppName} is what the launcher passes down to the game's own command line. */
        @Override
        public String runningToken() {
            return appName;
        }

        @Override
        public String spec() {
            return "epic:" + appName;
        }
    }

    /**
     * A Heroic Games Launcher title, launched by its Heroic {@code AppName} (see {@link Game#launchHeroic(String)}).
     * The practical way to run Epic/GOG games on Linux.
     */
    record Heroic(String appName) implements LaunchTarget {
        @Override
        public void start() {
            Game.launchHeroic(appName);
        }

        /** Heroic hands the {@code AppName} to {@code legendary}/{@code gogdl}, which keeps it in its argv. */
        @Override
        public String runningToken() {
            return appName;
        }

        @Override
        public String spec() {
            return "heroic:" + appName;
        }
    }

    /**
     * A <a href="https://faugus.github.io/">Faugus Launcher</a> entry, launched by its {@code gameid} (see
     * {@link Game#launchFaugus(String)}) — how non-Steam Windows launchers and games run under umu/Proton.
     */
    record Faugus(String gameId) implements LaunchTarget {
        @Override
        public void start() {
            Game.launchFaugus(gameId);
        }

        /** Faugus builds the umu/Proton command line around the {@code gameid} it was asked for. */
        @Override
        public String runningToken() {
            return gameId;
        }

        @Override
        public String spec() {
            return "faugus:" + gameId;
        }
    }

    /**
     * An arbitrary command line, run as an external process — the escape hatch for any launcher we don't model
     * directly (a custom script, {@code legendary}, {@code lutris}, a Flatpak invocation, …). The command is
     * split on whitespace into an executable + arguments and handed to {@link Game#launch(String, String...)}.
     */
    record Cli(String commandLine) implements LaunchTarget {
        @Override
        public void start() {
            String[] parts = tokens();
            if (parts.length == 0) {
                Debug.log("[Target] cli: empty command — nothing to launch");
                return;
            }
            String[] args = new String[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, args.length);
            // The command we run *is* the target (no launcher hand-off), so the handle stays worth keeping.
            RunningProbe.record(spec(), Game.launch(parts[0], args));
        }

        /** The executable's own name — this variant's process really does run under it. */
        @Override
        public String runningToken() {
            return processName();
        }

        @Override
        public void restart() {
            String name = processName();
            if (name != null) {
                Game.kill(name);
            }
            start();
        }

        @Override
        public String spec() {
            return "cli:" + commandLine;
        }

        /** The command split on whitespace: executable first, then arguments. */
        private String[] tokens() {
            if (commandLine == null || commandLine.isBlank()) {
                return new String[0];
            }
            return commandLine.trim().split("\\s+");
        }

        /** The executable's file name — the process/image name {@link Game#kill}/{@link Game#isRunning} match. */
        private String processName() {
            String[] parts = tokens();
            if (parts.length == 0) {
                return null;
            }
            String exe = parts[0];
            int slash = Math.max(exe.lastIndexOf('/'), exe.lastIndexOf('\\'));
            String name = (slash >= 0 && slash < exe.length() - 1) ? exe.substring(slash + 1) : exe;
            return name.isBlank() ? null : name;
        }
    }

    /** A plain executable, launched directly (see {@link Game#launch(String, String...)}). */
    record Exe(String path) implements LaunchTarget {
        @Override
        public void start() {
            // The process we spawn *is* the target, so keep its handle as a first-hand "still running" answer.
            RunningProbe.record(spec(), Game.launch(path));
        }

        /** The executable's own name — this variant's process really does run under it. */
        @Override
        public String runningToken() {
            return processName(path);
        }

        @Override
        public void restart() {
            // A frozen exe won't exit on its own: force-stop it by process name, then relaunch.
            String name = processName(path);
            if (name != null) {
                Game.kill(name);
            }
            start();
        }

        @Override
        public String spec() {
            return "exe:" + path;
        }

        /** The executable's file name (the process/image name {@link Game#kill}/{@link Game#isRunning} match). */
        private static String processName(String path) {
            if (path == null || path.isBlank()) {
                return null;
            }
            int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            String name = (slash >= 0 && slash < path.length() - 1) ? path.substring(slash + 1) : path;
            return name.isBlank() ? null : name;
        }
    }

    /**
     * An app ({@code packageName}) inside the named Android emulator {@code instance}. Starting it makes sure the
     * instance is running (launching + waiting when it isn't), connects over ADB, and starts the app's launcher
     * activity. The capture side is handled independently by the {@code emulator:<instance>} capture source, so
     * this only has to bring the app to the foreground.
     */
    record EmulatorApp(String packageName, String instance) implements LaunchTarget {

        /** How long to wait for a just-launched emulator instance to come up before giving up. */
        private static final long BOOT_TIMEOUT_MS = 120_000;

        @Override
        public void start() {
            withRunningEmulator(emu -> emu.startApp(packageName));
        }

        @Override
        public void restart() {
            withRunningEmulator(emu -> {
                emu.stopApp(packageName);
                emu.startApp(packageName);
            });
        }

        /**
         * Asked over ADB, the same channel this variant's capture path uses: the instance must be up and the
         * app must be the one in the foreground. Nothing on the host process table describes an app running
         * <em>inside</em> an emulator, so the generic layers cannot answer this one.
         */
        @Override
        public boolean isRunning() {
            Optional<EmulatorRef> match = Emulators.listAll().stream()
                    .filter(ref -> instance.equals(ref.name()))
                    .findFirst();
            if (match.isEmpty() || !match.get().running()) {
                return false;
            }
            Emulator emu = null;
            try {
                emu = match.get().connect();
                String current = emu.currentApp();
                return current != null && current.contains(packageName);
            } catch (Exception e) {
                Debug.log("[Target] emu-app: probing " + instance + " failed: " + e.getMessage());
                return false;
            } finally {
                if (emu != null) {
                    emu.disconnect();
                }
            }
        }

        /** Handled by {@link #isRunning()} over ADB; there is no host-side token to match. */
        @Override
        public String runningToken() {
            return null;
        }

        @Override
        public String spec() {
            return "emu-app:" + packageName + "@" + instance;
        }

        /**
         * Resolves the named instance, ensuring it is running (launch + wait), connects, hands the live
         * {@link Emulator} to {@code action}, then disconnects. No-op (logged) when the instance can't be found
         * or brought up — the supervisor will try again next recovery.
         */
        private void withRunningEmulator(java.util.function.Consumer<Emulator> action) {
            Optional<EmulatorRef> match = Emulators.listAll().stream()
                    .filter(ref -> instance.equals(ref.name()))
                    .findFirst();
            if (match.isEmpty()) {
                Debug.log("[Target] emu-app: no emulator instance named '" + instance + "'");
                return;
            }
            EmulatorRef ref = match.get();
            if (!awaitRunning(ref)) {
                Debug.log("[Target] emu-app: instance '" + instance + "' did not come up");
                return;
            }
            Emulator emu = null;
            try {
                emu = ref.connect();
                action.accept(emu);
            } catch (Exception e) {
                Debug.log("[Target] emu-app: " + instance + " failed: " + e.getMessage());
            } finally {
                if (emu != null) {
                    emu.disconnect();
                }
            }
        }

        /** True once {@code ref} answers on ADB, launching it (once) and polling up to {@link #BOOT_TIMEOUT_MS}. */
        private boolean awaitRunning(EmulatorRef ref) {
            if (ref.running()) {
                return true;
            }
            Debug.log("[Target] emu-app: launching emulator instance '" + instance + "'");
            ref.launch();
            long deadline = System.currentTimeMillis() + BOOT_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                if (ref.running()) {
                    return true;
                }
                Wait.seconds(2);
            }
            return ref.running();
        }
    }
}
