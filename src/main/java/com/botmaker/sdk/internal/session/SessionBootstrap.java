package com.botmaker.sdk.internal.session;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.bot.Session;
import com.botmaker.sdk.internal.config.ProjectDefaults;
import com.botmaker.session.launch.LaunchIsolation;
import com.botmaker.shared.platform.Os;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.session.DesktopSession;
import com.botmaker.session.PrivateSession;
import com.botmaker.session.SessionBackend;
import com.botmaker.session.SessionHealth;
import com.botmaker.session.SessionOptions;
import com.botmaker.session.SessionStartException;
import com.botmaker.session.Sessions;
import com.botmaker.session.VmOptions;
import com.botmaker.shared.vm.GuestLaunch;
import com.botmaker.shared.vm.VmInventory;
import com.botmaker.shared.vm.VmRecord;
import com.botmaker.session.display.BackendInstall;
import com.botmaker.session.display.SessionBackends;

import java.util.List;
import java.util.Optional;

/**
 * The bot-runtime producer: the one place that, for an <em>isolated</em> bot, brings up a private nested
 * {@code :N} display, registers it with {@link BotSession} (so {@code Mouse}/{@code Keyboard}/{@code Source}
 * all follow it), and launches the target into it. The pilot's equivalent is Studio's {@code NestedSessionLauncher};
 * this is its bot-process twin, reached from the generated bot's {@code Target.start()}.
 *
 * <p><b>A session it is handed beats a session it builds.</b> When the process that spawned this bot already owns
 * a private display with the target up — Studio's background launcher does, after "▶ Launch now" — it passes it
 * through {@link Sessions#handoffArguments} and the bot joins it ({@link Sessions#offered()}) instead of bringing up
 * a second one.
 * Without that, the second bring-up would hand its launch to the copy already running (every store launcher is
 * single-instance) and the game would appear on a display nobody is watching.
 *
 * <p><b>On by default, with an opt-out.</b> Where the game runs resolves through one ladder, highest first: an
 * explicit {@link Session} call in bot code → the {@code botmaker.session.where} run property →
 * {@code BOTMAKER_SESSION_WHERE} → the bot's settings ({@code BotSettings.Where}) → a private display. Bot code
 * sits at the top so a bot can force its own behaviour on a machine whose environment disagrees. On the desktop,
 * {@link #launchIsolated} returns {@code false} and the caller runs its normal global {@code :0} launch. The
 * backend is gamescope for everything but an emulator app ({@link SessionBackends#preferredBackend}), with the
 * settings' display backend and {@code botmaker.session.backend} as explicit overrides.
 *
 * <p><b>A missing backend stops the run.</b> It used to log an install hint and run the game on the desktop,
 * which was the opposite of what the user asked for and easy to miss in a log. Studio's ⚙ Bot Settings and
 * ▶ Launch now offer to install it; a bot run says how and stops.
 */
public final class SessionBootstrap {

    /** The run property that says where the game runs: a {@code BotSettings.Where} id. Outranks the settings. */
    public static final String WHERE_PROPERTY = "botmaker.session.where";
    /** The environment form of {@link #WHERE_PROPERTY}, for a bot started from a shell or a service unit. */
    public static final String WHERE_ENV = "BOTMAKER_SESSION_WHERE";
    /** The run property naming the game VM, for {@code BotSettings.Where.VM}: a fact about this computer. */
    public static final String VM_PROPERTY = "botmaker.session.vm";
    /** The environment form of {@link #VM_PROPERTY}. */
    public static final String VM_ENV = "BOTMAKER_SESSION_VM";
    /**
     * System property that <em>overrides</em> the default backend when isolated: {@code gamescope} for 3D,
     * {@code xephyr} for 2D. When unset the backend is {@link SessionBackends#preferredBackend(LaunchSpec)},
     * which is gamescope for every launch kind.
     */
    public static final String BACKEND_PROPERTY = "botmaker.session.backend";

    /**
     * Nested display size used when the project has no authored resolution — single-sourced in
     * {@link SessionBackends}, which Studio's launcher answers from too.
     */
    public static final int DEFAULT_WIDTH = SessionBackends.DEFAULT_WIDTH;
    public static final int DEFAULT_HEIGHT = SessionBackends.DEFAULT_HEIGHT;

    private SessionBootstrap() {}

    /**
     * Whether this bot's game runs off the desktop: on a private display (Linux) or in a game VM (Windows), as
     * {@link #where()} resolves it. <b>Default: its settings' {@code BotSettings.Where}, which itself defaults to
     * a private display</b>.
     */
    public static boolean isolationRequested() {
        return isolatedOn(where(), Os.current());
    }

