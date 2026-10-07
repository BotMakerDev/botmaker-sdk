package com.botmaker.sdk.plugin.launch;

import com.botmaker.shared.game.InstalledGame;
import com.botmaker.shared.game.FaugusLibraryScanner;
import com.botmaker.shared.game.GogLibraryScanner;
import com.botmaker.shared.game.LutrisLibraryScanner;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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

    private static List<String> specs(GameCatalog.Section section) {
        return section.items().stream().map(GameCatalog.Item::spec).toList();
    }
}
