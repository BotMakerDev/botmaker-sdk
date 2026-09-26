package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.geometry.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every compass direction, diagonals included, has a square of its own on the pad. */
class DirectionPadTest {

    @Test
    void every_direction_has_its_own_square() {
        var cells = InputEditors.padCells();
        assertEquals(Direction.values().length, cells.size(), "a direction fell to the spare row");
        assertArrayEquals(new int[]{2, 0}, cells.get(Direction.NORTH_EAST));
        assertArrayEquals(new int[]{0, 0}, cells.get(Direction.NORTH_WEST));
        assertArrayEquals(new int[]{2, 2}, cells.get(Direction.SOUTH_EAST));
        assertArrayEquals(new int[]{0, 2}, cells.get(Direction.SOUTH_WEST));
        assertEquals(cells.size(), cells.values().stream().map(java.util.Arrays::toString).distinct().count());
    }
}
