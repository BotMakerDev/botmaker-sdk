package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.Pressed;
import com.botmaker.sdk.internal.screens.MapWalk.Arrival;
import com.botmaker.sdk.internal.screens.MapWalk.Took;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How a press's or a walk's end reads as an arrival; the moves themselves need a game. */
class LiveMovesTest {

    private static final ScreenGraph.Edge EDGE = new ScreenGraph.Edge("A", "B", "b", null, false);

    @Test
    void whereTheGameIsSaysTheArrival() {
        assertEquals(new Took(Arrival.AT_TARGET, "B"), LiveMoves.arrived(EDGE, "B", null));
        assertEquals(new Took(Arrival.UNCHANGED, "A", null), LiveMoves.arrived(EDGE, "A", null));
        assertEquals(new Took(Arrival.ELSEWHERE, "C", null), LiveMoves.arrived(EDGE, "C", null));
        assertEquals(new Took(Arrival.UNKNOWN, null, "lost"), LiveMoves.arrived(EDGE, null, "lost"));
    }

    @Test
    void aPressThatPressedNothingIsNothingHappening() {
        ScreenWalk.Press absent = new ScreenWalk.Press(Pressed.Result.ABSENT, "A", 0, "A's b is not on the screen.");
        ScreenWalk.Press done = new ScreenWalk.Press(Pressed.Result.DONE, "B", 1, "");

        assertEquals(new Took(Arrival.UNCHANGED, "A", "A's b is not on the screen."), LiveMoves.pressed(EDGE, absent));
        assertEquals(new Took(Arrival.AT_TARGET, "B"), LiveMoves.pressed(EDGE, done));
        assertEquals(new Took(Arrival.ELSEWHERE, "C", null),
                LiveMoves.pressed(EDGE, new ScreenWalk.Press(Pressed.Result.LEFT, "C", 1, "")));
    }

    @Test
    void aPressWhoseWayIsClosedNowIsNoFinding() {
        ScreenWalk.Press locked = new ScreenWalk.Press(Pressed.Result.NOT_NOW, "A", 0, "locked");
        ScreenWalk.Press greyed = new ScreenWalk.Press(Pressed.Result.DISABLED, "A", 0, "greyed");

        assertEquals(Arrival.NOT_NOW, LiveMoves.pressed(EDGE, locked).arrival());
        assertEquals(Arrival.NOT_NOW, LiveMoves.pressed(EDGE, greyed).arrival());
    }
}
