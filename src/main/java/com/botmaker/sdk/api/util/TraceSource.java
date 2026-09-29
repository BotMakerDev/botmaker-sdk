package com.botmaker.sdk.api.util;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The source name a class's {@link Debug} lines are printed and traced under, when its simple name is not the
 * one to show. Without it a line written from {@code Collect} reads {@code [Collect] …}; with
 * {@code @TraceSource("Farming")} on {@code Collect} it reads {@code [Farming] …}.
 *
 * <p>It is read from the top-level class, so a nested class or a lambda inside one is traced under its
 * enclosing class's name. A message that starts with its own {@code [Name]} keeps that one.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TraceSource {

    /** The name, e.g. {@code "Vision"}. A blank one means the class's simple name. */
    String value();
}
