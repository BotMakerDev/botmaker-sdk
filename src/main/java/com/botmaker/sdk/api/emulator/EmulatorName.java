package com.botmaker.sdk.api.emulator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter that takes the name of an emulator instance, as the emulator itself lists it.
 *
 * <p>Nothing reads it while a bot runs. Studio offers the instances found on this computer for an argument
 * passed to such a parameter.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface EmulatorName {}
