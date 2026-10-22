package com.botmaker.sdk.internal.ocr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A line read is the words when it is all of them, case and edge marks aside, a misread forgiven in longer words. */
class LineMatchTest {

    @Test
    void aLineIsTheWordsWhateverItsCaseAndEdges() {
        assertTrue(LineMatch.same("  CLAIM! ", "Claim"));
        assertTrue(LineMatch.same("Game   over", "game over"));
        assertTrue(LineMatch.same("Setttings", "Settings"), "one misread in eight letters is forgiven");
        assertTrue(LineMatch.same("Leaderb0ard5", "Leaderboards"), "two in twelve");
    }

    @Test
    void anotherWordIsNoMatch() {
        assertFalse(LineMatch.same("Claimed", "Claim"), "a word one state on is another word");
        assertFalse(LineMatch.same("O", "OK"), "short words are read exactly");
        assertFalse(LineMatch.same("Claim 50 gold", "Claim"), "the whole line, never a part of it");
        assertFalse(LineMatch.same("", "Claim"));
        assertFalse(LineMatch.same("Claim", " "), "no words match nothing");
    }

    @Test
    void marksAloneAreReadExactly() {
        assertTrue(LineMatch.same(" >> ", ">>"));
        assertFalse(LineMatch.same(">", ">>"));
        assertFalse(LineMatch.same("Next", "+"));
    }

    @Test
    void longerWordsAreForgivenMore() {
        assertEquals(0, LineMatch.edits(4));
        assertEquals(1, LineMatch.edits(5));
        assertEquals(1, LineMatch.edits(8));
        assertEquals(2, LineMatch.edits(9));
    }
}
