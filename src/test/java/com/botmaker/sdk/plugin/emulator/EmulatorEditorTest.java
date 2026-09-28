package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.shared.emulator.EmulatorInstances;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What the {@code @EmulatorName} pill says before anything is picked. */
class EmulatorEditorTest {

    @Test
    void a_name_reads_as_its_caption() {
        assertEquals(EmulatorInstances.captionFor("Pie64"),
                EmulatorEditors.label(TestContexts.typedSlot(String.class, "\"Pie64\"").withValue("Pie64")));
    }

    @Test
    void an_empty_name_asks_for_a_device() {
        assertEquals(EmulatorEditors.CHOOSE,
                EmulatorEditors.label(TestContexts.typedSlot(String.class, "\"\"").withValue("")));
    }

    /** A constant or a variable is a filled slot the host could not read, never an empty one. */
    @Test
    void an_unread_name_is_shown_as_written() {
        assertEquals("Config.DEVICE", EmulatorEditors.label(TestContexts.typedSlot(String.class, "Config.DEVICE")));
    }
}
