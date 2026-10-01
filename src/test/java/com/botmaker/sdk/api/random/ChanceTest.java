package com.botmaker.sdk.api.random;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ChanceTest {

    @Test
    void between_includes_both_ends_in_either_order() {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            int n = Chance.between(3, 1);
            assertTrue(n >= 1 && n <= 3, "" + n);
            seen.add(n);
        }
        assertEquals(Set.of(1, 2, 3), seen);
        assertEquals(7, Chance.between(7, 7));
        int extreme = Chance.between(Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
        assertTrue(extreme >= Integer.MAX_VALUE - 1);
    }

    @Test
    void chance_at_zero_is_never_and_at_one_is_always() {
        for (int i = 0; i < 500; i++) {
            assertFalse(Chance.chance(0));
            assertFalse(Chance.chance(-1));
            assertTrue(Chance.chance(1));
            assertTrue(Chance.chance(2));
        }
    }

    @Test
    void pick_returns_an_option_and_refuses_none() {
        List<String> options = List.of("farm", "fight");
        for (int i = 0; i < 100; i++) assertTrue(options.contains(Chance.pick("farm", "fight")));
        assertThrows(IllegalArgumentException.class, Chance::pick);
        assertThrows(IllegalArgumentException.class, () -> Chance.pick((String[]) null));
    }

    @Test
    void duration_stays_in_range_and_falls_back_on_a_missing_bound() {
        Duration lo = Duration.ofSeconds(2);
        Duration hi = Duration.ofMillis(2010);
        for (int i = 0; i < 500; i++) {
            Duration d = Chance.duration(hi, lo);
            assertTrue(d.compareTo(lo) >= 0 && d.compareTo(hi) <= 0, d.toString());
        }
        assertEquals(lo, Chance.duration(lo, lo));
        assertEquals(hi, Chance.duration(null, hi));
        assertEquals(Duration.ZERO, Chance.duration(null, null));
    }
}
