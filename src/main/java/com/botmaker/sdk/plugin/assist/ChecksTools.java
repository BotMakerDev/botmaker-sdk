package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.plugin.emulator.ChecksRow;
import com.botmaker.sdk.plugin.launch.QuickLaunch;
import com.botmaker.sdk.plugin.pictures.ScreenSize;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.emulator.WaydroidResolution;
import com.botmaker.shared.emulator.checks.CheckAnswers;
import com.botmaker.shared.emulator.checks.CheckResult;
import com.botmaker.shared.emulator.checks.EmulatorCheck;
import com.botmaker.shared.emulator.checks.EmulatorChecks;
import com.botmaker.shared.emulator.checks.Fix;
import com.botmaker.shared.emulator.checks.FixRunner;
import com.botmaker.shared.emulator.checks.Fixes;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * setup's {@code emulator_checks} and {@code fix}: the Game dialog's rows and Fix buttons, for the assistant, in the
 * words {@link CheckAnswers} gives them (the core's {@code device} tool says the same). A Fix runs in the background
 * on the runner the rows use, so the two never run at once, and any root step opens the password window on the
 * user's desktop, which is the user's say on it.
 */
final class ChecksTools {

    private static final String NO_EMULATOR = "The bot's launch target is no emulator app, so there is nothing to "
            + "check. list_games and setup's set_launch_target pick one.";

    private ChecksTools() {}

    static AgentReply checks(AgentContext context) {
        LaunchSpec spec = target(context);
        Optional<AgentReply> refused = refusal(spec);
        if (refused.isPresent()) return refused.get();
        ChecksRow.Read read = ChecksRow.read(spec, size(context.services()));
        Fixes.Context fixes = fixContext(read, context.services());
        return AgentReply.text(CheckAnswers.answer(read.results(), result -> Fixes.of(result, fixes).isPresent(),
                CheckAnswers.runnerOf(read.instance())));
    }

    static AgentReply fix(String check, boolean undo, AgentContext context) {
        LaunchSpec spec = target(context);
        Optional<AgentReply> refused = refusal(spec);
        if (refused.isPresent()) return refused.get();
        ChecksRow.Read read = ChecksRow.read(spec, size(context.services()));
        Fixes.Context fixes = fixContext(read, context.services());
        return fix(check, undo, read.results(), result -> Fixes.of(result, fixes), named -> Fixes.undo(fixes, named),
                CheckAnswers.runnerOf(read.instance()));
    }

    /**
     * Why launch_game can't start {@code spec}'s emulator, naming the check and its fix; empty for anything else,
     * a stopped session among them.
     */
    static Optional<String> stoppingLaunch(LaunchSpec spec) {
        if (spec.kind() != LaunchKind.EMULATOR_APP || spec.emulatorInstance() == null) return Optional.empty();
        return EmulatorChecks.stoppingLaunch(ChecksRow.read(spec, null).results()).map(ChecksTools::stopping);
    }

    static String stopping(CheckResult failed) {
        return failed.check().displayName() + " fails: " + failed.reason() + (failed.fixable()
                ? " setup's fix runs its fix." : " " + CheckAnswers.byHand(failed));
    }

    /** Picks the fix {@code check} names in {@code results} and starts it. */
    static AgentReply fix(String check, boolean undo, List<CheckResult> results,
                          Function<CheckResult, Optional<Fix>> fixOf, Function<EmulatorCheck, Optional<Fix>> undoFix,
                          FixRunner runner) {
        CheckAnswers.Said said = CheckAnswers.fix(check, undo, results, fixOf, undoFix, runner, "emulator_checks");
        return said.refused() ? AgentReply.refused(said.text()) : AgentReply.text(said.text());
    }

    /** The bot's target, or null when it has none it reads. */
    private static LaunchSpec target(AgentContext context) {
        String current = FxCall.call(() -> LaunchTargetValue.current(context.services()));
        return current == null ? null : LaunchSpec.parse(current);
    }

    /** Only an emulator app has checks; its package reaches adb when a fix starts it, so it is held to a package. */
    private static Optional<AgentReply> refusal(LaunchSpec spec) {
        if (spec == null || spec.kind() != LaunchKind.EMULATOR_APP || spec.emulatorInstance() == null) {
            return Optional.of(AgentReply.refused(NO_EMULATOR));
        }
        String pkg = spec.emulatorPackage();
        return pkg != null && SetupTools.PACKAGE.matcher(pkg).matches() ? Optional.empty()
                : Optional.of(AgentReply.refused("\"" + pkg + "\" is not an app's package, like com.example.game."));
    }

    private static Fixes.Context fixContext(ChecksRow.Read read, StudioServices services) {
        return ChecksRow.context(read, services, QuickLaunch::startAndWait);
    }

    private static WaydroidResolution size(StudioServices services) {
        return FxCall.call(() -> ScreenSize.read(services)
                .map(screen -> new WaydroidResolution(screen.width(), screen.height())).orElse(null));
    }
}
