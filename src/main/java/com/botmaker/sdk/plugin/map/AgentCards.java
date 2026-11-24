// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.internal.explore.Agent;
import com.botmaker.sdk.internal.explore.AgentSummary;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The Explore page's strip of crew chips under its bar (plan 2026-11-24-explore-drive-fixes, phase 4): one chip per
 * {@link Agent#CREW} member, its name and state, a dot while it works; a chip's click opens the Crew tab on it. The
 * words of a whole {@link AgentSummary} ({@link #line}) are the Crew tab's list and a chip's tooltip.
 */
final class AgentCards {

    /** What marks a working agent's chip. */
    static final String WORKING_DOT = "● ";

    private final HBox root = new HBox(6);
    private final Map<Agent, Chip> chips = new EnumMap<>(Agent.class);
    private List<AgentSummary> shown = List.of();

    /** {@code onPick} is handed the agent whose chip was clicked. */
    AgentCards(Consumer<Agent> onPick) {
        root.getStyleClass().add("explore-chips");
        for (Agent agent : Agent.CREW) {
            Chip chip = new Chip(agent);
            chip.label.setOnMouseClicked(e -> onPick.accept(agent));
            chips.put(agent, chip);
            root.getChildren().add(chip.label);
        }
    }

    Node node() {
        return root;
    }

    /** Draws {@code summaries}, one per chip; a chip not among them keeps what it showed. */
    void show(List<AgentSummary> summaries) {
        if (summaries.equals(shown)) return;
        shown = List.copyOf(summaries);
        for (AgentSummary summary : summaries) {
            Chip chip = chips.get(summary.agent());
            if (chip != null) chip.show(summary);
        }
    }

    /** The chips shown, one line each, in the strip's order. */
    List<String> lines() {
        return shown.stream().map(AgentCards::chipText).toList();
    }

    /** What {@code summary}'s chip says: "● Reader · Working", the dot only while it works. */
    static String chipText(AgentSummary summary) {
        return (summary.state() == AgentSummary.State.WORKING ? WORKING_DOT : "") + summary.agent().displayName()
                + " · " + summary.state().displayName();
    }

    /** {@code summary} as one line: "Reader · Working · on unknown-1 · ranked … · 1 session · 13 s". */
    static String line(AgentSummary summary) {
        List<String> parts = new ArrayList<>();
        parts.add(summary.agent().displayName());
        parts.add(summary.state().displayName());
        if (!summary.stopId().isBlank()) parts.add("on " + summary.stopId());
        parts.add(lastLine(summary.last()));
        parts.add(counted(summary.agent(), summary.calls()));
        parts.add(took(summary.time()));
        return String.join(" · ", parts);
    }

    /** What {@code last} says, as the feed says it, or in a few words for a record the feed does not show. */
    static String lastLine(Optional<ExploreEvent> last) {
        if (last.isEmpty()) return "nothing yet";
        return switch (last.get()) {
            case ExploreEvent.Ranked r -> "ranked " + r.screen() + " › " + r.press() + " " + r.rank() + " ("
                    + r.tag().displayName() + ")";
            case ExploreEvent.EditQueued q -> "queued " + q.tool() + " for " + q.stopId();
            case ExploreEvent event -> Optional.ofNullable(ExploreLines.line(event)).orElse(event.type());
        };
    }

    /** {@code calls} with the word for what an agent's call is: a press, a lease handed, a worker's session. */
    static String counted(Agent agent, int calls) {
        String word = switch (agent) {
            case EXPLORER -> calls == 1 ? "press" : "presses";
            case COORDINATOR -> calls == 1 ? "lease" : "leases";
            default -> calls == 1 ? "session" : "sessions";
        };
        return calls + " " + word;
    }

    /** {@code time} as seconds under a minute, else "m:ss", else "h:mm:ss". */
    static String took(Duration time) {
        long seconds = time.toSeconds();
        if (seconds < 60) return seconds + " s";
        if (time.toHours() == 0) return String.format("%d:%02d", time.toMinutesPart(), time.toSecondsPart());
        return String.format("%d:%02d:%02d", time.toHours(), time.toMinutesPart(), time.toSecondsPart());
    }

    /** One agent's chip. */
    private static final class Chip {

        private final Label label;
        private final Tooltip tip = new Tooltip();
        private PseudoClass stateClass;

        Chip(Agent agent) {
            label = new Label(agent.displayName());
            label.getStyleClass().add("explore-agent-chip");
            label.setTooltip(tip);
        }

        void show(AgentSummary summary) {
            if (stateClass != null) label.pseudoClassStateChanged(stateClass, false);
            stateClass = PseudoClass.getPseudoClass(summary.state().id());
            label.pseudoClassStateChanged(stateClass, true);
            label.setText(chipText(summary));
            tip.setText(line(summary) + "\nClick: its sessions in the Crew tab.");
        }
    }
}
