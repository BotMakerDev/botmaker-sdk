package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.SlotEditor;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.sdk.api.bot.Activities;
import com.botmaker.sdk.api.bot.ActivityContext;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.emulator.Emulators;
import com.botmaker.sdk.api.launch.Game;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Which of this plugin's calls each call-site editor belongs to.
 *
 * <p>Eight constants and nothing else. The matching itself — declining a Parameters row, comparing an
 * argument index, tolerating a qualified or a simple class name — is
 * {@link SlotEditor#onCall}'s. It was the toolkit's {@code CallSites} from 2026-08-28 and is the
 * <b>contract's</b> from 2026-09-22: not a line of it drew anything, and <em>which slot an editor claims</em>
 * is contract vocabulary, the same argument that put {@code SlotEditor.of} there.
 *
 * <p>What is left is the part that is genuinely this plugin's, and it is the part a reader wants: a Steam app
 * id, an Epic app name, a program path, a launch flag and a bounded setting are all {@code String} or all
 * {@code double}, and only the call says which. Every one of these declines a row of the Parameters window,
 * because a row has no call behind it — see the toolkit class for why that is the honest answer rather than a
 * limitation.
 */
final class CallSites {

    private CallSites() {}

    /** Where the varargs begin, per overload — the one shape {@link SlotEditor#onCall} cannot state alone. */
    private static final Map<String, Integer> LAUNCH_VARARGS =
            Map.of("launch", 1, "launchIfNotRunning", 2, "launchAndWait", 3);

    private static final java.util.Set<String> EMULATOR_METHODS =
            java.util.Set.of("use", "named", "launch", "stop");

    /** The Steam app id of {@code Game.launchSteam(id)} / {@code launchSteamIfNotRunning(id, source)}. */
    static final Predicate<ValueContext> STEAM_APP_ID = ctx -> SlotEditor.onCall(ctx, Game.class, 0,
            name -> "launchSteam".equals(name) || "launchSteamIfNotRunning".equals(name));

    /** The Epic app name of {@code Game.launchEpic(name)} / {@code launchEpicIfNotRunning(name, source)}. */
    static final Predicate<ValueContext> EPIC_APP_NAME = ctx -> SlotEditor.onCall(ctx, Game.class, 0,
            name -> "launchEpic".equals(name) || "launchEpicIfNotRunning".equals(name));

    /**
     * The program path of {@code Game.launch(path, …)}, {@code launchIfNotRunning(path, source, …)} or
     * {@code launchAndWait(path, source, timeout, …)} — always argument 0.
     */
    static final Predicate<ValueContext> LAUNCH_PROGRAM = ctx -> SlotEditor.onCall(ctx, Game.class, 0, LAUNCH_VARARGS::containsKey);

    /**
     * A trailing command-line argument of the same three methods.
     *
     * <p>Where the varargs start differs per overload, because the fixed parameters do: {@code launch(path,
     * …)} from index 1, {@code launchIfNotRunning(path, source, …)} from 2, and {@code launchAndWait(path,
     * source, timeout, …)} from 3. Below those indices the argument is the path, the capture source or the
     * timeout, and each of those has an editor of its own.
     */
    static final Predicate<ValueContext> LAUNCH_OPTION = ctx -> {
        SlotContext slot = ctx.slot().orElse(null);
        if (slot == null) return false;
        Integer from = LAUNCH_VARARGS.get(slot.enclosingMethodName().orElse(""));
        return from != null && slot.argIndex() >= from
                && SlotEditor.onCall(ctx, Game.class, slot.argIndex(), LAUNCH_VARARGS::containsKey);
    };

    /**
     * The single argument of a bounded {@code BotSettings} setter.
     *
     * <p>The set of names is {@link SettingsEditors#bounds}'s, asked rather than repeated: a setter this
     * predicate claimed and that table had no entry for would be offered an editor with no idea what range to
     * enforce, which is the free-typed number the editor exists to replace.
     */
    static final Predicate<ValueContext> BOT_SETTING = ctx -> SlotEditor.onCall(ctx, BotSettings.class, 0,
            setter -> SettingsEditors.bounds(setter) != null);

    /**
     * The instance name of {@code Emulators.use(name)}, {@code named(name)}, {@code launch(name)} or
     * {@code stop(name)} — always argument 0.
     *
     * <p>{@code use()} with no argument is not matched and cannot be: there is no slot. That overload means
     * <i>the project's default emulator</i>, which is a capture target rather than a name typed into code.
     */
    static final Predicate<ValueContext> EMULATOR_NAME = ctx -> SlotEditor.onCall(ctx, Emulators.class, 0,
            EMULATOR_METHODS::contains);

    private static final java.util.Set<String> ACTIVITY_METHODS =
            java.util.Set.of("active", "enable", "disable", "setEnabled");

    /** The activity named by {@code Activities.enable(name)}, {@code disable}, {@code active} or {@code setEnabled}. */
    static final Predicate<ValueContext> ACTIVITY_NAME = ctx -> SlotEditor.onCall(ctx, Activities.class, 0,
            ACTIVITY_METHODS::contains);

    /**
     * The outcome named by {@code ctx.outcome(name)}.
     *
     * <p>The receiver is an {@link ActivityContext}, which is the parameter of the body. That is the reason the
     * method takes a context at all rather than the body returning a bare {@code String}: a call on a typed
     * receiver is one this predicate can recognise, and a returned string is indistinguishable from every other
     * string in the bot.
     */
    static final Predicate<ValueContext> OUTCOME_NAME = ctx -> SlotEditor.onCall(ctx, ActivityContext.class, 0, "outcome"::equals);
}
