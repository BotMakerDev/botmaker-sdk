package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.geometry.Point;

/** A found {@link MatchResult} for tests outside this package; its constructor is package-private. */
public final class FoundMatches {

    private FoundMatches() {
    }

    public static MatchResult at(String templateId, int x, int y) {
        return new MatchResult(new Point(x, y), 10, 10, 0.95, templateId);
    }
}
