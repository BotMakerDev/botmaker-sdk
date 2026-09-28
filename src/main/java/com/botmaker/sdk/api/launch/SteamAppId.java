package com.botmaker.sdk.api.launch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter that takes a Steam application id, such as {@code "570"}.
 *
 * <p>Nothing reads it while a bot runs. Studio offers a grid of the Steam games installed on this computer for
 * an argument passed to such a parameter, where a plain {@code String} would only get a text field.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface SteamAppId {}
