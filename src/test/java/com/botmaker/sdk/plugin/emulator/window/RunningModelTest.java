package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.sdk.plugin.emulator.window.EmulatorsModel.InstanceRow;
import com.botmaker.sdk.plugin.emulator.window.RunningModel.Card;
import com.botmaker.sdk.plugin.emulator.window.RunningModel.CpuTimes;
import com.botmaker.sdk.plugin.emulator.window.RunningModel.Group;
import com.botmaker.sdk.plugin.emulator.window.RunningModel.Grouping;
import com.botmaker.sdk.plugin.emulator.window.RunningModel.Headroom;
import com.botmaker.sdk.plugin.emulator.window.RunningModel.Tap;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.ConfigValue;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.InstanceFacts;
import com.botmaker.shared.emulator.config.InstanceKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunningModelTest {

    private static final EmulatorInstance FARM = new EmulatorInstance(PlatformId.LDPLAYER, "Farm", "127.0.0.1", 5555);
    private static final EmulatorInstance ALT = new EmulatorInstance(PlatformId.LDPLAYER, "alt", "127.0.0.1", 5557);
    private static final EmulatorInstance IDLE = new EmulatorInstance(PlatformId.LDPLAYER, "Idle", "127.0.0.1", 5559);
    private static final EmulatorInstance MUMU = new EmulatorInstance(PlatformId.MUMU, "MuMu-0", "127.0.0.1", 16384);
    private static final EmulatorInstance MEMU = new EmulatorInstance(PlatformId.MEMU, "MEmu", "127.0.0.1", 21503);

    private static InstanceRow row(EmulatorInstance instance, boolean running, int memoryMb, List<String> usedBy) {
        InstanceKey key = InstanceKey.of(instance);
        InstanceFacts facts = memoryMb <= 0 ? null : new InstanceFacts(key,
                Map.of(ConfigField.MEMORY, new ConfigValue.Megabytes(memoryMb)), Instant.EPOCH, Optional.empty(),
                false);
        return new InstanceRow(instance, key, running, EmulatorConfig.EMPTY.wanted(key), facts, null, usedBy);
    }

    /** MuMu-0 and Farm and alt run; MEmu is stopped and SwordXStaff targets it; Idle is stopped and nobody's. */
    private static EmulatorsModel.Snapshot snapshot() {
        return new EmulatorsModel.Snapshot(List.of(), List.of(
                row(MUMU, true, 4096, List.of()),
                row(FARM, true, 3072, List.of("SwordXStaff")),
                row(MEMU, false, 2048, List.of("SwordXStaff")),
                row(IDLE, false, 2048, List.of()),
                row(ALT, true, 0, List.of())), EmulatorConfig.EMPTY, Instant.EPOCH);
    }

    private static List<String> names(Group group) {
        return group.cards().stream().map(card -> card.row().instance().name()).toList();
    }

    @Test
    void allTogetherListsTheRunningThenTheExpectedByEmulatorThenName() {
        List<Group> groups = RunningModel.groups(snapshot(), Grouping.ALL, null, null);
        assertEquals(1, groups.size());
        assertEquals(List.of("alt", "Farm", "MuMu-0", "MEmu"), names(groups.getFirst()));
        assertEquals("All instances · 3 running, 1 expected", groups.getFirst().title());
        assertTrue(groups.getFirst().cards().getLast().expected());
        assertEquals(List.of("alt", "Farm", "MuMu-0"),
                groups.getFirst().running().stream().map(row -> row.instance().name()).toList());
    }

    @Test
    void byEmulatorGivesEachProductItsGroupAndItsOwnStopAll() {
        List<Group> groups = RunningModel.groups(snapshot(), Grouping.BY_EMULATOR, null, null);
        assertEquals(List.of(PlatformId.LDPLAYER, PlatformId.MEMU, PlatformId.MUMU),
                groups.stream().map(Group::platform).toList());
        assertEquals(List.of("alt", "Farm"), names(groups.get(0)));
        assertEquals("MEmu · 0 running, 1 expected", groups.get(1).title());
        assertTrue(groups.get(1).running().isEmpty());
        assertEquals("SwordXStaff targets it; it is not running.", groups.get(1).cards().getFirst().expectedLine());
    }

    @Test
    void noRunningAndNoExpectedInstanceDrawsNoGroup() {
        EmulatorsModel.Snapshot quiet = new EmulatorsModel.Snapshot(List.of(), List.of(row(IDLE, false, 0, List.of())),
                EmulatorConfig.EMPTY, Instant.EPOCH);
        assertTrue(RunningModel.groups(quiet, Grouping.ALL, null, null).isEmpty());
        assertTrue(RunningModel.groups(quiet, Grouping.BY_EMULATOR, null, null).isEmpty());
    }

    @Test
    void theOpenBotsRunShowsOnTheInstanceItLaunches() {
        List<Card> cards = RunningModel.groups(snapshot(), Grouping.ALL, "SwordXStaff", FARM.reference())
                .getFirst().cards();
        assertEquals("SwordXStaff", cards.get(1).bot());
        assertNull(cards.get(0).bot());
        assertNull(cards.get(3).bot(), "a stopped instance runs no bot");
    }

    @Test
    void aNameTwoRunningInstancesShareShowsTheBotOnNeither() {
        EmulatorInstance twin = new EmulatorInstance(PlatformId.MUMU, "Farm", "127.0.0.1", 16416);
        EmulatorsModel.Snapshot both = new EmulatorsModel.Snapshot(List.of(), List.of(
                row(FARM, true, 0, List.of()), row(twin, true, 0, List.of())), EmulatorConfig.EMPTY, Instant.EPOCH);
        RunningModel.groups(both, Grouping.ALL, "SwordXStaff", FARM.reference()).getFirst().cards()
                .forEach(card -> assertNull(card.bot(), "which one it runs on is not known"));
    }

    @Test
    void headroomAddsWhatTheRunningInstancesHold() {
        Headroom headroom = Headroom.of(5000, snapshot());
        assertEquals(7168, headroom.heldMb(), "MuMu-0 and Farm; alt was not read and the stopped ones hold nothing");
        assertEquals("The running instances hold 7168 MB. Windows can still give 5000 MB.", headroom.line());
        assertEquals("How much memory Windows can still give is not known here.", new Headroom(-1, 0).line());
    }

    @Test
    void aStartThatWouldNotFitIsWarnedOf() {
        InstanceRow memu = row(MEMU, false, 2048, List.of());
        InstanceRow idle = row(IDLE, false, 2048, List.of());
        assertTrue(new Headroom(3000, 0).warning(List.of(memu)).isEmpty());
        assertTrue(new Headroom(2000, 0).warning(List.of(memu)).orElseThrow().contains("MEmu (2048 MB) does not fit"));
        assertTrue(new Headroom(-1, 0).warning(List.of(memu)).isEmpty(), "unknown on another OS: no warning");
        assertTrue(new Headroom(2000, 0).warning(List.of(row(ALT, false, 0, List.of()))).isEmpty(),
                "an ask not read");

        assertTrue(new Headroom(5000, 0).warning(List.of(memu, idle)).isEmpty());
        String both = new Headroom(3000, 0).warning(List.of(memu, idle)).orElseThrow();
        assertTrue(both.contains("Idle (2048 MB) does not fit"), both);
    }

    @Test
    void aClickLandsOnTheSamePlaceOfTheFrameAtAnyScale() {
        assertEquals(Optional.of(new Tap(540, 960)), RunningModel.tap(210, 373.34, 420, 746.67, 1080, 1920));
        assertEquals(Optional.of(new Tap(0, 0)), RunningModel.tap(0, 0, 420, 746.67, 1080, 1920));
        assertEquals(Optional.of(new Tap(1079, 1919)), RunningModel.tap(419.99, 746.66, 420, 746.67, 1080, 1920));
        assertEquals(Optional.of(new Tap(800, 450)), RunningModel.tap(400, 225, 800, 450, 1600, 900));
        assertEquals(Optional.of(new Tap(100, 50)), RunningModel.tap(100, 50, 1600, 900, 1600, 900), "at full size");
        assertEquals(Optional.of(new Tap(200, 100)), RunningModel.tap(100, 50, 800, 450, 1600, 900));
        assertTrue(RunningModel.tap(420, 10, 420, 746, 1080, 1920).isEmpty(), "past the right edge");
        assertTrue(RunningModel.tap(-1, 10, 420, 746, 1080, 1920).isEmpty());
        assertTrue(RunningModel.tap(10, 10, 420, 746, 0, 0).isEmpty(), "no frame yet");
    }

    @Test
    void cpuShareIsTheBusyPartOfTheTicksSinceTheLastReading() {
        CpuTimes before = CpuTimes.parse("cpu  100 0 100 700 100 0 0 0 0 0\ncpu0 1 2 3 4").orElseThrow();
        CpuTimes after = CpuTimes.parse("cpu  200 0 200 1300 200 0 0 0 0 0").orElseThrow();
        assertEquals(new CpuTimes(200, 1000), before);
        assertEquals(22, after.percentSince(before), "200 busy of 900");
        assertEquals(-1, after.percentSince(null));
        assertEquals(-1, before.percentSince(after), "counters went back: Android restarted");
        assertTrue(CpuTimes.parse("nothing here").isEmpty());
        assertTrue(CpuTimes.parse(null).isEmpty());
    }

    @Test
    void uptimeAndMemoryReadAndroidsOwnFiles() {
        assertEquals(3725, RunningModel.uptimeSeconds("3725.41 12000.02\n"));
        assertEquals(-1, RunningModel.uptimeSeconds("odd"));
        assertArrayEquals(new long[] {1500, 4000}, RunningModel.memoryMb(
                "MemTotal:        4096000 kB\nMemFree:          100000 kB\nMemAvailable:    2560000 kB\n"));
        assertArrayEquals(new long[] {-1, -1}, RunningModel.memoryMb("MemTotal: 4096000 kB\n"));
        assertEquals("45 s", RunningModel.duration(45));
        assertEquals("12 min", RunningModel.duration(720));
        assertEquals("1 h 02 min", RunningModel.duration(3725));
        assertEquals("2 d 4 h", RunningModel.duration(2 * 86400 + 4 * 3600 + 59));
    }

    @Test
    void aReadingSaysWhatWasNotRead() {
        assertEquals("ADB: no answer", RunningModel.Live.NO_ANSWER.adb());
        assertEquals("CPU — · memory —", RunningModel.Live.NO_ANSWER.cpuAndMemory());
        RunningModel.Live live = new RunningModel.Live(1500, 60, null, 37, 1500, 4000, "com.zjcs.android.us");
        assertEquals("ADB: slow, 1500 ms", live.adb());
        assertEquals("CPU 37% · memory 1500 / 4000 MB", live.cpuAndMemory());
        assertEquals("In front: com.zjcs.android.us", live.inFront());
    }

    @Test
    void showFindsTheInstancesWindowByItsName() {
        assertEquals(Optional.of("Farm"), RunningModel.windowTitle("Farm", List.of("LDPlayer", "Farm", "Farmhouse")));
        assertEquals(Optional.of("MuMu Player 12 - MuMu-0"),
                RunningModel.windowTitle("MuMu-0", List.of("MuMu Player 12 - MuMu-0", "MuMu Player 12 - MuMu-1")));
        assertTrue(RunningModel.windowTitle("Farm", List.of("Farmhouse")).isEmpty(), "a word, not a part of one");
        assertTrue(RunningModel.windowTitle("Farm", List.of("Farm 1", "Farm 2")).isEmpty(), "two match: none shown");
        assertTrue(RunningModel.windowTitle("", List.of("")).isEmpty());
    }

}
