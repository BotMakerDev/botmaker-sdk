// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.ChecksRow;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsWindow;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.emulator.checks.CheckResult;
import com.botmaker.shared.emulator.checks.EmulatorChecks;
import com.botmaker.shared.launch.GameLauncher;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The Game window: what this computer starts for the open bot, picked from what the launchers, the emulators and
 * the web offer, and a <b>Launch</b> that starts it without running the bot.
 *
 * <p>A frame (spec {@code 2026-11-25-game-window-design.md}): a top bar saying what the bot plays, with a ▾ of the
 * other targets it supports ({@code launchTargets} in {@code Sdk.java}, {@link LaunchTargetValue}) to prefer or
 * remove, and Launch; under it, only when the target's emulator has a problem, one line linking to the Emulators
 * window. Then the tabs — {@link GamesTab}, {@link AndroidTab}, {@link WebsitesTab}, {@link RecentTab} — and the
 * {@link PickStrip} of the picked tile, whose Use makes it the bot's preferred target and remembers it in
 * {@link RecentTargets}. The emulator checks and installs are not this window's: they run by themselves, and the
 * Emulators window keeps them.
 *
 * <p>It builds no launch settings of its own (the maintainer's call): a target is which launcher and which of its
 * entries, and the launcher already knows how to start it.
 */
public final class GameDialog {

    private static GameDialog active;

    private final StudioServices services;
    private final Runnable onChanged;
    /** Set when a launcher editor asked: a pick goes to it and closes the dialog, and nothing is written. */
    private final Consumer<String> picked;
    private final GamesTab games;
    private final AndroidTab android;
    private final RecentTab recent;
    private final WebsitesTab websites;
    private final PickStrip strip;
    private final Label plays = Styles.on(new Label(), Styles.DIALOG_SUBHEADING);
    private final MenuButton supported = new MenuButton("▾");
    private final Button launch;
    private final Label warning = Styles.on(new Label(), Styles.WARNING_TEXT);
    private final HBox warningRow;
    private final Label status = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    private boolean emulatorsRefreshed;
    /** Set by Add a game in Lutris…: the next time this window is focused, the libraries are read again once. */
    private boolean lutrisOpened;
    private List<GameCatalog.Section> lastScan;
    private Stage stage;

    private GameDialog(StudioServices services, Runnable onChanged, Consumer<String> picked) {
        this.services = services;
        this.onChanged = onChanged == null ? () -> { } : onChanged;
        this.picked = picked;
        this.launch = Styles.on(QuickLaunch.button(services, this::report), Styles.PRIMARY_BUTTON);
        launch.setText("Launch");
        this.strip = new PickStrip(services, this::report, this::choose);
        // A group per tab: each shows the current target's tile picked.
        this.games = new GamesTab(new ToggleGroup(), strip::show, this::openLutris,
                () -> InstallGame.fromFile(services, stage, this::report, this::installed));
        this.android = new AndroidTab(services, new ToggleGroup(), strip::show);
        this.recent = new RecentTab(new ToggleGroup(), strip::show);
        this.websites = new WebsitesTab(strip::show, this::report);
        Hyperlink emulators = new Hyperlink("Emulators…");
        emulators.setOnAction(e -> EmulatorsWindow.open(services));
        warningRow = new HBox(6, warning, emulators);
        warningRow.setAlignment(Pos.CENTER_LEFT);
    }

    /** The toolbar's press. */
    public static void open(ActionContext context) {
        open(context.services(), Modals.owner(context.services()), null);
    }

    /**
     * Opens the dialog, or brings the open one forward. {@code onChanged} runs after each change of target, on
     * the JavaFX thread.
     */
    public static void open(StudioServices services, Window owner, Runnable onChanged) {
        if (active != null && active.stage != null && active.stage.isShowing()) {
            // A pick an editor is waiting for is brought forward too, never dropped.
            if (active.services == services) {
                active.stage.toFront();
                active.stage.requestFocus();
                return;
            }
            // Left open from the project before: a pick there would land in that project's settings.
            active.stage.close();
        }
        active = new GameDialog(services, onChanged, null);
        active.show(owner);
    }

    /**
     * Opens the dialog to pick one target for a launcher editor (the Runner's, the Parameters window's): {@code picked}
     * gets its launch spec, on the JavaFX thread, and the dialog closes. Nothing is written to the bot; the editor
     * hands the value back.
     */
    public static void pick(StudioServices services, Window owner, Consumer<String> picked) {
        if (active != null && active.stage != null && active.stage.isShowing()) active.stage.close();
        active = new GameDialog(services, null, picked);
        active.show(owner);
    }

    private void show(Window owner) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, plays, supported, spacer, launch);
        top.setAlignment(Pos.CENTER_LEFT);
        // Picking for an editor: the bot's targets are not what is being chosen, and Remove would write them.
        boolean own = picked == null;
        for (Node node : List.<Node>of(top, warningRow)) {
            node.setVisible(own);
            node.setManaged(own);
        }
        warningRow.setVisible(false);
        warningRow.setManaged(false);

        TabPane tabs = new TabPane(tab("Games", games.node()), tab("Android apps", android.node()),
                tab("Websites", websites.node()), tab("Recent", recent.node()));
        tabs.getSelectionModel().selectedItemProperty().addListener((o, was, is) -> strip.clear());
        VBox.setVgrow(tabs, Priority.ALWAYS);

        status.setWrapText(true);
        Button close = new Button("Close");
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox bottom = new HBox(8, status, gap, close);
        bottom.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(status, Priority.SOMETIMES);

        VBox root = new VBox(8, top, warningRow, tabs, strip.node(), bottom);
        root.setPadding(new Insets(12, 16, 12, 16));
        stage = Modals.window(services, owner, Modals.Frame.modeless("Game", 820, 680, 560, 460), root);
        stage.setOnHidden(e -> active = null);
        // Back from Lutris with a game added: read the libraries again, once per visit to Lutris.
        stage.focusedProperty().addListener((o, was, is) -> {
            if (!is || !lutrisOpened) return;
            lutrisOpened = false;
            rescan();
        });
        refreshCurrent();
        stage.show();
        Async.load("game-dialog-saved", GameCatalog::saved, saved -> {
            if (lastScan == null) saved.ifPresent(this::fill);
            rescan();
        });
    }

    private static Tab tab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    /**
     * Re-reads every library off the JavaFX thread; the tabs change only when the libraries did. The saved list is
     * on screen meanwhile, so a game added or removed since appears or goes, and nothing else moves.
     */
    private void rescan() {
        Async.load("game-dialog-scan", GameCatalog::scan, scanned -> {
            if (!scanned.equals(lastScan)) fill(scanned);
            // After the scan, not the saved fill: a scan that read the emulator cache before the refresh wrote it
            // would put the old apps back, and a refresh over the saved list would be dropped by the scan's.
            refreshEmulatorsOnce();
        }, why -> report(false, "Couldn't read the libraries: " + why));
    }

    /** Each tab from {@code scanned}; the recents take a scanned picture when the remembered pick has none. */
    private void fill(List<GameCatalog.Section> scanned) {
        lastScan = scanned;
        Map<String, Path> art = new HashMap<>();
        for (GameCatalog.Section section : scanned) {
            for (GameCatalog.Item item : section.items()) {
                if (item.artwork() != null) art.putIfAbsent(item.spec(), item.artwork());
            }
        }
        List<RecentTargets.Recent> recents = RecentTargets.list(services);
        games.fill(scanned, recents.stream().map(RecentTargets.Recent::spec).toList());
        android.fill(scanned);
        recent.fill(recents, art);
        markCurrent();
    }

    /**
     * Once per dialog, after the remembered apps are on screen: asks each running emulator for its apps now, and
     * refills if they changed — a game installed since, or an instance never listed before. Reading a new app's
     * name and icon takes a moment, which is why the remembered ones are shown first.
     */
    private void refreshEmulatorsOnce() {
        if (emulatorsRefreshed) return;
        emulatorsRefreshed = true;
        Async.load("game-dialog-emulators", GameCatalog::refreshEmulators, changed -> {
            if (!changed || stage == null || !stage.isShowing() || lastScan == null) return;
            // Only the emulator sections changed: the launchers' libraries aren't read again.
            List<GameCatalog.Section> scanned = lastScan;
            Async.load("game-dialog-emulator-sections", () -> GameCatalog.withEmulatorsReread(scanned),
                    reread -> fillOver(scanned, reread));
        });
    }

    /**
     * A game just installed on an emulator: it becomes what this computer launches, and the emulators are read
     * again so its tile is there. The install already read its name and icon into the cache.
     */
    private void installed(GameCatalog.Item item) {
        choose(item);
        if (stage == null || !stage.isShowing() || lastScan == null) return;
        List<GameCatalog.Section> scanned = lastScan;
        Async.load("game-dialog-installed", () -> GameCatalog.withEmulatorsReread(scanned),
                reread -> fillOver(scanned, reread));
    }

    /**
     * Fills {@code reread}, the emulator sections read again over {@code base}, unless a scan replaced {@code base}
     * meanwhile: the saved list is on screen first, and its launchers must not come back over the scan's.
     */
    private void fillOver(List<GameCatalog.Section> base, List<GameCatalog.Section> reread) {
        if (lastScan == base) fill(reread);
    }

    /** Makes {@code item} what this computer launches, remembers it, and says so. */
    private void choose(GameCatalog.Item item) {
        RecentTargets.Recent remembered = new RecentTargets.Recent(item.spec(), item.name(), item.artwork());
        if (picked != null) {
            RecentTargets.remember(services, remembered);
            stage.close();
            picked.accept(item.spec());
            return;
        }
        Optional<String> refused = LaunchTargetValue.set(services, item.spec());
        if (refused.isPresent()) {
            report(false, refused.get());
            return;
        }
        // After the write: remembering is a settings change, which relabels the toolbar from the bot's new first
        // launcher — remembered before, it read the old one (2026-10-19).
        RecentTargets.remember(services, remembered);
        changed("The bot now supports " + item.name() + " and prefers it; this computer launches it.");
    }

    /** The top bar, its ▾ of the bot's targets, Launch, and the emulator's warning line. */
    private void refreshCurrent() {
        String spec = LaunchTargetValue.current(services);
        plays.setText(spec == null ? "Plays nothing yet: pick a game below."
                : "▶ Plays: " + GameButton.caption(services, spec));
        QuickLaunch.bind(launch, services, this::report);
        if (picked == null) {
            refreshSupported(spec);
            refreshWarning(spec);
        }
    }

    /**
     * The ▾: one entry per target the bot supports, preferred first, each with Prefer and Remove. {@code now} — what
     * this computer launches — is marked ▶; one this system cannot start says so.
     */
    private void refreshSupported(String now) {
        List<String> targets = LaunchTargetValue.all(services);
        supported.getItems().clear();
        supported.setDisable(targets.isEmpty());
        Os os = Os.current();
        for (String spec : targets) {
            LaunchSpec parsed = LaunchSpec.parse(spec);
            Optional<String> notHere = parsed == null
                    ? Optional.of(spec + " is not a launch target.") : parsed.notHere(os);
            Menu entry = new Menu((spec.equals(now) ? "▶ " : "") + GameButton.caption(services, spec)
                    + (notHere.isEmpty() ? "" : " (not on " + os.displayName() + ")"));
            MenuItem prefer = new MenuItem("Prefer");
            prefer.setDisable(spec.equals(now));
            prefer.setOnAction(e -> prefer(spec));
            MenuItem drop = new MenuItem("Remove from this bot");
            drop.setOnAction(e -> drop(spec));
            entry.getItems().addAll(prefer, new SeparatorMenuItem(), drop);
            supported.getItems().add(entry);
        }
    }

    /** The one line under the top bar when the target's emulator would stop a launch, read off the FX thread. */
    private void refreshWarning(String spec) {
        LaunchSpec parsed = spec == null ? null : LaunchSpec.parse(spec);
        if (parsed == null || parsed.kind() != LaunchKind.EMULATOR_APP || parsed.emulatorInstance() == null) {
            warningRow.setVisible(false);
            warningRow.setManaged(false);
            return;
        }
        Async.load("game-dialog-checks", () -> EmulatorChecks.stoppingLaunch(ChecksRow.read(parsed, null).results()),
                stopping -> {
                    // A pick made meanwhile has its own read.
                    if (!spec.equals(LaunchTargetValue.current(services))) return;
                    warning.setText(stopping.map(failed -> warningOf(parsed, failed)).orElse(""));
                    warningRow.setVisible(stopping.isPresent());
                    warningRow.setManaged(stopping.isPresent());
                }, why -> { });
    }

    private static String warningOf(LaunchSpec spec, CheckResult failed) {
        return spec.emulatorInstance() + ": " + failed.check().displayName()
                + (failed.reason() == null || failed.reason().isBlank() ? "" : " — " + failed.reason());
    }

    /** Makes {@code spec}, already one of the bot's targets, the preferred one. */
    private void prefer(String spec) {
        Optional<String> refused = LaunchTargetValue.set(services, spec);
        if (refused.isPresent()) {
            report(false, refused.get());
            return;
        }
        changed("The bot now prefers " + GameButton.nameOf(services, spec) + ".");
    }

    /** Takes {@code spec} out of the bot's targets. */
    private void drop(String spec) {
        Optional<String> refused = LaunchTargetValue.remove(services, spec);
        if (refused.isPresent()) {
            report(false, refused.get());
            return;
        }
        String next = LaunchTargetValue.current(services);
        changed(GameButton.nameOf(services, spec) + " is no longer one of the bot's games"
                + (next == null ? "; this computer launches nothing now." : "; this computer launches "
                + GameButton.nameOf(services, next) + "."));
    }

    /** After a write: the top bar and the picked tile follow, and the status says {@code what}. */
    private void changed(String what) {
        refreshCurrent();
        markCurrent();
        report(true, what);
        onChanged.run();
    }

    /** Selects the current target's tile, so reopening shows what is picked. */
    private void markCurrent() {
        // Picking for an editor: the bot's first launcher is not the one being edited.
        if (picked != null) return;
        String spec = LaunchTargetValue.current(services);
        games.mark(spec);
        android.mark(spec);
        recent.mark(spec);
    }

    /**
     * Opens Lutris for the user to add a game, the one launcher on Linux (2026-10-29). Its library is read again
     * when this window is focused next ({@code pga.db} changed), so the game appears without a button.
     */
    private void openLutris() {
        Async.load("open-lutris", GameLauncher::openLutris, opened -> {
            lutrisOpened = opened;
            report(opened, opened
                    ? "Lutris is open: add the game there, then come back to pick it."
                    : "Lutris isn't installed. Install it from your distribution or Flathub (net.lutris.Lutris): it "
                            + "runs native, Steam, Epic, GOG and Windows games.");
        });
    }

    private void report(boolean ok, String message) {
        status.setText(message);
        Styles.pick(status, ok ? Styles.MUTED_TEXT : Styles.ERROR_TEXT, Styles.MUTED_TEXT, Styles.ERROR_TEXT);
    }
}
