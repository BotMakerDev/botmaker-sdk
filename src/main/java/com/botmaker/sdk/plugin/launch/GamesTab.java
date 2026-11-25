// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Game window's Games tab: every game this computer's launchers and app menu list, each once with a badge naming
 * its launcher ({@link GameCatalog#games}), chips with counts that filter by launcher, a search, and its footer's Add
 * a game in Lutris… (Linux) and Install from file….
 */
final class GamesTab {

    /** The chip that shows every game, the menu's other apps left out. */
    private static final String ALL = "All";
    /** The chip of the menu's other apps, which can be hundreds. */
    private static final String OTHER_APPS = "Other apps";

    private final Consumer<GameCatalog.Game> onPick;
    private final ToggleGroup tiles;
    private final ToggleGroup chipGroup = new ToggleGroup();
    private final FlowPane chips = new FlowPane(6, 6);
    private final FlowPane grid = new FlowPane(10, 10);
    private final Label notice = Styles.on(new Label(), Styles.DIALOG_HINT);
    private final VBox box;
    /** Each tile kept while its game is listed the same, so a rescan reloads no picture. */
    private Map<GameCatalog.Game, ToggleButton> shown = new HashMap<>();
    private List<GameCatalog.Game> games = List.of();
    private String chip = ALL;
    private String query = "";

    GamesTab(ToggleGroup tiles, Consumer<GameCatalog.Game> onPick, Runnable addInLutris, Runnable fromFile) {
        this.tiles = tiles;
        this.onPick = onPick;
        TextField search = new TextField();
        search.setPromptText("Search…");
        search.textProperty().addListener((obs, was, typed) -> {
            query = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT);
            applyFilter();
        });
        notice.setWrapText(true);
        notice.setText("Looking through this computer's launchers…");
        grid.setRowValignment(VPos.TOP);
        VBox listing = new VBox(8, notice, grid);
        listing.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(listing);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        if (Os.current() == Os.LINUX) {
            Button lutris = new Button("Add a game in Lutris…");
            lutris.setTooltip(new Tooltip("Opens Lutris: add the game there (a store's tab, an installer, or a "
                    + "Windows program from its .exe) and it is listed here"));
            lutris.setOnAction(e -> addInLutris.run());
            footer.getChildren().add(lutris);
        }
        Button file = new Button("Install from file…");
        file.setTooltip(new Tooltip("Pick an .apk, .xapk or .apks file and the emulator or phone to install it on; it "
                + "becomes what this computer launches"));
        file.setOnAction(e -> fromFile.run());
        footer.getChildren().add(file);

        search.setPrefColumnCount(14);
        HBox filterRow = new HBox(8, chips, search);
        HBox.setHgrow(chips, Priority.ALWAYS);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        box = new VBox(8, filterRow, scroll, footer);
        box.setPadding(new Insets(8, 0, 0, 0));
    }

    Node node() {
        return box;
    }

    /** Lists {@code scanned}'s games, the targets in {@code recent} first; the chip that was on stays on. */
    void fill(List<GameCatalog.Section> scanned, List<String> recent) {
        games = GameCatalog.games(scanned, recent);
        Map<GameCatalog.Game, ToggleButton> kept = new HashMap<>();
        for (GameCatalog.Game game : games) {
            ToggleButton had = shown.get(game);
            kept.put(game, had != null ? had : tile(game));
        }
        shown = kept;

        Map<String, Integer> counts = GameCatalog.launcherCounts(games);
        long other = games.stream().filter(GameCatalog.Game::folded).count();
        if (!chip.equals(ALL) && !counts.containsKey(chip) && !(chip.equals(OTHER_APPS) && other > 0)) chip = ALL;
        chips.getChildren().clear();
        chips.getChildren().add(chip(ALL, games.size() - (int) other));
        counts.forEach((launcher, count) -> chips.getChildren().add(chip(launcher, count)));
        if (other > 0) chips.getChildren().add(chip(OTHER_APPS, (int) other));

        boolean none = games.stream().noneMatch(g -> !g.folded());
        notice.setText(Os.current() == Os.LINUX
                ? "Lutris lists no game on this computer. Press Add a game in Lutris…, or pick an Android app or a "
                        + "website in their tabs."
                : "No launcher on this computer lists a game: Steam, Epic, Heroic, GOG and the app menu were looked "
                        + "at. Pick an Android app or a website in their tabs.");
        notice.setVisible(none);
        notice.setManaged(none);
        applyFilter();
    }

    /** Selects the tile whose game {@code spec} starts, or none when this tab doesn't list it. */
    void mark(String spec) {
        for (Map.Entry<GameCatalog.Game, ToggleButton> entry : shown.entrySet()) {
            boolean has = entry.getKey().ways().stream().anyMatch(w -> w.item().spec().equals(spec));
            if (has) {
                tiles.selectToggle(entry.getValue());
                return;
            }
        }
        tiles.selectToggle(null);
    }

    private ToggleButton chip(String name, int count) {
        ToggleButton button = Styles.on(new ToggleButton(name + " " + count), Styles.PILL);
        button.setToggleGroup(chipGroup);
        button.setSelected(name.equals(chip));
        button.setOnAction(e -> {
            // A chip pressed twice stays on: one is always.
            button.setSelected(true);
            chip = name;
            applyFilter();
        });
        return button;
    }

    private ToggleButton tile(GameCatalog.Game game) {
        List<String> launchers = game.launchers();
        String badge = launchers.size() == 1 ? launchers.getFirst() : launchers.size() + " launchers";
        StringBuilder tip = new StringBuilder(game.name());
        for (GameCatalog.Way way : game.ways()) {
            tip.append('\n').append(way.launcher()).append(": ").append(LaunchSpec.describe(way.item().spec()));
        }
        return Tiles.tile(tiles, game.name(), game.artwork(), badge, tip.toString(), () -> onPick.accept(game));
    }

    /** The grid: the games the chip and the search keep, in {@link GameCatalog#games}'s order. */
    private void applyFilter() {
        List<Node> nodes = new ArrayList<>();
        for (GameCatalog.Game game : games) {
            boolean inChip = switch (chip) {
                case ALL -> !game.folded();
                case OTHER_APPS -> game.folded();
                default -> !game.folded() && game.launchers().contains(chip);
            };
            boolean found = query.isEmpty()
                    || (game.name() != null && game.name().toLowerCase(Locale.ROOT).contains(query));
            if (inChip && found) nodes.add(shown.get(game));
        }
        grid.getChildren().setAll(nodes);
    }
}
