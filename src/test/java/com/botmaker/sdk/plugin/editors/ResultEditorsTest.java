package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.vision.ColorMatch;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Matches;
import com.botmaker.sdk.api.vision.TextMatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The words a result's pill says: what the bot fills in, never an empty pill or raw Java. */
class ResultEditorsTest {

    @Test
    void eachResultIsNamedInPlainWords() {
        assertEquals("Last picture match", ResultEditors.words(MatchResult.class));
        assertEquals("Pictures found", ResultEditors.words(Matches.class));
        assertEquals("Last colour match", ResultEditors.words(ColorMatch.class));
        assertEquals("Last text read", ResultEditors.words(TextMatch.class));
    }

    @Test
    void theTooltipShowsTheJavaAndSaysTheBotFillsItIn() {
        String help = ResultEditors.help(MatchResult.class, "Vision.lastMatch()");
        assertTrue(help.startsWith("Vision.lastMatch()"), help);
        assertTrue(help.contains("the bot fills this in"), help);
    }

    @Test
    void aSourceTheHostCouldNotShowStillGetsTheExplanation() {
        String help = ResultEditors.help(TextMatch.class, "");
        assertTrue(help.startsWith("The bot fills this in"), help);
    }
}
