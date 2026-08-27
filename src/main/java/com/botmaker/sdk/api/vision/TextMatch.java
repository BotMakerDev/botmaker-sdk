package com.botmaker.sdk.api.vision;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;

/**
 * The result of a {@link Text} OCR search: one recognized word or line of text and where it sits.
 *
 * <p>Mirrors {@link ColorMatch}/{@link MatchResult} — package-private constructors, a {@link #notFound()}
 * sentinel, and {@code null} accessors when {@link #isFound()} is false — so the vision result types read
 * the same way.
 *
 * <p>The bounding box is in <b>absolute screen coordinates</b> (the search's capture-source origin is
 * already applied), so {@link #center()} can be handed straight to {@code Mouse}.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): all six are offered, the verdict {@link MatchResult}
 * and {@link ColorMatch} reached and for the same reason — the questions a result type answers are different
 * questions, not competing spellings of one. This is the smallest of the three and the one that most looks like
 * it should have been a record; it is not, because a public record cannot keep the package-private constructor
 * that makes {@link Text} the only thing able to mint one, and because {@link #center()} and {@link #topLeft()}
 * are derived from {@link #bounds()} rather than stored beside it.
 */
@Palette(category = "vision", categoryLabel = "Vision", order = 100)
@Hidden("a value type: a bot receives one from Text, it does not build one from a menu")
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
    public String text() {
        return text;
    }

    /** Tesseract's confidence for this text (0..100, higher is better); {@code 0} if not found. */
    public float confidence() {
        return confidence;
    }

    /** The text's bounding box in absolute screen coordinates, or {@code null} if not found. */
    public Rect bounds() {
        // Rect is immutable, so the defensive copy this used to make is no longer needed.
        return found ? bounds : null;
    }

    /** Centre of the text's bounding box — a click target — or {@code null} if not found. */
    public Point center() {
        if (!found) return null;
        return bounds.center();
    }

    /** Top-left of the text's bounding box, or {@code null} if not found. */
    public Point topLeft() {
        if (!found) return null;
        return bounds.topLeft();
    }

    @Override
    public String toString() {
        if (!found) return "TextMatch{notFound}";
        return "TextMatch{text=\"" + text + "\", bounds=" + bounds
                + ", confidence=" + String.format("%.1f", confidence) + "}";
    }
}
