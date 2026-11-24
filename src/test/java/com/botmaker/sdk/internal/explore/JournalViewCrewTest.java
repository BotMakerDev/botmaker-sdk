// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The explore crew's records (spec 2026-11-24-explore-agents-design): each reads back, and the view answers from them. */
class JournalViewCrewTest {

    @Test
    void everyCrewRecordReadsBack(@TempDir Path folder) {
        Journal journal = new Journal(folder);
        List<ExploreEvent> written = List.of(
                new ExploreEvent.Began(false, 1),
                new ExploreEvent.Leased("unknown-1", StopRole.CARTOGRAPHER, 1, 120_000, 2),
                new ExploreEvent.Released("unknown-1", 1, false, "timeout", 3),
                new ExploreEvent.Heartbeat(4),
                new ExploreEvent.Finding(FindingKind.DRIFT, "frame-7", "Settings › Network is off the goal", 5),
                new ExploreEvent.Witnessed("reach the Google sign-in page", "frame-9", WitnessCheck.TEXT, "Sign in", 6),
                new ExploreEvent.Ranked("SETTINGS", "Passwords & accounts", 10, PressTag.SAFE, 7),
                new ExploreEvent.EditApplied("edit-1", 8),
                new ExploreEvent.Ended(ExploreLoop.End.UNREACHABLE, 9));
        written.forEach(journal::append);

        assertEquals(written, journal.events());
    }

    @Test
    void aKindOrTagANewerSdkWroteReadsAsUnknown(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve(Journal.EVENTS), """
                {"type":"finding","kind":"lost_in_space","evidence":"","why":"","at":1}
                {"type":"ranked","screen":"A","press":"b","rank":3,"tag":"radioactive","at":2}
                """);
        List<ExploreEvent> read = new Journal(folder).events();

