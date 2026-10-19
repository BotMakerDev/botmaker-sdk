package com.botmaker.sdk.plugin.launch;

import com.botmaker.shared.emulator.AdbEndpoint;
import com.botmaker.shared.emulator.EmulatorAppCache;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorProbe;
import com.botmaker.shared.emulator.PhoneState;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.game.InstalledGame;
import com.botmaker.shared.game.FaugusLibraryScanner;
import com.botmaker.shared.game.GogLibraryScanner;
import com.botmaker.shared.game.LutrisLibraryScanner;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the game dialog lists and stores: a launcher entry as its kind and id, the menu split into Android apps,
 * games and the rest, and the recents kept newest first.
 */
class GameCatalogTest {

    @Test
    void eachLauncherEntryIsStoredAsItsKindAndId() {
        assertEquals(Optional.of("faugus:battlenet"), GameCatalog.specOf(new FaugusLibraryScanner(),
                new InstalledGame("faugus", "battlenet", "Battle.net", null)));
        assertEquals(Optional.of("lutris:3"), GameCatalog.specOf(new LutrisLibraryScanner(),
                new InstalledGame("lutris", "3", "Epic Games Store", null)));
        assertEquals(Optional.of("exe:C:\\GOG\\ut.exe"), GameCatalog.specOf(new GogLibraryScanner(),
                new InstalledGame("exe", "C:\\GOG\\ut.exe", "Unreal Tournament", null)),
                "a GOG game needs no launcher, so it is its executable");
        assertEquals(Optional.empty(), GameCatalog.specOf(new FaugusLibraryScanner(),
                new InstalledGame("faugus", " ", "x", null)));
    }

    @Test
    void aTypedTargetIsKeptOnlyWhenARunCanLaunchIt() {
        assertEquals(Optional.of("faugus:battlenet"), GameCatalog.typed("  faugus: battlenet "));
        assertEquals(Optional.of("lutris:3"), GameCatalog.typed("lutris:3"));
        assertEquals(Optional.of("desktop:org.kde.kpat"), GameCatalog.typed("desktop:org.kde.kpat"));
        assertEquals(Optional.empty(), GameCatalog.typed("battlenet"));
        assertEquals(Optional.empty(), GameCatalog.typed("gog:1"));
    }

    @Test
    void theMenuIsSplitAndLauncherShortcutsAreLeftToTheirLauncher() {
        var kpat = new com.botmaker.shared.launch.DesktopEntries.Entry("org.kde.kpat", "KPatience", "kpat",
                "", java.util.Set.of("Game"), "");
        var editor = new com.botmaker.shared.launch.DesktopEntries.Entry("org.kde.kate", "Kate", "kate %U",
                "", java.util.Set.of("Utility"), "");
        var balatro = new com.botmaker.shared.launch.DesktopEntries.Entry("Balatro", "Balatro",
                "steam steam://rungameid/2379780", "", java.util.Set.of("Game"), "");
        var firestone = new com.botmaker.shared.launch.DesktopEntries.Entry("waydroid.com.x", "Firestone",
                "waydroid app launch com.x", "", java.util.Set.of("X-WayDroid-App"), "");

        List<GameCatalog.Section> sections = GameCatalog.menuSections(List.of(kpat, editor, balatro, firestone));

        assertEquals(List.of(GameCatalog.Kind.ANDROID, GameCatalog.Kind.MENU_GAMES, GameCatalog.Kind.OTHER_APPS),
                sections.stream().map(GameCatalog.Section::kind).toList());
        assertEquals(List.of("emu-app:com.x@Waydroid"), specs(sections.get(0)));
        assertEquals(List.of("desktop:org.kde.kpat"), specs(sections.get(1)), "Balatro is in Steam's section");
        assertEquals(List.of("desktop:org.kde.kate"), specs(sections.get(2)));
        assertTrue(sections.get(2).folded(), "every other app starts folded");
    }

    @Test
    void recentsAreNewestFirstWithoutDuplicatesAndRoundTrip() {
        var a = new RecentTargets.Recent("steam:1", "Alpha", Path.of("/covers/a.jpg"));
        var b = new RecentTargets.Recent("lutris:2", "Beta", null);
        List<RecentTargets.Recent> list = RecentTargets.pushed(RecentTargets.pushed(List.of(a), b), a);
        assertEquals(List.of(a, b), list);
        assertEquals(list, RecentTargets.decode(RecentTargets.encode(list)));

        List<RecentTargets.Recent> many = List.of();
        for (int i = 0; i < 12; i++) many = RecentTargets.pushed(many, new RecentTargets.Recent("steam:" + i, "G", null));
        assertEquals(RecentTargets.LIMIT, many.size());
        assertEquals("steam:11", many.getFirst().spec());
    }

