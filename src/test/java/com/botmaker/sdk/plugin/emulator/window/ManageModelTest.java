package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.InstanceKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Clone, Rename and Delete's names and words, and which emulators offer them (the picker's Manage… too). */
class ManageModelTest {

    private static EmulatorInstance ld(String name) {
        return new EmulatorInstance(PlatformId.LDPLAYER, name, "127.0.0.1", 5555);
    }

    @Test
    void aCopyIsOfferedTheFirstFreeName() {
        assertEquals("Farm copy", ManageModel.cloneName("Farm", List.of(ld("Farm"))));
        assertEquals("Farm copy 3", ManageModel.cloneName("Farm",
                List.of(ld("Farm"), ld("farm COPY"), ld("Farm copy 2"))));
    }

    @Test
    void theConsolesAndBlueStacksManagerAreManaged() {
        for (PlatformId managed : List.of(PlatformId.LDPLAYER, PlatformId.MUMU, PlatformId.MEMU,
                PlatformId.BLUESTACKS)) {
            assertTrue(ManageModel.manageable(managed), managed.id());
            assertTrue(EmulatorsWindow.manages(managed), managed.id());
        }
        for (PlatformId not : List.of(PlatformId.PHYSICAL, PlatformId.AVD, PlatformId.WAYDROID, PlatformId.UNKNOWN)) {
            assertFalse(ManageModel.manageable(not), not.id());
        }
    }

    @Test
    void deleteNamesTheBotsThatLaunchIt() {
        EmulatorInstance farm = ld("Farm");
        EmulatorsModel.InstanceRow used = row(farm, true, List.of("SwordXStaff", "Other"));
        String asked = ManageModel.deleteQuestion(used);
        assertTrue(asked.startsWith("Delete Farm (LDPlayer)?"), asked);
        assertTrue(asked.contains("It is running: it is stopped first."), asked);
        assertTrue(asked.contains("Bots launch it: SwordXStaff, Other."), asked);
        String alone = ManageModel.deleteQuestion(row(farm, false, List.of()));
        assertFalse(alone.contains("launch"), alone);
        assertFalse(alone.contains("running"), alone);
    }

    @Test
    void renameSaysWhenItsBotsFollow() {
        EmulatorInstance farm = ld("Farm");
        assertEquals("No bot was seen launching it.", ManageModel.renameNote(row(farm, false, List.of())));
        assertTrue(ManageModel.renameNote(row(farm, false, List.of("SwordXStaff")))
                .startsWith("Launched by SwordXStaff. Each is pointed at the new name"));
    }

    private static EmulatorsModel.InstanceRow row(EmulatorInstance instance, boolean running, List<String> usedBy) {
        return new EmulatorsModel.InstanceRow(instance, InstanceKey.of(instance), running, null, null, null, usedBy);
    }
}
