package com.botmaker.sdk.plugin.launch;

import com.botmaker.session.display.SessionBackends;
import com.botmaker.session.DesktopSession;
import com.botmaker.session.PrivateSession;
import com.botmaker.session.SessionBackend;
import com.botmaker.session.SessionStartException;
import com.botmaker.session.Sessions;
import com.botmaker.session.VmOptions;
import com.botmaker.session.launch.BackgroundLauncher;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.internal.session.SessionBootstrap;
import com.botmaker.sdk.plugin.pictures.ScreenSize;
import com.botmaker.sdk.plugin.settings.BackendInstallPrompt;
import com.botmaker.sdk.plugin.settings.BotSettingsWindow;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.sdk.plugin.settings.VmChoice;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.Launcher;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The "▶ Launch now" button: brings the project's configured {@code launch.target} up <em>without</em>
 * compiling and running the bot, so a user can check the target is right — and, in the capture-targets
 * dialog, so the game's window exists to be picked at all.
 *
 * <p>It is one line of real work ({@link Launcher#start}) because <b>shared</b> owns the launch stack. That is
 * the point: an earlier attempt at this button copied the protocol URLs and CLI ladders into the editor, which
 * is documented as explicitly <em>not</em> their owner.
 *
 * <p><b>Background isolation.</b> When the bot's settings say a private display (the default), the button
 * does not launch on the real {@code :0} desktop — it brings the target up in a private nested display via
 * {@link BackgroundLauncher}, on the backend the settings pin or else {@link SessionBackends}' choice, so the
 * real cursor stays free and the Remote Pilot's Stop and status reflect the same live session. My desktop
 * returns to a {@code :0} {@link Launcher#start}.
 *
 * <p>A target that does not run on a desktop at all — an {@code emu-app:}, driven over ADB inside the emulator
 * — takes the direct path whatever the settings say ({@link #usesBackgroundSession}). It has no child process
 * to hand a private {@code DISPLAY} to, so the background path could only ever refuse it.
 *
 * <p>Two things every call site gets by going through here rather than wiring its own button: the launch runs
 * <b>off the FX thread</b> (a protocol hand-off blocks on process spawns, and an {@code emu-app:} target polls
 * an emulator to its boot timeout — seconds of frozen UI otherwise), and a project with no target configured
 * yields a <b>disabled</b> button that says why in its tooltip, rather than an enabled one that silently does
 * nothing.
 *
 * <p><b>Where its two answers come from.</b> The launch target is the first of the bot's {@code launchTargets} this
 * computer's system can launch ({@link LaunchTargetValue}), the isolation flag the bot's own {@code Sdk.settings()}.
 */
public final class QuickLaunch {

    private QuickLaunch() {
    }

    /** How a call site shows the outcome — its own status label, in its own dialog's idiom. */
    @FunctionalInterface
    public interface Report {
        void accept(boolean ok, String message);
    }

    /**
     * A button that launches the target this machine has for the open bot, reporting through {@code report}
     * (always on the FX thread). Disabled with an explanatory tooltip when no target is configured.
     */
    public static Button button(StudioServices services, Report report) {
        Button button = new Button("▶ Launch now");
        bind(button, services, report);
        return button;
    }

    /**
     * (Re)points an existing button at whatever this machine currently has as the launch target. A call site
     * that can <em>change</em> the launch target while the button is on screen calls this after a save, so the
     * button and the target cannot disagree.
     */
    public static void bind(Button button, StudioServices services, Report report) {
        bind(button, LaunchTargetValue.spec(services), services, report);
    }

    /**
     * Points {@code button} at {@code spec} itself rather than at the one this machine launches: a launcher
     * editor's ▶ Launch, so the game is up and its window can be picked as the capture source. Disabled with an
     * explanatory tooltip for {@code null}.
     */
    public static void bind(Button button, LaunchSpec spec, StudioServices services, Report report) {
        if (spec == null) {
            button.setDisable(true);
            button.setOnAction(null);
            button.setTooltip(new Tooltip(
                    "No launch target on this computer — start the game from its own launcher, or pick an "
                            + "emulator app in an Emulators block"));
            return;
        }
        button.setDisable(false);
        button.setTooltip(new Tooltip("Start " + spec.describe() + " now, without running the bot"));
        button.setOnAction(e -> launch(button, spec, report, services));
    }

    /**
     * Whether a launch of {@code spec} should go through a private nested display: whether the settings say
     * one, <em>except</em> for a target that does not run on a desktop at all.
     *
     * <p>That exception is the whole point of this predicate. An {@code emu-app:} target on most products is
     * started, captured and clicked over ADB inside the emulator; there is no child process of ours to hand a
     * {@code DISPLAY} to, so isolation refuses it — and with isolation on by default that refusal was the
     * <b>only</b> thing the launch button ever did for such a target. It is not a failure to route around: the
     * target is already off the user's desktop, which is what background mode is for.
     *
     * <p>The question is asked of the <em>spec</em> rather than its kind, because Waydroid answers differently:
     * its UI is a Wayland client we start under our own gamescope, so it does map a window on a display we own
     * and does belong in a background session. Kept static and pure so the routing is unit-testable without an
     * FX button.
     */
    public static boolean usesBackgroundSession(LaunchSpec spec, boolean isolated) {
        return isolated && spec != null && !spec.runsOffDesktop();
    }

    /**
     * The trailing half-sentence explaining why an off-desktop target ignored "Run the game in", or empty for
     * every other kind. Said on success rather than hidden: a private display is the default, so without it
     * the user is left wondering whether the launch respected the setting they can see.
     */
    private static String offDesktopNote(LaunchSpec spec) {
        return spec.runsOffDesktop()
                ? " It runs inside the emulator over ADB, so the private display doesn't apply to it."
                : "";
    }

    private static void launch(Button button, LaunchSpec spec, Report report, StudioServices services) {
        button.setDisable(true);
        BotSettings settings = BotSettingsWindow.current(services);
        if (SessionBootstrap.wantsVm(settings)) {
            launchInVm(button, spec, report, VmChoice.current(services));
            return;
        }
        if (usesBackgroundSession(spec, SessionBootstrap.wantsPrivateDisplay(settings))) {
            launchInBackground(button, spec, report, services,
                    SessionBootstrap.backendFor(spec, settings.runIn().displayBackend()));
            return;
        }
        onDesktop(spec, services, report, launched -> button.setDisable(false));
    }

    /**
     * Starts {@code spec} on this desktop whatever the settings say — 🎯 Capture Source's ▶ Launch and pick, where
     * the user chose the place. {@code report} hears progress and how it ended, {@code ended} whether it launched,
     * just before; FX thread.
     */
    public static void launchOnDesktop(LaunchSpec spec, StudioServices services, Report report,
                                       Consumer<Boolean> ended) {
        onDesktop(spec, services, report, ended);
    }

    /** As {@link #launchOnDesktop}, then in the game VM the bot's settings name. */
    public static void launchInVm(LaunchSpec spec, StudioServices services, Report report, Consumer<Boolean> ended) {
        inVm(spec, report, VmChoice.current(services), ended);
    }

    private static void onDesktop(LaunchSpec spec, StudioServices services, Report report, Consumer<Boolean> ended) {
        report.accept(true, "Launching " + spec.describe() + "…");
        // On this desktop: a run must not join a private display an earlier launch offered.
        SessionHandoff.offer(services, null);
        // The answer is the last thing the launcher said about its own progress. An emulator app narrates
        // ("starting Waydroid…", "waiting for Android…"); every other kind says nothing (null) and falls back to
        // the generic line. Launcher.start throws the underlying failure (Steam not installed, no protocol
        // handler, an emulator that never finished booting) precisely so it can be shown here.
        Async.load("quick-launch", () -> {
            String[] note = new String[1];
            Launcher.start(spec, message -> {
                note[0] = message;
                Platform.runLater(() -> report.accept(true, message));
            });
            return note[0];
        }, last -> {
            ended.accept(true);
            report.accept(true, (last != null ? last : "Launched " + spec.describe() + ".") + offDesktopNote(spec));
        }, why -> {
            ended.accept(false);
            report.accept(false, "Couldn't launch: " + why);
        });
    }

    /**
     * The VM path (Windows, the settings say a game VM): start the VM if it is off, start the game on its
     * desktop, and disconnect. The game goes on running there for the bot's run to drive.
     */
    private static void launchInVm(Button button, LaunchSpec spec, Report report, String vmName) {
        inVm(spec, report, vmName, launched -> button.setDisable(false));
    }

    private static void inVm(LaunchSpec spec, Report report, String vmName, Consumer<Boolean> ended) {
        Optional<String> refused = SessionBootstrap.vmRefusal(spec);
        if (refused.isPresent()) {
            ended.accept(false);
            report.accept(false, refused.get());
            return;
        }
        report.accept(true, "Starting the game VM and " + spec.describe() + " in it…");
        Async.load("quick-launch-vm", () -> {
            String name = SessionBootstrap.vmName(vmName);
            try (DesktopSession session = Sessions.startVm(VmOptions.of(name))) {
                session.launch(spec);
            } catch (SessionStartException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            return name;
        }, name -> {
            ended.accept(true);
            report.accept(true, "Launched " + spec.describe() + " in the game VM " + name + ".");
        }, why -> {
            ended.accept(false);
            report.accept(false, "Couldn't launch: " + why);
        });
    }

    /**
     * The background path (the settings say a private display): bring the target up there instead of on the
     * real {@code :0} desktop, on the backend the settings pin or else the kind's. When it isn't installed the
     * user is offered the install, and declining leaves the game where it was: never a Xephyr that would crash
     * it, never the desktop they didn't ask for.
     */
    private static void launchInBackground(Button button, LaunchSpec spec, Report report, StudioServices services,
                                           SessionBackend backend) {
        if (!SessionBackends.isAvailable(backend)) {
            button.setDisable(false);
            report.accept(false, SessionBootstrap.missingBackend(backend));
            BackendInstallPrompt.offer(services, button.getScene() == null ? null : button.getScene().getWindow(),
                    backend, (installed, message) -> report.accept(installed, installed
                            ? message + " Press ▶ Launch again." : message));
            return;
        }
        Window owner = button.getScene() == null ? null : button.getScene().getWindow();
        HostLauncherPrompt.clearThen(services, owner, spec, () -> {
            Path resourcesDir = services.resourcesDir();
            // The session is made at the bot's screen size, not a fixed 1280x720: gamescope's -w/-h *is* the
            // screen the game inside sees, so another size caps the game's own resolution options at it — and
            // makes the capture a scaled copy of what the pictures were cut from.
            SessionBackends.DisplaySize size = ScreenSize.display(services);
            BackgroundLauncher launcher = BackgroundLauncher.forProject(resourcesDir);
            launcher.start(
                    backend, spec,
                    size.width(),
                    size.height(),
                    // The hop is the caller's: BackgroundLauncher lives in botmaker-session, which has no JavaFX,
                    // so the outcome arrives on whichever thread produced it and this one touches a button.
                    (ok, message) -> Platform.runLater(() -> {
                        button.setDisable(false);
                        report.accept(ok, message);
                        if (ok && launcher.session() != null) SessionHandoff.offer(services, launcher.session());
                    }));
        }, (ok, why) -> {
            button.setDisable(false);
            report.accept(ok, why);
        });
    }

    /**
     * Brings {@code spec} up in a private display for the assistant's launch_game, as ▶ Launch does with the
     * settings saying one, and offers it to the runs ({@link SessionHandoff}). {@code done} hears the outcome: true
     * once the game's window is up there, false and why otherwise. FX thread, and {@code done} runs on it.
     */
    public static void launchPrivately(LaunchSpec spec, StudioServices services, BiConsumer<Boolean, String> done) {
        BotSettings settings = BotSettingsWindow.current(services);
        SessionBackend backend = SessionBootstrap.backendFor(spec, settings.runIn().displayBackend());
        if (!SessionBackends.isAvailable(backend)) {
            Platform.runLater(() -> done.accept(false, SessionBootstrap.missingBackend(backend)));
            return;
        }
        HostLauncherPrompt.clearThen(services, Modals.owner(services), spec, () -> {
            SessionBackends.DisplaySize size = ScreenSize.display(services);
            BackgroundLauncher launcher = BackgroundLauncher.forProject(services.resourcesDir());
            // start reports "Bringing up…" first, at once, on success: only a refusal ends the launch then.
            // Counted here, as it is reported, since the session may be held by the time the FX thread reads it.
            boolean[] first = {true};
            launcher.start(backend, spec, size.width(), size.height(), (ok, message) -> {
                boolean progress = ok && first[0];
                first[0] = false;
                if (progress) return;
                Platform.runLater(() -> {
                    PrivateSession session = launcher.session();
                    if (ok && session != null) SessionHandoff.offer(services, session);
                    done.accept(ok && session != null, message);
                });
            });
        }, done);
    }
}
