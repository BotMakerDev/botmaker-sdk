package com.botmaker.sdk.plugin.source;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.plugin.launch.GameButton;
import com.botmaker.sdk.plugin.launch.QuickLaunch;
import com.botmaker.sdk.plugin.launch.SessionHandoff;
import com.botmaker.sdk.plugin.settings.BotSettingsWindow;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.sdk.plugin.settings.VmChoice;
import com.botmaker.session.PrivateSession;
import com.botmaker.session.launch.BackgroundLauncher;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.RunningProbe;
import com.botmaker.shared.platform.Os;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 🎯 Capture Source's ▶ Launch and pick: starts the game this computer launches — on this desktop, in a private
 * display or in the game VM, as the user chooses here — watches the windows that open, and recommends the one the
 * game opened ({@link WindowWatch}). A pick saves the window by its class, and its title only when the class is one
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
        String target = LaunchTargetValue.current(services);
        LaunchSpec spec = target == null ? null : LaunchSpec.parse(target);
        Label heading = Styles.on(new Label(spec == null ? "No game to launch"
                : "Launch " + GameButton.caption(services, target) + " and pick its window"), Styles.DIALOG_HEADING);
        heading.setWrapText(true);

        ToggleGroup where = new ToggleGroup();
        HBox places = new HBox(12);
        places.setAlignment(Pos.CENTER_LEFT);
        BotSettings.Where preferred = BotSettingsWindow.current(services).runIn().where();
        for (BotSettings.Where place : BotSettings.Where.values()) {
            RadioButton button = new RadioButton(placeName(place));
            button.setUserData(place);
            button.setToggleGroup(where);
            Optional<String> unavailable = unavailable(place);
            button.setDisable(unavailable.isPresent());
            unavailable.ifPresent(why -> button.setTooltip(new javafx.scene.control.Tooltip(why)));
            if (place == preferred && unavailable.isEmpty()) button.setSelected(true);
            places.getChildren().add(button);
        }
        if (where.getSelectedToggle() == null) where.selectToggle((RadioButton) places.getChildren().get(
                BotSettings.Where.MY_DESKTOP.ordinal()));
        Label placesHint = Styles.on(new Label("Only where it starts now: the bot's runs keep \"Run the game in\" "
                + "from its settings. The window is the same wherever it opens."), Styles.DIALOG_HINT);
        placesHint.setWrapText(true);

        Button launch = Styles.on(new Button("▶ Launch"), Styles.PRIMARY_BUTTON);
        String refusal = refusal(spec);
        launch.setDisable(refusal != null);
        status.setText(refusal == null ? "Its windows are listed here as they open." : refusal);
        status.setWrapText(true);
        launch.setOnAction(e -> {
            launch.setDisable(true);
            rows.getChildren().clear();
            // A launch that failed can be tried again, here or elsewhere.
            start(spec, (BotSettings.Where) where.getSelectedToggle().getUserData(), () -> launch.setDisable(false));
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

        VBox root = new VBox(10, heading, places, placesHint, status, list, bar);
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
        if (!spec.runsOn(Os.current())) return spec.describe() + " does not run on " + Os.current().displayName() + ".";
        return null;
    }

    /** Why the game cannot be started in {@code place} on this computer, or empty when it can. */
    private Optional<String> unavailable(BotSettings.Where place) {
        return switch (place) {
            case MY_DESKTOP -> Optional.empty();
            case PRIVATE_DISPLAY -> Os.current() == Os.LINUX ? Optional.empty()
                    : Optional.of("A private display is Linux's: on " + Os.current().displayName() + " a game VM keeps "
                    + "the game off your desktop");
            case VM -> VmChoice.current(services) != null ? Optional.empty()
                    : Optional.of("No game VM is set up for this bot: set one up in its settings");
        };
    }

    private static String placeName(BotSettings.Where place) {
        return switch (place) {
            case MY_DESKTOP -> "This desktop";
            case PRIVATE_DISPLAY -> "A private display";
            case VM -> "The game VM";
        };
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
                    if (launched) watch(desktop, before);
                    else failed.run();
                });
            }
            case PRIVATE_DISPLAY -> QuickLaunch.launchPrivately(spec, services, (ok, message) -> {
                status.setText(message);
                PrivateSession session = ok
                        ? SessionHandoff.rejoin(services, BackgroundLauncher.forProject(services.resourcesDir())) : null;
                // Everything in a display of the game's own is the game's: no window was there before.
                if (session != null) watch(session.controller(), Set.of());
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

    /** Lists {@code controller}'s windows until the dialog closes or {@link #WATCH_MS} passes, showing the new ones. */
    private void watch(NativeController controller, Set<Object> before) {
        watching = true;
        Thread.ofVirtual().name("launch-and-pick").start(() -> {
            long until = System.currentTimeMillis() + WATCH_MS;
            List<WindowWatch.Candidate> shown = List.of();
            while (watching && System.currentTimeMillis() < until) {
                List<WindowWatch.Candidate> ranked = WindowWatch.ranked(before, windows(controller),
                        RunningProbe::launcherUi);
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
    private void render(List<WindowWatch.Candidate> ranked) {
        if (!watching) return;
        rows.getChildren().clear();
        for (WindowWatch.Candidate candidate : ranked) {
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
