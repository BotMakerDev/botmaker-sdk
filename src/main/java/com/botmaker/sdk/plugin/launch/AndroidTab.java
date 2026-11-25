// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsWindow;
import com.botmaker.shared.emulator.ApkLibrary;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The Game window's Android apps tab (spec {@code 2026-11-25-game-window-design.md}, Android apps): a chip per
 * instance of this computer's emulator and per connected phone ({@link AndroidDevices}), each with a running dot, then
 * + New instance… and System apps; the chip's apps as tiles, dimmed while it is stopped (an action that needs it
 * starts it), and a last tile, + Get an app ({@link GetAppDialog}). The footer opens the Downloaded apps window.
 */
final class AndroidTab {

    private final StudioServices services;
    private final ToggleGroup tiles;
    private final BiConsumer<AndroidDevices.Device, AndroidDevices.App> onPick;
    private final Supplier<Window> owner;
    private final ToggleGroup chipGroup = new ToggleGroup();
    private final FlowPane chips = new FlowPane(6, 6);
    private final CheckBox systemApps = new CheckBox("System apps");
    private final Label note = Styles.on(new Label("Looking for Android devices…"), Styles.DIALOG_HINT);
    private final FlowPane grid = new FlowPane(10, 10);
    private final Button getApp;
    private final Hyperlink downloaded = new Hyperlink("Downloaded apps…");
    private final VBox box;
    private final Map<String, ToggleButton> bySpec = new LinkedHashMap<>();
    /** The desktop's Android entries (Waydroid's), by package: their names and icons win over the cache's. */
    private Map<String, GameCatalog.Item> menu = Map.of();
    private List<AndroidDevices.Device> devices = List.of();
    /** The chip on, by device reference. */
    private String chip;
    /** The bot's target, whose device's chip is on first and whose tile is picked. */
    private String current;
    private boolean scanning;

    AndroidTab(StudioServices services, ToggleGroup tiles, BiConsumer<AndroidDevices.Device, AndroidDevices.App> onPick,
               Supplier<Window> owner) {
        this.services = services;
        this.tiles = tiles;
        this.onPick = onPick;
        this.owner = owner;
        getApp = Styles.on(new Button(), Styles.TILE);
        getApp.setGraphic(Tiles.cover("+ Get an app", null, "+", null));
        Tiles.fixSize(getApp);
        getApp.setOnAction(e -> GetAppDialog.show(services, owner.get(), devices, chip, this::rescan));
        systemApps.setTooltip(new Tooltip("Also list the apps that have no icon on the device: they start on a page"));
        systemApps.setOnAction(e -> showChip());
        note.setWrapText(true);
        downloaded.setOnAction(e -> DownloadedAppsWindow.show(services, owner.get(), devices, this::showSize));
        grid.setRowValignment(VPos.TOP);
        VBox listing = new VBox(8, note, grid);
        listing.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(listing);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        HBox footer = new HBox(8, downloaded);
        footer.setAlignment(Pos.CENTER_LEFT);
        box = new VBox(8, chips, scroll, footer);
        box.setPadding(new Insets(8, 0, 0, 0));
        showSize();
    }

    Node node() {
        return box;
    }

    /** Takes the desktop's Android entries from {@code scanned}, and reads the devices the first time. */
    void fill(List<GameCatalog.Section> scanned) {
        Map<String, GameCatalog.Item> entries = new LinkedHashMap<>();
        for (GameCatalog.Section section : scanned) {
            if (section.kind() != GameCatalog.Kind.ANDROID) continue;
            for (GameCatalog.Item item : section.items()) {
                LaunchSpec spec = LaunchSpec.parse(item.spec());
                if (spec != null && spec.kind() == LaunchKind.EMULATOR_APP) entries.put(spec.emulatorPackage(), item);
            }
        }
        boolean changed = !entries.equals(menu);
        menu = Map.copyOf(entries);
        if (devices.isEmpty() || changed) rescan();
    }

    /** Reads the devices and their apps again, off the JavaFX thread. */
    void rescan() {
        if (scanning) return;
        scanning = true;
        Map<String, GameCatalog.Item> entries = menu;
        Async.load("android-devices", () -> AndroidDevices.scan(entries), found -> {
            scanning = false;
            devices = found;
            showChips();
        }, why -> {
            scanning = false;
            note.setText("Couldn't look for Android devices: " + why);
        });
    }

