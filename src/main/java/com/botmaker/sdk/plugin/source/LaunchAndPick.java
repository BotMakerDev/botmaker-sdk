// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.source;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.capture.NewWindows;
import com.botmaker.sdk.internal.capture.NewWindows.Candidate;
import com.botmaker.sdk.internal.session.SessionBootstrap;
import com.botmaker.sdk.plugin.launch.GameButton;
import com.botmaker.sdk.plugin.launch.QuickLaunch;
import com.botmaker.sdk.plugin.launch.SessionHandoff;
import com.botmaker.sdk.plugin.pictures.ResourceMove;
import com.botmaker.sdk.plugin.settings.SettingsValue;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.sdk.plugin.settings.VmChoice;
import com.botmaker.session.PrivateSession;
import com.botmaker.session.launch.BackgroundLauncher;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.launch.GameMarker;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.RunningProbe;
import com.botmaker.shared.platform.Os;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongPredicate;

/**
 * The source picker's ▶ Launch and pick: starts the game this computer launches — where the bot's "Run the game in"
 * setting says, a private display by default, or this desktop where that place is refused ({@link #place}) — watches
 * the windows that open, and recommends the one the game opened ({@link WindowWatch}). A pick saves the window by its class, and its title only when the class is one
 * many programs share, so the next run finds it on this computer whatever its process id (2026-10-19).
 *
 * <p>Only for a game with a window: an emulator or phone app is captured from its device, which its pick already
 * points capture at. The game VM's windows are the guest's, listed by the picker's Game VM section, so a launch
 * there hands back to it.
 *
 * <p>The windows are listed four times a second while the dialog is open, for up to {@link #WATCH_MS}: a native
 * window event would say the same a quarter-second sooner, and this works alike on every system.
 */
final class LaunchAndPick {

    /** What the dialog ended with: a window to capture, the game VM's section to look in, or nothing. */
    sealed interface Outcome permits Outcome.Picked, Outcome.InVm {
        record Picked(CaptureSource window) implements Outcome {}

        record InVm() implements Outcome {}
    }

    private static final long POLL_MS = 250;
    private static final long WATCH_MS = 180_000;

    private final StudioServices services;
    private final Window owner;
    private final Label status = Styles.on(new Label(), Styles.DIALOG_HINT);
    private final VBox rows = new VBox(6);
    private volatile boolean watching;
    /** A launch where the settings say failed: the next ▶ Launch starts the game on this desktop. FX thread. */
    private boolean desktopAfterFailure;
    private Stage stage;
    private Outcome outcome;

    private LaunchAndPick(StudioServices services, Window owner) {
        this.services = services;
        this.owner = owner;
    }

    /** Shows the dialog modally over {@code owner}; empty when it was closed with no window picked. */
    static Optional<Outcome> show(StudioServices services, Window owner) {
        return new LaunchAndPick(services, owner).showAndWait();
    }

