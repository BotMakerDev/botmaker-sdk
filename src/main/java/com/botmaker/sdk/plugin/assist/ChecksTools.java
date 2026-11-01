package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.plugin.emulator.ChecksRow;
import com.botmaker.sdk.plugin.launch.QuickLaunch;
import com.botmaker.sdk.plugin.pictures.ScreenSize;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.emulator.WaydroidResolution;
import com.botmaker.shared.emulator.checks.CheckResult;
import com.botmaker.shared.emulator.checks.CheckState;
import com.botmaker.shared.emulator.checks.EmulatorCheck;
import com.botmaker.shared.emulator.checks.EmulatorChecks;
import com.botmaker.shared.emulator.checks.Fix;
import com.botmaker.shared.emulator.checks.FixRunner;
import com.botmaker.shared.emulator.checks.Fixes;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * setup's {@code emulator_checks} and {@code fix}: the Game dialog's rows and Fix buttons, for the assistant. A Fix
 * runs in the background on the runner the rows use, so the two never run at once, and any root step opens the
 * password window on the user's desktop, which is the user's say on it.
 */
final class ChecksTools {

    /** Per emulator's runner: what its last fix is doing, or how it ended with what it printed. */
    private static final Map<FixRunner, String> FIX_LINE = new ConcurrentHashMap<>();

    private static final String NO_EMULATOR = "The bot's launch target is no emulator app, so there is nothing to "
            + "check. list_games and setup's set_launch_target pick one.";

    private ChecksTools() {}

    static AgentReply checks(AgentContext context) {
        LaunchSpec spec = target(context);
        Optional<AgentReply> refused = refusal(spec);
        if (refused.isPresent()) return refused.get();
        ChecksRow.Read read = ChecksRow.read(spec, size(context.services()));
        Fixes.Context fixes = fixContext(read, context.services());
        return AgentReply.text(answer(read.results(), result -> Fixes.of(result, fixes).isPresent(),
                ChecksRow.runnerOf(read.instance())));
    }

    static AgentReply fix(String check, boolean undo, AgentContext context) {
        LaunchSpec spec = target(context);
        Optional<AgentReply> refused = refusal(spec);
        if (refused.isPresent()) return refused.get();
        ChecksRow.Read read = ChecksRow.read(spec, size(context.services()));
        Fixes.Context fixes = fixContext(read, context.services());
        return fix(check, undo, read.results(), result -> Fixes.of(result, fixes), Fixes::undo,
                ChecksRow.runnerOf(read.instance()));
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
                ? " setup's fix runs its fix." : " " + byHand(failed));
    }

    /** The rows, one a line, under how the last fix went. */
    static String answer(List<CheckResult> results, Function<CheckResult, Boolean> canFix, FixRunner runner) {
        if (results.isEmpty()) return "BotMaker has no checks for this emulator.";
        List<String> lines = new ArrayList<>();
        String last = FIX_LINE.get(runner);
        if (last != null) lines.add("Last fix: " + last);
        for (CheckResult result : results) lines.add(row(result, canFix.apply(result)));
        return String.join("\n", lines);
    }

    private static String row(CheckResult result, boolean canFix) {
        String ok = switch (result.state()) {
            case OK -> "true";
            case FAILED -> "false";
            case AFTER_ABOVE -> "after_the_one_above";
            case INFO -> "info";
            case UNKNOWN -> "unknown";
        };
        String row = result.check().id() + " ok=" + ok + " fix=" + (canFix ? "yes" : "no")
                + (result.undoable() ? " undo=yes" : "");
        if (result.state() == CheckState.AFTER_ABOVE) return row;
        String said = result.reason();
        if (result.failed()) said += " " + (canFix ? result.remedy() : byHand(result));
        return said.isBlank() ? row : row + ": " + said.strip();
    }

    /** What the user does for a failure no fix repairs. */
    private static String byHand(CheckResult result) {
        return result.remedy() + (result.commands().isEmpty() ? "" : " The user runs: "
                + String.join("; ", result.commands())) + (result.link() == null ? "" : " See " + result.link());
    }

    /** Picks the fix {@code check} names in {@code results} and starts it. */
    static AgentReply fix(String check, boolean undo, List<CheckResult> results,
                          Function<CheckResult, Optional<Fix>> fixOf, Supplier<Optional<Fix>> undoFix,
                          FixRunner runner) {
        EmulatorCheck named = EmulatorCheck.fromId(check);
        CheckResult result = results.stream().filter(r -> r.check() == named).findFirst().orElse(null);
        if (result == null) {
            return AgentReply.refused("\"" + check + "\" is no check on this emulator; they are "
                    + String.join(", ", results.stream().map(r -> r.check().id()).toList()) + ".");
        }
        if (undo) {
            if (!result.undoable()) return AgentReply.refused(named.displayName() + " has no fix to undo.");
            return undoFix.get().map(fix -> started(fix, runner))
                    .orElseGet(() -> AgentReply.refused("No backup is left to restore."));
        }
        if (!result.failed()) return AgentReply.refused(named.displayName() + " does not fail; nothing to fix.");
        return fixOf.apply(result).map(fix -> started(fix, runner))
                .orElseGet(() -> AgentReply.refused(named.displayName() + " has no fix: " + result.reason() + " "
                        + byHand(result)));
    }

    /** Started and answered at once: a fix takes minutes and waits on the user's password. */
    private static AgentReply started(Fix fix, FixRunner runner) {
        String name = fix.check().displayName();
        boolean started = runner.start(fix, progress -> FIX_LINE.put(runner, progress.done() ? ended(fix, progress)
                : name + " is running, step " + progress.step() + "/" + progress.of() + ": " + progress.words()));
        if (!started) {
            return AgentReply.text("A fix is already running on this emulator; emulator_checks says how it went.");
        }
        return AgentReply.text(name + "'s fix is running" + (fix.needsRoot()
                ? ": the user's desktop asks for their password" : "") + "; emulator_checks says how it went.");
    }

    private static String ended(Fix fix, FixRunner.Progress end) {
        String line = ChecksRow.ended(fix, end);
        return end.output().isEmpty() ? line : line + " It printed:\n" + String.join("\n", end.output());
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