        assertEquals(FindingKind.UNKNOWN, ((ExploreEvent.Finding) read.get(0)).kind());
        assertEquals(PressTag.UNKNOWN, ((ExploreEvent.Ranked) read.get(1)).tag());
    }

    /** A line missing its kind, tag or role reads as unknown, never null: an untagged press is never pressed unattended. */
    @Test
    void aMissingKindTagOrRoleReadsAsUnknown(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve(Journal.EVENTS), """
                {"type":"finding","at":1}
                {"type":"ranked","screen":"A","press":"b","rank":1,"at":2}
                {"type":"leased","stopId":"s-1","attempt":1,"expiresAt":5,"at":3}
                {"type":"witnessed","at":4}
                """);
        List<ExploreEvent> read = new Journal(folder).events();

        assertEquals(FindingKind.UNKNOWN, ((ExploreEvent.Finding) read.get(0)).kind());
        assertFalse(((ExploreEvent.Ranked) read.get(1)).tag().unattended());
        assertEquals(StopRole.UNKNOWN, ((ExploreEvent.Leased) read.get(2)).role());
        ExploreEvent.Witnessed witnessed = (ExploreEvent.Witnessed) read.get(3);
        assertEquals(WitnessCheck.UNKNOWN, witnessed.check());
        assertEquals("", witnessed.goal());
    }

    /** A worker of an earlier try that finishes late closes its own lease, not the try that replaced it. */
    @Test
    void aLateReleaseOfAnEarlierTryLeavesTheNewLeaseOpen() {
        ExploreEvent.Leased second = new ExploreEvent.Leased("s-1", StopRole.CARTOGRAPHER, 2, 300, 200);
        JournalView view = view(new ExploreEvent.Began(false, 1),
                new ExploreEvent.Leased("s-1", StopRole.CARTOGRAPHER, 1, 100, 10),
                new ExploreEvent.Released("s-1", 1, false, "timeout", 150), second,
                new ExploreEvent.Released("s-1", 1, true, "", 210));

        assertEquals(Optional.of(second), view.leaseOf("s-1"));
    }

    @Test
    void aLeasePastItsTimeIsExpired() {
        ExploreEvent.Leased lease = new ExploreEvent.Leased("s-1", StopRole.CARTOGRAPHER, 1, 100, 10);

        assertFalse(lease.expired(99));
        assertTrue(lease.expired(100));
    }

    @Test
    void theViewCountsLeasesAndKnowsTheOpenOne() {
        ExploreEvent.Leased first = new ExploreEvent.Leased("s-1", StopRole.CARTOGRAPHER, 1, 100, 10);
        ExploreEvent.Leased second = new ExploreEvent.Leased("s-1", StopRole.CARTOGRAPHER, 2, 300, 200);
        JournalView view = view(new ExploreEvent.Began(false, 1), first,
                new ExploreEvent.Released("s-1", 1, false, "timeout", 150), second,
                new ExploreEvent.Leased("s-2", StopRole.MECHANIC, 1, 400, 210),
                new ExploreEvent.Released("s-2", 1, true, "", 220));

        assertEquals(2, view.attempts("s-1"));
        assertEquals(Optional.of(second), view.leaseOf("s-1"), "the latest lease, not released");
        assertEquals(Optional.empty(), view.leaseOf("s-2"), "released");
        assertEquals(0, view.attempts("s-3"));
    }

    @Test
    void theViewKnowsFindingsHeartbeatAndAppliedEdits() {
        ExploreEvent.Finding finding = new ExploreEvent.Finding(FindingKind.NO_WITNESS, "frame-2", "no match", 5);
        JournalView view = view(new ExploreEvent.Heartbeat(1), new ExploreEvent.Began(false, 2),
                new ExploreEvent.Heartbeat(3), new ExploreEvent.Heartbeat(4), finding,
                new ExploreEvent.EditApplied("edit-9", 6));

        assertEquals(List.of(finding), view.findings());
        assertEquals(Optional.of(Instant.ofEpochMilli(4)), view.lastHeartbeat());
        assertTrue(view.applied("edit-9"));
        assertFalse(view.applied("edit-10"));
        assertEquals(Optional.empty(), view(new ExploreEvent.Began(false, 1)).lastHeartbeat());
    }

    @Test
    void anEarlierRunsLeasesAndEditsAreNotThisRuns() {
        JournalView view = view(new ExploreEvent.Began(false, 1),
                new ExploreEvent.Leased("s-1", StopRole.CARTOGRAPHER, 1, 100, 2),
                new ExploreEvent.EditApplied("edit-1", 3),
                new ExploreEvent.Began(false, 4));

        assertEquals(0, view.attempts("s-1"));
        assertEquals(Optional.empty(), view.leaseOf("s-1"));
        assertFalse(view.applied("edit-1"));
    }

    /**
     * Device drive 2026-11-24: a goal run stuck on a screen whose presses it all tried is the Reader's, which maps the
     * frame's; once its try added nothing, that stop and the next stuck there are the user's; a run with no goal keeps
     * its stuck the user's.
     */
    @Test
    void aGoalRunsStuckIsTheReadersUntilItsTryAddsNothing() {
        Stop first = stuck("stuck-1");
        assertEquals(StopRole.USER, view(new ExploreEvent.Began(false, 1),
                new ExploreEvent.Stopped(first, false, 2)).roleOf(first), "no goal, no crew: the user's");

        List<ExploreEvent> events = new ArrayList<>(List.of(new ExploreEvent.Began(false, "reach Accounts", 1),
                new ExploreEvent.Stopped(first, false, 2)));
        assertEquals(StopRole.CARTOGRAPHER, view(events).roleOf(first));
        events.add(new ExploreEvent.Leased("stuck-1", StopRole.CARTOGRAPHER, 1, 100, 3));
        assertEquals(StopRole.CARTOGRAPHER, view(events).roleOf(first), "the Reader's while it works");
        // A bare cut and a rank give the run nothing new to press.
        events.add(new ExploreEvent.EditQueued("stuck-1-1", "stuck-1", "crop_pictures", "{\"boxes\":[]}", 4));
        events.add(new ExploreEvent.EditApplied("stuck-1-1", true, "cut", 4));
        events.add(new ExploreEvent.Ranked("HOME", "search", 2, PressTag.SAFE, 4));
        events.add(new ExploreEvent.Released("stuck-1", 1, true, "", 5));
        assertEquals(StopRole.USER, view(events).roleOf(first), "its try added nothing: the user's");
        Stop again = stuck("stuck-2");
        events.add(new ExploreEvent.Stopped(again, false, 6));
        assertEquals(StopRole.USER, view(events).roleOf(again), "nothing added since: no second try");

        // A press since: the run moved on, the next stuck there is the Reader's again.
        events.add(new ExploreEvent.Tried("HOME", "back", ExploreLoop.Mode.UNTRIED, null, 7));
        assertEquals(StopRole.CARTOGRAPHER, view(events).roleOf(again));
    }

    /**
     * A try that added a press keeps its stop until its resume; a stuck after it with no press between is the user's,
     * so a press the run cannot take is not added again and again.
     */
    @Test
    void aTryThatAddedAPressHoldsItsStopButNotTheNextStuck() {
        Stop first = stuck("stuck-1");
        List<ExploreEvent> events = new ArrayList<>(List.of(new ExploreEvent.Began(false, "reach Accounts", 1),
                new ExploreEvent.Stopped(first, false, 2),
                new ExploreEvent.Leased("stuck-1", StopRole.CARTOGRAPHER, 1, 100, 3),
                new ExploreEvent.EditQueued("stuck-1-1", "stuck-1", "crop_pictures", "{\"screen\":\"HOME\"}", 4),
                new ExploreEvent.EditApplied("stuck-1-1", true, "cut", 4),
                new ExploreEvent.Released("stuck-1", 1, true, "", 5)));
        assertEquals(StopRole.CARTOGRAPHER, view(events).roleOf(first), "its resume is on its way");

        Stop again = stuck("stuck-2");
        events.add(new ExploreEvent.Stopped(again, true, 6));
        assertEquals(StopRole.USER, view(events).roleOf(again), "the run could not press what it added");
    }

    /** Review 2026-11-24: a try that failed to start, or another stop's edit meanwhile, says nothing of this try. */
    @Test
    void aFailedTryOrAnotherStopsEditIsNotTheReadersTry() {
        Stop first = stuck("stuck-1");
        List<ExploreEvent> events = new ArrayList<>(List.of(new ExploreEvent.Began(false, "reach Accounts", 1),
                new ExploreEvent.Stopped(first, false, 2),
                new ExploreEvent.Leased("stuck-1", StopRole.CARTOGRAPHER, 1, 100, 3),
                new ExploreEvent.Released("stuck-1", 1, false, "timeout", 4)));
        assertEquals(StopRole.CARTOGRAPHER, view(events).roleOf(first), "a timeout is no try");

        events.add(new ExploreEvent.Leased("stuck-1", StopRole.CARTOGRAPHER, 2, 200, 5));
        events.add(new ExploreEvent.EditQueued("u-1", "unknown_screen-2", "edit_map", "{\"action\":\"new_screen\"}", 6));
        events.add(new ExploreEvent.EditApplied("u-1", true, "new SETTINGS", 6));
        events.add(new ExploreEvent.Released("stuck-1", 2, true, "", 7));
        assertEquals(StopRole.USER, view(events).roleOf(first), "another stop's edit adds nothing to this one");

        Stop early = new Stop("stuck-9", StopKind.STUCK, null, "", "", List.of(), List.of(), Map.of(), List.of(),
                "nothing pressed for 20m", 8);
        assertEquals(StopRole.USER, view(new ExploreEvent.Began(false, "reach Accounts", 1),
                new ExploreEvent.Stopped(early, false, 8)).roleOf(early), "no screen to add a press to");
    }

    @Test
    void onlyAMapEditOrACutOnTheStuckScreenChangesTheMap() {
        assertTrue(EditTool.EDIT_MAP.changesMap("{\"action\":\"new_screen\"}", "HOME"));
        assertTrue(EditTool.CROP_PICTURES.changesMap("{\"screen\":\"HOME\",\"boxes\":[]}", "HOME"));
        assertFalse(EditTool.CROP_PICTURES.changesMap("{\"screen\":\"SETTINGS\"}", "HOME"));
        assertFalse(EditTool.CROP_PICTURES.changesMap("{\"boxes\":[]}", "HOME"));
        assertFalse(EditTool.CROP_PICTURES.changesMap("{\"screen\":\" \"}", " "));
        assertFalse(EditTool.CROP_PICTURES.changesMap("not json", "HOME"));
        assertFalse(EditTool.UNKNOWN.changesMap("{\"screen\":\"HOME\"}", "HOME"));
    }

    private static Stop stuck(String id) {
        return new Stop(id, StopKind.STUCK, "HOME", "HOME", "", List.of(), List.of(), Map.of(), List.of(),
                "nothing is left the run can press itself", 2);
    }

    private static JournalView view(List<ExploreEvent> events) {
        return view(events.toArray(ExploreEvent[]::new));
    }

    private static JournalView view(ExploreEvent... events) {
        List<ExploreEvent> all = List.of(events);
        int began = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i) instanceof ExploreEvent.Began) {
                began = i;
                break;
            }
        }
        return new JournalView(all, all.subList(began, all.size()), List.of());
    }
}
