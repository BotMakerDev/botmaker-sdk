package com.botmaker.sdk.api.params;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** How long a {@link GameState} fact's value holds after it was written; past that it is unknown (2026-11-04). */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface StaleAfter {

    /** The minutes it holds. */
    int minutes();
}