    /** Selects {@code spec}'s tile, or none when the chip on doesn't show it; its device's chip goes on first. */
    void mark(String spec) {
        current = spec;
        LaunchSpec parsed = spec == null ? null : LaunchSpec.parse(spec);
        String key = parsed == null || parsed.kind() != LaunchKind.EMULATOR_APP ? null
                : LaunchSpec.emulatorApp(parsed.emulatorPackage(), parsed.emulatorInstance(), null).spec();
        tiles.selectToggle(key == null ? null : bySpec.get(key));
    }

    private void showChips() {
        if (devices.stream().noneMatch(d -> d.instance().reference().equals(chip))) {
            LaunchSpec target = current == null ? null : LaunchSpec.parse(current);
            String wanted = target != null && target.kind() == LaunchKind.EMULATOR_APP ? target.emulatorInstance() : null;
            chip = devices.stream().map(d -> d.instance().reference()).filter(r -> r.equals(wanted)).findFirst()
                    .orElse(devices.isEmpty() ? null : devices.getFirst().instance().reference());
        }
        List<Node> nodes = new ArrayList<>();
        for (AndroidDevices.Device device : devices) {
            String reference = device.instance().reference();
            ToggleButton button = Styles.on(new ToggleButton(device.chip()), Styles.PILL);
            button.setToggleGroup(chipGroup);
            button.setSelected(reference.equals(chip));
            button.setTooltip(new Tooltip(device.running() ? device.instance().caption() + " is running"
                    : device.instance().caption() + " isn't running: what needs it starts it"));
            button.setOnAction(e -> {
                // A chip pressed twice stays on: one is always.
                button.setSelected(true);
                chip = reference;
                showChip();
            });
            nodes.add(button);
        }
        Button newInstance = Styles.on(new Button("+ New instance…"), Styles.PILL);
        newInstance.setTooltip(new Tooltip("Opens the Emulators window, where an instance is made"));
        newInstance.setOnAction(e -> EmulatorsWindow.open(services));
        nodes.add(newInstance);
        nodes.add(systemApps);
        chips.getChildren().setAll(nodes);
        showChip();
    }

    private void showChip() {
        bySpec.clear();
        AndroidDevices.Device device = devices.stream().filter(d -> d.instance().reference().equals(chip))
                .findFirst().orElse(null);
        List<Node> nodes = new ArrayList<>();
        if (device == null) {
            say("No Waydroid, LDPlayer or phone was found on this computer. Press + New instance…, or connect a "
                    + "phone in the Emulators window.");
            grid.getChildren().setAll(nodes);
            return;
        }
        for (AndroidDevices.App app : device.apps()) nodes.add(tile(device, app));
        say(device.apps().isEmpty() ? device.running() ? "No app with an icon is on " + device.instance().caption()
                + " yet. Press + Get an app." : device.instance().caption() + " is stopped and no app was seen on it "
                + "yet. Get an app starts it." : null);
        nodes.add(getApp);
        grid.getChildren().setAll(nodes);
        if (systemApps.isSelected()) showSystemApps(device);
        mark(current);
    }

    private void showSystemApps(AndroidDevices.Device device) {
        if (!device.running()) {
            say("Start " + device.instance().caption() + " to list its system apps.");
            return;
        }
        Async.load("android-system-apps", () -> AndroidDevices.systemApps(device), system -> {
            if (!systemApps.isSelected() || !device.instance().reference().equals(chip)) return;
            List<Node> nodes = new ArrayList<>(grid.getChildren());
            nodes.remove(getApp);
            for (AndroidDevices.App app : system) nodes.add(tile(device, app));
            nodes.add(getApp);
            grid.getChildren().setAll(nodes);
            mark(current);
        });
    }

    private ToggleButton tile(AndroidDevices.Device device, AndroidDevices.App app) {
        String spec = device.spec(app);
        String tip = app.name() + "\n" + app.packageName() + " on " + device.instance().caption()
                + (app.launchable() ? "" : "\nNo icon: it starts on a page")
                + (device.running() ? "" : "\n" + device.instance().caption() + " isn't running: launching starts it");
        ToggleButton tile = Tiles.tile(tiles, app.name(), app.icon(), app.launchable() ? null : "system", tip,
                () -> onPick.accept(device, app));
        tile.setOpacity(device.running() ? 1 : 0.55);
        bySpec.put(spec, tile);
        return tile;
    }

    private void say(String text) {
        note.setText(text == null ? "" : text);
        note.setVisible(text != null);
        note.setManaged(text != null);
    }

    /** The footer's link with the library's size, read off the JavaFX thread. */
    private void showSize() {
        Async.load("apk-library-size", () -> ApkLibrary.shared().size(),
                size -> downloaded.setText("Downloaded apps · " + DownloadedApps.size(size) + "…"));
    }
}
