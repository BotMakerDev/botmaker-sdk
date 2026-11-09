package com.botmaker.sdk.plugin.types;

import com.botmaker.java.grammar.ValueGrammar;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Custom;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Verdict;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each of an input's four verdicts is written into the bot's Java and read back the same. */
class InputVerdictTest {

    private static final ImageTemplate GEMS = new ImageTemplate("images/gems.png");

    /** The four verdicts, as a run's author writes them after the input's factory. */
    private static final List<UnaryOperator<Input>> VERDICTS = List.of(
            input -> input,
            Input::free,
            input -> input.spends(ScreenTypes.fact("Progress::gems")),
            input -> input.never("spends gems"));

    private static <T> T roundTrip(ComponentType<T> shape, T value) {
        return shape.build(shape.components(value));
    }

    @Test
    void anInputHasNoVerdictUntilOneIsWritten() {
        assertInstanceOf(Verdict.None.class, Button.of(GEMS).verdict());
        assertNull(Button.of(GEMS).never());
        assertInstanceOf(Verdict.None.class, Button.of(GEMS).never("  ").verdict(), "a blank reason is no verdict");
        assertTrue(Button.of(GEMS).free().isFree());
        assertEquals("Progress::gems", ScreenTypes.spentOf(Button.of(GEMS).spends(ScreenTypes.fact("Progress::gems"))));
        assertEquals("spends gems", Button.of(GEMS).free().never("spends gems").never(), "the last verdict wins");
    }

    @Test
    void eachVerdictComesApartAndGoesBackTogetherOnEveryKindOfInput() {
        for (UnaryOperator<Input> verdict : VERDICTS) {
            Button button = (Button) verdict.apply(Button.of(GEMS));
            Hotkey key = (Hotkey) verdict.apply(Hotkey.of(Key.ESCAPE));
            Strip strip = (Strip) verdict.apply(Strip.of(Scroll.wheel(4), List.of(GEMS)));
            Custom custom = (Custom) verdict.apply(Custom.of(ScreenTypes.method("Puzzles::solve")));
            assertEquals(button, roundTrip(ScreenTypes.BUTTON_SHAPE, button));
            assertEquals(key, roundTrip(ScreenTypes.HOTKEY_KEY_SHAPE, key));
            assertEquals(strip, roundTrip(ScreenTypes.STRIP_SHAPE, strip));
            assertEquals(custom, roundTrip(ScreenTypes.CUSTOM_SHAPE, custom));
        }
    }

    @Test
    void eachVerdictIsWrittenAsItsLinkAndReadBackEqual() {
        ValueGrammar grammar = ValueGrammar.of(List.of(), List.of(ScreenTypes.CUSTOM_SHAPE));
        List<String> written = List.of("Custom.of(Puzzles::solve)", "Custom.of(Puzzles::solve).free()",
                "Custom.of(Puzzles::solve).spends(Progress::gems)",
                "Custom.of(Puzzles::solve).never(\"spends gems\")");
        for (int i = 0; i < VERDICTS.size(); i++) {
            Custom custom = (Custom) VERDICTS.get(i).apply(Custom.of(ScreenTypes.method("Puzzles::solve")));
            String java = grammar.write(Custom.class, custom).orElseThrow().source();
            assertTrue(java.endsWith(written.get(i)), java);
            String full = java.startsWith("Custom.") ? Custom.class.getPackageName() + "." + java : java;
            assertEquals(custom, grammar.read(Custom.class, full).orElseThrow(), java);
        }
    }
}