    /**
     * Whether {@code where} keeps the game off the desktop on {@code os}: a private display on Linux, a game VM on
     * Windows. A place this computer doesn't have puts the game on the desktop.
     */
    static boolean isolatedOn(BotSettings.Where where, Os os) {
        return switch (where) {
            case PRIVATE_DISPLAY -> os == Os.LINUX;
            case VM -> os == Os.WINDOWS;
            case MY_DESKTOP -> false;
        };
    }

    /**
     * Where this bot's game runs, highest first: bot code ({@link Session#override()}: off is the desktop; on is a
     * private display, or on Windows the settings' VM when they name one), the {@link #WHERE_PROPERTY} run
     * property, {@link #WHERE_ENV}, the settings.
     */
    public static BotSettings.Where where() {
        return where(BotSettings.current().where(), Os.current());
    }

    static BotSettings.Where where(BotSettings.Where settings, Os os) {
        Boolean override = Session.override();
        if (override != null) {
            if (!override) return BotSettings.Where.MY_DESKTOP;
            return os == Os.WINDOWS && settings == BotSettings.Where.VM
                ? BotSettings.Where.VM : BotSettings.Where.PRIVATE_DISPLAY;
        }
        return BotSettings.Where.fromId(System.getProperty(WHERE_PROPERTY))
            .or(() -> BotSettings.Where.fromId(System.getenv(WHERE_ENV)))
            .orElse(settings);
    }

    /**
     * Whether Studio's launch surfaces put the game on a private display for {@code settings}: Linux, and the
     * settings say one. Read from the bot's Java, so it has none of a run's property or code overrides.
     */
    public static boolean wantsPrivateDisplay(BotSettings settings) {
        return Os.current() == Os.LINUX && settings.where() == BotSettings.Where.PRIVATE_DISPLAY;
    }

    /** Whether Studio's launch surfaces put the game in a game VM for {@code settings}: Windows, and they say one. */
    public static boolean wantsVm(BotSettings settings) {
        return Os.current() == Os.WINDOWS && settings.where() == BotSettings.Where.VM;
    }

    /**
     * Why a game VM can't start {@code spec}, or empty when it can: asked before a VM boots, which can take
     * minutes, rather than after.
     */
    public static Optional<String> vmRefusal(LaunchSpec spec) {
        return GuestLaunch.command(spec).isPresent() ? Optional.empty()
            : Optional.of("A game VM can't start " + spec.describe()
                + ": it runs a Windows game by path, command, Steam or Epic." + OR_THE_DESKTOP);
    }

    /**
     * The backend for {@code spec} when the settings pin {@code pinned}: that one, else
     * {@link SessionBackends#preferredBackend}. What Studio's launch surfaces use, which read the bot's settings
     * from its Java rather than from a running bot.
     */
    public static SessionBackend backendFor(LaunchSpec spec, BotSettings.DisplayBackend pinned) {
        return SessionBackend.fromId(ProjectDefaults.backendId(pinned))
            .orElseGet(() -> SessionBackends.preferredBackend(spec));
    }

    /**
     * The backend to isolate {@code spec} on, highest precedence first: a bot's {@link Session#useBackend} pin,
     * the {@link #BACKEND_PROPERTY} system property, the settings' display backend, else
     * {@link SessionBackends#preferredBackend(LaunchSpec)} — gamescope, for every kind. Defaulting to gamescope
     * rather than to Xephyr is what stops a store launcher SIGTRAPping on Xephyr's software GL; the three pins
     * above it exist so a Xephyr run remains possible, never so one can happen by accident.
     *
     * <p>Every rung parses through {@link SessionBackend#fromId}, which is total and empty for anything
     * that isn't a backend id — {@code "auto"} included. That is a fix, not just tidying: the previous
     * {@code "gamescope".equalsIgnoreCase(x) ? GAMESCOPE : XEPHYR} mapped an explicit {@code auto} (and any typo)
     * onto Xephyr, i.e. onto the software GL that crashes the games this whole ladder exists to run.
     */
    public static SessionBackend backend(LaunchSpec spec) {
        return SessionBackend.fromId(Session.pinnedBackend())
            .or(() -> SessionBackend.fromId(System.getProperty(BACKEND_PROPERTY)))
            .or(() -> SessionBackend.fromId(ProjectDefaults.sessionBackend()))
            .orElseGet(() -> SessionBackends.preferredBackend(spec));
    }

