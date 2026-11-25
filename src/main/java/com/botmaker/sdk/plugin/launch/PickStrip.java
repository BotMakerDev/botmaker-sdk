// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * The strip under the Game window's grid: what the picked tile is, which launcher starts it when several list it,
 * ▶ Try, which starts it now without writing anything, and Use, which makes it the bot's game. Hidden until a tile
 * is picked.
 */
final class PickStrip {

    private final StudioServices services;
    private final QuickLaunch.Report report;
    private final Label name = Styles.on(new Label(), Styles.DIALOG_SUBHEADING);
    private final Label what = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    private final FlowPane launchers = new FlowPane(10, 4);
    private final Button tryIt = new Button("▶ Try");
    private final Button use = Styles.on(new Button("Use"), Styles.PRIMARY_BUTTON);
    private final VBox box;
    private GameCatalog.Game shown;
    private GameCatalog.Way way;

    PickStrip(StudioServices services, QuickLaunch.Report report, Consumer<GameCatalog.Item> onUse) {
        this.services = services;
        this.report = report;
        what.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, name, spacer, tryIt, use);
        top.setAlignment(Pos.CENTER_LEFT);
        use.setOnAction(e -> {
            if (way == null) return;
            // Under the game's own name, whichever launcher's listing spells it.
            onUse.accept(new GameCatalog.Item(way.item().spec(), shown.name(),
                    shown.artwork() != null ? shown.artwork() : way.item().artwork()));
        });
        box = Styles.on(new VBox(6, top, what, launchers), Styles.INSET_FIELD);
        box.setPadding(new Insets(8));
        clear();
    }

    Node node() {
        return box;
    }

    /** Shows {@code game}, its first way picked. */
    void show(GameCatalog.Game game) {
        shown = game;
        name.setText(game.name());
        launchers.getChildren().clear();
        boolean several = game.ways().size() > 1;
        if (several) {
            ToggleGroup group = new ToggleGroup();
            launchers.getChildren().add(Styles.on(new Label("Starts from:"), Styles.SMALL_TEXT));
            for (GameCatalog.Way each : game.ways()) {
                RadioButton radio = new RadioButton(each.launcher());
                radio.setToggleGroup(group);
                radio.setSelected(each == game.ways().getFirst());
                radio.setOnAction(e -> pick(each));
                launchers.getChildren().add(radio);
            }
        }
        launchers.setVisible(several);
        launchers.setManaged(several);
        pick(game.ways().getFirst());
        box.setVisible(true);
        box.setManaged(true);
    }

    /** Hides the strip: nothing is picked. */
    void clear() {
        shown = null;
        way = null;
        box.setVisible(false);
        box.setManaged(false);
    }

    private void pick(GameCatalog.Way chosen) {
        way = chosen;
        what.setText(LaunchSpec.describe(chosen.item().spec()));
        QuickLaunch.bind(tryIt, LaunchSpec.parse(chosen.item().spec()), services, report);
        tryIt.setText("▶ Try");
    }

    /** One tile's worth of {@code item}, for a tab whose tiles are single targets (Recent, Android, Websites). */
    static GameCatalog.Game single(String badge, GameCatalog.Item item) {
        return new GameCatalog.Game(item.name(), item.artwork(), List.of(new GameCatalog.Way(badge, item)), false);
    }
}
