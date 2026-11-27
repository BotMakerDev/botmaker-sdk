// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.plan;

import com.botmaker.plugin.api.managed.HostHolder;
import com.botmaker.plugin.api.managed.ManagedId;
import com.botmaker.plugin.api.managed.ManagedMarker;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one of the SDK's values of <b>this computer</b> that a run writes (2026-11-15, {@code RunHostValues}): what
 * the runs of every bot learned of a game on an instance. Java in BotMaker's own folder of this PC, beside
 * {@code @SdkHostValue}'s, never in a bot, never published. Here, not beside {@code SdkHostValue}, because the run that
 * writes it names no {@code plugin} class.
 */
@Documented
@ManagedMarker
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface SdkRunValue {

    /** Which of the run's values of this PC this is. */
    Id value();

    /** The values, one constant each. */
    enum Id implements ManagedId<Holder> {
        /** When each {@code Do} was last done on an instance's game, and whether its check held. */
        DONE,
        /** The window a run picked for each desktop game it launched, by launch target ({@code AutoPicks}). */
        WINDOWS,
        /** The Google accounts each emulator instance last said it holds, and since when ({@code GoogleReads}). */
        GOOGLE_READS
    }

    /** This PC's class the values live in: {@code Runs.java}. */
    enum Holder implements HostHolder {
        RUNS
    }
}
