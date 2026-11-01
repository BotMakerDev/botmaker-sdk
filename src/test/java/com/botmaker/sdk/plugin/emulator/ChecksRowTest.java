package com.botmaker.sdk.plugin.emulator;

import com.botmaker.shared.emulator.checks.CheckResult;
import com.botmaker.shared.emulator.checks.CheckState;
import com.botmaker.shared.emulator.checks.EmulatorCheck;
import com.botmaker.shared.emulator.checks.Fix;
import com.botmaker.shared.emulator.checks.FixRunner;
import com.botmaker.shared.emulator.checks.RootScript.Outcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What each row says, whichever state its check is in. */
class ChecksRowTest {

    private static CheckResult result(EmulatorCheck check, CheckState state, String reason, boolean fixable,
                                      boolean undoable) {
        return new CheckResult(check, state, reason, "", fixable, undoable, List.of(), null);
    }

    @Test
    void eachStateHasItsLine() {
        assertEquals(List.of(
                "✓ Internet inside Android — online",
                "! ARM translator — libhoudini on an AMD CPU [Fix]",
                "! Waydroid installed — not found",
                "· Screen size (after the one above)",
                "○ Do Not Disturb during runs — off"), ChecksRow.lines(List.of(
                result(EmulatorCheck.INTERNET, CheckState.OK, "online", false, false),
                result(EmulatorCheck.ARM_TRANSLATOR, CheckState.FAILED, "libhoudini on an AMD CPU", true, false),
                result(EmulatorCheck.WAYDROID_INSTALLED, CheckState.FAILED, "not found", false, false),
                result(EmulatorCheck.RESOLUTION, CheckState.AFTER_ABOVE, "", false, false),
                result(EmulatorCheck.DO_NOT_DISTURB, CheckState.INFO, "off", false, false))));
    }

    @Test
    void undoShowsOnAnOkRow() {
        assertEquals("✓ ARM translator — libndk [Undo]", ChecksRow.line(
                result(EmulatorCheck.ARM_TRANSLATOR, CheckState.OK, "libndk", false, true), false, null));
    }

    @Test
    void aRunningFixSaysItsStep() {
        FixRunner.Progress step = new FixRunner.Progress(2, 4, "installing libndk…", null, false, List.of());
        assertEquals("⟳ ARM translator — Step 2/4: installing libndk…", ChecksRow.line(
                result(EmulatorCheck.ARM_TRANSLATOR, CheckState.FAILED, "x", true, false), true, step));
    }

    @Test
    void theEndSaysWhetherItTook() {
        Fix fix = new Fix(EmulatorCheck.INTERNET, List.of(), null);
        assertEquals("Internet inside Android: done.", ChecksRow.ended(fix,
                new FixRunner.Progress(1, 1, "", Outcome.DONE, false, List.of())));
        assertEquals("Internet inside Android: the fix ran, but the check still fails.", ChecksRow.ended(fix,
                new FixRunner.Progress(1, 1, "", Outcome.DONE, true, List.of())));
        assertEquals("Internet inside Android: Cancelled, nothing changed.", ChecksRow.ended(fix,
                new FixRunner.Progress(1, 1, "", Outcome.CANCELLED, false, List.of())));
    }
}
