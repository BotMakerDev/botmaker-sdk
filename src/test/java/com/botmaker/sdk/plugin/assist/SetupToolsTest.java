package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.shared.emulator.checks.CheckResult;
import com.botmaker.shared.emulator.checks.CheckState;
import com.botmaker.shared.emulator.checks.EmulatorCheck;
import com.botmaker.shared.emulator.checks.Fix;
import com.botmaker.shared.emulator.checks.FixRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** setup's {@code emulator_checks} and {@code fix}, over rows and fixes the tests give: nothing asks for a password. */
class SetupToolsTest {

    private static final CheckResult TRANSLATOR = new CheckResult(EmulatorCheck.ARM_TRANSLATOR, CheckState.FAILED,
            "libhoudini on an AMD processor.", "Fix installs libndk.", true, false, List.of(), null);
    private static final CheckResult INSTALLED = new CheckResult(EmulatorCheck.WAYDROID_INSTALLED, CheckState.OK,
            "Waydroid is installed.", "", false, false, List.of(), null);
    private static final CheckResult BY_HAND = new CheckResult(EmulatorCheck.WAYDROID_RUNNING, CheckState.FAILED,
            "The service can't start.", "Restore the labels.", false, false, List.of("sudo restorecon"), null);

    private static String text(AgentReply reply) {
        return reply.parts().stream().filter(p -> p.kind() == AgentReply.Kind.TEXT).map(AgentReply.Part::text)
                .collect(Collectors.joining("\n"));
    }

    private static AgentReply fix(String check, boolean undo, CheckResult result, Optional<Fix> fix) {
        return ChecksTools.fix(check, undo, List.of(INSTALLED, result), r -> fix, () -> fix,
                FixRunner.of("setup-tools-test-refused"));
    }

    @Test
    void theAnswerSaysOfEachCheckWhetherItIsOkAndWhetherFixRepairsIt() {
        String answer = ChecksTools.answer(List.of(INSTALLED, TRANSLATOR), CheckResult::fixable, FixRunner.of("none"));
        assertTrue(answer.contains("arm_translator ok=false fix=yes: libhoudini"), answer);
        assertTrue(answer.contains("waydroid_installed ok=true fix=no"), answer);
        assertTrue(answer.contains("Fix installs libndk."), answer);
    }

    @Test
    void aFailureNoFixRepairsSaysWhatTheUserRuns() {
        String answer = ChecksTools.answer(List.of(BY_HAND), CheckResult::fixable, FixRunner.of("none"));
        assertTrue(answer.contains("waydroid_running ok=false fix=no"), answer);
        assertTrue(answer.contains("The user runs: sudo restorecon"), answer);
    }

    @Test
    void anUnknownCheckIsRefusedNamingTheValidOnes() {
        AgentReply reply = fix("unknown", false, TRANSLATOR, Optional.empty());
        assertTrue(reply.isRefused());
        assertTrue(text(reply).contains("waydroid_installed, arm_translator"), text(reply));
    }

    @Test
    void whatCannotBeFixedIsRefusedAndStartsNothing() {
        assertTrue(fix("waydroid_installed", false, TRANSLATOR, Optional.empty()).isRefused(), "it does not fail");
        AgentReply byHand = fix("waydroid_running", false, BY_HAND, Optional.empty());
        assertTrue(byHand.isRefused());
        assertTrue(text(byHand).contains("sudo restorecon"), text(byHand));
        AgentReply noUndo = fix("arm_translator", true, TRANSLATOR, Optional.empty());
        assertTrue(noUndo.isRefused());
        assertTrue(text(noUndo).contains("no fix to undo"), text(noUndo));
    }

    @Test
    void aFixStartsInTheBackgroundAndEmulatorChecksSaysHowItWent() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        Fix fix = new Fix(EmulatorCheck.ARM_TRANSLATOR, List.of(new Fix.AsUser("Waiting", words -> {
            release.await();
            return null;
        })), null);
        FixRunner runner = FixRunner.of("setup-tools-test-started");

        AgentReply started = ChecksTools.fix("arm_translator", false, List.of(TRANSLATOR), r -> Optional.of(fix),
                Optional::empty, runner);
        assertFalse(started.isRefused());
        assertTrue(text(started).contains("emulator_checks says how it went"), text(started));
        assertTrue(runner.running(), "it answered while the fix still runs");

        AgentReply second = ChecksTools.fix("arm_translator", false, List.of(TRANSLATOR), r -> Optional.of(fix),
                Optional::empty, runner);
        assertTrue(text(second).contains("already running"), text(second));

        release.countDown();
        for (int i = 0; i < 200 && runner.running(); i++) Thread.sleep(25);
        assertFalse(runner.running());
        // The last report arrives just after running() turns false.
        String answer = "";
        for (int i = 0; i < 200 && !answer.startsWith("Last fix: ARM translator: done."); i++) {
            answer = ChecksTools.answer(List.of(TRANSLATOR), CheckResult::fixable, runner);
            Thread.sleep(25);
        }
        assertTrue(answer.startsWith("Last fix: ARM translator: done."), answer);
        assertFalse(ChecksTools.answer(List.of(TRANSLATOR), CheckResult::fixable, FixRunner.of("another emulator"))
                .contains("Last fix"), "another emulator's fix is not this one's");
    }

    @Test
    void aRefusedLaunchNamesTheCheckAndItsFix() {
        CheckResult down = new CheckResult(EmulatorCheck.WAYDROID_RUNNING, CheckState.FAILED,
                "The service is not running.", "Fix starts it.", true, false, List.of(), null);
        assertTrue(ChecksTools.stopping(down).equals("Container and session running fails: The service is not "
                + "running. setup's fix runs its fix."), ChecksTools.stopping(down));
        assertTrue(ChecksTools.stopping(BY_HAND).contains("sudo restorecon"), ChecksTools.stopping(BY_HAND));
    }
}
