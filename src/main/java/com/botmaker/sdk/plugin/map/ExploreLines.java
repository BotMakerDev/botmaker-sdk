// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.ExploreLoop;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What 🔭 Explore ({@link ExploreView}) says of a run, as text: its status line, its feed's lines, the user's queue's
 * and the stop pinned. Pure functions of a {@link JournalView}, so the view only draws them.
 */
final class ExploreLines {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ExploreLines() {
    }

    /** The status line: how long, how many screens, how many stops fixed, and whether it runs or waits on Claude. */
    static String statusLine(JournalView read, int screensKnown, long nowMs, boolean live) {
        if (!read.began()) return "No explore run yet · " + screensKnown + " screens known";
        long fresh = read.events().stream()
                .filter(e -> e instanceof ExploreEvent.NewScreen(String s, String from, String by, long at) && from != null)
                .count();
        // A run that never wrote its end ran until its last event.
        long until = live || read.ended().isPresent() || read.events().isEmpty() ? nowMs
                : read.events().getLast().at();
        Duration ran = read.ranFor(until);
        String took = String.format("%d:%02d:%02d", ran.toHours(), ran.toMinutesPart(), ran.toSecondsPart());
        String state = read.ended().map(end -> "ended: " + end.displayName())
                .orElse(!live ? "ended" : read.open().filter(read::settled).isPresent() ? "✎ writing what the page settled"
                        : read.open().isPresent() ? "⏸ waiting on Claude" : "running");
        return took + " · " + screensKnown + " screens known (+" + fresh + " new) · "
                + read.count(ExploreEvent.Fixed.class) + " stops fixed · " + state;
    }

    /** The latest run's events as feed lines, newest first; Claude's calls are the pinned stop's, not the feed's. */
    static List<String> feedLines(JournalView read) {
        List<String> lines = new ArrayList<>();
        for (ExploreEvent event : read.events()) {
            String line = line(event);
            if (line != null) lines.add(clock(event.at()) + "  " + line);
        }
        return lines.reversed();
    }

