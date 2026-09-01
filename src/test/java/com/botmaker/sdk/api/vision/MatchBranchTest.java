package com.botmaker.sdk.api.vision;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MatchBranch} — the two guarantees a reader of a branch chain assumes without checking.
 *
 * <p>Both are invisible when broken: a chain that ran two branches would look like a bot doing something
 * odd once in a while, and one that evaluated a later predicate after an earlier match would only show up as
 * a side effect nobody expected. Neither is a crash, so neither is noticed without a test.
 */
class MatchBranchTest {

    /** Records the order things happened in, which is the only thing worth asserting about a chain. */
    private static List<String> trace() {
        return new ArrayList<>();
    }

    @Test
    void the_first_matching_branch_runs_and_the_rest_do_not() {
        List<String> ran = trace();
        Matches.none()
                .when(m -> true, () -> ran.add("first"))
                .when(m -> true, () -> ran.add("second"))
                .otherwise(() -> ran.add("otherwise"));

        assertEquals(List.of("first"), ran);
    }

    /**
     * A later predicate is not evaluated at all once a branch has matched.
     *
     * <p>This is why the argument is a {@code Predicate} and not a {@code boolean}: an argument would be
     * evaluated before the call, so every branch's test would run — cheap for {@code hasAny}, and not cheap
     * or side-effect-free in general.
     */
    @Test
    void a_settled_chain_does_not_even_ask_the_later_questions() {
        List<String> asked = trace();
        Matches.none()
                .when(m -> { asked.add("first"); return true; }, () -> { })
                .when(m -> { asked.add("second"); return true; }, () -> { })
                .otherwise(() -> { });

        assertEquals(List.of("first"), asked);
    }

    @Test
    void otherwise_runs_only_when_nothing_matched() {
        List<String> ran = trace();
        Matches.none()
                .when(m -> false, () -> ran.add("first"))
                .when(m -> false, () -> ran.add("second"))
                .otherwise(() -> ran.add("otherwise"));

        assertEquals(List.of("otherwise"), ran);
    }

    /** A chain may end without a fallback; nothing happens and the value is discardable. */
    @Test
    void a_chain_with_no_otherwise_simply_does_nothing() {
        List<String> ran = trace();
        Matches.none().when(m -> false, () -> ran.add("first"));

        assertTrue(ran.isEmpty());
    }

    /** Every predicate is handed the same frame, which is the property Matches exists for. */
    @Test
    void every_branch_sees_the_one_frame_the_chain_started_from() {
        Matches frame = Matches.none();
        List<Boolean> sameInstance = new ArrayList<>();

        frame.when(m -> { sameInstance.add(m == frame); return false; }, () -> { })
             .when(m -> { sameInstance.add(m == frame); return false; }, () -> { })
             .otherwise(() -> { });

        assertEquals(List.of(true, true), sameInstance);
    }

    /**
     * A half-written branch is skipped, never thrown on.
     *
     * <p>An editor leaves a slot empty while the user is still filling it in, and a bot that refuses to run
     * because one branch is unfinished is worse than one that does the rest — the same reasoning as every
     * other degradation in this module.
     */
    @Test
    void a_null_test_or_action_is_skipped_rather_than_thrown_on() {
        List<String> ran = trace();
        Matches.none()
                .when(null, () -> ran.add("no test"))
                .when(m -> true, null)
                .when(m -> true, () -> ran.add("complete"))
                .otherwise(() -> ran.add("otherwise"));

        assertEquals(List.of("complete"), ran,
                "an unfinished branch does not match, and does not settle the chain either");
    }

    @Test
    void settled_reports_whether_a_branch_has_won() {
        assertFalse(Matches.none().when(m -> false, () -> { }).settled());
        assertTrue(Matches.none().when(m -> true, () -> { }).settled());
    }
}
