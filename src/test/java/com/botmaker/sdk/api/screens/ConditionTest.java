package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.internal.flow.FlowWalker;
import com.botmaker.sdk.internal.screens.Conditions;
import org.junit.jupiter.api.Test;

import static com.botmaker.sdk.api.screens.Answer.NO;
import static com.botmaker.sdk.api.screens.Answer.UNKNOWN;
import static com.botmaker.sdk.api.screens.Answer.YES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Conditions answer yes, no or unknown, and unknown never closes a way. */
class ConditionTest {

    private static final Condition Y = () -> YES;
    private static final Condition N = () -> NO;
    private static final Condition U = () -> UNKNOWN;

    @Test
    void allAnyNotFollowThreeValuedLogic() {
        assertEquals(YES, Condition.all(Y, Y).answer());
        assertEquals(NO, Condition.all(Y, U, N).answer());
        assertEquals(UNKNOWN, Condition.all(Y, U).answer());
        assertEquals(YES, Condition.all().answer());
        assertEquals(YES, Condition.any(N, U, Y).answer());
        assertEquals(UNKNOWN, Condition.any(N, U).answer());
        assertEquals(NO, Condition.any(N, N).answer());
        assertEquals(NO, Condition.any().answer());
        assertEquals(NO, Condition.not(Y).answer());
        assertEquals(YES, Condition.not(N).answer());
        assertEquals(UNKNOWN, Condition.not(U).answer());
    }

    @Test
    void aThrowingMethodIsUnknown() {
        Condition c = Condition.of(() -> { throw new IllegalStateException("not read yet"); });
        assertEquals(UNKNOWN, c.answer());
        assertTrue(c.getAsBoolean(), "unknown never closes a way");
        assertEquals(UNKNOWN, Conditions.answer(() -> { throw new IllegalStateException(); }));
        assertTrue(Conditions.holds(U));
    }

    @Test
    void aThrowingPartIsUnknownWhateverItsPlace() {
        Condition throwing = () -> { throw new IllegalStateException(); };
        assertEquals(NO, Condition.all(throwing, N).answer());
        assertEquals(NO, Condition.all(N, throwing).answer());
        assertEquals(YES, Condition.any(throwing, Y).answer());
        assertEquals(UNKNOWN, Condition.not(throwing).answer());
    }

    @Test
    void aConditionWrappedKeepsItsUnknown() {
        assertEquals(UNKNOWN, Condition.of(U).answer());
        assertEquals(UNKNOWN, Condition.not(Condition.of(U)).answer());
        assertThrows(NullPointerException.class, () -> Condition.of(null));
    }

    @Test
    void aStopInsideAConditionEndsTheRun() {
        RuntimeException stop = assertThrows(RuntimeException.class,
                () -> Condition.all(Condition.of(() -> { Bot.stop(); return true; })).answer());
        assertTrue(FlowWalker.isStop(stop));
        assertThrows(RuntimeException.class, () -> Conditions.holds(() -> { Bot.stop(); return true; }));
    }

    @Test
    void aPlainSupplierIsYesOrNo() {
        assertEquals(YES, Condition.of(() -> true).answer());
        assertEquals(NO, Condition.of(() -> false).answer());
        assertEquals(NO, Conditions.answer(() -> false));
        assertEquals(YES, Conditions.answer(null));
        assertFalse(Conditions.holds(N));
        assertEquals(UNKNOWN, Answer.fromId("maybe"));
        assertEquals(NO, Answer.fromId(NO.id()));
    }
}
