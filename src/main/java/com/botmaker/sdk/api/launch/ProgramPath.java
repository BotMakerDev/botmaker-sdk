package com.botmaker.sdk.api.launch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter that takes the program to run: a path to an executable, or a command on {@code PATH}.
 *
 * <p>Nothing reads it while a bot runs. Studio offers a file chooser, and keeps the value typeable, for an
 * argument passed to such a parameter.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ProgramPath {}
