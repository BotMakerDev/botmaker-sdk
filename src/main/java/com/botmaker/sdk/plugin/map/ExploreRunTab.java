// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.explore.Agent;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.plugin.run.SdkRuns;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Explore page's Run tab (plan 2026-11-24-explore-drive-fixes, phase 4): on the left the game's frame, fitted whole
 * into its box by width and height with its readouts' areas drawn over it, the screen's name and the facts it reads;
 * on the right the stop the run waits on, pinned, and the feed, newest first. FX thread only.
 */
final class ExploreRunTab {

    private final FramePane frame = new FramePane();
    private final Label screenName = new Label();
    private final VBox facts = new VBox(2);
    private final Label pinnedStop = new Label();
    private final Label pinnedClaude = new Label();
    private final VBox pinned = new VBox(2, pinnedStop, pinnedClaude);
    private final ListView<ExploreLines.FeedRow> feed = new ListView<>();
    private final Thumbnails thumbnails;
    private final SplitPane root;

    ExploreRunTab(Thumbnails thumbnails) {
        this.thumbnails = thumbnails;
        screenName.getStyleClass().add("explore-screen");
        VBox.setVgrow(frame, Priority.ALWAYS);
        VBox now = new VBox(6, frame, screenName, facts);
        now.setPadding(new Insets(8));
        now.setMinWidth(0);

        pinned.getStyleClass().add("explore-pinned");
        pinned.setPadding(new Insets(6));
        pinnedStop.setWrapText(true);
        pinnedClaude.setWrapText(true);
        pinnedClaude.getStyleClass().add("explore-claude");
        feed.setPlaceholder(new Label("Nothing yet: ▶ " + SdkRuns.KEEP_EXPLORING
                + " explores the game, and what it finds shows here."));
        feed.setCellFactory(list -> new FeedCell());
        VBox right = new VBox(6, pinned, feed);
        VBox.setVgrow(feed, Priority.ALWAYS);
        right.setPadding(new Insets(8));
        right.setMinWidth(0);

        root = new SplitPane(now, right);
        root.setDividerPositions(0.45);
        show(new JournalView(List.of(), List.of(), List.of()), false, true);
    }

    Node node() {
        return root;
    }

    /** The scale that fits a {@code width}×{@code height} frame whole into a {@code boxWidth}×{@code boxHeight} box. */
    static double fit(double width, double height, double boxWidth, double boxHeight) {
        if (width <= 0 || height <= 0 || boxWidth <= 0 || boxHeight <= 0) return 0;
        return Math.min(boxWidth / width, boxHeight / height);
    }

    /** Draws {@code image}, with {@code areas} (in its pixels) over it. */
    void frame(Image image, Map<String, Rect> areas) {
        frame.show(image, areas);
    }

    /** The areas drawn over the frame, which stays as last seen. */
    void areas(Map<String, Rect> areas) {
        frame.show(frame.image.getImage(), areas);
    }

    /** The scale the frame is drawn at now; 0 while none is. */
    double frameScale() {
        return frame.scale;
    }

    /** {@code read} drawn: the stop pinned while {@code live}, and the feed when {@code changed}. */
    void show(JournalView read, boolean live, boolean changed) {
        Optional<Stop> open = live ? read.open() : Optional.empty();
        pinned.setVisible(open.isPresent());
        pinned.setManaged(open.isPresent());
        open.ifPresent(waiting -> {
            pinnedStop.setText(ExploreLines.pinnedLine(waiting));
            pinnedClaude.setText(ExploreLines.claudeSince(read, waiting).map(call -> "Claude: " + call)
                    .orElse("Claude: not on it yet"));
        });
        if (!changed) return;
        List<ExploreLines.FeedRow> rows = ExploreLines.feedRows(read);
        if (!rows.equals(feed.getItems())) feed.getItems().setAll(rows);
    }

    /** The screen the frame shows, and the facts it reads. */
    void screen(String on, List<String> factLines) {
        screenName.setText(on == null ? "No screen of the map" : on);
        List<String> shown = facts.getChildren().stream().map(n -> ((Label) n).getText()).toList();
        if (!factLines.equals(shown)) facts.getChildren().setAll(factLines.stream().map(Label::new).toList());
    }

    /** The feed shown, newest first. */
    List<ExploreLines.FeedRow> feedRows() {
        return List.copyOf(feed.getItems());
    }

    /** The stop pinned and what Claude did last, one line each; empty while nothing is pinned. */
    List<String> pinnedLines() {
        return pinned.isVisible() ? List.of(pinnedStop.getText(), pinnedClaude.getText()) : List.of();
    }

    /**
     * The game's frame, fitted whole into whatever box its column gives it, keeping its ratio (Review Focus 5): a tall
     * phone frame by its height, a wide one by its width. It asks for no size of its own, so a large frame never
     * pushes the column wider.
     */
    private static final class FramePane extends Region {

        private final ImageView image = new ImageView();
        private final Pane boxes = new Pane();
        private Map<String, Rect> areas = Map.of();
        private double scale;
        /** The areas and scale they were last drawn for, so a layout pass draws them again only when one moved. */
        private Map<String, Rect> drawnAreas;
        private double drawnScale = -1;

        FramePane() {
            image.setPreserveRatio(true);
            image.setSmooth(true);
            boxes.setMouseTransparent(true);
            boxes.setManaged(false);
            image.setManaged(false);
            getChildren().addAll(image, boxes);
            setMinSize(0, 0);
        }

        void show(Image frame, Map<String, Rect> areas) {
            image.setImage(frame);
            this.areas = areas;
            requestLayout();
        }

        @Override
        protected double computePrefWidth(double height) {
            return 240;
        }

        @Override
        protected double computePrefHeight(double width) {
            return 240;
        }

        @Override
        protected void layoutChildren() {
            Image frame = image.getImage();
            scale = frame == null ? 0 : fit(frame.getWidth(), frame.getHeight(), getWidth(), getHeight());
            if (scale == 0) {
                image.setVisible(false);
                boxes.getChildren().clear();
                drawnScale = -1;
                return;
            }
            image.setVisible(true);
            image.setFitWidth(frame.getWidth() * scale);
            image.setFitHeight(frame.getHeight() * scale);
            image.relocate(0, 0);
            boxes.relocate(0, 0);
            if (scale != drawnScale || !areas.equals(drawnAreas)) {
                boxes.getChildren().clear();
                FrameBoxes.areas(boxes, areas, scale);
                drawnScale = scale;
                drawnAreas = areas;
            }
        }
    }

    /** A feed row: the agent's chip, a thumbnail of the frame its record names, and its line. */
    private final class FeedCell extends ListCell<ExploreLines.FeedRow> {

        private static final double THUMB_WIDTH = 48;

        private final Label chip = new Label();
        private final ImageView thumb = new ImageView();
        private final Label text = new Label();
        private final HBox box = new HBox(6, chip, thumb, text);

        FeedCell() {
            chip.getStyleClass().add("explore-chip");
            thumb.setPreserveRatio(true);
            thumb.setFitWidth(THUMB_WIDTH);
            text.setMinWidth(0);
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(ExploreLines.FeedRow row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            boolean named = row.agent() != Agent.UNKNOWN;
            chip.setText(named ? row.agent().displayName() : "");
            chip.setVisible(named);
            chip.setManaged(named);
            Image image = thumbnails.of(row.frame(), THUMB_WIDTH).orElse(null);
            thumb.setImage(image);
            thumb.setVisible(image != null);
            thumb.setManaged(image != null);
            text.setText(row.text());
            setText(null);
            setGraphic(box);
        }
    }
}
