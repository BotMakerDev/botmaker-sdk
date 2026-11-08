package com.botmaker.sdk.plugin.assist;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The names {@link ScreenEdits} gives what it adds to the bot. */
class ScreenEditsTest {

    /** A field's text kept in {@code Texts} is named after it, as a constant, never a name taken already. */
    @Test
    void aTextsConstantIsNamedAfterItsText() {
        assertEquals("DRAGONS_GUILD", ScreenEdits.textName("  Dragons guild! ", Set.of()));
        assertEquals("DRAGONS_2", ScreenEdits.textName("dragons", Set.of("DRAGONS")));
        assertEquals("DRAGONS_3", ScreenEdits.textName("dragons", Set.of("DRAGONS", "DRAGONS_2")));
        assertEquals("TEXT_42", ScreenEdits.textName("42", Set.of()));
        assertEquals("TEXT", ScreenEdits.textName("…", Set.of()));
        assertEquals("A_VERY_LONG_SEARCH_THAT_GOES_ON", ScreenEdits.textName(
                "a very long search that goes on and on", Set.of()), "cut at a word's end");
    }
}
