package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.SlotEditor;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.sdk.api.bot.Activities;
import com.botmaker.sdk.api.bot.ActivityContext;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.emulator.Emulators;
import com.botmaker.sdk.api.launch.Game;

import java.lang.reflect.Executable;
import java.util.function.Predicate;

/**
 * Which of this plugin's calls each call-site editor belongs to.
 *
 * <p>Eight constants and nothing else. The matching itself — declining a Parameters row, comparing an
 * argument index, comparing the resolved call's declaring class — is {@link SlotEditor#onCall}'s, in the
 * contract: <em>which slot an editor claims</em> is contract vocabulary, the same argument that put
 * {@code SlotEditor.of} there.
 *
 * <p>What is left is the part that is genuinely this plugin's, and it is the part a reader wants: a Steam app
 * id, an Epic app name, a program path, a launch flag and a bounded setting are all {@code String} or all
 * {@code double}, and only the call says which. Every one of these declines a row of the Parameters window,
 * because a row has no call behind it.
 *
 * <p><b>Each set of methods is checked when this class loads</b> ({@link SlotEditor#calls}): a renamed
 * method fails every test that touches an editor, instead of leaving an editor that never appears.
 */
final class CallSites {

    private CallSites() {}

    /** {@code launch}, {@code launchIfNotRunning} and {@code launchAndWait}: a program path, then flags. */
    private static final Predicate<Executable> LAUNCHES =
            SlotEditor.calls(Game.class, "launch", "launchIfNotRunning", "launchAndWait");

    private static final Predicate<Executable> STEAM_LAUNCHES =
            SlotEditor.calls(Game.class, "launchSteam", "launchSteamIfNotRunning");
    private static final Predicate<Executable> EPIC_LAUNCHES =
            SlotEditor.calls(Game.class, "launchEpic", "launchEpicIfNotRunning");
    private static final Predicate<Executable> BOUNDED_SETTERS = SlotEditor.declaredOn(BotSettings.class)
            .and(setter -> SettingsEditors.bounds(setter.getName()) != null);
    private static final Predicate<Executable> EMULATOR_CALLS =
            SlotEditor.calls(Emulators.class, "use", "named", "launch", "stop");
    private static final Predicate<Executable> ACTIVITY_CALLS =
            SlotEditor.calls(Activities.class, "active", "enable", "disable", "setEnabled");
    private static final Predicate<Executable> OUTCOME = SlotEditor.calls(ActivityContext.class, "outcome");

    /** The Steam app id of {@code Game.launchSteam(id)} / {@code launchSteamIfNotRunning(id, source)}. */
    static final Predicate<ValueContext> STEAM_APP_ID = ctx -> SlotEditor.onCall(ctx, STEAM_LAUNCHES, 0);

    /** The Epic app name of {@code Game.launchEpic(name)} / {@code launchEpicIfNotRunning(name, source)}. */
    static final Predicate<ValueContext> EPIC_APP_NAME = ctx -> SlotEditor.onCall(ctx, EPIC_LAUNCHES, 0);

    /**
     * The program path of {@code Game.launch(path, …)}, {@code launchIfNotRunning(path, source, …)} or
     * {@code launchAndWait(path, …)} — always argument 0.
     */
    static final Predicate<ValueContext> LAUNCH_PROGRAM = ctx -> SlotEditor.onCall(ctx, LAUNCHES, 0);

    /**
     * A trailing command-line argument of the same three methods.
     *
     * <p>Where the flags start differs per overload, because the fixed parameters do — {@code launch(path,
     * …)} from index 1, {@code launchIfNotRunning(path, source, …)} from 2. The resolved overload says so
     * itself: the flags are its varargs tail. Below that index the argument is the path, the capture source or
     * the timeout, and each of those has an editor of its own.
     */
    static final Predicate<ValueContext> LAUNCH_OPTION = ctx -> {
        SlotContext slot = ctx.slot().orElse(null);
        if (slot == null) return false;
        Executable call = slot.enclosingExecutable().filter(LAUNCHES).orElse(null);
        return call != null && call.isVarArgs() && slot.argIndex() >= call.getParameterCount() - 1;
    };

    /**
     * The single argument of a bounded {@code BotSettings} setter.
     *
     * <p>The set of names is {@link SettingsEditors#bounds}'s, asked rather than repeated: a setter this
     * predicate claimed and that table had no entry for would be offered an editor with no idea what range to
     * enforce, which is the free-typed number the editor exists to replace.
     */
    static final Predicate<ValueContext> BOT_SETTING = ctx -> SlotEditor.onCall(ctx, BOUNDED_SETTERS, 0);

    /**
     * The instance name of {@code Emulators.use(name)}, {@code named(name)}, {@code launch(name)} or
     * {@code stop(name)} — always argument 0.
     *
     * <p>{@code use()} with no argument is not matched and cannot be: there is no slot. That overload means
     * <i>the project's default emulator</i>, which is a capture target rather than a name typed into code.
     */
    static final Predicate<ValueContext> EMULATOR_NAME = ctx -> SlotEditor.onCall(ctx, EMULATOR_CALLS, 0);

    /** The activity named by {@code Activities.enable(name)}, {@code disable}, {@code active} or {@code setEnabled}. */
    static final Predicate<ValueContext> ACTIVITY_NAME = ctx -> SlotEditor.onCall(ctx, ACTIVITY_CALLS, 0);

    /**
     * The outcome named by {@code ctx.outcome(name)}.
     *
     * <p>The receiver is an {@link ActivityContext}, which is the parameter of the body. That is the reason the
     * method takes a context at all rather than the body returning a bare {@code String}: a call on a typed
     * receiver is one this predicate can recognise, and a returned string is indistinguishable from every other
     * string in the bot.
     */
    static final Predicate<ValueContext> OUTCOME_NAME = ctx -> SlotEditor.onCall(ctx, OUTCOME, 0);
}
