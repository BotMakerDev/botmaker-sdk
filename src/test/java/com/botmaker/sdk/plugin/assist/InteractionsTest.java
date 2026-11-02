package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Shade;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.internal.screens.ShadeFit.Fit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How buttons answer the bot's own input: a change two buttons show alike is a shade of that cause, meaning Normal. */
class InteractionsTest {

    private static final Rect AT = new Rect(0, 0, 10, 10);
    private static final Fit SAME = fit(1, 0);
    private static final Fit BRIGHTER = fit(1.2, 0.05);

    @TempDir
    Path folder;

    @AfterEach
    void forget() {
        Interactions.forget();
    }

    private static Fit fit(double gain, double offset) {
        return new Fit(false, Shade.Channels.all(gain), Shade.Channels.all(offset), 0.01, null);
    }

    private static Versions.Seen seen(String picture, Fit fit) {
        return new Versions.Seen(picture, AT, fit);
    }

    @Test
    void twoButtonsChangedAlikeProposeAShadeOfThatCauseMeaningNormal() {
        Interactions.Answer answer = Interactions.answer(Shade.Cause.HOVER,
                List.of(seen("buy", BRIGHTER), seen("sell", fit(1.21, 0.05))));
        assertTrue(answer.keeps());
        assertEquals(Shade.Cause.HOVER, answer.shade().cause());
        assertEquals(State.NORMAL, answer.shade().state());
        assertTrue(answer.id().startsWith("hover-"), answer.id());
        assertEquals(List.of("buy", "sell"), answer.pictures());
    }

    @Test
    void oneButtonChangedTwoUnlikeOrNoneProposeNothing() {
        assertFalse(Interactions.answer(Shade.Cause.PRESS, List.of(seen("buy", BRIGHTER), seen("sell", SAME))).keeps());
        Interactions.Answer unlike = Interactions.answer(Shade.Cause.PRESS,
                List.of(seen("buy", BRIGHTER), seen("sell", fit(0.5, 0))));
        assertFalse(unlike.keeps());
        assertTrue(unlike.said().contains("unlike"), unlike.said());
        assertEquals("no button changed", Interactions.answer(Shade.Cause.FOCUS,
                List.of(seen("buy", SAME), seen("sell", SAME))).said());
        // The same button twice (Tab twice landing on it) is one button.
        assertFalse(Interactions.answer(Shade.Cause.FOCUS, List.of(seen("buy", BRIGHTER), seen("buy", BRIGHTER)))
                .keeps());
    }

    @Test
    void theLastTryIsWhatLearnShadeKeepsAndItIsSaidWithItsCall() {
        Interactions.Answer hover = Interactions.answer(Shade.Cause.HOVER,
                List.of(seen("buy", BRIGHTER), seen("sell", BRIGHTER)));
        Interactions.Answer skipped = new Interactions.Answer(Shade.Cause.FOCUS, null, null, List.of(),
                "skipped: an emulator has no keys to Tab");
        Interactions.Tried tried = new Interactions.Tried("SHOP", List.of(hover, skipped), null);
        Interactions.remember(folder, tried);
        assertEquals(hover, Interactions.proposed(folder, " " + hover.id() + " ").orElseThrow());
        assertTrue(Interactions.proposed(folder, "focus-0000").isEmpty());
        assertTrue(Interactions.proposed(folder.resolve("other"), hover.id()).isEmpty());
        String said = Interactions.said(tried);
        assertTrue(said.contains("learn_shade fit " + hover.id()), said);
        assertTrue(said.contains("Focus: skipped"), said);
        assertTrue(said.contains("Nothing was written"), said);
    }

    @Test
    void aTryThatCouldNotStartSaysWhyOnly() {
        assertEquals("No screen X in the bot's Screens.", Interactions.said(
                new Interactions.Tried("X", List.of(), "No screen X in the bot's Screens.")));
    }
}
