package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.internal.bot.InstalledRun;

import java.util.function.BooleanSupplier;

/**
 * A screen map's conditions asked: {@link com.botmaker.sdk.api.screens.Input#when()}, {@link
 * com.botmaker.sdk.api.screens.Way#when}, {@link com.botmaker.sdk.api.screens.ScreenVariant#when}. A run asks the
 * bot's method; Studio's tools hold only its name, which cannot be asked, so they read it as unknown, which never
 * closes a way (the user's call, 2026-10-15).
 */
public final class Conditions {

    private Conditions() {}

    /**
     * What {@code condition} says: a {@link Condition}'s own answer; a plain supplier yes or no; none yes; one that
     * cannot answer (a name, a method that threw) unknown. A stop thrown inside it, or wrapped, goes on: the run
     * ends.
     */
    public static Answer answer(BooleanSupplier condition) {
        if (condition == null) return Answer.YES;
        try {
            return condition instanceof Condition c ? c.answer() : Answer.of(condition.getAsBoolean());
        } catch (RuntimeException unknown) {
            for (Throwable at = unknown; at != null; at = at.getCause() == at ? null : at.getCause()) {
                if (InstalledRun.isStop(at)) throw (RuntimeException) at;
            }
            return Answer.UNKNOWN;
        }
    }

    /** Whether {@code condition} holds: anything but no. */
    public static boolean holds(BooleanSupplier condition) {
        return answer(condition) != Answer.NO;
    }
}
