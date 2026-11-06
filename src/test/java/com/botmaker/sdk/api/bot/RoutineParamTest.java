package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.plan.Routine;
import com.botmaker.sdk.internal.flow.BotConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** What a run runs: {@code routine()}, else, while it is no routine, the {@code flow()} it replaces. */
@SuppressWarnings("removal")
class RoutineParamTest {

    static final Flow MAIN = BotConstants.flow("MAIN");
    static final Flow WEEKLY = BotConstants.flow("WEEKLY");
    static final Plan DAILY = Plan.of(Plan.tier(Goal.of(() -> true)));

    /** A bot of before: a flow picked, no routine. */
    static final class FlowOnly {
        @SdkValue(SdkValue.Id.FLOW)
        public static Flow flow() {
            return MAIN;
        }
    }

    /** A bot just bound: the routine at its first value, the flow picked before still there. */
    static final class RoutineUnpicked {
        @SdkValue(SdkValue.Id.ROUTINE)
        public static Routine routine() {
            return Flow.NONE;
        }

        @SdkValue(SdkValue.Id.FLOW)
        public static Flow flow() {
            return MAIN;
        }
    }

    /** A routine picked wins over the old flow. */
    static final class RoutinePicked {
        @SdkValue(SdkValue.Id.ROUTINE)
        public static Routine routine() {
            return WEEKLY;
        }

        @SdkValue(SdkValue.Id.FLOW)
        public static Flow flow() {
            return MAIN;
        }
    }

    static final class APlan {
        @SdkValue(SdkValue.Id.ROUTINE)
        public static Routine routine() {
            return DAILY;
        }
    }

    static final class OnlyNone {
        @SdkValue(SdkValue.Id.ROUTINE)
        public static Routine routine() {
            return Flow.NONE;
        }
    }

    static final class Nothing {}

    @Test
    void flowValueMovesToRoutine() {
        assertSame(MAIN, Bot.routine(FlowOnly.class), "no routine yet: the flow picked runs");
        assertSame(MAIN, Bot.routine(RoutineUnpicked.class), "a routine at none still runs the flow picked");
        assertSame(WEEKLY, Bot.routine(RoutinePicked.class), "a routine picked wins");
        assertSame(DAILY, Bot.routine(APlan.class));
        assertSame(Flow.NONE, Bot.routine(OnlyNone.class), "none and no old flow: nothing runs");
        assertNull(Bot.routine(Nothing.class));
    }
}
