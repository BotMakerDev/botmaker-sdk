package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;

/**
 * Thrown when the {@link Watchdog} decides the bot is stuck — the screen has not advanced for
 * {@link BotSettings#maxRetryAttempts()} consecutive match attempts
 * (a frozen screen, or the same item clicked over and over with no effect).
 *
 * <p>It is an unchecked exception so it can propagate out of an activity's {@code run()} without
 * cluttering signatures. {@link Bot#supervise} catches it and runs the recovery hook
 * (typically {@code goHome()} then {@code startGame()}) before restarting the bot loop.
 */
@Palette(category = "bot", categoryLabel = "Bot", order = 84)
@Hidden("a value type: a bot catches it, it is never inserted from a menu")
public class BotStuckException extends RuntimeException {

    public BotStuckException(String message) {
        super(message);
    }

    public BotStuckException(String message, Throwable cause) {
        super(message, cause);
    }
}
