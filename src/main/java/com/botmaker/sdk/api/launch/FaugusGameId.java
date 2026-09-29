package com.botmaker.sdk.api.launch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter that takes a Faugus Launcher game id, the {@code gameid} of an entry in Faugus's library.
 *
 * <p>Nothing reads it while a bot runs. Studio offers a grid of the games and launchers Faugus has on this
 * computer for an argument passed to such a parameter.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface FaugusGameId {}
