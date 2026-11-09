package com.botmaker.sdk.internal.bot;


/**
 * Thrown when the bot is stuck: the {@link Watchdog} saw the game frozen or owing a restart, a vision loop ran
 * {@link BotSettings#maxRounds()} rounds without ending, or a walk could not arrive.
 *
 * <p>It is an unchecked exception so it can propagate out of a plan round without
 * cluttering signatures. {@link Bot#supervise} catches it and runs the recovery ({@code goHome()}, and
 * {@code startGame()} only when that fails) before restarting the bot loop.
 */
public class BotStuckException extends RuntimeException {

    public BotStuckException(String message) {
        super(message);
    }

    public BotStuckException(String message, Throwable cause) {
        super(message, cause);
    }
}
