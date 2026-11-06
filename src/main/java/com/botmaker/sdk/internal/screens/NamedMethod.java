package com.botmaker.sdk.internal.screens;

/**
 * A method as a name, {@code Puzzles::solve}: what Studio reads of a {@code Custom.of(…)} out of {@code Screens.java},
 * with no classpath to call it on. Nothing runs it: {@link Reach#perform} says it did not press what it holds.
 */
public record NamedMethod(String source) implements Runnable {

    @Override
    public void run() {
        throw new IllegalStateException(source + " runs only in a run of the bot");
    }
}
