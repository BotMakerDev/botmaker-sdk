package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.EmulatorPicker;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.Spawn;
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
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
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
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The game dialog: what this computer starts for the open bot, picked from what the launchers and the app menu
 * on this computer already have, and a <b>▶ Launch game</b> that starts it without running the bot.
 *
 * <p>Opened from the toolbar's game button and from Project Setup's launch row. The recent picks come first,
 * then one section per launcher (Steam, Heroic, Faugus, Lutris, …), then Waydroid's Android apps, the menu's
 * games and — folded — every other app. A pick sets this machine's {@code botmaker.launch.target}
 * ({@link LaunchTargetValue}) and is remembered in {@link RecentTargets}.
 *
 * <p>It builds no launch settings of its own (the maintainer's call): a target is which launcher and which of
 * its entries, and the launcher already knows how to start it. The one thing it adds to a launcher is
 * <b>Add a Windows program…</b>, which puts an {@code .exe} into Faugus with Faugus's own defaults.
 */
public final class GameDialog {

    private static final double COVER_WIDTH = 96;
    private static final double COVER_HEIGHT = 128;

    /** A Flathub download of Faugus and its runtime; a slow mirror, not a hang. */
    private static final Duration FAUGUS_INSTALL_TIMEOUT = Duration.ofMinutes(20);

    private static final ButtonType INSTALL = new ButtonType("Install", ButtonBar.ButtonData.OK_DONE);

    private static GameDialog active;

    private final StudioServices services;
    private final Runnable onChanged;
    private final ToggleGroup picks = new ToggleGroup();
    private final List<Tile> tiles = new ArrayList<>();
    private final List<TitledPane> panes = new ArrayList<>();
    private final VBox sections = new VBox(8);
    private final Label current = Styles.on(new Label(), Styles.DIALOG_SUBHEADING);
    private final Label status = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    private final Button launch;
    private final Button clear = new Button("Clear");
    private Stage stage;

    /** One tile, kept for the search and for marking the current target. */
    private record Tile(GameCatalog.Item item, ToggleButton button) {}

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
        search.textProperty().addListener((obs, was, query) -> filter(query));

        sections.getChildren().setAll(Styles.on(new Label("Looking through this computer's launchers…"),
                Styles.DIALOG_HINT));
        ScrollPane scroll = new ScrollPane(sections);
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

        VBox root = new VBox(10, heading, hint, currentRow, search, scroll, bottom, status, closeRow);
        root.setPadding(new Insets(16));
        stage = Modals.window(services, owner, Modals.Frame.modeless("Game", 760, 640, 520, 420), root);
        stage.setOnHidden(e -> active = null);
        refreshCurrent();
        stage.show();
        rescan();
    }

    /** Re-reads every library off the JavaFX thread and rebuilds the sections. */
    private void rescan() {
        Async.load("game-dialog-scan", GameCatalog::scan, this::fill,
                why -> sections.getChildren().setAll(Styles.on(new Label("Couldn't read the libraries: " + why),
                        Styles.ERROR_TEXT)));
    }

    private void fill(List<GameCatalog.Section> scanned) {
        tiles.clear();
        panes.clear();
        List<Node> nodes = new ArrayList<>();
        List<GameCatalog.Item> recent = RecentTargets.list(services).stream()
                .map(r -> new GameCatalog.Item(r.spec(), r.name(), r.artwork())).toList();
        if (GameCatalog.nothingListed(scanned)) {
            Label none = Styles.on(new Label("No launcher on this computer lists a game: Steam, Epic, Heroic, "
                    + "Faugus, Lutris, GOG and the app menu were looked at. Pick an emulator app below, or type a "
                    + "target."), Styles.DIALOG_HINT);
            none.setWrapText(true);
            nodes.add(none);
        }
        if (!recent.isEmpty()) nodes.add(pane("Recent", false, recent, null));
        for (GameCatalog.Section section : scanned) {
            Node extra = section.kind() == GameCatalog.Kind.ANDROID ? emulatorTile() : null;
            nodes.add(pane(section.title(), section.folded(), section.items(), extra));
        }
        sections.getChildren().setAll(nodes);
        markCurrent();
    }

    private TitledPane pane(String title, boolean folded, List<GameCatalog.Item> items, Node extra) {
        FlowPane grid = new FlowPane(10, 10);
        for (GameCatalog.Item item : items) {
            ToggleButton button = tile(item);
            tiles.add(new Tile(item, button));
            grid.getChildren().add(button);
        }
        if (extra != null) grid.getChildren().add(extra);
        TitledPane pane = new TitledPane(title + " (" + items.size() + ")", grid);
        pane.setExpanded(!folded);
        pane.setAnimated(false);
        pane.setUserData(folded);
        panes.add(pane);
        return pane;
    }

    private ToggleButton tile(GameCatalog.Item item) {
        ToggleButton button = Styles.on(new ToggleButton(), Styles.TILE);
        button.setToggleGroup(picks);
        button.setGraphic(cover(item.name(), item.artwork()));
        button.setPrefWidth(COVER_WIDTH + 12);
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
        other.setGraphic(cover("Other emulator or phone…", null));
        other.setPrefWidth(COVER_WIDTH + 12);
        other.setOnAction(e -> EmulatorPicker.show(services, stage).ifPresent(chosen -> {
            if (!chosen.hasApp()) {
                report(false, "Pick an app inside " + chosen.instance().name() + ", not only the device.");
                return;
            }
            String spec = new LaunchSpec(LaunchKind.EMULATOR_APP,
                    chosen.appPackage() + "@" + chosen.instance().name()).spec();
            choose(new GameCatalog.Item(spec, chosen.appPackage(), null));
        }));
        return other;
    }

    private static Node cover(String name, Path artwork) {
        StackPane frame = new StackPane();
        frame.setPrefSize(COVER_WIDTH, COVER_HEIGHT);
        frame.setMinSize(COVER_WIDTH, COVER_HEIGHT);
        frame.setMaxSize(COVER_WIDTH, COVER_HEIGHT);
        if (artwork != null) {
            ImageView view = new ImageView(new Image(artwork.toUri().toString(), 0, COVER_HEIGHT, true, true, true));
            view.setPreserveRatio(true);
            view.setFitWidth(COVER_WIDTH);
            view.setFitHeight(COVER_HEIGHT);
            frame.getChildren().add(view);
        } else {
            frame.getChildren().add(Styles.on(new Label(initials(name)), Styles.CAPTION_STRONG));
        }
        Label caption = Styles.on(new Label(name), Styles.TILE_NAME);
        caption.setWrapText(true);
        caption.setMaxWidth(COVER_WIDTH + 8);
        caption.setAlignment(Pos.CENTER);
        VBox content = new VBox(6, frame, caption);
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

    /** Selects the first tile for the current target, so reopening shows what is picked. */
    private void markCurrent() {
        String spec = LaunchTargetValue.current(services);
        for (Tile tile : tiles) {
            if (tile.item().spec().equals(spec)) {
                picks.selectToggle(tile.button());
                return;
            }
        }
        picks.selectToggle(null);
    }

    /** Hides tiles that don't match, and sections left empty; a search opens the folded ones. */
    private void filter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (Tile tile : tiles) {
            boolean match = needle.isEmpty() || tile.item().name().toLowerCase(Locale.ROOT).contains(needle);
            tile.button().setVisible(match);
            tile.button().setManaged(match);
        }
        for (TitledPane pane : panes) {
            FlowPane grid = (FlowPane) pane.getContent();
            boolean any = needle.isEmpty() || grid.getChildren().stream().anyMatch(n -> n.isManaged()
                    && n instanceof ToggleButton);
            pane.setVisible(any);
            pane.setManaged(any);
            pane.setExpanded(needle.isEmpty() ? !(Boolean) pane.getUserData() : any);
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
