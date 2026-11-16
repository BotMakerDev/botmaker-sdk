// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.screens.PageUnknown;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ❔ Unknown, the view 🗺 Screens' rail shows (2026-11-15, the user's pick: the SDK's Screens window, not Studio): the
 * pages the open bot's recent runs met that no screen of its map matched ({@link PageUnknown}), newest run first, and
 * the one picked as an outline of its tree. The pages are masked as they were saved and again as they are read, so
 * nothing here shows an address, a code or what was typed. Read off the FX thread when the view is built and on
 * <b>Reload</b>.
 */
final class UnknownPagesView {

    /** How many of the newest runs are read. */
    static final int RUNS = 10;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private UnknownPagesView() {}

    static Node view(StudioServices services) {
        Path bot = services.projectDir();
        Label status = new Label();
        ListView<PageUnknown.Saved> pages = new ListView<>();
        pages.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(PageUnknown.Saved saved, boolean empty) {
                super.updateItem(saved, empty);
                setText(empty || saved == null ? null : title(saved));
            }
        });
        TreeView<String> outline = new TreeView<>();
        outline.setShowRoot(false);
        pages.getSelectionModel().selectedItemProperty().addListener((o, was, is) ->
                outline.setRoot(is == null ? null : outline(is.page())));

        Button reload = new Button("Reload");
        Runnable read = () -> {
            if (bot == null) {
                status.setText("Open a bot to see the pages its runs did not know.");
                return;
            }
            reload.setDisable(true);
            Thread reader = new Thread(() -> {
                try {
                    List<PageUnknown.Saved> found = PageUnknown.recent(bot, RUNS);
                    Platform.runLater(() -> {
                        pages.getItems().setAll(found);
                        status.setText(said(found.size()));
                        if (!found.isEmpty()) pages.getSelectionModel().selectFirst();
                        reload.setDisable(false);
                    });
                } catch (RuntimeException e) {
                    Platform.runLater(() -> {
                        status.setText("The runs' pages could not be read: " + e.getMessage());
                        reload.setDisable(false);
                    });
                }
            }, "unknown-pages");
            reader.setDaemon(true);
            reader.start();
        };
        reload.setOnAction(e -> read.run());

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox bar = new HBox(8, status, gap, reload);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8));

        SplitPane split = new SplitPane(pages, outline);
        split.setDividerPositions(0.32);
        BorderPane root = new BorderPane(split);
        root.setTop(bar);
        read.run();
        return root;
    }

    /** What the status line says of {@code count} pages. */
    static String said(int count) {
        return count == 0
                ? "The last " + RUNS + " runs met no page their map didn't know."
                : count + (count == 1 ? " page" : " pages") + " the last " + RUNS
                        + " runs didn't know, masked; map one from it in 🗺 Map.";
    }

    /** A page's line in the list: its run, when it was saved, its file. */
    static String title(PageUnknown.Saved saved) {
        String at = CLOCK.format(Instant.ofEpochMilli(saved.page().atMillis()).atZone(ZoneId.systemDefault()));
        return saved.run() + " · " + at + " · " + saved.file();
    }

    /** {@code page} as an outline: one item a node, its children under it. */
    static TreeItem<String> outline(PageUnknown page) {
        TreeItem<String> root = new TreeItem<>("");
        for (PageUnknown.Node node : page.roots()) root.getChildren().add(item(node));
        return root;
    }

    private static TreeItem<String> item(PageUnknown.Node node) {
        TreeItem<String> item = new TreeItem<>(line(node));
        item.setExpanded(true);
        for (PageUnknown.Node child : node.children()) item.getChildren().add(item(child));
        return item;
    }

    /** One node as the outline says it: its class, #id, "text", [label], whether it is a field, where it is. */
    static String line(PageUnknown.Node node) {
        List<String> parts = new ArrayList<>();
        parts.add(node.className().isEmpty() ? "?" : node.className());
        if (!node.id().isEmpty()) parts.add("#" + node.id());
        if (!node.text().isEmpty()) parts.add("\"" + node.text() + "\"");
        if (!node.label().isEmpty()) parts.add("[" + node.label() + "]");
        if (node.field()) parts.add("field");
        Rect r = node.rect();
        if (r != null) parts.add("at " + r.x() + "," + r.y() + " " + r.width() + "×" + r.height());
        return String.join(" ", parts);
    }
}
