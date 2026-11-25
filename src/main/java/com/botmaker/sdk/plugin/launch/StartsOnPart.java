// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.apps.AndroidDevices;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import com.botmaker.shared.emulator.AdbDevice;
import com.botmaker.shared.emulator.AndroidDoor;
import com.botmaker.shared.emulator.AndroidPage;
import com.botmaker.shared.emulator.AppPages;
import com.botmaker.shared.emulator.EmulatorProbe;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The strip's Starts on, for an Android app ({@link StartsOn}): its first screen (absent for an app with no icon), a
 * page from the list — the doors that land in it, then the activities it exports — or the page open on the device
 * now, read again on ↻, with a green line when a page reaches it and a red one, Use off, when none does. After ▶ Try
 * on a page, a small frame shows where it landed.
 */
final class StartsOnPart {

    /** How long after a launch the frame is taken: the page has drawn by then. */
    private static final long FRAME_AFTER_MS = 2_500;

    private final Runnable onChange;
    private final ToggleGroup group = new ToggleGroup();
    private final RadioButton first = radio(StartsOn.Choice.FIRST_SCREEN);
    private final RadioButton page = radio(StartsOn.Choice.PAGE);
    private final RadioButton now = radio(StartsOn.Choice.OPEN_NOW);
    private final ComboBox<AndroidPage> pages = new ComboBox<>();
    private final Button reread = new Button("↻");
    private final Label line = Styles.on(new Label(), Styles.SMALL_TEXT);
    private final ImageView frame = new ImageView();
    private final VBox box;
    private AndroidDevices.Device device;
    private AndroidDevices.App app;
    private AppPages.Now openNow;

    StartsOnPart(Runnable onChange) {
        this.onChange = onChange;
        pages.setButtonCell(new PageCell());
        pages.setCellFactory(list -> new PageCell());
        pages.setPromptText("Pick a page");
        pages.valueProperty().addListener((o, was, is) -> {
            if (is != null) page.setSelected(true);
            changed();
        });
        reread.setTooltip(new Tooltip("Read the page open on the device again"));
        reread.setOnAction(e -> {
            now.setSelected(true);
            readNow();
        });
        group.selectedToggleProperty().addListener((o, was, is) -> {
            if (is == now && openNow == null) readNow();
            changed();
        });
        line.setWrapText(true);
        frame.setFitHeight(120);
        frame.setPreserveRatio(true);
        frame.setVisible(false);
        frame.setManaged(false);
        HBox choices = new HBox(10, Styles.on(new Label("Starts on:"), Styles.SMALL_TEXT), first, page, pages, now,
                reread);
        choices.setAlignment(Pos.CENTER_LEFT);
        box = new VBox(4, choices, line, frame);
    }

    Node node() {
        return box;
    }

    /** Shows the choices for {@code chosen} on {@code on}: its first screen picked when it has one, else a page. */
    void show(AndroidDevices.Device on, AndroidDevices.App chosen) {
        device = on;
        app = chosen;
        openNow = null;
        frame.setImage(null);
        frame.setVisible(false);
        frame.setManaged(false);
        now.setText(StartsOn.Choice.OPEN_NOW.displayName().replace("now", "on " + on.instance().name() + " now"));
        first.setVisible(chosen.launchable());
        first.setManaged(chosen.launchable());
        List<AndroidPage> doors = new ArrayList<>();
        AndroidDoor.into(chosen.packageName()).forEach(door -> doors.add(new AndroidPage.Door(door)));
        pages.getItems().setAll(doors);
        pages.setValue(null);
        group.selectToggle(chosen.launchable() ? first : page);
        if (on.running()) {
            Async.load("starts-on-pages", () -> {
                try (AdbDevice adb = AdbDevice.connect(on.instance())) {
                    return AppPages.pages(adb, chosen.packageName());
                }
            }, read -> {
                if (device == on && app == chosen && !read.isEmpty()) {
                    AndroidPage was = pages.getValue();
                    pages.getItems().setAll(read);
                    pages.setValue(was);
                }
            }, why -> { });
        }
        changed();
    }

    /** The spec Use writes and ▶ Try starts, or {@code null} when the choice gives none. */
    String spec() {
        if (device == null) return null;
        return StartsOn.spec(app.packageName(), device.instance().reference(), app.launchable(), choice(),
                pages.getValue(), openNow);
    }

    /** After an ok ▶ Try on a page: a frame of where it landed, a moment later. */
    void tried() {
        if (device == null || choice() == StartsOn.Choice.FIRST_SCREEN) return;
        AndroidDevices.Device on = device;
        Async.load("starts-on-frame", () -> {
            try {
                Thread.sleep(FRAME_AFTER_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            BufferedImage shot = EmulatorProbe.screencap(on.instance());
            return shot == null ? null : ScreenCapture.toFxImage(shot);
        }, (Image image) -> {
            if (device != on || image == null) return;
            frame.setImage(image);
            frame.setVisible(true);
            frame.setManaged(true);
        }, why -> { });
    }

    private StartsOn.Choice choice() {
        if (group.getSelectedToggle() == page) return StartsOn.Choice.PAGE;
        if (group.getSelectedToggle() == now) return StartsOn.Choice.OPEN_NOW;
        return StartsOn.Choice.FIRST_SCREEN;
    }

    private void readNow() {
        AndroidDevices.Device on = device;
        if (on == null) return;
        line.setText("Reading the page open on " + on.instance().name() + "…");
        Styles.pick(line, Styles.MUTED_TEXT, Styles.MUTED_TEXT, Styles.OK_TEXT, Styles.ERROR_TEXT);
        Async.load("starts-on-now", () -> {
            try (AdbDevice adb = AdbDevice.connect(on.instance())) {
                return AppPages.openNow(adb);
            }
        }, read -> {
            if (device != on) return;
            openNow = read;
            changed();
        }, why -> {
            if (device != on) return;
            openNow = new AppPages.NoPage();
            changed();
        });
    }

    /** The line under the choices, and the strip told the spec changed. */
    private void changed() {
        if (device == null) return;
        String text;
        boolean ok;
        switch (choice()) {
            case OPEN_NOW -> {
                StartsOn.Line said = StartsOn.line(app.packageName(), device.instance().name(), openNow);
                text = said.text();
                ok = said.ok();
            }
            case PAGE -> {
                ok = pages.getValue() != null;
                text = ok ? "Starts on " + StartsOn.label(pages.getValue()) + "." : pages.getItems().isEmpty()
                        ? "No page of this app opens to an intent." : "Pick the page it starts on.";
            }
            default -> {
                ok = true;
                text = "";
            }
        }
        line.setText(text);
        line.setVisible(!text.isEmpty());
        line.setManaged(!text.isEmpty());
        Styles.pick(line, ok ? Styles.OK_TEXT : Styles.ERROR_TEXT, Styles.MUTED_TEXT, Styles.OK_TEXT,
                Styles.ERROR_TEXT);
        onChange.run();
    }

    private RadioButton radio(StartsOn.Choice choice) {
        RadioButton radio = new RadioButton(choice.displayName());
        radio.setToggleGroup(group);
        return radio;
    }

    /** A page by its plain name, the component in its tooltip. */
    private static final class PageCell extends ListCell<AndroidPage> {
        @Override
        protected void updateItem(AndroidPage item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : StartsOn.label(item));
            setTooltip(empty || item == null ? null : new Tooltip(item.id()));
        }
    }
}
