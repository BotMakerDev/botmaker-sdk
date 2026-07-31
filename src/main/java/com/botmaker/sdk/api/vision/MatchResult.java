package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import java.util.Random;

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
     * {@code false} and {@link #getCenter()}/click points still return {@code null}, so the public
     * find contract is unchanged.
     */
    static MatchResult miss(double bestScore) {
        return new MatchResult(bestScore);
    }

    public boolean isFound() {
        return found;
    }

    public double getConfidence() {
        return confidence;
    }

    public Point getCenter() {
        if (!found) return null;
        return new Point(
                location.x + width / 2.0,
                location.y + height / 2.0
        );
    }

    public Point getRandomClickPoint() {
        if (!found) return null;

        int randomXOffset = RANDOM.nextInt((width / 2) + 1);
        int randomYOffset = RANDOM.nextInt((height / 2) + 1);

        return new Point(
                location.x + randomXOffset + 1,
                location.y + randomYOffset + 1
        );
    }

    public Point getTopLeft() {
        return found ? location : null;
    }

    public Point getTopRight() {
        return found ? new Point(location.x + width, location.y) : null;
    }

    public Point getBottomLeft() {
        return found ? new Point(location.x, location.y + height) : null;
    }

    public Point getBottomRight() {
        return found ? new Point(location.x + width, location.y + height) : null;
    }

    public Point getPointWithOffset(int offsetX, int offsetY) {
        if (!found) return null;
        return new Point(location.x + offsetX, location.y + offsetY);
    }

    public Rect getRect() {
        // Cast double coordinates to int for Rect
        return found ? new Rect((int) location.x, (int) location.y, width, height) : null;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getTemplateId() {
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