    /** {@code event} as a feed line, without its clock; null for one the feed does not show. */
    static String line(ExploreEvent event) {
        return switch (event) {
            case ExploreEvent.Began e -> "▶ Began";
            case ExploreEvent.Ended e -> "■ Ended: " + (e.end() == null ? "unknown" : e.end().displayName());
            case ExploreEvent.Claude e -> null;
            case ExploreEvent.Pictured e -> "📷 " + e.screen() + " pictured";
            case ExploreEvent.Settled e -> "✎ " + e.stopId() + ": the page settles " + e.answers().size()
                    + (e.answers().size() == 1 ? " change" : " changes");
            case ExploreEvent.NewScreen e -> "✚ " + e.screen()
                    + (e.from() == null ? ", where it began" : ", from " + e.from() + " by " + e.by());
            case ExploreEvent.Tried e -> "→ " + e.screen() + "." + e.by()
                    + (e.mode() == null ? "" : " (" + e.mode().displayName() + ")")
                    + (e.arrival() == null ? "" : ": " + e.arrival().name().toLowerCase().replace('_', ' '));
            case ExploreEvent.Refused e -> "🛡 " + e.screen() + "." + e.by() + " refused"
                    + (e.guard() == null ? "" : " by " + e.guard().name().toLowerCase().replace('_', ' '))
                    + (e.why() == null || e.why().isBlank() ? "" : ": " + e.why());
            case ExploreEvent.GoalMet e -> "🎯 " + e.goal() + " met";
            case ExploreEvent.Built e -> "✅ " + e.screen() + " built in " + JournalView.took(e.ms());
            case ExploreEvent.Building e -> "… " + e.screen() + " left after " + JournalView.took(e.ms()) + ": "
                    + String.join(", ", e.open());
            case ExploreEvent.SetAside e -> "⏭ " + e.screen() + " set aside after " + JournalView.took(e.ms()) + ": "
                    + String.join(", ", e.open());
            case ExploreEvent.Stopped e -> "⏸ " + e.stop().id() + (e.again() ? " again" : "") + " — "
                    + e.stop().kind().displayName() + ": " + e.stop().missing();
            case ExploreEvent.Fixed e -> "✔ " + e.stopId() + " fixed" + (e.why() == null ? "" : ": " + e.why());
            case ExploreEvent.Parked e -> "⏸ " + e.stopId() + " parked: the run goes on elsewhere";
            case ExploreEvent.Asked e -> "❓ " + e.stopId() + " for you: " + e.question();
            case ExploreEvent.Faulted e -> "🛠 " + e.stopId() + " Discover fault: " + e.why();
            case ExploreEvent.ReplayLost e -> "⚠ " + e.faultId() + " not replayed: " + e.why();
            case ExploreEvent.Restarted e -> "↻ Restarted on the rebuilt SDK, replaying " + e.faultId();
            case ExploreEvent.Leased e -> "⇢ " + e.stopId() + " to " + e.role().displayName()
                    + (e.attempt() > 1 ? " (try " + e.attempt() + ")" : "");
            case ExploreEvent.Released e -> e.ok() ? null : "↺ " + e.stopId() + " taken back: " + e.why();
            case ExploreEvent.Heartbeat e -> null;
            case ExploreEvent.Finding e -> "⚑ " + e.kind().displayName() + ": " + e.why();
            case ExploreEvent.Witnessed e -> "🎯 Goal reached: " + e.goal() + " (" + e.check().displayName()
                    + (e.mark().isBlank() ? "" : ": " + e.mark()) + ")";
            case ExploreEvent.Ranked e -> null;
            case ExploreEvent.EditQueued e -> null;
            case ExploreEvent.EditApplied e -> e.ok() ? null : "⚠ " + e.editId() + " not written: " + e.said().lines().findFirst().orElse("");
            case ExploreEvent.Branched e -> "🔧 " + e.stopId() + ": " + (e.branch().isBlank() ? "no change" : e.branch())
                    + e.note().lines().findFirst().map(line -> " — " + line).orElse("");
            case ExploreEvent.Summary e -> e.unresolved().isEmpty() ? "📋 No finding left open"
                    : "📋 " + e.unresolved().size() + " finding(s) left open";
            case ExploreEvent.Reloaded e -> e.refused()
                    ? "⏸ " + e.stopId() + (e.errors().getFirst().equals(ExploreLoop.FAULT_RESUMES)
                    ? ": resume refused, " : ": the bot did not compile, ") + e.errors().getFirst()
                    : "✔ " + e.stopId() + ": the bot reloaded, generation " + e.generation();
        };
    }

    /** One stop of the user's queue as its line. */
    static String yourLine(JournalView.ForUser item) {
        Stop stop = item.stop();
        return "❓ " + stop.id() + " — " + stop.kind().displayName() + " on "
                + (stop.screen() == null ? "an unknown screen" : stop.screen()) + ": " + item.question();
    }

    /** The stop the run waits on, as the line pinned above the feed. */
    static String pinnedLine(Stop waiting) {
        return "⏸ " + waiting.id() + " — " + waiting.kind().displayName() + " on "
                + (waiting.screen() == null ? "an unknown screen" : waiting.screen()) + ": " + waiting.missing();
    }

    /** The last tool Claude called since the run last stopped on {@code open}; empty when none since. */
    static Optional<String> claudeSince(JournalView read, Stop open) {
        String call = null;
        for (ExploreEvent event : read.events()) {
            if (event instanceof ExploreEvent.Stopped stopped && stopped.stop().id().equals(open.id())) call = null;
            else if (event instanceof ExploreEvent.Claude claude) call = claude.call();
        }
        return Optional.ofNullable(call);
    }

    static String clock(long atMs) {
        return LocalTime.ofInstant(Instant.ofEpochMilli(atMs), ZoneId.systemDefault()).format(CLOCK);
    }
}