    /** The nested-display options for {@code spec}: its selected backend at the project (or fallback) resolution. */
    public static SessionOptions options(LaunchSpec spec) {
        SessionBackends.DisplaySize size = size();
        return SessionBackends.optionsFor(spec, backend(spec), size.width(), size.height());
    }

    /**
     * The nested display size, which is {@link SessionBackends}' own default.
     *
     * <p>The shape is kept rather than inlined at the call site, because a display size a bot did choose is
     * a plausible thing to want — as a {@code @Managed} value beside the capture source, so it would have
     * exactly one author.
     */
    static SessionBackends.DisplaySize size() {
        return SessionBackends.sizeFor(0, 0);
    }

    /**
     * If this bot is isolated, bring up its nested display (once), register it, and launch {@code spec} into it —
     * returning {@code true} so the caller skips its normal {@code :0} launch. Returns {@code false} when
     * isolation isn't requested (caller runs its normal launch) <em>or</em> when bring-up fails (graceful
     * fallback to {@code :0}). Idempotent: once a session is registered, later calls no-op and return {@code true}.
     *
     * @throws IllegalStateException when the backend the game needs is not installed: the run stops with the
     *                               command that installs it rather than putting the game on the desktop. For a
     *                               game VM, when it can't be opened or the game can't be started in it
     */
    public static boolean launchIsolated(LaunchSpec spec) {
        if (!isolationRequested() || spec == null) {
            return false;
        }
        if (BotSession.isActive()) {
            // Already brought up and launched on a prior call — don't relaunch.
            return true;
        }
        if (where() == BotSettings.Where.VM) {
            return launchInVm(spec);
        }
        // Above every other rung: a live session we were handed is better than any session we could build. Studio
        // passes it when the game is already up in its background session — bringing up a second private display
        // would hand the launch to that first copy (the launcher is single-instance) and the game would end up
        // somewhere nobody is watching.
        DesktopSession adopted = Sessions.offered().orElse(null);
        if (adopted != null && adopted.attached() == null) {
            // A private display with nothing on it is not the session anyone meant: the target isn't up there, so
            // adopting would give the bot a black frame and no way to fix it (an adopted session never launches).
            Debug.log("the offered display " + adopted.displayName() + " has no window — not adopting it");
            adopted.close();
            adopted = null;
        }
        if (adopted != null) {
            BotSession.set(adopted);
            Debug.log("adopted the live display " + adopted.displayName() + " — not launching "
                + spec.spec() + " again");
            return true;
        }
        // Before the verdict, not after: the probes behind it read a dead session's leftovers as a launcher that is
        // up (measured — a bot refused to isolate because of a Heroic that had been closed for hours), and the only
        // other sweep is inside a successful Sessions.startPrivate, which a refusal never reaches.
        Sessions.reapOrphans();
        LaunchIsolation.Verdict verdict = LaunchIsolation.check(spec);
        if (!verdict.isolatable()) {
            // Asked before anything is spawned: a target that cannot be confined would otherwise cost the full
            // window budget and then land on :0 anyway, with a guess as the explanation.
            Debug.log("isolated launch declined — running on :0. " + verdict.reason());
            return false;
        }
        SessionBackend chosen = backend(spec);
        if (!SessionBackends.isAvailable(chosen)) {
            throw new IllegalStateException(missingBackend(chosen));
        }
        PrivateSession session = null;
        try {
            session = Sessions.startPrivate(options(spec));
            BotSession.set(session);
            session.launch(spec);
            if (session.attached() == null) {
                // Display came up but the game never mapped a window on :N — tear down and fall back to :0. What
                // actually happened is read off the process table rather than guessed at, in the same words
                // Studio uses (shared owns the wording).
                Debug.log("isolated launch: no window appeared on the nested display — falling back "
                    + "to :0. " + LaunchIsolation.noWindowDiagnosis(spec));
                BotSession.clear();
                session.close();
                return false;
            }
            Debug.log("isolated: running " + spec.spec() + " on nested " + chosen + " display at "
                + size().describe());
            return true;
        } catch (Exception e) {
            String why = e.getMessage() == null ? e.toString() : e.getMessage();
            Debug.log("isolated bring-up failed: " + why + " — falling back to :0");
            BotSession.clear();
            if (session != null) {
                try { session.close(); } catch (Exception ignored) { /* best-effort teardown */ }
            }
            return false;
        }
    }