    @Test
    void aSpecNotPickedFromTheDialogIsNamedByItsShortLabel() {
        assertEquals("1145350", GameButton.nameOf(null, "steam:1145350"));
        assertEquals("run.sh", GameButton.nameOf(null, "exe:/opt/game/run.sh"));
        assertEquals(new LaunchSpec(LaunchKind.LUTRIS, "3"), LaunchSpec.parse("lutris:3"));
    }

    @Test
    void eachEmulatorInstanceWithRememberedAppsIsASectionOfItsAppsByNameDimmedWhenStopped(@TempDir Path root)
            throws Exception {
        EmulatorAppCache cache = new EmulatorAppCache(root);
        EmulatorInstance ld = new EmulatorInstance(PlatformId.LDPLAYER, "LDPlayer", "127.0.0.1", 5555);
        EmulatorInstance mumu = new EmulatorInstance(PlatformId.MUMU, "Android Device", "127.0.0.1", 16384);
        EmulatorInstance memu = new EmulatorInstance(PlatformId.MEMU, "MEmu", "127.0.0.1", 21503);
        EmulatorInstance waydroid = new EmulatorInstance(PlatformId.WAYDROID, "Waydroid", "192.168.240.112", 5555);
        cache.putPackages(ld, List.of(new EmulatorProbe.InstalledApp("com.supercell.clashofclans", "Clash of Clans"),
                new EmulatorProbe.InstalledApp("com.unnamed.game", null)));
        cache.putPackages(mumu, List.of(new EmulatorProbe.InstalledApp("com.farm", "Farm")));
        cache.putPackages(waydroid, List.of(new EmulatorProbe.InstalledApp("com.farm", "Farm")));
        java.awt.image.BufferedImage icon = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        cache.putIcon(ld, "com.supercell.clashofclans", icon);

        List<GameCatalog.Section> sections = GameCatalog.emulatorSections(List.of(ld, memu, mumu, waydroid), cache,
                instance -> instance == mumu, instance -> PhoneState.UNKNOWN);

        assertEquals(List.of("LDPlayer: LDPlayer", "MuMu Player: Android Device"),
                sections.stream().map(GameCatalog.Section::title).toList(),
                "MEmu was never listed, and Waydroid's apps are in the app menu's section");
        GameCatalog.Section ldSection = sections.get(0);
        assertEquals(GameCatalog.Kind.EMULATOR, ldSection.kind());
        assertTrue(ldSection.stopped());
        assertEquals(List.of("Clash of Clans", "com.unnamed.game"),
                ldSection.items().stream().map(GameCatalog.Item::name).toList());
        assertEquals(List.of("emu-app:com.supercell.clashofclans@LDPlayer", "emu-app:com.unnamed.game@LDPlayer"),
                specs(ldSection));
        assertTrue(ldSection.items().get(0).artwork() != null && Files.isRegularFile(ldSection.items().get(0).artwork()));
        assertEquals(null, ldSection.items().get(1).artwork());
        assertEquals(false, sections.get(1).stopped());
    }

    /** A phone whose apps were never read is still a section, saying what it waits on; an emulator's isn't. */
    @Test
    void aPhoneWithNoAppsSeenYetIsASectionWithItsNote(@TempDir Path root) {
        EmulatorAppCache cache = new EmulatorAppCache(root);
        EmulatorInstance phone = new EmulatorInstance(PlatformId.PHYSICAL, "R58M1", new AdbEndpoint.Server("R58M1"));
        EmulatorInstance ld = new EmulatorInstance(PlatformId.LDPLAYER, "LDPlayer", "127.0.0.1", 5555);

        List<GameCatalog.Section> sections = GameCatalog.emulatorSections(List.of(phone, ld), cache,
                instance -> false, instance -> PhoneState.UNAUTHORIZED);

        assertEquals(1, sections.size(), "an emulator with no apps seen stays out");
        assertEquals(List.of(), sections.getFirst().items());
        assertEquals(PhoneState.UNAUTHORIZED.advice(), sections.getFirst().note());
    }

    @Test
    void reReadEmulatorSectionsTakeTheOldOnesPlaceAfterTheLaunchers() {
        GameCatalog.Section steam = new GameCatalog.Section(GameCatalog.Kind.LAUNCHER, "Steam", false, List.of());
        GameCatalog.Section oldLd = new GameCatalog.Section(GameCatalog.Kind.EMULATOR, "LDPlayer: LDPlayer", false,
                true, List.of());
        GameCatalog.Section android = new GameCatalog.Section(GameCatalog.Kind.ANDROID, "Android", false, List.of());
        GameCatalog.Section newLd = new GameCatalog.Section(GameCatalog.Kind.EMULATOR, "LDPlayer: LDPlayer", false,
                false, List.of());
        GameCatalog.Section mumu = new GameCatalog.Section(GameCatalog.Kind.EMULATOR, "MuMu Player: Android Device",
                false, false, List.of());

        assertEquals(List.of(steam, newLd, mumu, android),
                GameCatalog.replaceEmulators(List.of(steam, oldLd, android), List.of(newLd, mumu)));
        assertEquals(List.of(steam, mumu, android),
                GameCatalog.replaceEmulators(List.of(steam, android), List.of(mumu)), "none listed before");
        assertEquals(List.of(steam, android), GameCatalog.replaceEmulators(List.of(steam, oldLd, android), List.of()));
    }

