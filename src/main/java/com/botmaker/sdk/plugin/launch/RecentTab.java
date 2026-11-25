// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The Game window's Recent tab: the targets this computer used last ({@link RecentTargets}), any kind, newest first. */
final class RecentTab {

    private final ToggleGroup tiles;
    private final Consumer<GameCatalog.Game> onPick;
    private final FlowPane grid = new FlowPane(10, 10);
    private final Label empty = Styles.on(new Label("Nothing picked on this computer yet."), Styles.DIALOG_HINT);
    private final VBox box;
    private final Map<String, ToggleButton> shown = new LinkedHashMap<>();

    RecentTab(ToggleGroup tiles, Consumer<GameCatalog.Game> onPick) {
        this.tiles = tiles;
        this.onPick = onPick;
        grid.setRowValignment(VPos.TOP);
        VBox listing = new VBox(8, empty, grid);
        listing.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(listing);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        box = new VBox(8, scroll);
        box.setPadding(new Insets(8, 0, 0, 0));
    }

    Node node() {
        return box;
    }

    /**
     * Shows {@code recent}; a pick with no picture of its own takes the one {@code art} has for its target (the
     * scan's).
     */
    void fill(List<RecentTargets.Recent> recent, Map<String, Path> art) {
        shown.clear();
        for (RecentTargets.Recent each : recent) {
            GameCatalog.Item item = new GameCatalog.Item(each.spec(), each.name(),
                    each.artwork() != null ? each.artwork() : art.get(each.spec()));
            LaunchSpec spec = LaunchSpec.parse(each.spec());
            String badge = spec == null ? null : spec.kind().displayName();
            GameCatalog.Game game = PickStrip.single(badge, item);
            shown.put(each.spec(), Tiles.tile(tiles, item.name(), item.artwork(), badge,
                    item.name() + "\n" + LaunchSpec.describe(item.spec()), () -> onPick.accept(game)));
        }
        grid.getChildren().setAll(shown.values());
        empty.setVisible(recent.isEmpty());
        empty.setManaged(recent.isEmpty());
    }

    /** Selects {@code spec}'s tile, or none when it is not one of the recents. */
    void mark(String spec) {
        tiles.selectToggle(spec == null ? null : shown.get(spec));
    }
}
