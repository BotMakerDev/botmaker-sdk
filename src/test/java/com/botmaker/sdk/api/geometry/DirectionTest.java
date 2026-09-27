package com.botmaker.sdk.api.geometry;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectionTest {

    /**
     * NORTH stays first: it is the fallback the scaffolding parses back to. The diagonals are appended, and
     * the centre after them (feedback 3): a constant is only ever added at the end.
     */
    @Test
    void the_compass_keeps_its_order_and_gains_the_diagonals_and_the_centre() {
        assertEquals(List.of("NORTH", "SOUTH", "EAST", "WEST",
                        "NORTH_EAST", "NORTH_WEST", "SOUTH_EAST", "SOUTH_WEST", "CENTER"),
                Arrays.stream(Direction.values()).map(Enum::name).toList());
    }
}
