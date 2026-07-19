package com.botmaker.sdk.api.launch;
import com.botmaker.sdk.api.Debug;

import com.botmaker.sdk.api.emulator.Emulator;
import com.botmaker.sdk.api.emulator.EmulatorRef;
import com.botmaker.sdk.api.emulator.Emulators;
import com.botmaker.sdk.api.interaction.Wait;

import java.util.Optional;

/**
 * A parsed, launchable description of <em>what the bot automates</em> — the value behind the ambient
 * {@link Target} holder. One of four things a Studio game picker can produce: a Steam game, an Epic game, a
 * plain executable, or an app running inside a named Android emulator.
 *
 * <p>Persisted as a single {@code launch.target} string in {@code botmaker-project.properties} (see
 * {@code ProjectDefaults}) using the {@link #spec()} form:
 * <pre>
 *   steam:&lt;appId&gt;
 *   epic:&lt;appName&gt;
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
     * opened by hand isn't relaunched. Defaults to {@link #start()} (for targets where re-launching an already
     * running game is a harmless focus, e.g. Steam/Epic); variants that can cheaply detect "already running"
     * override this to skip.
     */
    default void startIfNotRunning() {
        start();
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

        @Override
        public String spec() {
            return "epic:" + appName;
        }
    }

    /** A plain executable, launched directly (see {@link Game#launch(String, String...)}). */
    record Exe(String path) implements LaunchTarget {
        @Override
        public void start() {
            Game.launch(path);
        }

        @Override
        public void startIfNotRunning() {
            String name = processName(path);
            if (name != null && Game.isRunning(name)) {
                Debug.log("[Target] exe '" + name + "' already running — skipping cold launch");
                return;
            }
            start();
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
