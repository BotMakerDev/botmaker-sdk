package com.botmaker.sdk.api.bot;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter that takes the name of an outcome declared on the bot's flow.
 *
 * <p>Nothing reads it while a bot runs. Studio offers the outcomes drawn in the project's Activity Flow for an
 * argument passed to such a parameter, and keeps the value typeable.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface OutcomeName {}
