package com.botmaker.sdk.api.launch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter that takes command-line arguments for the launched program, such as {@code --fullscreen}.
 * On a varargs parameter it marks every argument of the tail.
 *
 * <p>Nothing reads it while a bot runs. Studio draws such an argument as a launch option rather than as a
 * second program to run.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface LaunchOption {}
