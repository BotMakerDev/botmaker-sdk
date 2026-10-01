package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.text.TextMatch;
import com.botmaker.sdk.api.vision.ColorMatch;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Matches;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The words a result's pill says: what the bot fills in, never an empty pill or raw Java. */
class ResultEditorsTest {

    @Test
    void eachResultIsNamedInPlainWords() {
        assertEquals("Picture match", ResultEditors.words(MatchResult.class));
        assertEquals("Pictures found", ResultEditors.words(Matches.class));
        assertEquals("Colour match", ResultEditors.words(ColorMatch.class));
        assertEquals("Text found", ResultEditors.words(TextMatch.class));
    }

    /** The pill draws every unreadable value of the type, so its words may not claim the fresh call's meaning. */
    @Test
    void theWordsNeverClaimTheLastMatchForSomeOtherCall() {
        String help = ResultEditors.help(MatchResult.class, "ImageFinder.find(Pictures.ORE)");
        assertTrue(help.startsWith("ImageFinder.find(Pictures.ORE)"), help);
        assertFalse(help.toLowerCase().contains("last"), help);
        assertFalse(ResultEditors.words(MatchResult.class).toLowerCase().contains("last"));
    }

    @Test
    void theTooltipShowsTheJavaAndSaysTheBotFillsItIn() {
        String help = ResultEditors.help(MatchResult.class, "Vision.lastMatch()");
        assertTrue(help.startsWith("Vision.lastMatch()"), help);
        assertTrue(help.contains("the bot works this out while it runs"), help);
    }

    @Test
    void aSourceTheHostCouldNotShowStillGetsTheExplanation() {
        String help = ResultEditors.help(TextMatch.class, "");
        assertTrue(help.startsWith("Text found: the bot works this out"), help);
    }
}