    private Optional<Outcome> showAndWait() {
        String target = LaunchTargetValue.played(services);
        LaunchSpec spec = target == null ? null : LaunchSpec.parse(target);
        Label heading = Styles.on(new Label(spec == null ? "No game to launch"
                : "Launch " + GameButton.caption(services, target) + " and pick its window"), Styles.DIALOG_HEADING);
        heading.setWrapText(true);

        Button launch = Styles.on(new Button("▶ Launch"), Styles.PRIMARY_BUTTON);
        String refusal = refusal(spec);
        launch.setDisable(refusal != null);
        status.setText(refusal == null ? place(spec).describe() + " Its windows are listed here as they open."
                : refusal);
        status.setWrapText(true);
        launch.setOnAction(e -> {
            launch.setDisable(true);
            rows.getChildren().clear();
            // Asked again on each press: an install or a VM made ready meanwhile counts.
            Place place = desktopAfterFailure ? new Place(BotSettings.Where.MY_DESKTOP, null) : place(spec);
            start(spec, place.where(), () -> {
                // A launch that failed elsewhere can be tried again on this desktop, the window being the same.
                if (place.where() != BotSettings.Where.MY_DESKTOP) {
                    desktopAfterFailure = true;
                    launch.setText("▶ Launch on this desktop");
                }
                launch.setDisable(false);
            });
        });

        ScrollPane list = new ScrollPane(rows);
        list.setFitToWidth(true);
        VBox.setVgrow(list, Priority.ALWAYS);
        Button close = new Button("Close");
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, launch, spacer, close);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, heading, status, list, bar);
        root.setPadding(new Insets(14));
        stage = Modals.window(services, owner, Modals.Frame.modal("Launch and pick its window", 620, 460, 480, 360),
                root);
        stage.setOnHidden(e -> watching = false);
        stage.showAndWait();
        watching = false;
        return Optional.ofNullable(outcome);
    }

    /** Why ▶ Launch cannot start {@code spec} here, or null when it can. */
    private static String refusal(LaunchSpec spec) {
        if (spec == null) return "The bot lists no game this computer launches: pick one in 🎮 Game first.";
        if (spec.kind() == LaunchKind.EMULATOR_APP) {
            return spec.describe() + " is captured from its device, not a window: picking it in 🎮 Game already "
                    + "pointed capture there.";
        }
        return spec.notHere(Os.current()).orElse(null);
    }

    /**
     * Why {@code spec} cannot be started in {@code place} on this computer, or empty when it can: the system, what is
     * installed ({@link SessionBootstrap#placeRefusal}), and for the game VM whether one is set up.
     */
    private Optional<String> unavailable(LaunchSpec spec, BotSettings.Where place) {
        if (spec == null) return Optional.empty();
        Optional<String> refused = SessionBootstrap.placeRefusal(spec, place);
        if (refused.isPresent() || place != BotSettings.Where.VM) return refused;
        try {
            SessionBootstrap.vmName(VmChoice.current(services));
            return Optional.empty();
        } catch (RuntimeException noVm) {
            return Optional.of(noVm.getMessage() == null ? "No game VM is set up." : noVm.getMessage());
        }
    }

    /** Where ▶ Launch starts {@code spec} now, from the bot's settings. */
    private Place place(LaunchSpec spec) {
        return place(SettingsValue.current(services).runIn().where(), where -> unavailable(spec, where));
    }

    /**
     * Where ▶ Launch starts the game, and why it is not where the bot's settings said when it is not: no choice here
     * (2026-11-26, the user's call), the bot's "Run the game in" setting, this desktop where that place is refused.
     */
    record Place(BotSettings.Where where, String refused) {
        /** The status line's first sentence. */
        String describe() {
            String in = "It starts " + switch (where) {
                case MY_DESKTOP -> "on this desktop";
                case PRIVATE_DISPLAY -> "in a private display";
                case VM -> "in the game VM";
            };
            return refused == null ? in + ", as the bot's \"Run the game in\" setting says."
                    : in + " for this pick only: " + refused
                    + " The bot's runs still start it where that setting says.";
        }
    }

    /** {@code preferred} when {@code unavailable} has nothing against it, else this desktop with the reason. */
    static Place place(BotSettings.Where preferred, Function<BotSettings.Where, Optional<String>> unavailable) {
        if (preferred == BotSettings.Where.MY_DESKTOP) return new Place(preferred, null);
        return unavailable.apply(preferred)
                .map(why -> new Place(BotSettings.Where.MY_DESKTOP, why))
                .orElseGet(() -> new Place(preferred, null));
    }

    /**
     * Starts {@code spec} in {@code place}, then watches the windows that open there; {@code failed} runs when it
     * did not start. FX thread.
     */
    private void start(LaunchSpec spec, BotSettings.Where place, Runnable failed) {
        switch (place) {
            case MY_DESKTOP -> {
                // Listed before the launch: a launcher hands the game off and returns long before its window opens.
                NativeController desktop = NativeControllerFactory.get();
                Set<Object> before = WindowWatch.handles(windows(desktop));
                QuickLaunch.launchOnDesktop(spec, services, (ok, message) -> status.setText(message), launched -> {
                    if (launched) watch(desktop, before, spec);
                    else failed.run();
                });
            }
            case PRIVATE_DISPLAY -> QuickLaunch.launchPrivately(spec, services, (ok, message) -> {
                status.setText(message);
                PrivateSession session = ok
                        ? SessionHandoff.rejoin(services, BackgroundLauncher.forProject(
                        ResourceMove.resourcesRoot(services.resourcesDir()))) : null;
                // Everything in a display of the game's own is the game's: no window was there before.
                if (session != null) watch(session.controller(), Set.of(), spec);
                else failed.run();
            });
            case VM -> QuickLaunch.launchInVm(spec, services, (ok, message) -> status.setText(message), launched -> {
                if (!launched) {
                    failed.run();
                    return;
                }
                outcome = new Outcome.InVm();
                stage.close();
            });
        }
    }

    /**
     * Lists {@code controller}'s windows until the dialog closes or {@link #WATCH_MS} passes, showing the new ones, a
     * window of {@code spec}'s class first ({@link NewWindows#gameClass}), then one whose process its launcher named
     * ({@link GameMarker}).
     */
    private void watch(NativeController controller, Set<Object> before, LaunchSpec spec) {
        watching = true;
        Thread.ofVirtual().name("launch-and-pick").start(() -> {
            String gameClass = NewWindows.gameClass(spec);
            GameMarker marker = GameMarker.of(spec);
            LongPredicate marked = pid -> marker != null && marker.marks(pid);
            long until = System.currentTimeMillis() + WATCH_MS;
            List<Candidate> shown = List.of();
            while (watching && System.currentTimeMillis() < until) {
                List<Candidate> ranked = WindowWatch.ranked(before, windows(controller),
                        RunningProbe::launcherUi, gameClass, marked);
                if (!WindowWatch.same(shown, ranked)) {
                    shown = ranked;
                    Platform.runLater(() -> render(ranked));
                }
                try {
                    Thread.sleep(POLL_MS);
                } catch (InterruptedException stop) {
                    return;
                }
            }
            if (watching) Platform.runLater(() -> status.setText("Stopped watching for windows: close and launch "
                    + "again, or pick the game's window in the list behind."));
        });
    }

    private static List<GenericWindow> windows(NativeController controller) {
        try {
            return controller.getAllWindows();
        } catch (RuntimeException | LinkageError noList) {
            return List.of();
        }
    }

    /** One row per window the launch opened, the recommended one marked. */
    private void render(List<Candidate> ranked) {
        if (!watching) return;
        rows.getChildren().clear();
        for (Candidate candidate : ranked) {
            Label title = Styles.on(new Label((candidate.recommended() ? "★ " : "") + candidate.window().getTitle()),
                    candidate.recommended() ? Styles.STRONG_TEXT : Styles.CAPTION);
            String note = candidate.recommended() ? "Recommended: the newest window the game opened"
                    : candidate.launcher() ? "The launcher's own window" : "Another new window";
            Label detail = Styles.on(new Label(note + " · " + candidate.identity().spec()), Styles.DIALOG_HINT);
            VBox text = new VBox(2, title, detail);
            HBox.setHgrow(text, Priority.ALWAYS);
            Button use = Styles.on(new Button("Use this window"),
                    candidate.recommended() ? Styles.PRIMARY_BUTTON : Styles.PILL);
            use.setOnAction(e -> {
                outcome = new Outcome.Picked(NamedWindow.of(candidate.identity()));
                stage.close();
            });
            HBox row = new HBox(10, text, use);
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
        }
    }
}
