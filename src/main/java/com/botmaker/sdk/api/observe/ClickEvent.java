package com.botmaker.sdk.api.observe;

import com.botmaker.sdk.api.Point;

/**
 * Reports a click performed by the interaction layer to registered {@link BotObserver}s. {@code point} is
 * the absolute (virtual-screen) coordinate the click landed on; {@code button} is 1=left, 2=middle, 3=right.
 */
public record ClickEvent(Surface surface, Point point, int button) {

    /** Left mouse button, the button used by the template-click helpers. */
    public static final int LEFT = 1;
}
