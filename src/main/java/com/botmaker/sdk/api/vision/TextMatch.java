package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;

/**
 * The result of a {@link Text} OCR search: one recognized word or line of text and where it sits.
 *
 * <p>Mirrors {@link ColorMatch}/{@link MatchResult} — package-private constructors, a {@link #notFound()}
 * sentinel, and {@code null} accessors when {@link #isFound()} is false — so the vision result types read
 * the same way.
 *
 * <p>The bounding box is in <b>absolute screen coordinates</b> (the search's capture-source origin is
 * already applied), so {@link #getCenter()} can be handed straight to {@code Mouse}.
 */
public class TextMatch {

    private final String text;
    private final Rect bounds;        // absolute screen coordinates
    private final float confidence;   // Tesseract confidence, 0..100
    private final boolean found;

    TextMatch(String text, Rect bounds, float confidence) {
        this.text = text;
        this.bounds = bounds;
        this.confidence = confidence;
        this.found = true;
    }

    private TextMatch() {
        this.text = null;
        this.bounds = null;
        this.confidence = 0f;
        this.found = false;
    }

    static TextMatch notFound() {
        return new TextMatch();
    }

    public boolean isFound() {
        return found;
    }

    /** The recognized text, or {@code null} if not found. */
    public String getText() {
        return text;
    }

    /** Tesseract's confidence for this text (0..100, higher is better); {@code 0} if not found. */
    public float getConfidence() {
        return confidence;
    }

    /** The text's bounding box in absolute screen coordinates, or {@code null} if not found. */
    public Rect getBounds() {
        if (!found) return null;
        return bounds.clone();
    }

    /** Centre of the text's bounding box — a click target — or {@code null} if not found. */
    public Point getCenter() {
        if (!found) return null;
        return bounds.getCenter();
    }

    /** Top-left of the text's bounding box, or {@code null} if not found. */
    public Point getTopLeft() {
        if (!found) return null;
        return bounds.getTopLeft();
    }

    @Override
    public String toString() {
        if (!found) return "TextMatch{notFound}";
        return "TextMatch{text=\"" + text + "\", bounds=" + bounds
                + ", confidence=" + String.format("%.1f", confidence) + "}";
    }
}
