package com.botmaker.sdk.api.bot;

/**
 * Thrown when the {@link Watchdog} decides the bot is stuck — the screen has not advanced for
 * {@link com.botmaker.sdk.api.vision.ClickConfig#MAX_RETRY_ATTEMPTS} consecutive match attempts
 * (a frozen screen, or the same item clicked over and over with no effect).
 *
 * <p>It is an unchecked exception so it can propagate out of an activity's {@code run()} without
 * cluttering signatures. {@link Bot#supervise} catches it and runs the recovery hook
 * (typically {@code goHome()} then {@code startGame()}) before restarting the bot loop.
 */
public class BotStuckException extends RuntimeException {

    public BotStuckException(String message) {
        super(message);
    }

    public BotStuckException(String message, Throwable cause) {
        super(message, cause);
    }
}
