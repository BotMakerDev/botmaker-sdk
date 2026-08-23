package com.botmaker.sdk.internal.observe;

import com.botmaker.sdk.api.geometry.Point;

/**
 * Reports a drag/swipe performed by the interaction layer to registered {@link BotObserver}s. {@code start}
 * and {@code end} are absolute coordinates on {@code surface}; {@code durationMs} is how long the gesture was
 * asked to take (0 for an instant drag).
 *
 * <p>One event for the whole gesture, not one per intermediate move: the bot decided to go from here to there,
 * and the moves in between are how the driver got that done. An observer drawing the swipe wants both ends at
 * once, and one that logs it would otherwise log a hundred lines for a single flick.
 */
public record SwipeEvent(Surface surface, Point start, Point end, long durationMs) {}
