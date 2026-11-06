package com.botmaker.sdk.api.params;

import com.botmaker.plugin.api.meta.Migrate;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How long a {@link Facts} fact's value holds after it was written; past that it is unknown (2026-11-04). Replaced by
 * the readout that reads it (2026-11-06): {@code Readout.into(Progress::gold).staleAfter(Duration.ofMinutes(30))}.
 */
@Deprecated(forRemoval = true)
@Migrate.Dropped("how long a reading holds is said by the screen's readout now: Readout.into(…).staleAfter(…)")
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface StaleAfter {

    /** The minutes it holds. */
    int minutes();
}
