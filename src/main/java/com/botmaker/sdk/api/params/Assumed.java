package com.botmaker.sdk.api.params;

import com.botmaker.plugin.api.meta.Migrate;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A {@link Facts} fact that answers its method's value until a run writes it, instead of being unknown (2026-11-04).
 * Dropped (2026-11-06): an assumption is state information in an annotation; a check that wants a default says it.
 */
@Deprecated(forRemoval = true)
@Migrate.Dropped("a fact is unknown until read; a check that wants a default writes it in the check")
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Assumed {}
