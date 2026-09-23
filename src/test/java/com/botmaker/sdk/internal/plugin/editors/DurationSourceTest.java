package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.toolkit.testing.TestContexts;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the duration editor shows and writes, over the value the host hands it.
 *
 * <p>The host reads and writes the Java; this editor sees a {@link Duration} or nothing. None of these cases
 * needs a JavaFX toolkit.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DurationSourceTest {

    private static TestContexts.Recording slot(String source, Duration value) {
        return TestContexts.slot("Wait", "time", 0, source).withType("java.time.Duration").withValue(value);
    }

    @Test
    void the_label_spells_the_whole_length_not_the_unit_it_is_stored_in() {
        assertEquals("1s500ms", DurationEditor.slotLabel(slot("Duration.ofMillis(1500)", Duration.ofMillis(1500))));
        assertEquals("4h30m", DurationEditor.slotLabel(slot("Duration.ofMillis(16200000)", Duration.ofMinutes(270))));
    }

    @Test
    void something_the_host_cannot_read_keeps_its_own_source_as_the_label() {
        assertEquals("timeout", DurationEditor.slotLabel(slot("timeout", null)));
        assertEquals("Duration.ZERO", DurationEditor.slotLabel(slot("Duration.ZERO", null)));
        assertEquals("Choose duration…", DurationEditor.slotLabel(slot("", null)));
    }

    @Test
    void an_unreadable_slot_opens_on_one_second() {
        assertEquals(1000L, DurationEditor.millis(slot("timeout", null)));
    }

    @Test
    void a_new_length_is_handed_back_as_a_value() {
        var slot = slot("Duration.ofMillis(1500)", Duration.ofMillis(1500));
        DurationEditor.write(slot, 1500L, 800L);
        assertEquals(Duration.ofMillis(800), slot.value());
        assertEquals(1, slot.writes());
    }

    @Test
    void an_untouched_length_writes_nothing() {
        var slot = slot("Duration.ofMillis(1500)", Duration.ofMillis(1500));
        DurationEditor.write(slot, 1500L, 1500L);
        assertEquals(0, slot.writes(), "opening the editor and pressing OK must leave the file alone");
    }

    @Test
    void confirming_the_default_over_an_unreadable_slot_writes_it() {
        var slot = slot("timeout", null);
        DurationEditor.write(slot, 1000L, 1000L);
        assertEquals(Duration.ofSeconds(1), slot.value());
    }

    @Test
    void zero_and_below_are_zero() {
        assertEquals("0s", DurationEditor.spell(0));
        var slot = slot("", null);
        DurationEditor.write(slot, 1000L, -5L);
        assertEquals(Duration.ZERO, slot.value());
    }
}
