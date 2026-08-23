package com.botmaker.sdk.api.launch;

import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.Launcher;

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
 * <p>The spec grammar, the launching and the host-side running probes all live in
 * {@code shared.launch} ({@link LaunchSpec} / {@link Launcher}), so Studio can launch and describe a target
 * without depending on the SDK. What stays here is what genuinely needs SDK types: the sealed, exhaustively
 * switchable hierarchy a bot's generated code names, and the one running-detection layer shared cannot see —
 * the ambient {@link Source#current() capture source}'s own window.
 */
public sealed interface LaunchTarget {

    /** The parsed spec this target wraps — the value {@code shared.launch} operates on. */
    LaunchSpec launchSpec();

    /** Brings the target up (launches the game/app). Best-effort — logs rather than throwing on failure. */
    default void start() {
        Launcher.startQuietly(launchSpec());
    }

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
     * "we launched it recently so it must be running".
     *
     * <p>The first layer is this module's alone: the ambient {@link Source#current() capture source}'s window,
     * when it has a window identity at all — the cheapest answer, and by definition the surface the bot
     * automates. Everything after it is host-observable and therefore lives in {@link Launcher#isRunning} (a
     * spawned process still alive, a wrapper process naming the launch token, a window titled after it), so a
     * Studio quick-launch and a running bot cannot disagree about what "running" means.
     *
     * <p>This layering replaced a probe that asked <em>only</em> the capture source: when the project captures
     * the desktop or a monitor, {@link CaptureSource#hasWindowIdentity()} is false, so the answer was an
     * unconditional "not running" and every Steam/Epic/Heroic/Faugus target relaunched on every run.
     */
    default boolean isRunning() {
        LaunchSpec spec = launchSpec();
        if (targetWindowOpen(spec.spec())) {
            return true;
        }
        return Launcher.isRunning(spec);
    }

    /**
     * The distinctive string a live incarnation of this target carries — in a process command line, and often in
     * a window title. Not the {@link #spec()}: that is our own encoding, whereas this is the launcher's own
     * launch identity ({@code AppId=570}, an Epic/Heroic {@code AppName}, an executable's file name).
     * {@code null} when the variant has no such token and answers {@link #isRunning()} another way.
     */
    default String runningToken() {
        return launchSpec().runningToken();
    }

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

    /** Restarts the target from a clean state — force-stopping it first for the variants that can be stopped. */
    default void restart() {
        Launcher.restart(launchSpec());
    }

    /** The canonical {@code launch.target} string this target round-trips to. */
    default String spec() {
        return launchSpec().spec();
    }

    /**
     * Parses a {@code launch.target} spec (see the class javadoc) into a target, or {@code null} when the spec
     * is null/blank or its kind is unrecognised. Never throws — an unparseable target simply yields {@code null}
     * so the holder falls back to "no target".
     */
    static LaunchTarget parse(String spec) {
        LaunchSpec parsed = LaunchSpec.parse(spec);
        if (parsed == null) {
            return null;
        }
        return switch (parsed.kind()) {
            case STEAM -> new Steam(parsed.token());
            case EPIC -> new Epic(parsed.token());
            case HEROIC -> new Heroic(parsed.token());
            case FAUGUS -> new Faugus(parsed.token());
            case CLI -> new Cli(parsed.token());
            case EXE -> new Exe(parsed.token());
            case EMULATOR_APP -> emulatorApp(parsed);
            // A kind this build doesn't model isn't launchable, so it is no target at all — the caller's
            // "no target" fallback is the honest answer, not a target that silently does nothing.
            case UNKNOWN -> null;
        };
    }

    /** {@code <package>@<instance>} — {@link LaunchSpec} splits on the last {@code @} so package dots survive. */
    private static LaunchTarget emulatorApp(LaunchSpec parsed) {
        String pkg = parsed.emulatorPackage();
        String instance = parsed.emulatorInstance();
        return pkg == null || instance == null ? null : new EmulatorApp(pkg, instance);
    }

    /** A Steam game, launched by its numeric appId (see {@link Game#launchSteam(String)}). */
    record Steam(String appId) implements LaunchTarget {
        @Override
        public LaunchSpec launchSpec() {
            return new LaunchSpec(LaunchKind.STEAM, appId);
        }
    }

    /** An Epic Games title, launched by its Epic {@code AppName} (see {@link Game#launchEpic(String)}). */
    record Epic(String appName) implements LaunchTarget {
        @Override
        public LaunchSpec launchSpec() {
            return new LaunchSpec(LaunchKind.EPIC, appName);
        }
    }

    /**
     * A Heroic Games Launcher title, launched by its Heroic {@code AppName} (see {@link Game#launchHeroic(String)}).
     * The practical way to run Epic/GOG games on Linux.
     */
    record Heroic(String appName) implements LaunchTarget {
        @Override
        public LaunchSpec launchSpec() {
            return new LaunchSpec(LaunchKind.HEROIC, appName);
        }
    }

    /**
     * A <a href="https://faugus.github.io/">Faugus Launcher</a> entry, launched by its {@code gameid} (see
     * {@link Game#launchFaugus(String)}) — how non-Steam Windows launchers and games run under umu/Proton.
     */
    record Faugus(String gameId) implements LaunchTarget {
        @Override
        public LaunchSpec launchSpec() {
            return new LaunchSpec(LaunchKind.FAUGUS, gameId);
        }
    }

    /**
     * An arbitrary command line, run as an external process — the escape hatch for any launcher we don't model
     * directly (a custom script, {@code legendary}, {@code lutris}, a Flatpak invocation, …). The command is
     * split on whitespace into an executable + arguments.
     */
    record Cli(String commandLine) implements LaunchTarget {
        @Override
        public LaunchSpec launchSpec() {
            return new LaunchSpec(LaunchKind.CLI, commandLine);
        }
    }

    /** A plain executable, launched directly (see {@link Game#launch(String, String...)}). */
    record Exe(String path) implements LaunchTarget {
        @Override
        public LaunchSpec launchSpec() {
            return new LaunchSpec(LaunchKind.EXE, path);
        }
    }

    /**
     * An app ({@code packageName}) inside the named Android emulator {@code instance}. Starting it makes sure the
     * instance is running (launching + waiting when it isn't), connects over ADB, and starts the app's launcher
     * activity. The capture side is handled independently by the {@code emulator:<instance>} capture source, so
     * this only has to bring the app to the foreground.
     */
    record EmulatorApp(String packageName, String instance) implements LaunchTarget {
        @Override
        public LaunchSpec launchSpec() {
            return new LaunchSpec(LaunchKind.EMULATOR_APP, packageName + "@" + instance);
        }

        /**
         * Asked over ADB, the same channel this variant's capture path uses — so the capture-source layer the
         * default adds on top is skipped: an app running <em>inside</em> an emulator is not the emulator window
         * being open, and treating it as such would report every app as running.
         */
        @Override
        public boolean isRunning() {
            return Launcher.isRunning(launchSpec());
        }
    }
}
