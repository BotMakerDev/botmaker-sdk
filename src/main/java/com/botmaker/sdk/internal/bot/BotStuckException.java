package com.botmaker.sdk.internal.bot;


/**
 * Thrown when the bot is stuck: the {@link Watchdog} saw the same match {@link BotSettings#maxRetryAttempts()}
 * times between activities, or a vision loop ran {@link BotSettings#maxRounds()} rounds without ending.
 *
 * <p>It is an unchecked exception so it can propagate out of an activity's {@code run()} without
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
