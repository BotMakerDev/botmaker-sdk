// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.apps.AndroidDevices;
import com.botmaker.sdk.plugin.emulator.apps.EmulatorApps;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsWindow;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The Game window's Android apps tab (spec {@code 2026-11-25-game-window-design.md}, Android apps): a chip per
 * instance of this computer's emulator and per connected phone ({@link AndroidDevices}), each with a running dot, then
 * + New instance… and System apps; the chip's apps as tiles, dimmed while it is stopped (an action that needs it
 * starts it). It only picks an app already on a device: getting or deleting one is the emulator's, in the Emulators
 * window ({@link EmulatorApps}), which the footer opens on the chip's instance; ↻ reads the devices again.
 */
final class AndroidTab {

    private final StudioServices services;
    private final ToggleGroup tiles;
    private final BiConsumer<AndroidDevices.Device, AndroidDevices.App> onPick;
    private final ToggleGroup chipGroup = new ToggleGroup();
    private final FlowPane chips = new FlowPane(6, 6);
    private final CheckBox systemApps = new CheckBox("System apps");
    private final Label note = Styles.on(new Label("Looking for Android devices…"), Styles.DIALOG_HINT);
    private final FlowPane grid = new FlowPane(10, 10);
    private final Hyperlink manageApps = new Hyperlink("Get or delete apps: Emulators…");
    private final VBox box;
    private final Map<String, ToggleButton> bySpec = new LinkedHashMap<>();
    /** The desktop's Android entries (Waydroid's), by package: their names and icons win over the cache's. */
    private Map<String, AndroidDevices.App> menu = Map.of();
    private List<AndroidDevices.Device> devices = List.of();
    /** The chip on, by device reference. */
    private String chip;
    /** The bot's target, whose device's chip is on first and whose tile is picked. */
    private String current;
    private boolean scanning;

    AndroidTab(StudioServices services, ToggleGroup tiles, BiConsumer<AndroidDevices.Device, AndroidDevices.App> onPick) {
        this.services = services;
        this.tiles = tiles;
        this.onPick = onPick;
        systemApps.setTooltip(new Tooltip("Also list the apps that have no icon on the device: they start on a page"));
        systemApps.setOnAction(e -> showChip());
        note.setWrapText(true);
        // Getting or deleting an app is the emulator's, in the Emulators window; this tab only picks one on a device.
        manageApps.setTooltip(new Tooltip("Get an app onto a device, or delete downloaded ones, in the Emulators window"));
        manageApps.setOnAction(e -> openEmulators());
        Button refresh = new Button("↻");
        refresh.setTooltip(new Tooltip("Read the devices and their apps again"));
        refresh.setOnAction(e -> rescan());
        grid.setRowValignment(VPos.TOP);
        VBox listing = new VBox(8, note, grid);
        listing.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(listing);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        HBox footer = new HBox(8, manageApps, refresh);
        footer.setAlignment(Pos.CENTER_LEFT);
        box = new VBox(8, chips, scroll, footer);
        box.setPadding(new Insets(8, 0, 0, 0));
    }

    /** The Emulators window, on the chip's instance when one is on. */
    private void openEmulators() {
        devices.stream().filter(d -> d.instance().reference().equals(chip)).findFirst()
                .ifPresentOrElse(d -> EmulatorsWindow.open(services, d.instance()), () -> EmulatorsWindow.open(services));
    }

    Node node() {
        return box;
    }

    /** Takes the desktop's Android entries from {@code scanned}, and reads the devices the first time. */
    void fill(List<GameCatalog.Section> scanned) {
        Map<String, AndroidDevices.App> entries = new LinkedHashMap<>();
        for (GameCatalog.Section section : scanned) {
            if (section.kind() != GameCatalog.Kind.ANDROID) continue;
            for (GameCatalog.Item item : section.items()) {
                LaunchSpec spec = LaunchSpec.parse(item.spec());
                if (spec != null && spec.kind() == LaunchKind.EMULATOR_APP) entries.put(spec.emulatorPackage(),
                        new AndroidDevices.App(spec.emulatorPackage(), item.name(), item.artwork(), true));
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
        Map<String, AndroidDevices.App> entries = menu;
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
        say(device.apps().isEmpty() ? (device.running() ? "No app with an icon is on " + device.instance().caption()
                + " yet." : device.instance().caption() + " is stopped and no app was seen on it yet.")
                + " Get one in the Emulators window, below." : null);
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
            for (AndroidDevices.App app : system) nodes.add(tile(device, app));
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
}
