package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.bot.ActivityContext;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.launch.Game;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which call each of this plugin's call-site editors claims, now that the host hands over the resolved
 * method rather than the names written in the source.
 *
 * <p>Loading {@link CallSites} at all is the first assertion: every method name in it is checked against the
 * class when it loads, so a rename of {@code Game.launchSteam} fails here.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CallSitesTest {

    @Test
    void the_steam_id_is_argument_zero_of_either_steam_launch() {
        assertTrue(CallSites.STEAM_APP_ID.test(
                TestContexts.slot(TestContexts.method(Game.class, "launchSteam", String.class), 0, "\"440\"")));
        assertTrue(CallSites.STEAM_APP_ID.test(TestContexts.slot(
                TestContexts.method(Game.class, "launchSteamIfNotRunning"), 0, "\"440\"")));
        assertFalse(CallSites.STEAM_APP_ID.test(TestContexts.slot(
                TestContexts.method(Game.class, "launchSteamIfNotRunning"), 1, "source")));
    }

    /** The flags start where the overload's varargs start — read off the resolved overload, not a table. */
    @Test
    void a_launch_flag_starts_at_the_overloads_varargs() {
        var launch = TestContexts.method(Game.class, "launch", String.class, String[].class);
        var ifNotRunning = TestContexts.method(Game.class, "launchIfNotRunning",
                String.class, CaptureSource.class, String[].class);

        assertTrue(CallSites.LAUNCH_PROGRAM.test(TestContexts.slot(launch, 0, "\"game.exe\"")));
        assertFalse(CallSites.LAUNCH_OPTION.test(TestContexts.slot(launch, 0, "\"game.exe\"")));
        assertTrue(CallSites.LAUNCH_OPTION.test(TestContexts.slot(launch, 1, "\"-windowed\"")));
        assertFalse(CallSites.LAUNCH_OPTION.test(TestContexts.slot(ifNotRunning, 1, "source")));
        assertTrue(CallSites.LAUNCH_OPTION.test(TestContexts.slot(ifNotRunning, 2, "\"-windowed\"")));
    }

    @Test
    void a_setter_is_claimed_only_when_the_table_bounds_it() {
        assertTrue(CallSites.BOT_SETTING.test(TestContexts.slot(
                TestContexts.method(BotSettings.class, "setDefaultConfidence"), 0, "0.8")));
    }

    @Test
    void an_outcome_is_the_argument_of_the_contexts_own_method() {
        assertTrue(CallSites.OUTCOME_NAME.test(
                TestContexts.slot(TestContexts.method(ActivityContext.class, "outcome"), 0, "\"done\"")));
    }

    /** No call, or one the host could not resolve: nothing is claimed, rather than guessed at. */
    @Test
    void a_row_and_an_unresolved_call_are_claimed_by_none() {
        var row = TestContexts.row(String.class, "\"440\"");
        var unresolved = TestContexts.slot(null, 0, "\"440\"");

        assertFalse(CallSites.STEAM_APP_ID.test(row));
        assertFalse(CallSites.STEAM_APP_ID.test(unresolved));
        assertFalse(CallSites.LAUNCH_OPTION.test(unresolved));
    }
}
