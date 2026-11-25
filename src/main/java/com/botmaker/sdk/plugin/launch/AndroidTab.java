// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Game window's Android apps tab: one chip per emulator instance or phone whose apps were seen, its apps as
 * tiles (dimmed while it is stopped: picking one starts it first), and a last tile to every other emulator or phone.
 */
final class AndroidTab {

    private final ToggleGroup tiles;
    private final Consumer<GameCatalog.Game> onPick;
    private final ToggleGroup chipGroup = new ToggleGroup();
    private final FlowPane chips = new FlowPane(6, 6);
    private final FlowPane grid = new FlowPane(10, 10);
    private final Button other;
    private final VBox box;
    private final Map<String, ToggleButton> bySpec = new LinkedHashMap<>();
    private List<GameCatalog.Section> sections = List.of();
    private String chip;

    AndroidTab(ToggleGroup tiles, Consumer<GameCatalog.Game> onPick, Runnable otherDevice) {
        this.tiles = tiles;
        this.onPick = onPick;
        other = Styles.on(new Button(), Styles.TILE);
        other.setGraphic(Tiles.cover("Other emulator or phone…", null, "+", null));
        Tiles.fixSize(other);
        other.setOnAction(e -> otherDevice.run());
        grid.setRowValignment(VPos.TOP);
        VBox listing = new VBox(8, grid);
        listing.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(listing);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        box = new VBox(8, chips, scroll);
        box.setPadding(new Insets(8, 0, 0, 0));
    }

    Node node() {
        return box;
    }

    /** Lists the emulator and Android sections of {@code scanned}; the chip that was on stays on. */
    void fill(List<GameCatalog.Section> scanned) {
        sections = scanned.stream().filter(s -> s.kind() == GameCatalog.Kind.EMULATOR
                || s.kind() == GameCatalog.Kind.ANDROID).toList();
        bySpec.clear();
        if (sections.stream().noneMatch(s -> s.title().equals(chip))) {
            chip = sections.isEmpty() ? null : sections.getFirst().title();
        }
        chips.getChildren().clear();
        for (GameCatalog.Section section : sections) {
            ToggleButton button = Styles.on(new ToggleButton((section.stopped() ? "○ " : "● ") + section.title()
                    + " " + section.items().size()), Styles.PILL);
            button.setToggleGroup(chipGroup);
            button.setSelected(section.title().equals(chip));
            button.setTooltip(new Tooltip(section.stopped() ? section.title() + " isn't running: launching one of "
                    + "its apps starts it first" : section.title() + " is running"));
            button.setOnAction(e -> {
                button.setSelected(true);
                chip = section.title();
                showChip();
            });
            chips.getChildren().add(button);
        }
        showChip();
    }

    /** Selects {@code spec}'s tile, or none when the chip on doesn't show it. */
    void mark(String spec) {
        tiles.selectToggle(spec == null ? null : bySpec.get(spec));
    }

    private void showChip() {
        List<Node> nodes = new ArrayList<>();
        bySpec.clear();
        for (GameCatalog.Section section : sections) {
            if (!section.title().equals(chip)) continue;
            if (section.note() != null) {
                Label note = Styles.on(new Label(section.note()), Styles.DIALOG_HINT);
                note.setWrapText(true);
                nodes.add(note);
            }
            for (GameCatalog.Item item : section.items()) {
                GameCatalog.Game game = PickStrip.single(null, item);
                String tip = item.name() + "\n" + LaunchSpec.describe(item.spec())
                        + (section.stopped() ? "\n" + section.title() + " isn't running: launching starts it first" : "");
                ToggleButton tile = Tiles.tile(tiles, item.name(), item.artwork(), null, tip,
                        () -> onPick.accept(game));
                tile.setOpacity(section.stopped() ? 0.55 : 1);
                bySpec.put(item.spec(), tile);
                nodes.add(tile);
            }
        }
        nodes.add(other);
        grid.getChildren().setAll(nodes);
    }
}
