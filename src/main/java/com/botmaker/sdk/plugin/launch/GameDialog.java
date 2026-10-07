package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.EmulatorPicker;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.Spawn;
import com.botmaker.shared.emulator.EmulatorAppCache;
import com.botmaker.shared.emulator.EmulatorProbe;
import com.botmaker.shared.game.FaugusEntries;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The game dialog: what this computer starts for the open bot, picked from what the launchers and the app menu
 * on this computer already have, and a <b>▶ Launch game</b> that starts it without running the bot.
 *
 * <p>Opened from the toolbar's game button and from Project Setup's launch row. A row of radio buttons picks
 * what the grid of covers shows: <b>All</b> (every game, each once), <b>Recent</b> picks, one per launcher
 * (Steam, Epic, Heroic, Faugus, Lutris, …), one per emulator instance (its apps by name, dimmed while it is
 * stopped), Waydroid's Android apps, the menu's games, and every other app,
 * which All leaves out. A pick sets this machine's {@code botmaker.launch.target}
 * ({@link LaunchTargetValue}) and is remembered in {@link RecentTargets}.
 *
 * <p>It builds no launch settings of its own (the maintainer's call): a target is which launcher and which of
 * its entries, and the launcher already knows how to start it. The one thing it adds to a launcher is
 * <b>Add a Windows program…</b>, which puts an {@code .exe} into Faugus with Faugus's own defaults.
 */
public final class GameDialog {

    /** A Steam portrait cover's 2:3; a square icon sits centred in it. */
    private static final double COVER_WIDTH = 72;
    private static final double COVER_HEIGHT = 108;
    /** Two caption lines; a longer name ends in an ellipsis and is whole in the tooltip. */
    private static final double CAPTION_HEIGHT = 34;
    /** The tile's own padding (the {@code template-tile} style) on each side, and the cover-to-caption gap. */
    private static final double TILE_PADDING = 6;
    private static final double TILE_GAP = 4;
    private static final double TILE_WIDTH = COVER_WIDTH + 2 * TILE_PADDING;
    private static final double TILE_HEIGHT = COVER_HEIGHT + TILE_GAP + CAPTION_HEIGHT + 2 * TILE_PADDING;

    /** A Flathub download of Faugus and its runtime; a slow mirror, not a hang. */
    private static final Duration FAUGUS_INSTALL_TIMEOUT = Duration.ofMinutes(20);

    private static final ButtonType INSTALL = new ButtonType("Install", ButtonBar.ButtonData.OK_DONE);

    private static GameDialog active;

    private final StudioServices services;
    private final Runnable onChanged;
    private final ToggleGroup picks = new ToggleGroup();
    private final ToggleGroup groupPicks = new ToggleGroup();
    /** One tile per target, so a game listed under All, Recent and its launcher is one selection. */
    private final Map<String, Tile> tiles = new LinkedHashMap<>();
    private final FlowPane filters = new FlowPane(12, 6);
    private final FlowPane grid = new FlowPane(10, 10);
    private final Label notice = Styles.on(new Label(), Styles.DIALOG_HINT);
    private Group shown;
    private String query = "";
    private boolean emulatorsRefreshed;
    private List<GameCatalog.Section> lastScan;
    private final Label current = Styles.on(new Label(), Styles.DIALOG_SUBHEADING);
    private final Label status = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    private final Button launch;
    private final Button clear = new Button("Clear");
    private Stage stage;

    /** One tile, kept for the search and for marking the current target. */
    private record Tile(GameCatalog.Item item, ToggleButton button) {}

    /** One radio button's worth of tiles; {@code extra} is the emulator picker's tile, or {@code null}. */
    private record Group(String title, List<Tile> tiles, Node extra) {}

    private GameDialog(StudioServices services, Runnable onChanged) {
        this.services = services;
        this.onChanged = onChanged == null ? () -> { } : onChanged;
        this.launch = Styles.on(QuickLaunch.button(services, this::report), Styles.PRIMARY_BUTTON);
        launch.setText("▶ Launch game");
    }

    /** The toolbar's press. */
    public static void open(ActionContext context) {
        open(context.services(), Modals.owner(context.services()), null);
    }

    /**
     * Opens the dialog, or brings the open one forward. {@code onChanged} runs after each change of target, on
     * the JavaFX thread — Project Setup re-checks its rows with it.
     */
    public static void open(StudioServices services, Window owner, Runnable onChanged) {
        if (active != null && active.stage != null && active.stage.isShowing()) {
            if (active.services == services) {
                active.stage.toFront();
                active.stage.requestFocus();
                return;
            }
            // Left open from the project before: a pick there would land in that project's settings.
            active.stage.close();
        }
        active = new GameDialog(services, onChanged);
        active.show(owner);
    }

    private void show(Window owner) {
        Label heading = Styles.on(new Label("Which game does this bot play?"), Styles.DIALOG_HEADING);
        Label hint = Styles.on(new Label("Pick it from what this computer's launchers already have — the pick is "
                + "kept on this computer only. ▶ Launch game starts it without running the bot."), Styles.DIALOG_HINT);
        hint.setWrapText(true);

        clear.setOnAction(e -> {
            LaunchTargetValue.set(services, null);
            picks.selectToggle(null);
            refreshCurrent();
            report(true, "This computer launches nothing for the bot now.");
            onChanged.run();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox currentRow = new HBox(8, current, spacer, clear, launch);
        currentRow.setAlignment(Pos.CENTER_LEFT);

        TextField search = new TextField();
        search.setPromptText("Search…");
        search.textProperty().addListener((obs, was, typedQuery) -> {
            query = typedQuery == null ? "" : typedQuery.trim().toLowerCase(Locale.ROOT);
            applySearch();
        });

        notice.setWrapText(true);
        notice.setText("Looking through this computer's launchers…");
        grid.setRowValignment(javafx.geometry.VPos.TOP);
        VBox listing = new VBox(8, notice, grid);
        listing.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(listing);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        TextField typed = new TextField();
        typed.setPromptText("…or type a target: steam:620, lutris:3, exe:/path/to/game");
        HBox.setHgrow(typed, Priority.ALWAYS);
        Button use = new Button("Use");
        Runnable useTyped = () -> GameCatalog.typed(typed.getText()).ifPresentOrElse(
                spec -> choose(new GameCatalog.Item(spec, LaunchSpec.describe(spec), null)),
                () -> report(false, "\"" + typed.getText().trim() + "\" is not a launch target: write it as "
                        + "kind:id, where kind is one of " + GameCatalog.kinds() + "."));
        use.setOnAction(e -> useTyped.run());
        typed.setOnAction(e -> useTyped.run());
        HBox bottom = new HBox(8);
        bottom.setAlignment(Pos.CENTER_LEFT);
        if (Os.current() == Os.LINUX) {
            Button addExe = new Button("Add a Windows program…");
            addExe.setTooltip(new javafx.scene.control.Tooltip("Pick an .exe; it is added to Faugus Launcher, "
                    + "which runs it under Proton, and becomes what this computer launches"));
            addExe.setOnAction(e -> addWindowsProgram());
            bottom.getChildren().add(addExe);
        }
        bottom.getChildren().addAll(typed, use);

        status.setWrapText(true);
        Button close = new Button("Close");
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());
        HBox closeRow = new HBox(close);
        closeRow.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, heading, hint, currentRow, search, filters, scroll, bottom, status, closeRow);
        root.setPadding(new Insets(16));
        stage = Modals.window(services, owner, Modals.Frame.modeless("Game", 760, 640, 520, 420), root);
        stage.setOnHidden(e -> active = null);
        refreshCurrent();
        stage.show();
        rescan();
    }

    /** Re-reads every library off the JavaFX thread and rebuilds the radio row and the grid. */
    private void rescan() {
        Async.load("game-dialog-scan", GameCatalog::scan, this::fill, why -> {
            notice.setText("Couldn't read the libraries: " + why);
            Styles.pick(notice, Styles.ERROR_TEXT, Styles.DIALOG_HINT);
            notice.setVisible(true);
            notice.setManaged(true);
        });
    }

    /**
     * One radio per section after <b>All</b> and <b>Recent</b>. All is every section but the folded one (every
     * other app in the menu, which can be hundreds), each target once; Recent's pictures come from the scan
     * when the remembered pick has none.
     */
    private void fill(List<GameCatalog.Section> scanned) {
        lastScan = scanned;
        tiles.clear();
        Map<String, Path> art = new HashMap<>();
        for (GameCatalog.Section section : scanned) {
            for (GameCatalog.Item item : section.items()) {
                if (item.artwork() != null) art.putIfAbsent(item.spec(), item.artwork());
            }
        }
        Node emulator = null;
        LinkedHashSet<Tile> all = new LinkedHashSet<>();
        List<Group> launchers = new ArrayList<>();
        for (GameCatalog.Section section : scanned) {
            List<Tile> own = section.items().stream().map(this::tileFor).toList();
            if (section.kind() == GameCatalog.Kind.EMULATOR) {
                for (Tile tile : own) stopped(tile, section.stopped() ? section.title() : null);
            }
            Node extra = null;
            if (section.kind() == GameCatalog.Kind.ANDROID) {
                emulator = emulatorTile();
                extra = emulator;
            }
            launchers.add(new Group(section.title(), own, extra));
            if (!section.folded()) all.addAll(own);
        }
        List<Tile> recent = RecentTargets.list(services).stream()
                .map(r -> tileFor(new GameCatalog.Item(r.spec(), r.name(),
                        r.artwork() != null ? r.artwork() : art.get(r.spec()))))
                .toList();
        List<Group> groups = new ArrayList<>();
        groups.add(new Group("All", List.copyOf(all), emulator));
        if (!recent.isEmpty()) groups.add(new Group("Recent", recent, null));
        groups.addAll(launchers);

        notice.setText("No launcher on this computer lists a game: Steam, Epic, Heroic, Faugus, Lutris, GOG and "
                + "the app menu were looked at. Pick an emulator app below, or type a target.");
        Styles.pick(notice, Styles.DIALOG_HINT, Styles.ERROR_TEXT);
        boolean none = GameCatalog.nothingListed(scanned);
        notice.setVisible(none);
        notice.setManaged(none);

        // A rescan (after adding a Windows program) keeps the radio that was on.
        String was = shown == null ? null : shown.title();
        Group first = groups.stream().filter(g -> g.title().equals(was)).findFirst().orElse(groups.getFirst());
        filters.getChildren().clear();
        for (Group group : groups) {
            RadioButton radio = new RadioButton(group.title() + " (" + group.tiles().size() + ")");
            radio.setToggleGroup(groupPicks);
            radio.setSelected(group == first);
            radio.setOnAction(e -> showGroup(group));
            filters.getChildren().add(radio);
        }
        showGroup(first);
        markCurrent();
        refreshEmulatorsOnce();
    }

    /**
     * Once per dialog, after the remembered apps are on screen: asks each running emulator for its apps now, and
     * rescans if they changed — a game installed since, or an instance never listed before. Reading a new app's
     * name and icon takes a moment, which is why the remembered ones are shown first.
     */
    private void refreshEmulatorsOnce() {
        if (emulatorsRefreshed) return;
        emulatorsRefreshed = true;
        Async.load("game-dialog-emulators", GameCatalog::refreshEmulators, changed -> {
            if (!changed || stage == null || !stage.isShowing() || lastScan == null) return;
            // Only the emulator sections changed: the launchers' libraries aren't read again.
            List<GameCatalog.Section> scanned = lastScan;
            Async.load("game-dialog-emulator-sections", () -> GameCatalog.withEmulatorsReread(scanned), this::fill);
        });
    }

    /**
     * A stopped instance's app is shown dimmed, and says that picking it starts the instance — a launch starts it
     * before the app ({@code EmulatorAppLauncher}). {@code instance} is its caption, or {@code null} when it runs.
     */
    private static void stopped(Tile tile, String instance) {
        tile.button().setOpacity(instance == null ? 1 : 0.55);
        tile.button().setTooltip(new javafx.scene.control.Tooltip(tile.item().name() + "\n"
                + LaunchSpec.describe(tile.item().spec())
                + (instance == null ? "" : "\n" + instance + " isn't running: launching starts it first")));
    }

    /** Puts {@code group}'s tiles in the grid, then hides those the search leaves out. */
    private void showGroup(Group group) {
        shown = group;
        List<Node> nodes = new ArrayList<>();
        for (Tile tile : group.tiles()) nodes.add(tile.button());
        if (group.extra() != null) nodes.add(group.extra());
        grid.getChildren().setAll(nodes);
        applySearch();
    }

    /** The tile for {@code item}'s target, made the first time the target is seen. */
    private Tile tileFor(GameCatalog.Item item) {
        return tiles.computeIfAbsent(item.spec(), spec -> new Tile(item, tile(item)));
    }

    private ToggleButton tile(GameCatalog.Item item) {
        ToggleButton button = Styles.on(new ToggleButton(), Styles.TILE);
        button.setToggleGroup(picks);
        button.setGraphic(cover(item.name(), item.artwork(), initials(item.name())));
        fixSize(button);
        button.setTooltip(new javafx.scene.control.Tooltip(item.name() + "\n" + LaunchSpec.describe(item.spec())));
        button.setOnAction(e -> {
            // A toggle clicked twice would deselect; the current target stays picked instead.
            button.setSelected(true);
            choose(item);
        });
        return button;
    }

    /** The Android section's way to every other emulator and a phone: the emulator picker. */
    private Node emulatorTile() {
        Button other = Styles.on(new Button(), Styles.TILE);
        other.setGraphic(cover("Other emulator or phone…", null, "+"));
        fixSize(other);
        other.setOnAction(e -> EmulatorPicker.show(services, stage).ifPresent(chosen -> {
            if (!chosen.hasApp()) {
                report(false, "Pick an app inside " + chosen.instance().name() + ", not only the device.");
                return;
            }
            String spec = new LaunchSpec(LaunchKind.EMULATOR_APP,
                    chosen.appPackage() + "@" + chosen.instance().name()).spec();
            // The app's own name and icon when the picker has seen them, as its card in the instance's section has.
            EmulatorAppCache cache = EmulatorAppCache.shared();
            String name = cache.packages(chosen.instance()).stream()
                    .filter(app -> app.packageName().equals(chosen.appPackage()))
                    .map(EmulatorProbe.InstalledApp::display).findFirst().orElse(chosen.appPackage());
            choose(new GameCatalog.Item(spec, name, cache.iconPath(chosen.instance(), chosen.appPackage())));
        }));
        return other;
    }

    /**
     * Every tile the same size, whatever its caption. Only a width used to be set, and a wrapping caption's
     * preferred height is measured before its width is known — a long name made a tile hundreds of pixels tall.
     */
    private static void fixSize(javafx.scene.control.ButtonBase tile) {
        tile.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        tile.setMinSize(TILE_WIDTH, TILE_HEIGHT);
        tile.setPrefSize(TILE_WIDTH, TILE_HEIGHT);
        tile.setMaxSize(TILE_WIDTH, TILE_HEIGHT);
    }

    /** {@code name}'s cover, or {@code mark} in its place when there is no picture. */
    private static Node cover(String name, Path artwork, String mark) {
        StackPane frame = new StackPane();
        frame.setPrefSize(COVER_WIDTH, COVER_HEIGHT);
        frame.setMinSize(COVER_WIDTH, COVER_HEIGHT);
        frame.setMaxSize(COVER_WIDTH, COVER_HEIGHT);
        if (artwork != null) {
            // Decoded at twice the frame so it stays sharp on a scaled display.
            ImageView view = new ImageView(new Image(artwork.toUri().toString(), 0, 2 * COVER_HEIGHT, true, true,
                    true));
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setFitWidth(COVER_WIDTH);
            view.setFitHeight(COVER_HEIGHT);
            frame.getChildren().add(view);
        } else {
            frame.getChildren().add(Styles.on(new Label(mark), Styles.CAPTION_STRONG));
        }
        Label caption = Styles.on(new Label(name), Styles.TILE_NAME);
        caption.setWrapText(true);
        caption.setTextOverrun(OverrunStyle.ELLIPSIS);
        caption.setAlignment(Pos.TOP_CENTER);
        caption.setTextAlignment(TextAlignment.CENTER);
        caption.setMinSize(COVER_WIDTH, CAPTION_HEIGHT);
        caption.setPrefSize(COVER_WIDTH, CAPTION_HEIGHT);
        caption.setMaxSize(COVER_WIDTH, CAPTION_HEIGHT);
        VBox content = new VBox(TILE_GAP, frame, caption);
        content.setAlignment(Pos.TOP_CENTER);
        return content;
    }

    /** Makes {@code item} what this computer launches, remembers it, and says so. */
    private void choose(GameCatalog.Item item) {
        // Remembered first: setting the target refreshes the toolbar, whose label reads the name from here.
        RecentTargets.remember(services, new RecentTargets.Recent(item.spec(), item.name(), item.artwork()));
        LaunchTargetValue.set(services, item.spec());
        refreshCurrent();
        markCurrent();
        report(true, "This computer now launches " + item.name() + ".");
        onChanged.run();
    }

    /** The header line, and the two buttons that act on the current target. */
    private void refreshCurrent() {
        String spec = LaunchTargetValue.current(services);
        current.setText(spec == null ? "Nothing picked yet." : "Launches: " + GameButton.nameOf(services, spec));
        clear.setDisable(spec == null);
        QuickLaunch.bind(launch, services, this::report);
    }

    /** Selects the current target's tile, so reopening shows what is picked. */
    private void markCurrent() {
        Tile tile = tiles.get(LaunchTargetValue.current(services));
        picks.selectToggle(tile == null ? null : tile.button());
    }

    /** Hides the shown group's tiles whose name doesn't contain the search; the emulator tile stays. */
    private void applySearch() {
        if (shown == null) return;
        for (Tile tile : shown.tiles()) {
            boolean match = query.isEmpty() || tile.item().name().toLowerCase(Locale.ROOT).contains(query);
            tile.button().setVisible(match);
            tile.button().setManaged(match);
        }
    }

    /**
     * Picks an {@code .exe}, adds it to Faugus under its file name, and makes it the target. Faugus missing: the
     * offer to install it from Flathub first.
     */
    private void addWindowsProgram() {
        // installed() may ask flatpak, so off the JavaFX thread.
        Async.load("faugus-installed", FaugusEntries::installed, installed -> {
            if (installed) pickWindowsProgram();
            else offerFaugus();
        });
    }

    private void pickWindowsProgram() {
        services.dialogs().chooseFile("Choose a Windows program", Path.of(System.getProperty("user.home", "")),
                "exe").path().ifPresent(exe -> {
            String file = exe.getFileName().toString();
            String title = file.toLowerCase(Locale.ROOT).endsWith(".exe") ? file.substring(0, file.length() - 4)
                    : file;
            try {
                String gameId = FaugusEntries.add(exe, title);
                choose(new GameCatalog.Item(new LaunchSpec(LaunchKind.FAUGUS, gameId).spec(), title, null));
                report(true, title + " is in Faugus now, with Faugus's default prefix and runner — change them in "
                        + "Faugus. This computer launches it.");
                rescan();
            } catch (Exception ex) {
                report(false, "Couldn't add " + file + " to Faugus: " + ex.getMessage());
            }
        });
    }

    private void offerFaugus() {
        List<String> command = FaugusEntries.installCommand();
        if (command.isEmpty()) {
            Alert info = services.theme().alert(Alert.AlertType.INFORMATION, "A Windows program runs here through "
                    + "Faugus Launcher, which isn't installed, and neither is Flatpak to fetch it. Install Faugus "
                    + "Launcher with your package manager, then try again.", ButtonType.CLOSE);
            info.initOwner(stage);
            info.setHeaderText("Faugus Launcher isn't installed");
            info.showAndWait();
            return;
        }
        Alert ask = services.theme().alert(Alert.AlertType.CONFIRMATION, "A Windows program runs here through "
                + "Faugus Launcher, which runs it under Proton. BotMaker will run\n\n" + String.join(" ", command)
                + "\n\nwhich downloads it from Flathub.", ButtonType.CANCEL, INSTALL);
        ask.initOwner(stage);
        ask.setHeaderText("Install Faugus Launcher?");
        if (ask.showAndWait().orElse(ButtonType.CANCEL) != INSTALL) return;
        report(true, "Installing Faugus Launcher…");
        Async.load("install-faugus", () -> {
            try {
                Spawn.Completed done = Spawn.run(FAUGUS_INSTALL_TIMEOUT, command);
                return done != null && done.ok() && FaugusEntries.installed();
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            }
        }, ok -> report(ok, ok ? "Faugus Launcher is installed — press Add a Windows program… again."
                : "Faugus Launcher wasn't installed. Run " + String.join(" ", command) + " in a terminal to see why."),
                why -> report(false, "Couldn't install Faugus Launcher: " + why));
    }

    private void report(boolean ok, String message) {
        status.setText(message);
        Styles.pick(status, ok ? Styles.MUTED_TEXT : Styles.ERROR_TEXT, Styles.MUTED_TEXT, Styles.ERROR_TEXT);
    }

    private static String initials(String label) {
        if (label == null || label.isBlank()) return "?";
        StringBuilder out = new StringBuilder();
        for (String word : label.trim().split("\\s+")) {
            if (!word.isEmpty()) out.append(Character.toUpperCase(word.charAt(0)));
            if (out.length() >= 3) break;
        }
        return out.toString();
    }
}
