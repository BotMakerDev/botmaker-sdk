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

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * The strip under the Game window's grid: what the picked tile is, which launcher starts it when several list it,
 * for an Android app where it starts ({@link StartsOnPart}), ▶ Try, which starts it now without writing anything, and
 * Use, which makes it the bot's game. Hidden until a tile is picked.
 */
final class PickStrip {

    private final StudioServices services;
    private final QuickLaunch.Report report;
    private final Label name = Styles.on(new Label(), Styles.DIALOG_SUBHEADING);
    private final Label what = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    private final FlowPane launchers = new FlowPane(10, 4);
    private final StartsOnPart startsOn = new StartsOnPart(this::androidChanged);
    private final Button tryIt = new Button("▶ Try");
    private final Button use = Styles.on(new Button("Use"), Styles.PRIMARY_BUTTON);
    private final VBox box;
    private GameCatalog.Game shown;
    /** The spec ▶ Try starts and Use writes; {@code null} while the choice gives none. */
    private String spec;
    private Path artwork;

    PickStrip(StudioServices services, QuickLaunch.Report report, Consumer<GameCatalog.Item> onUse) {
        this.services = services;
        this.report = report;
        what.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, name, spacer, tryIt, use);
        top.setAlignment(Pos.CENTER_LEFT);
        // Under the game's own name, whichever launcher's listing spells it.
        use.setOnAction(e -> {
            if (spec != null) onUse.accept(new GameCatalog.Item(spec, shown.name(), artwork));
        });
        box = Styles.on(new VBox(6, top, what, launchers, startsOn.node()), Styles.INSET_FIELD);
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
        visible(launchers, several);
        visible(startsOn.node(), false);
        pick(game.ways().getFirst());
        visible(box, true);
    }

    /** Shows {@code app} on {@code device}, with where it starts. */
    void show(AndroidDevices.Device device, AndroidDevices.App app) {
        shown = new GameCatalog.Game(app.name(), app.icon(), List.of(), false);
        artwork = app.icon();
        name.setText(app.name());
        visible(launchers, false);
        visible(startsOn.node(), true);
        startsOn.show(device, app);
        visible(box, true);
    }

    /** Hides the strip: nothing is picked. */
    void clear() {
        shown = null;
        spec = null;
        visible(box, false);
    }

    private void pick(GameCatalog.Way chosen) {
        artwork = shown.artwork() != null ? shown.artwork() : chosen.item().artwork();
        bind(chosen.item().spec(), report);
    }

    /** The Android choice changed: ▶ Try and Use follow it, and an ok Try on a page shows where it landed. */
    private void androidChanged() {
        if (!startsOn.node().isManaged()) return;
        bind(startsOn.spec(), (ok, message) -> {
            report.accept(ok, message);
            if (ok) startsOn.tried();
        });
    }

    private void bind(String target, QuickLaunch.Report tried) {
        spec = target;
        what.setText(target == null ? "" : LaunchSpec.describe(target));
        QuickLaunch.bind(tryIt, target == null ? null : LaunchSpec.parse(target), services, tried);
        tryIt.setText("▶ Try");
        use.setDisable(target == null);
    }

    private static void visible(Node node, boolean on) {
        node.setVisible(on);
        node.setManaged(on);
    }

    /** One tile's worth of {@code item}, for a tab whose tiles are single targets (Recent, Websites). */
    static GameCatalog.Game single(String badge, GameCatalog.Item item) {
        return new GameCatalog.Game(item.name(), item.artwork(), List.of(new GameCatalog.Way(badge, item)), false);
    }
}
