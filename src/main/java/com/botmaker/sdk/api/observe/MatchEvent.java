package com.botmaker.sdk.api.observe;

import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.vision.MatchResult;

/**
 * Reports a template-match attempt to registered {@link BotObserver}s. Carries the {@link Surface} searched,
 * the search {@code region} (null for a whole-surface search) and the {@link MatchResult} — which itself
 * exposes {@code isFound()}, the matched {@code getRect()}, confidence and template id.
 */
public record MatchEvent(Surface surface, Rect region, MatchResult result) {}
