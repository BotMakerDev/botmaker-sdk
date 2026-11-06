package com.botmaker.sdk.api.plan;

import com.botmaker.sdk.api.flow.Flow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanTest {

    private static final Goal A = Goal.of(() -> true);
    private static final Goal B = Goal.of(() -> true);
    private static final Goal C = Goal.of(() -> true);

    @Test
    void tiersKeepOrder() {
        Plan plan = Plan.of(Plan.tier(B), Plan.tier(C, A));

        assertEquals(List.of(List.of(B), List.of(C, A)), plan.tiers().stream().map(Plan.Tier::goals).toList());
    }

    @Test
    void readOnly() {
        List<Plan.Tier> tiers = new ArrayList<>(List.of(Plan.tier(A)));
        Plan plan = new Plan(tiers);
        tiers.clear();

        assertEquals(1, plan.tiers().size());
        assertThrows(UnsupportedOperationException.class, () -> plan.tiers().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.tiers().getFirst().goals().clear());
    }

    @Test
    void refusesWhatMeansNothing() {
        assertThrows(IllegalArgumentException.class, Plan::of);
        assertThrows(IllegalArgumentException.class, Plan::tier);
        IllegalArgumentException loading = assertThrows(IllegalArgumentException.class, () -> Plan.tier(A, null));
        assertTrue(loading.getMessage().contains("declared below"), loading.getMessage());
    }

    @Test
    void aRunRunsAFlowOrAPlan() {
        assertInstanceOf(Routine.class, Flow.NONE);
        assertInstanceOf(Routine.class, Plan.of(Plan.tier(A)));
    }
}
