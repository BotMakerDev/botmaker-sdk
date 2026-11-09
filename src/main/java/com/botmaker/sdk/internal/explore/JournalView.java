package com.botmaker.sdk.internal.explore;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * A {@link Journal} read as the plugin side needs it: the latest run's events, its open stop, its parked stops, its
 * counts. The {@code explore} tool and the Explore tab both read the run through it.
 *
 * @param all    every event of the journal, oldest first
 * @param events the latest run's: from its {@link ExploreEvent.Began} on (all of them when none began)
 * @param queue  the parked stops, oldest first
 */
public record JournalView(List<ExploreEvent> all, List<ExploreEvent> events, List<Stop> queue) {

    public JournalView {
        all = List.copyOf(all);
        events = List.copyOf(events);
        queue = List.copyOf(queue);
    }

    /** {@code journal} as it is now. */
    public static JournalView of(Journal journal) {
        List<ExploreEvent> all = journal.events();
        int began = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i) instanceof ExploreEvent.Began) {
                began = i;
                break;
            }
        }
        return new JournalView(all, all.subList(began, all.size()), journal.queue());
    }

    /** Whether a run ever began on this journal. */
    public boolean began() {
        return events.stream().anyMatch(ExploreEvent.Began.class::isInstance);
    }

    /** How the latest run ended; empty while it runs (or was killed before it could say). */
    public Optional<ExploreLoop.End> ended() {
        return events.stream().filter(ExploreEvent.Ended.class::isInstance).map(e -> ((ExploreEvent.Ended) e).end())
                .map(end -> end == null ? ExploreLoop.End.UNKNOWN : end).reduce((first, last) -> last);
    }

    /** Whether the latest run began and has not ended. */
    public boolean running() {
        return began() && ended().isEmpty();
    }

    /**
     * The stop the run waits on: the latest it stopped on that was neither fixed, parked, nor reloaded for since. A
     * refused reload leaves it open.
     */
    public Optional<Stop> open() {
        if (!running()) return Optional.empty();
        for (int i = events.size() - 1; i >= 0; i--) {
            if (!(events.get(i) instanceof ExploreEvent.Stopped(Stop stop, boolean again, long at))) continue;
            return closedAfter(stop.id(), i) ? Optional.empty() : Optional.of(stop);
        }
        return Optional.empty();
    }

    private boolean closedAfter(String stopId, int index) {
        for (ExploreEvent event : events.subList(index + 1, events.size())) {
            boolean closes = switch (event) {
                case ExploreEvent.Fixed fixed -> fixed.stopId().equals(stopId);
                case ExploreEvent.Parked parked -> parked.stopId().equals(stopId);
                case ExploreEvent.Reloaded reloaded -> reloaded.stopId().equals(stopId) && !reloaded.refused();
                default -> false;
            };
            if (closes) return true;
        }
        return false;
    }

    /** The parked stop {@code stopId}, if the queue holds it. */
    public Optional<Stop> parked(String stopId) {
        return queue.stream().filter(s -> s.id().equals(stopId)).findFirst();
    }

    /** The latest reload answered for {@code stopId}, after the last time the run stopped on it. */
    public Optional<ExploreEvent.Reloaded> reloadOf(String stopId) {
        ExploreEvent.Reloaded last = null;
        for (ExploreEvent event : all) {
            if (event instanceof ExploreEvent.Stopped(Stop stop, boolean again, long at) && stop.id().equals(stopId)) {
                last = null;
            } else if (event instanceof ExploreEvent.Reloaded reloaded && reloaded.stopId().equals(stopId)) {
                last = reloaded;
            }
        }
        return Optional.ofNullable(last);
    }

    /** The screens the latest run found since Claude last waited on it, oldest first. */
    public List<String> newScreensSinceWait() {
        int since = 0;
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof ExploreEvent.Claude(String call, long at) && call.startsWith(WAIT)) {
                since = i + 1;
                break;
            }
        }
        return events.subList(since, events.size()).stream().filter(ExploreEvent.NewScreen.class::isInstance)
                .map(e -> ((ExploreEvent.NewScreen) e).screen()).toList();
    }

    /** The call a {@code wait} writes as its {@link ExploreEvent.Claude} event. */
    public static final String WAIT = "wait";

    /** The screen the latest run was last on; empty before it reached one. */
    public Optional<String> where() {
        for (int i = events.size() - 1; i >= 0; i--) {
            String screen = switch (events.get(i)) {
                case ExploreEvent.NewScreen e -> e.screen();
                case ExploreEvent.Tried e -> e.screen();
                case ExploreEvent.Refused e -> e.screen();
                case ExploreEvent.Stopped e -> e.stop().screen();
                default -> null;
            };
            if (screen != null) return Optional.of(screen);
        }
        return Optional.empty();
    }

    /** The generation the latest run plays: its last reload that took; 1 before any. */
    public int generation() {
        return events.stream().filter(ExploreEvent.Reloaded.class::isInstance).map(ExploreEvent.Reloaded.class::cast)
                .filter(r -> !r.refused()).mapToInt(ExploreEvent.Reloaded::generation).reduce((a, b) -> b).orElse(1);
    }

    /** When the latest run began, epoch milliseconds; 0 when none did. */
    public long beganAt() {
        return events.stream().filter(ExploreEvent.Began.class::isInstance).mapToLong(ExploreEvent::at).findFirst()
                .orElse(0);
    }

    /** How long the latest run has run: to its end when it ended, else to {@code nowMs}. */
    public Duration ranFor(long nowMs) {
        if (beganAt() == 0) return Duration.ZERO;
        long until = events.stream().filter(ExploreEvent.Ended.class::isInstance).mapToLong(ExploreEvent::at)
                .reduce((a, b) -> b).orElse(nowMs);
        return Duration.ofMillis(Math.max(0, until - beganAt()));
    }

    /** Whether the latest run parked a stop since Claude last waited on it. */
    public boolean parkedSinceWait() {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof ExploreEvent.Parked) return true;
            if (events.get(i) instanceof ExploreEvent.Claude(String call, long at) && call.startsWith(WAIT)) return false;
        }
        return false;
    }

    /** How many of the latest run's events are {@code type}. */
    public long count(Class<? extends ExploreEvent> type) {
        return events.stream().filter(type::isInstance).count();
    }
}
