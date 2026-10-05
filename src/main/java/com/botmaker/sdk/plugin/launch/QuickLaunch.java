package com.botmaker.sdk.plugin.launch;

import com.botmaker.session.display.SessionBackends;
import com.botmaker.session.impl.NestedSession;
import com.botmaker.session.launch.BackgroundLauncher;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.sdk.plugin.settings.BotSettingsWindow;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.Launcher;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

import java.awt.Dimension;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The "▶ Launch now" button: brings the project's configured {@code launch.target} up <em>without</em>
 * compiling and running the bot, so a user can check the target is right — and, in the capture-targets
 * dialog, so the game's window exists to be picked at all.
 *
 * <p>It is one line of real work ({@link Launcher#start}) because <b>shared</b> owns the launch stack. That is
 * the point: an earlier attempt at this button copied the protocol URLs and CLI ladders into the editor, which
 * is documented as explicitly <em>not</em> their owner.
 *
 * <p><b>Background isolation.</b> When the project has {@code session.isolated} on (the default), the button
 * does not launch on the real {@code :0} desktop — it brings the target up in a private nested display via
 * {@link BackgroundLauncher} (gamescope for games, Xephyr for a plain command, per {@link SessionBackends}),
 * so the real cursor stays free and the Remote Pilot's Stop and status reflect the same live session. Turning
 * the toggle off returns to a {@code :0} {@link Launcher#start}.
 *
 * <p>A target that does not run on a desktop at all — an {@code emu-app:}, driven over ADB inside the emulator
 * — takes the direct path whatever the toggle says ({@link #usesBackgroundSession}). It has no child process
 * to hand a private {@code DISPLAY} to, so the background path could only ever refuse it.
 *
 * <p>Two things every call site gets by going through here rather than wiring its own button: the launch runs
 * <b>off the FX thread</b> (a protocol hand-off blocks on process spawns, and an {@code emu-app:} target polls
 * an emulator to its boot timeout — seconds of frozen UI otherwise), and a project with no target configured
 * yields a <b>disabled</b> button that says why in its tooltip, rather than an enabled one that silently does
 * nothing.
 *
 * <p><b>Where its two answers come from (2026-09-27).</b> The launch target is this machine's run property
 * ({@link LaunchTargetValue}), the isolation flag is the bot's own {@code Sdk.settings()}. Both were keys of a
 * {@code botmaker-project.properties} until then.
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
        LaunchSpec spec = LaunchTargetValue.spec(services);
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
     * Whether a launch of {@code spec} should go through a private nested display: the project's
     * {@code session.isolated} setting, <em>except</em> for a target that does not run on a desktop at all.
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
     * The trailing half-sentence explaining why an off-desktop target ignored the "Run in background" toggle,
     * or empty for every other kind. Said on success rather than hidden: the toggle is on by default, so
     * without it the user is left wondering whether the launch respected the setting they can see.
     */
    private static String offDesktopNote(LaunchSpec spec) {
        return spec.runsOffDesktop()
                ? " It runs inside the emulator over ADB, so background mode doesn't apply to it."
                : "";
    }

    private static void launch(Button button, LaunchSpec spec, Report report, StudioServices services) {
        button.setDisable(true);
        if (usesBackgroundSession(spec, BotSettingsWindow.current(services).session().isolated())) {
            launchInBackground(button, spec, report, services.resourcesDir());
            return;
        }
        report.accept(true, "Launching " + spec.describe() + "…");
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
            button.setDisable(false);
            report.accept(true, (last != null ? last : "Launched " + spec.describe() + ".") + offDesktopNote(spec));
        }, why -> {
            button.setDisable(false);
            report.accept(false, "Couldn't launch: " + why);
        });
    }

    /**
     * The background path (project {@code session.isolated} on): bring the target up in a private nested
     * display instead of the real {@code :0} desktop. The backend is chosen by kind — gamescope for games,
     * Xephyr for a plain command; when the backend a game needs is not installed we fail <b>loudly</b> with
     * the install hint rather than dropping to a Xephyr that would crash it.
     */
    private static void launchInBackground(Button button, LaunchSpec spec, Report report, Path resourcesDir) {
        Optional<NestedSession.Backend> backend = SessionBackends.availableBackendFor(spec);
        if (backend.isEmpty()) {
            button.setDisable(false);
            report.accept(false, "Can't run " + spec.describe() + " in the background — "
                    + SessionBackends.installHint(SessionBackends.preferredBackend(spec))
                    + ". Turn off \"Run in a private display\" (⚙ Bot Settings) to launch on your desktop instead.");
            return;
        }
        // The session is created at the project's standard resolution, not a fixed 1280x720: gamescope's -w/-h
        // *is* the screen the game inside sees, so a hardcoded size caps the game's own resolution options at
        // that size whatever the project is authored at — and makes the capture a scaled copy of what the
        // pictures were made from.
        // The authored size is gone and it was never there: capture.width / capture.height were read here
        // and written by nothing in any module, so this took the launcher's own default on every call.
        BackgroundLauncher.forProject(resourcesDir).start(
                backend.get(), spec,
                BackgroundLauncher.DEFAULT_WIDTH,
                BackgroundLauncher.DEFAULT_HEIGHT,
                // The hop is the caller's: BackgroundLauncher lives in botmaker-session, which has no JavaFX,
                // so the outcome arrives on whichever thread produced it and this one touches a button.
                (ok, message) -> Platform.runLater(() -> {
                    button.setDisable(false);
                    report.accept(ok, message);
                }));
    }
}
