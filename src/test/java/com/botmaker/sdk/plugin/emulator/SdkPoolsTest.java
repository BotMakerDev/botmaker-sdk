// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.api.run.PoolOffer;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.platform.Os;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdkPoolsTest {

    private static final List<String> ANDROID = List.of("emu-app:com.game");
    private static final List<String> STEAM = List.of("steam:1145350");
    private static final SdkPools.Pc LINUX = new SdkPools.Pc(Os.LINUX, Optional.empty(), 2, List.of(), Optional.empty(), Map.of());

    @Test
    void theAndroidCandidatesAreThisPcsInstancesByNameEachOnce() {
        Map<InstanceKey, List<String>> usedBy = new LinkedHashMap<>();
        usedBy.put(new InstanceKey(PlatformId.LDPLAYER, "LDPlayer-2"), List.of("farm"));
        usedBy.put(new InstanceKey(PlatformId.LDPLAYER, "LDPlayer-1"), List.of("farm"));
        usedBy.put(new InstanceKey(PlatformId.MUMU, "LDPlayer-1"), List.of("arena"));

        assertEquals(List.of(Place.android("LDPlayer-1"), Place.android("LDPlayer-2")),
                SdkPools.candidates(new HostConfig.State(EmulatorConfig.EMPTY, usedBy), ANDROID, LINUX).places());
        assertEquals(List.of(), SdkPools.candidates(HostConfig.State.EMPTY, ANDROID, LINUX).places());
    }

    @Test
    void aSteamOnlyBotGetsThisDesktopAndItsDisplaysAndNoAndroidPlace() {
        SdkPools.Candidates got = SdkPools.candidates(twoInstances(), STEAM, LINUX);

        assertEquals(List.of(Place.desktop(), Place.privateDisplay(1), Place.privateDisplay(2)), got.places());
        assertTrue(got.leftOut().isEmpty(), got.leftOut().toString());
    }

    @Test
    void anAndroidBotGetsOnlyTheInstancesWithItsGameInstalledOrNeverRead() {
        SdkPools.Pc read = new SdkPools.Pc(Os.LINUX, Optional.empty(), 2, List.of(), Optional.empty(),
                Map.of("Pixel 9", Set.of("com.other"), "Pixel 10", Set.of("com.game")));

        SdkPools.Candidates got = SdkPools.candidates(threeInstances(), ANDROID, read);

        assertEquals(List.of(Place.android("Pixel 10"), Place.android("Pixel 8")), got.places());
        assertEquals(List.of("Pixel 9: com.game is not installed"), got.leftOut());
    }

    @Test
    void aBotWithBothKindsGetsBoth() {
        List<String> both = List.of("emu-app:com.game", "steam:1145350");
        SdkPools.Pc vms = new SdkPools.Pc(Os.WINDOWS, Optional.of("A private display is Linux's"), 4,
                List.of("Win11"), Optional.empty(), Map.of());

        assertEquals(List.of(Place.android("Pixel 10"), Place.android("Pixel 9"), Place.desktop(), Place.vm("Win11")),
                SdkPools.candidates(twoInstances(), both, vms).places());
    }

    @Test
    void aGameAVmCannotPlayGetsNoVmAndSaysWhy() {
        SdkPools.Pc refused = new SdkPools.Pc(Os.WINDOWS, Optional.of("A private display is Linux's"), 4,
                List.of("Win11"), Optional.of("This game cannot run in a game VM."), Map.of());

        SdkPools.Candidates got = SdkPools.candidates(HostConfig.State.EMPTY, STEAM, refused);

        assertEquals(List.of(Place.desktop()), got.places());
        assertEquals(List.of("Game VMs: This game cannot run in a game VM."), got.leftOut());
    }

    @Test
    void aRefusedPrivateDisplayIsNoCandidateAndSaysWhy() {
        SdkPools.Pc refused = new SdkPools.Pc(Os.LINUX, Optional.of("Steam would open on your desktop."), 4,
                List.of(), Optional.empty(), Map.of());

        SdkPools.Candidates got = SdkPools.candidates(twoInstances(), STEAM, refused);

        assertEquals(List.of(Place.desktop()), got.places());
        assertEquals(List.of("Private displays: Steam would open on your desktop."), got.leftOut());
    }

    @Test
    void theDisplaysStopAtWhatMemoryHoldsAndAtTheMost() {
        SdkPools.Pc none = new SdkPools.Pc(Os.LINUX, Optional.empty(), 0, List.of(), Optional.empty(), Map.of());
        SdkPools.Pc plenty = new SdkPools.Pc(Os.LINUX, Optional.empty(), 40, List.of(), Optional.empty(), Map.of());

        assertEquals(List.of(Place.desktop()), SdkPools.candidates(HostConfig.State.EMPTY, STEAM, none).places());
        assertEquals(1 + SdkPools.MOST_DISPLAYS,
                SdkPools.candidates(HostConfig.State.EMPTY, STEAM, plenty).places().size());
    }

    @Test
    void aBotThatListsNoTargetIsDealtTheAndroidPlacesAsBefore() {
        assertEquals(List.of(Place.android("Pixel 10"), Place.android("Pixel 9")),
                SdkPools.candidates(twoInstances(), List.of(), LINUX).places());
    }

    @Test
    void aGameThisSystemCannotRunGetsNoPcPlaceAndIsDealtTheAndroidPlacesAsBefore() {
        SdkPools.Pc windows = new SdkPools.Pc(Os.WINDOWS, Optional.empty(), 2, List.of(), Optional.empty(), Map.of());

        assertEquals(List.of(Place.android("Pixel 10"), Place.android("Pixel 9")),
                SdkPools.candidates(twoInstances(), List.of("lutris:game"), windows).places());
    }

    @Test
    void theFirstTargetThisSystemRunsIsTheOneThePcPlacesAreAskedAbout() {
        assertEquals(List.of(Place.desktop(), Place.privateDisplay(1), Place.privateDisplay(2)),
                SdkPools.candidates(HostConfig.State.EMPTY, List.of("lutris:game", "steam:1145350"), LINUX).places());
    }

    @Test
    void theOfferCreatesWhatIsMissingAsFarAsMemoryHoldsAndSaysWhatWasLeftOut() {
        SdkPools.Candidates android = new SdkPools.Candidates(List.of(), List.of(), true);
        SdkPools.Candidates without = new SdkPools.Candidates(List.of(), List.of("Pixel 9: com.game is not installed"),
                true);

        assertEquals(List.of("Create 2 instances"), titles(SdkPools.offers(2, 5, android)));
        assertEquals(List.of("Create 1 instance (this PC's memory holds no more)"),
                titles(SdkPools.offers(3, 1, android)));
        assertEquals(List.of("Create 2 instances; left out: Pixel 9: com.game is not installed"),
                titles(SdkPools.offers(2, 5, without)));
        assertTrue(SdkPools.offers(2, 0, android).isEmpty(), "no room: no offer, and the host says how many are missing");
        assertTrue(SdkPools.offers(2, 5, new SdkPools.Candidates(List.of(), List.of(), false)).isEmpty(),
                "a game of this PC is offered no instances");
    }

    private static HostConfig.State twoInstances() {
        Map<InstanceKey, List<String>> usedBy = new LinkedHashMap<>();
        usedBy.put(new InstanceKey(PlatformId.LDPLAYER, "Pixel 9"), List.of("farm"));
        usedBy.put(new InstanceKey(PlatformId.LDPLAYER, "Pixel 10"), List.of("farm"));
        return new HostConfig.State(EmulatorConfig.EMPTY, usedBy);
    }

    private static HostConfig.State threeInstances() {
        Map<InstanceKey, List<String>> usedBy = new LinkedHashMap<>(twoInstances().usedBy());
        usedBy.put(new InstanceKey(PlatformId.MUMU, "Pixel 8"), List.of("arena"));
        return new HostConfig.State(EmulatorConfig.EMPTY, usedBy);
    }

    private static List<String> titles(List<PoolOffer> offers) {
        return offers.stream().map(PoolOffer::title).toList();
    }
}
