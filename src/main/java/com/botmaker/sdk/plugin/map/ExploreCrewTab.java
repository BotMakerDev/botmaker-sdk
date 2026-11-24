// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.internal.explore.Agent;
import com.botmaker.sdk.internal.explore.AgentSummary;
import com.botmaker.sdk.plugin.assist.WorkerLog;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

/**
 * The Explore page's Crew tab (plan 2026-11-24-explore-drive-fixes, phase 4): the crew listed on the left, each with
 * its sessions, time and last line; on the right the selected agent's log as its worker writes it, read by
 * {@link CrewSessions}. It follows the newest session, scrolled to its end as it grows, until the user picks an
 * earlier one in the picker. The Explorer and the Coordinator are the run's own code and have no session. FX thread
 * only.
 */
final class ExploreCrewTab {

    private final ListView<AgentSummary> crew = new ListView<>();
    private final Label heading = new Label();
    private final ComboBox<CrewSessions.Session> picker = new ComboBox<>();
    private final ListView<WorkerLog.Line> log = new ListView<>();
    private final SplitPane root;
    /** The sessions the picker holds. */
    private List<CrewSessions.Session> sessions = List.of();
    /** Set while the tab moves the picker itself, so its move is not read as the user's pick. */
    private boolean moving;
    /** See {@link #filled()}. */
    private boolean filled;
    /** Whether the log follows the newest session: until the user picks another. */
    private boolean following = true;
    /** The agent whose log is shown. */
    private Agent chosen = Agent.READER;

    /** {@code onSelect} is told when the selected agent changes, to read its logs at once. */
    ExploreCrewTab(Runnable onSelect) {
        crew.setCellFactory(list -> new AgentCell());
        // The list is set again as its times move, which clears and restores the selection: only a new agent counts.
        crew.getSelectionModel().selectedItemProperty().addListener((o, was, is) -> {
            if (is == null || is.agent() == chosen) return;
            chosen = is.agent();
            following = true;
            clear();
            heading();
            onSelect.run();
        });
        crew.setMinWidth(0);

        heading.getStyleClass().add("explore-heading");
        picker.setPromptText("No session yet");
        picker.setMaxWidth(Double.MAX_VALUE);
        picker.valueProperty().addListener((o, was, is) -> {
            if (moving) return;
            following = is == null || is.equals(sessions.isEmpty() ? null : sessions.getLast());
            showLog();
        });
        HBox.setHgrow(picker, Priority.ALWAYS);
        HBox head = new HBox(8, heading, picker);
        head.setAlignment(Pos.CENTER_LEFT);
        log.setCellFactory(list -> new LineCell());
        log.setPlaceholder(new Label("Nothing written yet."));
        VBox right = new VBox(6, head, log);
        VBox.setVgrow(log, Priority.ALWAYS);
        right.setPadding(new Insets(8));
        right.setMinWidth(0);

        root = new SplitPane(crew, right);
        root.setDividerPositions(0.3);
    }

    Node node() {
        return root;
    }

    /** The agent whose log is shown; the Reader before any is picked. */
    Agent selected() {
        return chosen;
    }

    /** Shows {@code agent}'s log, as its chip's click does. */
    void select(Agent agent) {
        if (agent != chosen) {
            chosen = agent;
            following = true;
            clear();
        }
        selectRow();
        heading();
    }

    private void selectRow() {
        AgentSummary row = crew.getSelectionModel().getSelectedItem();
        if (row != null && row.agent() == chosen) return;
        for (int i = 0; i < crew.getItems().size(); i++) {
            if (crew.getItems().get(i).agent() == chosen) crew.getSelectionModel().select(i);
        }
    }

    private void heading() {
        boolean worker = CrewSessions.roleOf(chosen).isPresent();
        heading.setText(worker ? chosen.displayName() : chosen.displayName()
                + ": the run's own code, no session to read; its lines are the Run tab's feed");
        picker.setVisible(worker);
        picker.setManaged(worker);
    }

