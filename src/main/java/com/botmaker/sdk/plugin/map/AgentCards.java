// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.internal.explore.Agent;
import com.botmaker.sdk.internal.explore.AgentSummary;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The Explore page's row of agent cards (spec 2026-11-24-explore-agents-design § The Explore page, layout A): one card
 * per {@link Agent#CREW} member, each its {@link AgentSummary} drawn: name, state, the stop it is on, a thumbnail of
 * the frame it looks at, what it last wrote, how many calls and how long. A thin render: every word comes from
 * {@link #line(AgentSummary)}'s parts, read off the journal by {@code JournalView.agentSummary}.
 */
final class AgentCards {

    /** How wide a card's thumbnail is drawn. */
    private static final double THUMB_WIDTH = 120;
    /** How many thumbnails are kept decoded: the cards' and the feed's rows on screen. */
    private static final int THUMBS_KEPT = 32;

    private final Function<String, Optional<Path>> frames;
    private final HBox root = new HBox(8);
    private final Map<Agent, Card> cards = new EnumMap<>(Agent.class);
    /** The thumbnails decoded, by file, oldest dropped first. FX thread only. */
    private final Map<String, Image> thumbs = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > THUMBS_KEPT;
        }
    };
    private List<AgentSummary> shown = List.of();

    /** {@code frames} finds the file a record's frame names, empty when it is gone. */
    AgentCards(Function<String, Optional<Path>> frames) {
        this.frames = frames;
        root.getStyleClass().add("explore-cards");
        for (Agent agent : Agent.CREW) {
            Card card = new Card(agent);
            cards.put(agent, card);
            root.getChildren().add(card.box);
        }
    }

    Node node() {
        return root;
    }

    /** Draws {@code summaries}, one per card; a card not among them keeps what it showed. */
    void show(List<AgentSummary> summaries) {
        if (summaries.equals(shown)) return;
        shown = List.copyOf(summaries);
        for (AgentSummary summary : summaries) {
            Card card = cards.get(summary.agent());
            if (card != null) card.show(summary);
        }
    }

    /** The cards shown, one line each, in the row's order. */
    List<String> lines() {
        return shown.stream().map(AgentCards::line).toList();
    }

    /** The frame {@code frame} names, scaled to {@code width}, decoded once while kept; empty when its file is gone. */
    Optional<Image> thumbnail(String frame, double width) {
        Optional<Path> file = frames.apply(frame);
        if (file.isEmpty()) return Optional.empty();
        // Kept per width, so the feed's small one never stands in for a card's; one that failed is decoded again.
        String key = file.get() + "@" + width;
        Image kept = thumbs.get(key);
        if (kept == null || kept.isError()) {
            kept = new Image(file.get().toUri().toString(), width * 2, 0, true, true, true);
            thumbs.put(key, kept);
        }
        return Optional.of(kept);
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

    /** One agent's card. */
    private final class Card {

        private final VBox box = new VBox(3);
        private final Label state = new Label();
        private final Label stop = new Label();
        private final ImageView frame = new ImageView();
        private final Label last = new Label();
        private final Label numbers = new Label();
        private PseudoClass stateClass;

        Card(Agent agent) {
            Label name = new Label(agent.displayName());
            name.getStyleClass().add("explore-card-name");
            state.getStyleClass().add("explore-card-state");
            stop.getStyleClass().add("explore-screen");
            frame.setPreserveRatio(true);
            frame.setFitWidth(THUMB_WIDTH);
            last.setWrapText(true);
            last.setMaxHeight(48);
            last.setMinWidth(0);
            numbers.getStyleClass().add("explore-claude");
            box.getChildren().setAll(name, state, stop, frame, last, numbers);
            box.getStyleClass().add("explore-card");
            box.setMinWidth(0);
            box.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(box, Priority.ALWAYS);
        }

        void show(AgentSummary summary) {
            if (stateClass != null) box.pseudoClassStateChanged(stateClass, false);
            stateClass = PseudoClass.getPseudoClass(summary.state().id());
            box.pseudoClassStateChanged(stateClass, true);
            state.setText(summary.state().displayName());
            stop.setText(summary.stopId());
            stop.setVisible(!summary.stopId().isBlank());
            stop.setManaged(stop.isVisible());
            Image image = thumbnail(summary.frame(), THUMB_WIDTH).orElse(null);
            frame.setImage(image);
            frame.setVisible(image != null);
            frame.setManaged(image != null);
            last.setText(lastLine(summary.last()));
            numbers.setText(counted(summary.agent(), summary.calls()) + " · " + took(summary.time()));
        }
    }
}
