// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.InstanceKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsedByTest {

    private static final EmulatorInstance LD = new EmulatorInstance(PlatformId.LDPLAYER, "LDPlayer", "127.0.0.1", 5555);
    private static final EmulatorInstance MUMU = new EmulatorInstance(PlatformId.MUMU, "MuMu", "127.0.0.1", 16384);

    @Test
    void aBotUsesTheInstanceItsTargetNamesElseTheOneItsPlaceNames() {
        List<EmulatorInstance> known = List.of(LD, MUMU);
        assertEquals(Set.of(InstanceKey.of(LD)), UsedBy.targets(List.of("emu-app:com.game@LDPlayer"), "MuMu", known),
                "an older bot's target names its own");
        assertEquals(Set.of(InstanceKey.of(MUMU)), UsedBy.targets(List.of("emu-app:com.game"), "MuMu", known));
        assertEquals(Set.of(), UsedBy.targets(List.of("emu-app:com.game"), null, known), "placed nowhere");
        assertEquals(Set.of(), UsedBy.targets(List.of("steam:570"), "MuMu", known), "a game of this PC uses none");
    }
}