    @Test
    void aGameIsHereWhenTheLastScanListedItOrItsProgramIsOnDisk(@TempDir Path dir) throws Exception {
        List<GameCatalog.Section> scanned = List.of(new GameCatalog.Section(GameCatalog.Kind.LAUNCHER, "Steam", false,
                List.of(new GameCatalog.Item("steam:570", "Dota 2", null))));
        java.util.Set<String> listed = GameCatalog.listedIn(scanned);
        assertEquals(Optional.of(true), GameCatalog.installed(" steam: 570", listed));
        assertEquals(Optional.of(false), GameCatalog.installed("lutris:3", listed));
        assertEquals(Optional.empty(), GameCatalog.installed("lutris:3", null), "no scan yet: unknown");
        Path exe = Files.createFile(dir.resolve("game.exe"));
        assertEquals(Optional.of(true), GameCatalog.installed("exe:" + exe, null), "a program is looked for on disk");
        assertEquals(Optional.of(false), GameCatalog.installed("exe:" + dir.resolve("gone.exe"), null));
        assertEquals(Optional.of(false), GameCatalog.installed("nonsense", listed));
    }

    @Test
    void theSavedListReadsBackAsItWasShown(@TempDir Path dir) {
        Path file = dir.resolve("cache/games.json");
        List<GameCatalog.Section> shown = List.of(
                new GameCatalog.Section(GameCatalog.Kind.LAUNCHER, "Steam", false, List.of(
                        new GameCatalog.Item("steam:570", "Dota 2", Path.of("/art/570.jpg")),
                        new GameCatalog.Item("steam:440", "Team Fortress 2", null))),
                new GameCatalog.Section(GameCatalog.Kind.EMULATOR, "Phone", false, false, List.of(), "Allow USB debugging"));
        GameCatalog.save(file, shown, Set.of("steam:570", "steam:440", "heroic:Fn"));

        GameCatalog.Snapshot back = GameCatalog.load(file).orElseThrow();
        assertEquals(shown, back.shown());
        assertEquals(Set.of("steam:570", "steam:440", "heroic:Fn"), back.listed());
    }

    @Test
    void aSavedListThatDoesNotReadIsNone(@TempDir Path dir) throws Exception {
        assertTrue(GameCatalog.load(dir.resolve("none.json")).isEmpty());
        Path corrupt = Files.writeString(dir.resolve("corrupt.json"), "{\"version\":1,\"sections\":[{");
        assertTrue(GameCatalog.load(corrupt).isEmpty());
        Path older = Files.writeString(dir.resolve("older.json"), "{\"version\":0,\"sections\":[],\"listed\":[]}");
        assertTrue(GameCatalog.load(older).isEmpty(), "another version's file is scanned again, not read");
        Path unknownKind = Files.writeString(dir.resolve("kind.json"),
                "{\"version\":1,\"sections\":[{\"kind\":\"GONE\",\"title\":\"x\",\"items\":[]}],\"listed\":[]}");
        assertTrue(GameCatalog.load(unknownKind).isEmpty());
    }

    @Test
    void aRescanRemakesOnlyTheTilesItDroppedOrChanged() {
        GameCatalog.Item dota = new GameCatalog.Item("steam:570", "Dota 2", null);
        GameCatalog.Item tf2 = new GameCatalog.Item("steam:440", "Team Fortress 2", null);
        GameCatalog.Item hades = new GameCatalog.Item("exe:/g/hades", "Hades", null);
        List<GameCatalog.Section> scanned = List.of(new GameCatalog.Section(GameCatalog.Kind.LAUNCHER, "Steam", false,
                List.of(dota, new GameCatalog.Item("steam:440", "TF2", null),
                        new GameCatalog.Item("steam:730", "Counter-Strike 2", null))));

        assertEquals(Set.of("steam:440", "exe:/g/hades"), GameCatalog.outdated(
                Map.of(dota.spec(), dota, tf2.spec(), tf2, hades.spec(), hades), scanned),
                "Dota 2 stays; TF2 renamed and Hades removed are remade; Counter-Strike 2 is simply new");
    }

    private static List<String> specs(GameCatalog.Section section) {
        return section.items().stream().map(GameCatalog.Item::spec).toList();
    }
}
