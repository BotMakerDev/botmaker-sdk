package com.botmaker.sdk.api.params;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A {@code @Param} group of what the game showed (2026-11-04): each method a fact, written during a run and unknown
 * until then. Its method's body before any write is only there to compile; {@link Assumed} makes it an answer.
 *
 * <pre>{@code
 * @Param
 * @GameState
 * public class Progress {
 *     @Param.Shown(changes = Param.Changes.DURING_RUN)
 *     @ReadOn(screen = Screens.Home.class, field = "gold")
 *     @StaleAfter(minutes = 30)
 *     public int gold() { return 0; }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GameState {}
