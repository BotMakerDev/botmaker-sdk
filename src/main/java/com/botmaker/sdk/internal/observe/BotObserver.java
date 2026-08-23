package com.botmaker.sdk.internal.observe;

/**
 * Observes what a running bot's vision/interaction layer does, in real time — every template match and
 * every click. A first-class SDK feature usable with or without the Studio: register one to log actions,
 * assert on them in tests, drive custom tooling, or (as the Studio does via an internal implementation)
 * ship the geometry to a live preview panel.
 *
 * <p>Register with {@link Bots#addObserver(BotObserver)}. All methods default to no-ops, so implement only
 * the events you care about. Observers are invoked synchronously on the bot's own thread at the moment the
 * action happens — keep them fast and non-blocking; throwing is caught and ignored by the dispatcher.
 */
public interface BotObserver {

    /** A template-match attempt completed (whether or not it was found). */
    default void onMatch(MatchEvent event) {}

    /** The interaction layer performed a click. */
    default void onClick(ClickEvent event) {}

    /** The interaction layer performed a drag/swipe — reported once, when the gesture is complete. */
    default void onSwipe(SwipeEvent event) {}
}
