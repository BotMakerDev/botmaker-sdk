package com.botmaker.sdk.api.flow;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter that takes one of the bot's activities, as its enum's class literal —
 * {@code Activities.Collect.class}.
 *
 * <p>Nothing reads it while a bot runs. Studio offers the flow's activities for an argument passed to such a
 * parameter, and writes the one picked as its class literal: a {@code Class} is no type an editor can hold.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ActivityClass {}
