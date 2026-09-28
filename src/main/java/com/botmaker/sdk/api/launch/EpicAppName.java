package com.botmaker.sdk.api.launch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter that takes an Epic Games Launcher app name, the launch token from the launcher's manifest.
 *
 * <p>Nothing reads it while a bot runs. Studio offers a grid of the Epic games installed on this computer for
 * an argument passed to such a parameter.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface EpicAppName {}
