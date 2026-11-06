package com.botmaker.sdk.api.params;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A {@code @Param} group of what the game showed (2026-11-04), read as {@link Facts} is. Replaced by {@link Facts}
 * (2026-11-06): a bot need not be a game, and Studio's upgrade rewrites it by the rule in the SDK's migrations.
 */
@Deprecated(forRemoval = true)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GameState {}
