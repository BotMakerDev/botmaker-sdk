package com.botmaker.sdk.api.vision;

import com.botmaker.plugin.api.palette.Facade;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import java.util.Random;

/**
 * What a template search found: whether it matched, how well, and where — in absolute screen coordinates,
 * ready to hand to {@code Mouse}. A not-found result is a real object rather than {@code null}, with
 * {@link #isFound()} false and every point accessor returning {@code null}.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): all thirteen are offered, which is
 * {@link com.botmaker.sdk.api.geometry.Rect}'s verdict for a reason worth restating, because phase 3.10 had
 * skipped this type on the opposite one. It was left uncurated then on the grounds that a result is
 * <em>received</em> rather than called — true of the statement menu, which is the only surface curation
 * reached at the time, and false everywhere else: a bot that receives a match immediately calls something on
 * it, and the member menu that appears when it does is exactly the surface phase 3.12 opened. So the type is
 * decided now, and the answer is that a result type has no rival spellings to choose between — the eight
 * points it can give you are eight different questions, not eight ways of asking one.
 *
 * <p>{@link #randomClickPoint()} is the one that might look like a duplicate of {@link #center()} and is not:
 * clicking the exact centre of the same button a thousand times is the most legible signature a bot can leave,
 * and this is the method that exists to not do that.
 */
@Facade(category = "vision", categoryLabel = "Vision", role = "VALUE", order = 98)
public class MatchResult {

    private static final Random RANDOM = new Random();

    private final Point location;        // Top-left corner
    private final int width;
    private final int height;
    private final double confidence;     // 0.0 to 1.0
    private final boolean found;
    private final String templateId;

    MatchResult(Point location, int width, int height, double confidence, String templateId) {
        this.location = location;
        this.width = width;
        this.height = height;
        this.confidence = confidence;
        this.found = true;
        this.templateId = templateId;
    }

    private MatchResult(double confidence) {
        this.location = new Point(0, 0);
        this.width = 0;
        this.height = 0;
        this.confidence = confidence;
        this.found = false;
        this.templateId = null;
    }

    static MatchResult notFound() {
        return new MatchResult(0.0);
    }

    /**
     * A not-found result that still carries the best (below-threshold) score observed. Intended for telemetry
     * so an observer can show a real near-miss confidence instead of {@code 0}; {@link #isFound()} is still
     * {@code false} and {@link #center()}/click points still return {@code null}, so the public
     * find contract is unchanged.
     */
    static MatchResult miss(double bestScore) {
        return new MatchResult(bestScore);
    }

    public boolean isFound() {
        return found;
    }

    public double confidence() {
        return confidence;
    }

    public Point center() {
        // The centre pixel — the midpoint rounded, since a click lands on a whole pixel.
        return found ? new Point(location.x() + width / 2, location.y() + height / 2) : null;
    }

    public Point randomClickPoint() {
        if (!found) return null;

        int randomXOffset = RANDOM.nextInt((width / 2) + 1);
        int randomYOffset = RANDOM.nextInt((height / 2) + 1);

        return new Point(
                location.x() + randomXOffset + 1,
                location.y() + randomYOffset + 1
        );
    }

    public Point topLeft() {
        return found ? location : null;
    }

    public Point topRight() {
        return found ? new Point(location.x() + width, location.y()) : null;
    }

    public Point bottomLeft() {
        return found ? new Point(location.x(), location.y() + height) : null;
    }

    public Point bottomRight() {
        return found ? new Point(location.x() + width, location.y() + height) : null;
    }

    public Point pointWithOffset(int offsetX, int offsetY) {
        if (!found) return null;
        return new Point(location.x() + offsetX, location.y() + offsetY);
    }

    public Rect rect() {
        return found ? new Rect(location, new Size(width, height)) : null;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String templateId() {
        return templateId;
    }

    @Override
    public String toString() {
        if (!found) {
            return "MatchResult[NOT_FOUND]";
        }
        return String.format("MatchResult[found=true, location=%s, confidence=%.3f, size=%dx%d, template=%s]",
                location, confidence, width, height, templateId);
    }
}
