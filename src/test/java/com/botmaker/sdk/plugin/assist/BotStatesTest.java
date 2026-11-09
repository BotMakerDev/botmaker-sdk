package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.screens.ButtonState;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.internal.bot.BotConstants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotStatesTest {

    @Test
    void theSdksStatesAreReadByIdOrNameAndTheBotsByGroupAndConstant() {
        assertEquals(Optional.of(State.DISABLED), BotStates.parse("disabled"));
        assertEquals(Optional.of(State.SELECTED), BotStates.parse(" Selected "));
        ButtonState maxed = BotConstants.state(ButtonState.BLOCKS, "MAXED");
        assertEquals(Optional.of(maxed), BotStates.parse("Blocks.MAXED"));
        assertEquals(Optional.of(BotConstants.state(ButtonState.BLOCKS, "TWO_STARS")), BotStates.parse("blocks.two stars"));
        assertEquals(Optional.of(BotConstants.state(ButtonState.PRESSES, "BADGE")), BotStates.parse("presses.badge"));
        assertTrue(maxed.blocks());
        assertFalse(BotStates.parse("Presses.BADGE").orElseThrow().blocks());
        assertEquals(Optional.empty(), BotStates.parse("Other.MAXED"), "only Blocks and Presses hold a bot's states");
        assertEquals(Optional.empty(), BotStates.parse("badge"), "a bot's state is named with its group");
        assertEquals(Optional.empty(), BotStates.parse("Blocks.9LIVES"));
        assertEquals(Optional.empty(), BotStates.parse(" "));
    }

    @Test
    void withNoProjectTheSdksStatesAreOfferedAndTheBotsLabelledByGroup() {
        assertEquals(List.of(State.NORMAL, State.SELECTED, State.DISABLED), BotStates.offered(null));
        assertEquals("Disabled", BotStates.label(State.DISABLED));
        assertEquals("Blocks.MAXED", BotStates.label(BotConstants.state(ButtonState.BLOCKS, "MAXED")));
        assertEquals(Optional.empty(), BotStates.declare(null, State.DISABLED), "the SDK's own need no constant");
        assertTrue(BotStates.declare(null, BotConstants.state(ButtonState.BLOCKS, "MAXED")).isPresent());
    }
}
