package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.bot.Activities;
import com.botmaker.sdk.api.bot.ActivityContext;
import com.botmaker.sdk.api.bot.ActivityName;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.OutcomeName;
import com.botmaker.sdk.api.bot.Setting;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.emulator.EmulatorName;
import com.botmaker.sdk.api.emulator.Emulators;
import com.botmaker.sdk.api.launch.Game;
import com.botmaker.sdk.api.launch.LaunchOption;
import com.botmaker.sdk.api.launch.ProgramPath;
import com.botmaker.sdk.api.launch.SteamAppId;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which arguments this plugin's call-site editors claim: the annotations sit on the right api parameters, and
 * {@link SdkEditors#ALL} claims exactly the arguments passed to them.
 *
 * <p>Loading {@link SdkEditors} at all is the first assertion: an annotation that reflection could not see
 * fails the list when it is built.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SdkEditorsTest {

    private static boolean claimed(ValueContext ctx) {
        return SdkEditors.ALL.stream().anyMatch(editor -> editor.matches(ctx));
    }

    private static boolean carries(Method call, int argIndex, Class<? extends Annotation> annotation) {
        return TestContexts.slot(call, argIndex, "x").slot().flatMap(SlotContext::parameter)
                .map(parameter -> parameter.isAnnotationPresent(annotation)).orElse(false);
    }

    @Test
    void the_steam_id_is_claimed_on_the_string_launches_only() {
        Method launchSteam = TestContexts.method(Game.class, "launchSteam", String.class);
        Method ifNotRunning = TestContexts.method(Game.class, "launchSteamIfNotRunning");

        assertTrue(carries(launchSteam, 0, SteamAppId.class));
        assertTrue(claimed(TestContexts.slot(launchSteam, 0, "\"440\"")));
        assertTrue(claimed(TestContexts.slot(ifNotRunning, 0, "\"440\"")));
        // The int overload: the game grid writes a String, which would not compile there.
        assertFalse(claimed(TestContexts.slot(TestContexts.method(Game.class, "launchSteam", int.class), 0, "440")));
    }

    /** The flags are the varargs tail, wherever the overload's fixed parameters end. */
    @Test
    void a_launch_flag_is_every_argument_of_the_varargs_tail() {
        Method launch = TestContexts.method(Game.class, "launch", String.class, String[].class);
        Method ifNotRunning = TestContexts.method(Game.class, "launchIfNotRunning",
                String.class, CaptureSource.class, String[].class);

        assertTrue(carries(launch, 0, ProgramPath.class));
        assertTrue(carries(launch, 1, LaunchOption.class));
        assertTrue(carries(launch, 3, LaunchOption.class));
        assertFalse(carries(ifNotRunning, 1, LaunchOption.class));
        assertFalse(claimed(TestContexts.slot(ifNotRunning, 1, "source")));
        assertTrue(carries(ifNotRunning, 2, LaunchOption.class));
    }

    @Test
    void every_bounded_setting_says_its_label_and_range_on_the_parameter() {
        Method confidence = TestContexts.method(BotSettings.class, "confidence", double.class);
        Setting setting = confidence.getParameters()[0].getAnnotation(Setting.class);

        assertEquals("Match confidence", setting.label());
        assertEquals(1, setting.max());
        assertTrue(claimed(TestContexts.slot(confidence, 0, "0.8")));
        assertTrue(claimed(TestContexts.slot(TestContexts.method(BotSettings.class, "debug", boolean.class), 0,
                "true")));
    }

    @Test
    void names_of_activities_outcomes_and_emulators_are_claimed() {
        assertTrue(carries(TestContexts.method(Activities.class, "disable"), 0, ActivityName.class));
        assertTrue(carries(TestContexts.method(ActivityContext.class, "outcome"), 0, OutcomeName.class));
        assertTrue(carries(TestContexts.method(Emulators.class, "use", String.class), 0, EmulatorName.class));
        assertTrue(claimed(TestContexts.slot(TestContexts.method(ActivityContext.class, "outcome"), 0, "\"done\"")));
    }

    /** No call, or one the host could not resolve: nothing is claimed, rather than guessed at. */
    @Test
    void a_row_and_an_unresolved_call_are_claimed_by_none() {
        assertFalse(claimed(TestContexts.row(String.class, "\"440\"")));
        assertFalse(claimed(TestContexts.slot(null, 0, "\"440\"")));
    }
}