    /** The crew's summaries, one row each, the selection kept. */
    void crew(List<AgentSummary> summaries) {
        if (!summaries.equals(crew.getItems())) crew.getItems().setAll(summaries);
        selectRow();
        heading();
    }

    /**
     * Whether the log shows the selected agent's sessions as read; false once a pick cleared it, until a read is shown:
     * the next read is then a whole one, as a read with nothing new would leave the log empty.
     */
    boolean filled() {
        return filled;
    }

    /** A new agent picked: its log is empty until its sessions are read. */
    private void clear() {
        sessions(List.of());
        filled = false;
    }

    /** The selected agent's sessions as read now, oldest first. */
    void sessions(List<CrewSessions.Session> now) {
        filled = true;
        CrewSessions.Session picked = picker.getValue();
        sessions = List.copyOf(now);
        moving = true;
        try {
            picker.getItems().setAll(sessions);
            CrewSessions.Session keep = following || picked == null ? null : sessions.stream()
                    .filter(s -> s.stopId().equals(picked.stopId()) && s.number() == picked.number()).findFirst()
                    .orElse(null);
            if (keep == null) following = true;
            picker.setValue(keep != null ? keep : sessions.isEmpty() ? null : sessions.getLast());
        } finally {
            moving = false;
        }
        showLog();
    }

    private void showLog() {
        CrewSessions.Session session = picker.getValue();
        List<WorkerLog.Line> lines = session == null ? List.of() : session.lines();
        if (lines.equals(log.getItems())) return;
        log.getItems().setAll(lines);
        if (following && !lines.isEmpty()) log.scrollTo(lines.size() - 1);
    }

    /** The crew list's rows, one line each. */
    List<String> crewLines() {
        return crew.getItems().stream().map(AgentCards::line).toList();
    }

    /** The log shown, one "Kind: text" line each. */
    List<String> logLines() {
        return log.getItems().stream().map(ExploreCrewTab::text).toList();
    }

    /** The sessions the picker offers, by their titles. */
    List<String> sessionTitles() {
        return picker.getItems().stream().map(CrewSessions.Session::title).toList();
    }

    static String text(WorkerLog.Line line) {
        return line.kind().displayName() + ": " + line.text();
    }

    /** A crew row: the agent's whole summary. */
    private static final class AgentCell extends ListCell<AgentSummary> {
        @Override
        protected void updateItem(AgentSummary summary, boolean empty) {
            super.updateItem(summary, empty);
            setGraphic(null);
            setText(empty || summary == null ? null : AgentCards.line(summary));
            setWrapText(true);
        }
    }

    /** A log line: its kind, then its text, wrapped. */
    private final class LineCell extends ListCell<WorkerLog.Line> {

        private final Label kind = new Label();
        private final Label text = new Label();
        private final HBox box = new HBox(8, kind, text);
        private PseudoClass kindClass;

        LineCell() {
            kind.getStyleClass().add("explore-log-kind");
            kind.setMinWidth(80);
            text.setWrapText(true);
            text.setMinWidth(0);
            text.maxWidthProperty().bind(log.widthProperty().subtract(120));
            box.setAlignment(Pos.TOP_LEFT);
            box.getStyleClass().add("explore-log-line");
        }

        @Override
        protected void updateItem(WorkerLog.Line line, boolean empty) {
            super.updateItem(line, empty);
            setText(null);
            if (empty || line == null) {
                setGraphic(null);
                return;
            }
            if (kindClass != null) box.pseudoClassStateChanged(kindClass, false);
            kindClass = PseudoClass.getPseudoClass(line.kind().id());
            box.pseudoClassStateChanged(kindClass, true);
            kind.setText(line.kind().displayName());
            text.setText(Objects.requireNonNullElse(line.text(), ""));
            setGraphic(box);
        }
    }
}
