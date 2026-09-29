package com.botmaker.sdk.plugin.launch;

import com.botmaker.shared.game.InstalledGame;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a pick in Project Setup's <b>Choose…</b> stores: the launcher's kind and its entry's id, which parses
 * back to the same launcher and id a run starts.
 */
class LaunchTargetChooserTest {

    @Test
    void eachLauncherEntryIsStoredAsItsKindAndId() {
        assertEquals(Optional.of("faugus:battlenet"),
                LaunchTargetChooser.specOf(new InstalledGame("faugus", "battlenet", "Battle.net", null)));
        assertEquals(Optional.of("heroic:43d4ab"),
                LaunchTargetChooser.specOf(new InstalledGame("heroic", "43d4ab", "Firestone", null)));
        assertEquals(Optional.of("steam:620"),
                LaunchTargetChooser.specOf(new InstalledGame("steam", "620", "Portal 2", null)));
        assertEquals(Optional.of("epic:Fortnite"),
                LaunchTargetChooser.specOf(new InstalledGame("epic", "Fortnite", "Fortnite", null)));
    }

    @Test
    void theStoredSpecParsesBackToTheSameLauncherAndId() {
        String spec = LaunchTargetChooser.specOf(new InstalledGame("faugus", "hoyoplay", "HoYoPlay", null))
                .orElseThrow();
        assertEquals(new LaunchSpec(LaunchKind.FAUGUS, "hoyoplay"), LaunchSpec.parse(spec));
    }

    @Test
    void anEntryWithNoIdOrAnUnknownLauncherIsLeftOut() {
        assertEquals(Optional.empty(), LaunchTargetChooser.specOf(new InstalledGame("faugus", " ", "x", null)));
        assertEquals(Optional.empty(), LaunchTargetChooser.specOf(new InstalledGame("gog", "1", "x", null)));
    }

    @Test
    void aTypedTargetIsKeptOnlyWhenARunCanLaunchIt() {
        assertEquals(Optional.of("faugus:battlenet"), LaunchTargetChooser.typed("  faugus: battlenet "));
        assertEquals(Optional.of("exe:/opt/game/run.sh"), LaunchTargetChooser.typed("exe:/opt/game/run.sh"));
        assertEquals(Optional.empty(), LaunchTargetChooser.typed("battlenet"));
        assertEquals(Optional.empty(), LaunchTargetChooser.typed("gog:1"));
        assertEquals(Optional.empty(), LaunchTargetChooser.typed(""));
    }
}
