package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.internal.emulator.EmulatorName;
import com.botmaker.sdk.internal.emulator.Emulators;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void every_bounded_setting_has_its_label_and_range_in_the_hints() {
        Method confidence = TestContexts.method(BotSettings.class, "confidence", double.class);
        SettingHints.Hint setting = SettingHints.of(confidence.getParameters()[0]);

        assertEquals("Match confidence", setting.label());
        assertEquals(1, setting.max());
        assertTrue(claimed(TestContexts.slot(confidence, 0, "0.8")));
        assertTrue(claimed(TestContexts.slot(TestContexts.method(BotSettings.class, "debug", boolean.class), 0,
                "true")));
        // Every wither that takes one number or flag is in the table: a new one without a hint is a free-typed
        // number. One taking an enum needs none, its constants say what it can be.
        for (Method wither : BotSettings.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(wither.getModifiers()) || wither.getParameterCount() != 1
                    || wither.getReturnType() != BotSettings.class || !java.lang.reflect.Modifier.isPublic(
                    wither.getModifiers()) || wither.getParameterTypes()[0].isEnum()) continue;
            assertNotNull(SettingHints.of(wither.getParameters()[0]), wither.getName() + " has no hint");
        }
    }

    @Test
    void emulator_names_are_claimed() {
        assertTrue(carries(TestContexts.method(Emulators.class, "use", String.class), 0, EmulatorName.class));
    }

    /** No call, or one the host could not resolve: nothing is claimed, rather than guessed at. */
    @Test
    void a_row_and_an_unresolved_call_are_claimed_by_none() {
        assertFalse(claimed(TestContexts.row(String.class, "\"440\"")));
        assertFalse(claimed(TestContexts.slot(null, 0, "\"440\"")));
    }
}
