// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.api.run.PoolOffer;
import com.botmaker.sdk.api.capture.Instance;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.InstanceKey;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdkPoolsTest {

    @Test
    void theCandidatesAreThisPcsInstancesByNameEachOnce() {
        Map<InstanceKey, List<String>> usedBy = new LinkedHashMap<>();
        usedBy.put(new InstanceKey(PlatformId.LDPLAYER, "LDPlayer-2"), List.of("farm"));
        usedBy.put(new InstanceKey(PlatformId.LDPLAYER, "LDPlayer-1"), List.of("farm"));
        usedBy.put(new InstanceKey(PlatformId.MUMU, "LDPlayer-1"), List.of("arena"));

        assertEquals(List.of(Instance.named("LDPlayer-1"), Instance.named("LDPlayer-2")),
                SdkPools.instances(new HostConfig.State(EmulatorConfig.EMPTY, usedBy)));
        assertEquals(List.of(), SdkPools.instances(HostConfig.State.EMPTY));
    }

    @Test
    void theOfferCreatesWhatIsMissingAsFarAsMemoryHolds() {
        assertEquals(List.of("Create 2 instances"), titles(SdkPools.offers(2, 5)));
        assertEquals(List.of("Create 1 instance (this PC's memory holds no more)"), titles(SdkPools.offers(3, 1)));
        assertTrue(SdkPools.offers(2, 0).isEmpty(), "no room: no offer, and the host says how many are missing");
    }

    private static List<String> titles(List<PoolOffer> offers) {
        return offers.stream().map(PoolOffer::title).toList();
    }
}
