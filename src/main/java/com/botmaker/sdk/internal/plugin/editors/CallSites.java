package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.ValueContext;
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
 * <p>Five constants and nothing else. The matching itself — declining a Parameters row, comparing an argument
 * index, tolerating a qualified or a simple class name — is
 * {@link com.botmaker.plugin.toolkit.CallSites}'s, and moved there on 2026-08-28: not a line of it named an
 * SDK type, and any plugin whose values are told apart by the call around them needs the same four shapes.
 *
 * <p>What is left is the part that is genuinely this plugin's, and it is the part a reader wants: a Steam app
 * id, an Epic app name, a program path, a launch flag and a bounded setting are all {@code String} or all
 * {@code double}, and only the call says which. Every one of these declines a row of the Parameters window,
 * because a row has no call behind it — see the toolkit class for why that is the honest answer rather than a
 * limitation.
 */
final class CallSites {

    private CallSites() {}

    /** The Steam app id of {@code Game.launchSteam(id)} / {@code launchSteamIfNotRunning(id, source)}. */
    static final Predicate<ValueContext> STEAM_APP_ID = com.botmaker.plugin.toolkit.CallSites
            .firstArgumentOf(Game.class, "launchSteam", "launchSteamIfNotRunning");

    /** The Epic app name of {@code Game.launchEpic(name)} / {@code launchEpicIfNotRunning(name, source)}. */
    static final Predicate<ValueContext> EPIC_APP_NAME = com.botmaker.plugin.toolkit.CallSites
            .firstArgumentOf(Game.class, "launchEpic", "launchEpicIfNotRunning");

    /**
     * The program path of {@code Game.launch(path, …)}, {@code launchIfNotRunning(path, source, …)} or
     * {@code launchAndWait(path, source, timeout, …)} — always argument 0.
     */
    static final Predicate<ValueContext> LAUNCH_PROGRAM = com.botmaker.plugin.toolkit.CallSites
            .firstArgumentOf(Game.class, "launch", "launchIfNotRunning", "launchAndWait");

    /**
     * A trailing command-line argument of the same three methods.
     *
     * <p>Where the varargs start differs per overload, because the fixed parameters do: {@code launch(path,
     * …)} from index 1, {@code launchIfNotRunning(path, source, …)} from 2, and {@code launchAndWait(path,
     * source, timeout, …)} from 3. Below those indices the argument is the path, the capture source or the
     * timeout, and each of those has an editor of its own.
     */
    static final Predicate<ValueContext> LAUNCH_OPTION = com.botmaker.plugin.toolkit.CallSites
            .trailingArgumentOf(Game.class, Map.of(
                    "launch", 1,
                    "launchIfNotRunning", 2,
                    "launchAndWait", 3));

    /**
     * The single argument of a bounded {@code BotSettings} setter.
     *
     * <p>The set of names is {@link SettingsEditors#bounds}'s, asked rather than repeated: a setter this
     * predicate claimed and that table had no entry for would be offered an editor with no idea what range to
     * enforce, which is the free-typed number the editor exists to replace.
     */
    static final Predicate<ValueContext> BOT_SETTING = com.botmaker.plugin.toolkit.CallSites
            .firstArgumentWhere(BotSettings.class, setter -> SettingsEditors.bounds(setter) != null);

    /**
     * The instance name of {@code Emulators.use(name)}, {@code named(name)}, {@code launch(name)} or
     * {@code stop(name)} — always argument 0.
     *
     * <p>{@code use()} with no argument is not matched and cannot be: there is no slot. That overload means
     * <i>the project's default emulator</i>, which is a capture target rather than a name typed into code.
     */
    static final Predicate<ValueContext> EMULATOR_NAME = com.botmaker.plugin.toolkit.CallSites
            .firstArgumentOf(Emulators.class, "use", "named", "launch", "stop");

    /** The activity named by {@code Activities.define(name, body)}. */
    static final Predicate<ValueContext> ACTIVITY_NAME = com.botmaker.plugin.toolkit.CallSites
            .firstArgumentOf(Activities.class, "define");

    /**
     * The outcome named by {@code ctx.outcome(name)}.
     *
     * <p>The receiver is an {@link ActivityContext}, which is a type the user never spells — the parameter of
     * the lambda they were handed. That is the reason the method takes a context at all rather than the body
     * returning a bare {@code String}: a call on a typed receiver is one this predicate can recognise, and a
     * returned string is indistinguishable from every other string in the bot.
     */
    static final Predicate<ValueContext> OUTCOME_NAME = com.botmaker.plugin.toolkit.CallSites
            .firstArgumentOf(ActivityContext.class, "outcome");
}
