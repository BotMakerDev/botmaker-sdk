package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.SlotContext;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The duration editor's reading and writing of source, inherited from Studio's {@code DurationPickerTest},
 * which guarded the same properties until the editor moved here on 2026-08-28.
 *
 * <p>Every assertion below was one of its, because a move must not change what a user reads or what lands in
 * their file. What changed is <b>how</b> the question is asked: the old picker held a JDT
 * {@code MethodInvocation} and could ask the tree whether a node was a call on {@code Wait}; the contract
 * hands over source text and no syntax tree, so the same question is asked of a string plus the three facts
 * {@link SlotContext} carries about the call. These cases are what pin the two readings to one answer — and
 * none of them needs a JavaFX toolkit, which the old test did.
 *
 * <p>The last group is new, and is the reason phase 12c grew the contract at all:
 * {@link SlotContext#replaceEnclosingCall} is the only way "wait a random amount" can be expressed, because
 * it is a change to the call and not to the value in the slot.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DurationSourceTest {

    /**
     * A slot holding {@code source}, optionally inside a call this editor may recognise.
     *
     * <p>A hand-rolled {@code SlotContext} of its own until 2026-08-28 — the fifty lines that were the
     * argument for lifting {@link TestContexts} into the toolkit, since a plugin author testing their first
     * editor had to write them before they could assert anything.
     */
    private static TestContexts.Recording slot(String source, String owner, String method, String call) {
        return TestContexts.slot(owner, method, 0, source)
                .withType("java.time.Duration")
                .withEnclosingSource(call);
    }

    /** A bare slot: a duration argument of nothing in particular. */
    private static TestContexts.Recording bare(String source) {
        return slot(source, null, null, null);
    }

    // --- what the pill says ---

    @Test
    void a_factory_call_is_read_back_as_the_length_it_means() {
        assertEquals("1s500ms", DurationEditor.slotLabel(bare("Duration.ofMillis(1500)")));
        assertEquals("2s", DurationEditor.slotLabel(bare("Duration.ofSeconds(2)")));
        assertEquals("2m", DurationEditor.slotLabel(bare("Duration.ofMinutes(2)")));
    }

    @Test
    void the_label_spells_the_whole_length_not_the_unit_it_is_stored_in() {
        assertEquals("4h30m", DurationEditor.slotLabel(bare("Duration.ofMinutes(270)")));
    }

    @Test
    void something_this_editor_cannot_read_keeps_its_own_source_as_the_label() {
        assertEquals("timeout", DurationEditor.slotLabel(bare("timeout")));
        assertEquals("Duration.ZERO", DurationEditor.slotLabel(bare("Duration.ZERO")));
    }

    // --- what it writes ---

    @Test
    void a_length_is_written_in_the_coarsest_unit_that_still_says_it_exactly() {
        assertEquals("Duration.ofMinutes(270)", DurationEditor.code(4 * 3_600_000L + 30 * 60_000L));
        assertEquals("Duration.ofHours(2)", DurationEditor.code(2 * 3_600_000L));
        assertEquals("Duration.ofSeconds(90)", DurationEditor.code(90_000L));
        assertEquals("Duration.ofMillis(1500)", DurationEditor.code(1_500L));
    }

    @Test
    void zero_is_milliseconds_rather_than_every_unit_at_once() {
        assertEquals("Duration.ofMillis(0)", DurationEditor.code(0L));
    }

    @Test
    void an_untouched_end_is_written_back_exactly_as_it_was_read() {
        var slot = bare("Duration.ofSeconds(120)");
        DurationEditor.Span span = DurationEditor.span(slot);
        DurationEditor.write(slot, span, span.from(), span.to(), false);
        assertEquals("Duration.ofSeconds(120)", slot.replacement(),
                "opening the editor and pressing OK must not rewrite seconds into minutes");
    }

    // --- reading a value out of source ---

    @Test
    void the_four_factories_are_read_and_nothing_else_is() {
        assertNotNull(DurationEditor.millis("Duration.ofMillis(1500)"));
        assertNotNull(DurationEditor.millis("Duration.ofHours(1)"));
        assertNull(DurationEditor.millis("timeout"), "a variable is not ours to rewrite");
        assertNull(DurationEditor.millis("Duration.ZERO"));
        assertNull(DurationEditor.millis("Precision.DEFAULT"));
    }

    @Test
    void a_literal_written_the_way_a_person_writes_one_still_reads() {
        assertEquals(1_500L, DurationEditor.millis("Duration.ofMillis(1_500)"));
        assertEquals(2_000L, DurationEditor.millis("Duration.ofSeconds(2L)"));
    }

    // --- which calls may be restructured ---

    @Test
    void a_wait_whose_every_argument_is_a_readable_length_may_be_rewritten() {
        assertTrue(DurationEditor.waitArguments(slot("Duration.ofSeconds(1)", "Wait", "time",
                "Wait.time(Duration.ofSeconds(1))")).size() == 1);
        assertEquals(2, DurationEditor.waitArguments(slot("Duration.ofMillis(800)", "Wait", "between",
                "Wait.between(Duration.ofMillis(800), Duration.ofSeconds(2))")).size());
    }

    @Test
    void a_range_with_a_variable_at_one_end_is_left_alone() {
        assertTrue(DurationEditor.waitArguments(slot("Duration.ofSeconds(2)", "Wait", "between",
                        "Wait.between(timeout, Duration.ofSeconds(2))")).isEmpty(),
                "rewriting the call would discard the end this editor cannot show");
    }

    @Test
    void a_call_on_something_else_is_not_a_wait_however_it_is_shaped() {
        assertTrue(DurationEditor.waitArguments(slot("Duration.ofSeconds(2)", "Sleeper", "time",
                "Sleeper.time(Duration.ofSeconds(2))")).isEmpty());
    }

    // --- the range, which is a change to the call and not to the slot ---

    @Test
    void ticking_the_range_rewrites_the_whole_call() {
        var slot = slot("Duration.ofMillis(800)", "Wait", "time", "Wait.time(Duration.ofMillis(800))");
        DurationEditor.write(slot, DurationEditor.span(slot), 800L, 2_000L, true);
        assertEquals("Wait.between(Duration.ofMillis(800), Duration.ofSeconds(2))",
                slot.enclosingReplacement());
        assertNull(slot.replacement(), "the value in the slot is not what changed");
    }

    @Test
    void an_inverted_range_is_written_the_way_round_it_reads() {
        var slot = slot("Duration.ofSeconds(2)", "Wait", "time", "Wait.time(Duration.ofSeconds(2))");
        DurationEditor.write(slot, DurationEditor.span(slot), 2_000L, 800L, true);
        assertEquals("Wait.between(Duration.ofMillis(800), Duration.ofSeconds(2))",
                slot.enclosingReplacement());
    }

    @Test
    void un_ticking_the_range_shrinks_the_call_back_to_one_argument() {
        var slot = slot("Duration.ofMillis(800)", "Wait", "between",
                "Wait.between(Duration.ofMillis(800), Duration.ofSeconds(2))");
        DurationEditor.Span span = DurationEditor.span(slot);
        DurationEditor.write(slot, span, span.from(), span.to(), false);
        assertEquals("Wait.time(Duration.ofMillis(800))", slot.enclosingReplacement());
    }

    @Test
    void a_range_whose_ends_are_equal_is_not_a_range() {
        var slot = slot("Duration.ofSeconds(2)", "Wait", "time", "Wait.time(Duration.ofSeconds(2))");
        DurationEditor.write(slot, DurationEditor.span(slot), 2_000L, 2_000L, true);
        assertEquals("Duration.ofSeconds(2)", slot.replacement());
        assertNull(slot.enclosingReplacement());
    }

    @Test
    void outside_a_wait_there_is_no_call_to_restructure_and_only_the_value_is_written() {
        var slot = bare("Duration.ofSeconds(2)");
        DurationEditor.write(slot, DurationEditor.span(slot), 800L, 2_000L, true);
        assertEquals("Duration.ofMillis(800)", slot.replacement());
        assertNull(slot.enclosingReplacement());
    }
}