    /**
     * Opens the game VM ({@link #vmName()}), registers it and starts {@code spec} in its Windows. A VM that can't
     * be opened stops the run, as a missing backend does: the user asked for the game off their desktop.
     */
    private static boolean launchInVm(LaunchSpec spec) {
        vmRefusal(spec).ifPresent(why -> {
            throw new IllegalStateException(why);
        });
        String name = vmName();
        DesktopSession session;
        try {
            session = Sessions.startVm(VmOptions.of(name));
        } catch (SessionStartException e) {
            throw new IllegalStateException(e.getMessage() + OR_THE_DESKTOP, e);
        }
        BotSession.set(session);
        try {
            session.launch(spec);
        } catch (RuntimeException e) {
            BotSession.clear();
            session.close();
            throw new IllegalStateException(e.getMessage(), e);
        }
        Debug.log("running " + spec.spec() + " in the game VM " + name);
        return true;
    }

    /** Whether this bot's game runs in a game VM: Windows, and {@link #where()} says one. */
    public static boolean inVm() {
        return Os.current() == Os.WINDOWS && where() == BotSettings.Where.VM;
    }

    /** Whether this bot's game VM is open and hasn't been given up on. */
    public static boolean vmAlive() {
        DesktopSession session = BotSession.get();
        return session != null && session.health() != SessionHealth.DEAD;
    }

    /**
     * Whether {@code spec} runs in this bot's game VM, as its guest says; where the guest can't tell (a command
     * line, a guest not answering), whether the VM is open ({@link #vmAlive()}). Not before this run opened the VM.
     */
    public static boolean runningInVm(LaunchSpec spec) {
        DesktopSession session = BotSession.get();
        if (session == null) return false;
        return switch (session.running(spec)) {
            case RUNNING -> true;
            case STOPPED -> false;
            case UNKNOWN -> vmAlive();
        };
    }

    /**
     * Starts {@code spec} again in the game VM, opening the VM first when this run hasn't yet: a recovery's
     * restart, which ends the game's processes in the guest first. A game the guest can't tell apart (a command
     * line) is asked to start again.
     */
    public static void relaunchInVm(LaunchSpec spec) {
        DesktopSession session = BotSession.get();
        // Shut down on purpose: the run ends, rather than start again the VM the user just stopped.
        Optional<String> ended = session == null ? Optional.empty() : session.endedBecause();
        if (ended.isPresent()) throw new IllegalStateException(ended.get() + " Run the bot again to start it.");
        if (session != null && session.health() == SessionHealth.DEAD) {
            // Given up on after restarts in a row: open the VM afresh rather than launch into nothing.
            BotSession.clear();
            session.close();
            session = null;
        }
        if (session == null) {
            launchIsolated(spec);
            return;
        }
        if (session.stop(spec)) Debug.log("stopped " + spec.spec() + " in the game VM");
        session.launch(spec);
    }

    /**
     * The game VM to run in: the {@link #VM_PROPERTY} run property, then {@link #VM_ENV}, else the one VM set up on
     * this computer.
     *
     * @throws IllegalStateException when none is named and there isn't exactly one
     */
    static String vmName() {
        String named = System.getProperty(VM_PROPERTY);
        return vmName(named != null && !named.isBlank() ? named : System.getenv(VM_ENV));
    }

    /**
     * {@code named} when it names a VM, else the one VM set up on this computer: what Studio, which holds the
     * run property itself, asks.
     *
     * @throws IllegalStateException when none is named and there isn't exactly one
     */
    public static String vmName(String named) {
        if (named != null && !named.isBlank()) return named.trim();
        List<VmRecord> ready = VmInventory.list().stream()
            .filter(vm -> vm.stage() == VmRecord.Stage.READY).toList();
        if (ready.size() == 1) return ready.get(0).name();
        throw new IllegalStateException(ready.isEmpty()
            ? "There's no game VM on this computer. Set one up in ⚙ Bot Settings." + OR_THE_DESKTOP
            : "There are " + ready.size() + " game VMs on this computer: name one with -D" + VM_PROPERTY + "=<name>.");
    }

    private static final String OR_THE_DESKTOP = " Or set ⚙ Bot Settings ▸ Run the game in ▸ My desktop.";

    /**
     * Why a run on a private display can't start without {@code backend}, and the two ways on: the install
     * command for this distro when one is known, and the desktop.
     */
    public static String missingBackend(SessionBackend backend) {
        String install = BackendInstall.forBackend(backend)
            .map(i -> "Install it with: " + i.describe() + (i.needsReboot() ? ", then restart" : "") + ".")
            .orElse("To fix it, " + SessionBackends.installHint(backend) + ".");
        return backend.binaryName() + " isn't installed, so the game can't run in a private display. " + install
            + " Or set ⚙ Bot Settings ▸ Run the game in ▸ My desktop.";
    }
}
