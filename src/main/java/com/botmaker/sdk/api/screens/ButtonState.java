package com.botmaker.sdk.api.screens;

import java.util.Locale;

/**
 * What a {@link Look} of a button means. The SDK's own are {@link State#NORMAL}, {@link State#SELECTED} and
 * {@link State#DISABLED}; every other is the bot's, a constant of an enum nested in its {@code States} class
 * (2026-11-02, the user's call): {@code States.Blocks.MAXED} is never pressed, {@code States.Presses.BADGE} is.
 * The enum it is in says which, so the bot's enum needs no body:
 *
 * <pre>{@code
 * public final class States {
 *     public enum Blocks implements ButtonState { MAXED, UPGRADING }
 *     public enum Presses implements ButtonState { BADGE }
 * }
 * }</pre>
 *
 * A press that meets a look of {@code Blocks} reports {@link Pressed.Result#BLOCKED} and carries the look in
 * {@link Pressed#state()}, so the flow branches on it.
 */
public interface ButtonState {

    /** The enum of the bot's {@code States} whose looks are never pressed. */
    String BLOCKS = "Blocks";

    /** The enum of the bot's {@code States} whose looks are pressed. */
    String PRESSES = "Presses";

    /** Whether a button showing it is never pressed: a constant of the bot's {@code States.Blocks}. */
    default boolean blocks() {
        return this instanceof Enum<?> constant && BLOCKS.equals(constant.getDeclaringClass().getSimpleName());
    }

    /** How a tool names it: {@code Blocks.MAXED} for a state of the bot's. */
    default String id() {
        return this instanceof Enum<?> constant
                ? constant.getDeclaringClass().getSimpleName() + "." + constant.name() : toString();
    }

    /** How a window shows it: {@code Maxed} for a state of the bot's. */
    default String displayName() {
        String name = this instanceof Enum<?> constant ? constant.name() : id();
        String words = name.substring(name.lastIndexOf('.') + 1).replace('_', ' ').toLowerCase(Locale.ROOT);
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
