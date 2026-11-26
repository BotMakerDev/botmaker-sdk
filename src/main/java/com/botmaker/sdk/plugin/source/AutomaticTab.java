// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.source;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.internal.capture.AutoPicks;
import com.botmaker.sdk.internal.capture.HostWindows;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.plugin.params.LaunchTargets;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.WindowIdentity;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The Automatic tab of the Choose capture source window: a card saying what {@link CaptureSource#automatic()} reads
 * for this bot's launch target, the window a run picked for it if any, and Forget the pick, so the next run watches
 * the launch again (spec {@code 2026-11-25-auto-capture-design.md}).
 */
final class AutomaticTab {

    private final StudioServices services;
    private final LaunchSpec target;
    private final VBox card;
    private final Label picked = Styles.on(new Label(), Styles.SMALL_TEXT);
    private final Button forget = new Button("Forget the pick");
    private final Button point;
    private final Label pointHint = Styles.on(new Label(PointAtWindow.HINT), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    /** The window kept by pointing in this window, so the line says so while it is still the pick. */
    private String pointedAt;
    /** Whether this desktop lets the user point, once probed. */
    private boolean canPoint;

    /**
     * @param onPick runs when the card is clicked; {@code onUse} when it is double-clicked or Use is pressed
     */
    AutomaticTab(StudioServices services, Runnable onPick, Runnable onUse) {
        this.services = services;
        this.target = LaunchTargets.spec(services);
        Label title = Styles.on(new Label("Automatic"), Styles.DIALOG_SUBHEADING);
        Label explains = Styles.on(new Label(explains(target)), Styles.DIALOG_HINT);
        explains.setWrapText(true);
        picked.setWrapText(true);
        forget.setTooltip(new Tooltip("The next run closes the game, launches it and picks the window it opens"));
        // The card picked again, so the top bar says what the next run reads now.
        forget.setOnAction(e -> {
            forget();
            onPick.run();
        });
        point = PointAtWindow.button(() -> PointAtWindow.start(services, target, window(), kept -> {
            if (kept.isEmpty()) return;
            pointedAt = kept.get();
            showPick();
            onPick.run();
        }));
        pointHint.setWrapText(true);
        Button use = Styles.on(new Button("Use"), Styles.PRIMARY_BUTTON);
        use.setOnAction(e -> onUse.run());
        HBox buttons = new HBox(8, use, forget, point);
        buttons.setAlignment(Pos.CENTER_LEFT);
        // A button's click is not the card's: a double-click on Point would otherwise also Use and close the window.
        buttons.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        card = Styles.on(new VBox(8, title, explains, picked, buttons, pointHint), Styles.TILE);
        card.setMaxWidth(560);
        card.setOnMouseClicked(e -> {
            onPick.run();
            if (e.getClickCount() == 2) onUse.run();
        });
        showPick();
        PointAtWindow.ifAvailable(() -> {
            canPoint = true;
            showPick();
        });
    }

    /** The card, which the window marks picked like a tile. */
    VBox card() {
        return card;
    }

    Node node() {
        VBox box = new VBox(card);
        box.setStyle("-fx-padding: 14;");
        return box;
    }

    private void showPick() {
        boolean desktopGame = desktopGame(target);
        Optional<CaptureSource> saved = desktopGame ? picks(services).saved(target) : Optional.empty();
        boolean pointed = pointedAt != null && desktopGame
                && picks(services).picked(target).map(WindowIdentity::spec).filter(pointedAt::equals).isPresent();
        String how = pointed ? "Picked by pointing: " : "Picked on a run: ";
        picked.setText(!desktopGame ? "" : saved.map(window -> how + named(window))
                .orElse("No window picked yet: the next run watches the game's launch."));
        picked.setVisible(desktopGame);
        picked.setManaged(desktopGame);
        forget.setVisible(desktopGame);
        forget.setManaged(desktopGame);
        forget.setDisable(saved.isEmpty());
        boolean pointable = desktopGame && canPoint;
        point.setVisible(pointable);
        point.setManaged(pointable);
        pointHint.setVisible(pointable);
        pointHint.setManaged(pointable);
    }

    private Window window() {
        return card.getScene() == null ? null : card.getScene().getWindow();
    }

    private void forget() {
        pointedAt = null;
        Optional<String> refused = picks(services).forget(target);
        services.status(refused.map(why -> "The pick is kept: " + why)
                .orElse("The pick is forgotten: the next run picks the game's window again."));
        showPick();
    }

    /** What the automatic source reads for this bot now, {@link #reads(LaunchSpec, Optional)}. */
    String reads() {
        return reads(target, desktopGame(target) ? picks(services).saved(target) : Optional.empty());
    }

    /**
     * What the automatic source reads for {@code target}, in the top bar's pill: {@code Waydroid (from the game)},
     * {@code window "X" (picked on the first run)}, {@code whole desktop (no game set)}.
     */
    static String reads(LaunchSpec target, Optional<CaptureSource> saved) {
        if (target == null || target.kind() == LaunchKind.UNKNOWN) return "whole desktop (no game set)";
        if (target.kind() == LaunchKind.WEB) return "the game's own browser (from the game)";
        if (target.kind() == LaunchKind.EMULATOR_APP) {
            String instance = target.emulatorInstance();
            return (instance == null || instance.isBlank() ? "the running instance" : instance) + " (from the game)";
        }
        return saved.map(window -> named(window) + " (picked on the first run)")
                .orElse("the window its launch opens (picked on the first run)");
    }

    /** The slot pill's words for the automatic source: {@code Automatic · picked <title>} once a run picked one. */
    static String pillLabel(LaunchSpec target, Optional<CaptureSource> saved) {
        if (!desktopGame(target)) return CaptureLabels.AUTOMATIC;
        return saved.filter(NamedWindow.class::isInstance).map(NamedWindow.class::cast)
                .map(window -> CaptureLabels.AUTOMATIC + " · picked " + window.label())
                .orElse(CaptureLabels.AUTOMATIC);
    }

    /** As {@link #pillLabel(LaunchSpec, Optional)}, for the bot open in Studio. */
    static String pillLabel(StudioServices services) {
        LaunchSpec target = LaunchTargets.spec(services);
        return pillLabel(target, desktopGame(target) ? picks(services).saved(target) : Optional.empty());
    }

    /** The card's sentence: how the automatic source decides for {@code target}. */
    static String explains(LaunchSpec target) {
        if (target == null || target.kind() == LaunchKind.UNKNOWN) {
            return "The bot has no game set, so it reads the whole desktop. Set its game in the Game window and "
                    + "Automatic reads where the game is.";
        }
        if (target.kind() == LaunchKind.WEB) return "The bot reads the website in the browser its run opens.";
        if (target.kind() == LaunchKind.EMULATOR_APP) {
            return "The bot reads the emulator or phone its game runs on: the instance the game names, the one a run "
                    + "is dealt, or the one running.";
        }
        return "The first run closes the game if it runs, launches it and reads the window it opens. That window is "
                + "kept on this PC, so the next runs read it straight away.";
    }

    /** Whether {@code target} is a game whose window a run picks. */
    static boolean desktopGame(LaunchSpec target) {
        return target != null && target.kind() != LaunchKind.UNKNOWN && target.kind() != LaunchKind.WEB
                && target.kind() != LaunchKind.EMULATOR_APP;
    }

    private static String named(CaptureSource window) {
        return window instanceof NamedWindow named ? "window \"" + named.label() + "\"" : String.valueOf(window);
    }

    /** The picks of this PC as Studio reads and changes them, through its values of this PC. */
    static AutoPicks picks(StudioServices services) {
        return new AutoPicks(new AutoPicks.Store() {
            @Override
            public HostWindows read() {
                return opened().flatMap(ctx -> ctx.value(HostWindows.class)).orElse(HostWindows.none());
            }

            @Override
            public Optional<String> update(UnaryOperator<HostWindows> change) {
                Optional<ValueContext> ctx;
                try {
                    ctx = services.hostValues().open(AutoPicks.VALUE);
                } catch (RuntimeException unreadable) {
                    return Optional.of("this PC's values do not open: " + unreadable.getMessage());
                }
                if (ctx.isEmpty()) {
                    // Nothing kept: nothing to forget, and a first pick (the doubt picker's) makes the value first.
                    if (change.apply(HostWindows.none()).equals(HostWindows.none())) return Optional.empty();
                    Optional<String> refused = services.hostValues().create(AutoPicks.VALUE);
                    if (refused.isPresent()) return refused;
                    ctx = services.hostValues().open(AutoPicks.VALUE);
                    if (ctx.isEmpty()) return Optional.of("BotMaker could not open its values of this PC.");
                }
                HostWindows kept = ctx.get().value(HostWindows.class).orElse(null);
                if (kept == null) return Optional.of("this PC's picks do not read, so nothing was written over them");
                HostWindows next = change.apply(kept);
                return next.equals(kept) ? Optional.empty() : ctx.get().write(next);
            }

            private Optional<ValueContext> opened() {
                try {
                    return services.hostValues().open(AutoPicks.VALUE);
                } catch (RuntimeException unreadable) {
                    return Optional.empty();
                }
            }
        });
    }
}
