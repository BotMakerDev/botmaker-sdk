package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.SlotContext;
import com.botmaker.plugin.api.ValueContext;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.launch.Game;

import java.util.function.Predicate;

/**
 * The editors that are chosen by <em>where</em> a value is used rather than by what type it is.
 *
 * <p>Most of this plugin's editors match on the type, which is what makes them work in the Parameters window
 * as well as in a bot's source (see {@link SdkEditors}). These cannot: a Steam app id, an Epic app name, a
 * program path, a command-line flag and an emulator instance name are all {@code String}, and the type says
 * nothing about which of the five a given slot holds. Only the call around it does.
 *
 * <p><b>So every predicate here declines when there is no call.</b> {@link ValueContext#asSlot()} answers
 * {@code null} for a Parameters row, and a row has no call site by construction — there is no
 * {@code Game.launchSteam(…)} behind a variable named {@code appId}. Declining is the honest answer, and it
 * is why these editors are absent from that window rather than misfiring in it.
 *
 * <p>The class name is matched by <em>simple</em> name, or by a qualified name ending in it. The host resolves
 * the call out of the bot's own classpath and may hand back either, and an SDK version older than this one may
 * have had the facade in a different package — {@code api.Game} before 1.1.0 sub-packaged everything. Matching
 * the tail is what keeps an old project's blocks drawing their real editors.
 */
final class CallSites {

    private CallSites() {}

    /** The Steam app id of {@code Game.launchSteam(id)} / {@code launchSteamIfNotRunning(id, source)}. */
    static final Predicate<ValueContext> STEAM_APP_ID =
            firstArgumentOf(Game.class, "launchSteam", "launchSteamIfNotRunning");

    /** The Epic app name of {@code Game.launchEpic(name)} / {@code launchEpicIfNotRunning(name, source)}. */
    static final Predicate<ValueContext> EPIC_APP_NAME =
            firstArgumentOf(Game.class, "launchEpic", "launchEpicIfNotRunning");

    /**
     * The program path of {@code Game.launch(path, …)}, {@code launchIfNotRunning(path, source, …)} or
     * {@code launchAndWait(path, source, timeout, …)} — always argument 0.
     */
    static final Predicate<ValueContext> LAUNCH_PROGRAM =
            firstArgumentOf(Game.class, "launch", "launchIfNotRunning", "launchAndWait");

    /**
     * A trailing command-line argument of the same three methods.
     *
     * <p>Where the varargs start differs per overload, because the fixed parameters do:
     * {@code launch(path, …)} from index 1, {@code launchIfNotRunning(path, source, …)} from 2, and
     * {@code launchAndWait(path, source, timeout, …)} from 3. Below those indices the argument is the path,
     * the capture source or the timeout, and each of those has an editor of its own.
     */
    static final Predicate<ValueContext> LAUNCH_OPTION = ctx -> {
        SlotContext slot = ctx.asSlot();
        if (slot == null || !isOn(slot, Game.class)) return false;
        String method = slot.enclosingMethod();
        int index = slot.argIndex();
        return switch (method == null ? "" : method) {
            case "launch" -> index >= 1;
            case "launchIfNotRunning" -> index >= 2;
            case "launchAndWait" -> index >= 3;
            default -> false;
        };
    };

    /**
     * The single argument of a bounded {@code BotSettings} setter.
     *
     * <p>The set of names is {@link SettingsEditors#bounds}'s, asked rather than repeated: a setter this
     * predicate claimed and that table had no entry for would be offered an editor with no idea what range to
     * enforce, which is the free-typed number the editor exists to replace.
     */
    static final Predicate<ValueContext> BOT_SETTING = ctx -> {
        SlotContext slot = ctx.asSlot();
        return slot != null && slot.argIndex() == 0 && isOn(slot, BotSettings.class)
               && SettingsEditors.bounds(slot.enclosingMethod()) != null;
    };

    /** Argument 0 of any of {@code methods} called on {@code facade}. */
    private static Predicate<ValueContext> firstArgumentOf(Class<?> facade, String... methods) {
        return ctx -> {
            SlotContext slot = ctx.asSlot();
            if (slot == null || slot.argIndex() != 0 || !isOn(slot, facade)) return false;
            for (String method : methods) {
                if (method.equals(slot.enclosingMethod())) return true;
            }
            return false;
        };
    }

    /** Whether the enclosing call is on {@code facade}, by simple name or by a qualified name ending in it. */
    private static boolean isOn(SlotContext slot, Class<?> facade) {
        String name = slot.enclosingClass();
        String simple = facade.getSimpleName();
        return name != null && (name.equals(simple) || name.endsWith("." + simple));
    }
}
