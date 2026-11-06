package com.botmaker.sdk.api.screens;

import java.time.Duration;
import java.util.Objects;

/** What a {@link Do} does once its check says no: {@link #again}, {@link #later} or {@link #never}. */
public sealed interface Retry permits Retry.Again, Retry.Later, Retry.Never {

    /** Unless a {@link Do#onFail} says otherwise: later, in half an hour. */
    Retry DEFAULT = later(Duration.ofMinutes(30));

    /** At once, up to {@code times} times more; then only when its cadence next comes round. */
    static Retry again(int times) {
        return new Again(times);
    }

    /** Not before {@code wait} has passed. */
    static Retry later(Duration wait) {
        return new Later(wait);
    }

    /** Not again until a press from elsewhere makes its check hold. */
    static Retry never() {
        return new Never();
    }

    /** {@link #again}. */
    record Again(int times) implements Retry {
        public Again {
            if (times < 1) throw new IllegalArgumentException("Retry.again takes at least 1 time, not " + times + ".");
        }
    }

    /** {@link #later}. */
    record Later(Duration after) implements Retry {
        public Later {
            Objects.requireNonNull(after, "after");
            if (after.isNegative() || after.isZero()) {
                throw new IllegalArgumentException("Retry.later takes a time ahead, not " + after + ".");
            }
        }
    }

    /** {@link #never}. */
    record Never() implements Retry {}
}
