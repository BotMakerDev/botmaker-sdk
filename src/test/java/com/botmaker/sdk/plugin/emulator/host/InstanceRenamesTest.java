package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.InstanceKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Review item 7: a rename while a bot targets the instance points the bot at the new name on its next bind. What the
 * bot's values do with it (one write each, which Undo takes back) is the host's; here is what is written.
 */
class InstanceRenamesTest {

    private static final InstanceKey A = new InstanceKey(PlatformId.LDPLAYER, "LDPlayer-1");
    private static final InstanceKey B = new InstanceKey(PlatformId.LDPLAYER, "Farm 1");
    private static final InstanceKey C = new InstanceKey(PlatformId.LDPLAYER, "Farm main");

    private static HostLabels.Label row(InstanceKey from, InstanceKey to) {
        return new HostLabels.Label(from.text(), to.text());
    }

    @Test
    void aRenameIsRecordedOldToNew() {
        assertEquals(List.of(row(A, B)), InstanceRenames.recorded(HostLabels.none(), A, B).list());
        HostLabels once = InstanceRenames.recorded(HostLabels.none(), A, B);
        assertSame(once, InstanceRenames.recorded(once, B, B), "a rename to itself records nothing");
    }

    @Test
    void aSecondRenameLeadsTheFirstOnwardWithNoChain() {
        HostLabels twice = InstanceRenames.recorded(InstanceRenames.recorded(HostLabels.none(), A, B), B, C);
        assertEquals(List.of(row(A, C), row(B, C)), twice.list());
    }

    @Test
    void aNameTakenBackIsAnInstancesOwnAgain() {
        // A → B, then B back to A: A is an instance's name again, and B now leads to it
        HostLabels back = InstanceRenames.recorded(InstanceRenames.recorded(HostLabels.none(), A, B), B, A);
        assertEquals(List.of(row(B, A)), back.list());
        assertEquals(Map.of("Farm 1", "LDPlayer-1"), InstanceRenames.moves(back, Set.of("LDPlayer-1")));
    }

    @Test
    void anOldNameGivenToANewInstanceAndRenamedAgainIsAmbiguous() {
        // LDPlayer-1 → Farm 1; a new LDPlayer-1 is made and renamed Farm main: a bot naming LDPlayer-1 meant either
        HostLabels both = InstanceRenames.recorded(InstanceRenames.recorded(HostLabels.none(), A, B), A, C);
        assertEquals(List.of(row(A, B), row(A, C)), both.list());
        assertEquals(Map.of(), InstanceRenames.moves(both, Set.of("Farm 1", "Farm main")));
    }

    @Test
    void aDeletedInstanceTakesItsRenamesWithIt() {
        HostLabels rows = HostLabels.of(row(A, B), new HostLabels.Label("mumu/MuMu-0", "mumu/MuMu farm"));
        assertEquals(List.of(new HostLabels.Label("mumu/MuMu-0", "mumu/MuMu farm")),
                InstanceRenames.dropped(rows, B).list());
    }

    @Test
    void anOldNameMovesOnlyWhileNothingIsCalledItAndToOneNewName() {
        HostLabels rows = HostLabels.of(row(A, B));
        assertEquals(Map.of("LDPlayer-1", "Farm 1"), InstanceRenames.moves(rows, Set.of("Farm 1")));
        assertEquals(Map.of(), InstanceRenames.moves(rows, Set.of("Farm 1", "LDPlayer-1")),
                "another emulator's instance still has the old name: that is what the bot means");
        HostLabels two = HostLabels.of(row(A, B), new HostLabels.Label("mumu/LDPlayer-1", "mumu/Other"));
        assertEquals(Map.of(), InstanceRenames.moves(two, Set.of()), "two instances of that name went two ways");
        HostLabels same = HostLabels.of(row(A, B), new HostLabels.Label("mumu/LDPlayer-1", "mumu/Farm 1"));
        assertEquals(Map.of("LDPlayer-1", "Farm 1"), InstanceRenames.moves(same, Set.of()));
        assertEquals(Map.of(), InstanceRenames.moves(HostLabels.of(new HostLabels.Label("not a key", "x")), Set.of()));
    }

    @Test
    void onlyAnEmulatorAppOnAnOldInstanceIsRespelled() {
        Map<String, String> moves = Map.of("LDPlayer-1", "Farm 1");
        assertEquals(List.of("emu-app:com.zjcs.android.us@Farm 1", "emu-app:com.zjcs.android.us@MuMu-0",
                        "steam:1145350", "not a spec"),
                InstanceRenames.respelled(List.of("emu-app:com.zjcs.android.us@LDPlayer-1",
                        "emu-app:com.zjcs.android.us@MuMu-0", "steam:1145350", "not a spec"), moves));
        assertEquals("", InstanceRenames.respelled("", moves));
    }
}
