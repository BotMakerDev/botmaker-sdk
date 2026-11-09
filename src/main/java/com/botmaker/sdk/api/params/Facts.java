package com.botmaker.sdk.api.params;

import com.botmaker.plugin.api.params.Param;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A {@code @Param} group of what is true in the app the bot runs (2026-11-06): each method a fact, its type the
 * fact's, written during a run and unknown until then. The group holds values only: where a fact is read, and how long
 * a reading holds, are said where it is read, by a screen's readout, {@code Readout.into(Progress::gold)}.
 *
 * <pre>{@code
 * @Param
 * @Facts
 * public class Progress {
 *     @Param.Shown(changes = Param.Changes.DURING_RUN)
 *     public int gold() { return 0; }
 * }
 * }</pre>
 *
 * <p>Its method's body before any write is only there to compile. Only a run writes the values
 * ({@link Param.WrittenByRun}): Studio locks the group on the canvas, and the Parameters window shows them read-only.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Param.WrittenByRun
public @interface Facts {}
