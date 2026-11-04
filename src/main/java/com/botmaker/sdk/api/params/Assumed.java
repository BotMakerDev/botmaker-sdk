package com.botmaker.sdk.api.params;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A {@link GameState} fact that answers its method's value until a run writes it, instead of being unknown
 * (2026-11-04).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Assumed {}
