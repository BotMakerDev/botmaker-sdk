package com.botmaker.sdk.plugin.emulator;

import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorState;
import com.botmaker.shared.emulator.PlatformId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What a row says its product needs: only a stopped AVD asks for KVM, and only when the machine lacks it. */
class EmulatorPickerNotesTest {

    private static final String NEED = "Needs KVM: add yourself to the kvm group, then log in again";

    @Test
    void kvmLineOnlyForAvd() {
        EmulatorInstance avd = new EmulatorInstance(PlatformId.AVD, "Pixel_7", "127.0.0.1", 5555)
                .withState(EmulatorState.STOPPED);
        EmulatorInstance waydroid = new EmulatorInstance(PlatformId.WAYDROID, "Waydroid", "192.168.240.112", 5555)
                .withState(EmulatorState.STOPPED);

        assertEquals(NEED, EmulatorPicker.avdNote(avd, NEED));
        assertNull(EmulatorPicker.avdNote(avd, null));
        assertNull(EmulatorPicker.avdNote(waydroid, NEED));
    }

    /** It is up, so whatever the check says, it needs nothing: the line would contradict the row. */
    @Test
    void noKvmLineOnAnAvdThatIsUp() {
        EmulatorInstance avd = new EmulatorInstance(PlatformId.AVD, "Pixel_7", "127.0.0.1", 5555);

        assertNull(EmulatorPicker.avdNote(avd.withState(EmulatorState.RUNNING), NEED));
        assertNull(EmulatorPicker.avdNote(avd.withState(EmulatorState.STARTING), NEED));
    }
}
