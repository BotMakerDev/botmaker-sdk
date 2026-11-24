// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The Explore page's For you tab (plan 2026-11-24-explore-drive-fixes, phase 4; the user's queue of 2026-11-21): the
 * stops only the user decides and the parked ones, each with Resume and Ignore, which the run takes at its next round.
 * FX thread only, but for the answer's write.
 */
final class ExploreYoursTab {

    /** How often, and how many times, a row's buttons look whether the run took the user's answer: two minutes. */
    private static final int ANSWER_POLL_MS = 250, ANSWER_POLLS = 480;

    private final Supplier<Optional<Journal>> journals;
    private final VBox rows = new VBox(6);
    private final Label empty = new Label("Nothing waits on you.");
    private final ScrollPane root;
    /** The queue as last shown, so its buttons are built again only when it changes. */
    private List<JournalView.ForUser> shown = List.of();

    /** {@code journals} gives the latest run's journal, where an answer is written. */
    ExploreYoursTab(Supplier<Optional<Journal>> journals) {
        this.journals = journals;
        rows.setPadding(new Insets(8));
        rows.getChildren().setAll(empty);
        root = new ScrollPane(rows);
        root.setFitToWidth(true);
    }

    Node node() {
        return root;
    }

    /** Shows {@code mine}; true when it differs from what was shown. */
    boolean show(List<JournalView.ForUser> mine) {
        if (mine.equals(shown)) return false;
        shown = List.copyOf(mine);
        if (mine.isEmpty()) rows.getChildren().setAll(empty);
        else rows.getChildren().setAll(mine.stream().map(this::row).toList());
        return true;
    }

    /** How many stops wait on the user. */
    int count() {
        return shown.size();
    }

    /** The queue shown, one line each. */
    List<String> lines() {
        return shown.stream().map(ExploreLines::yourLine).toList();
    }

    /** One stop: what to decide, then resume and ignore. */
    private Node row(JournalView.ForUser item) {
        Stop stop = item.stop();
        Label what = new Label(ExploreLines.yourLine(item));
        what.setWrapText(true);
        what.setMinWidth(0);
        HBox.setHgrow(what, Priority.ALWAYS);
        HBox row = new HBox(6, what);
        row.getStyleClass().add("explore-pinned");
        row.setPadding(new Insets(6));
        row.setAlignment(Pos.CENTER_LEFT);
        if (!item.resumable()) return row;
        Button resume = new Button("Resume");
        resume.setTooltip(new Tooltip("Goes on from it with the bot as it is now: fix it first, by hand or with Claude."));
        Button ignore = new Button("Ignore");
        ignore.setTooltip(new Tooltip("It is fine as it is: the run never raises it again."));
        resume.setOnAction(e -> answer(stop.id(), false, resume, ignore));
        ignore.setOnAction(e -> answer(stop.id(), true, resume, ignore));
        row.getChildren().addAll(resume, ignore);
        return row;
    }

    /**
     * Writes the user's resume of {@code stopId} for the run, off the FX thread. The row's buttons wait until the run
     * took it, then come back: a resume the run refused (the bot does not compile) leaves the stop listed, to try again.
     */
    private void answer(String stopId, boolean ignore, Button... buttons) {
        for (Button button : buttons) button.setDisable(true);
        Thread.ofVirtual().name("explore-answer").start(() -> {
            try {
                Optional<Journal> journal = journals.get();
                if (journal.isEmpty()) return;
                journal.get().command("resume " + stopId + (ignore ? " ignore" : ""));
                for (int i = 0; i < ANSWER_POLLS && journal.get().commandWaiting(); i++) Thread.sleep(ANSWER_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                Platform.runLater(() -> {
                    for (Button button : buttons) button.setDisable(false);
                });
            }
        });
    }
}
