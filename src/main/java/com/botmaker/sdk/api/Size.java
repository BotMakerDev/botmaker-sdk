package com.botmaker.sdk.api;

/**
 * A width and height in pixels.
 *
 * <p><b>Integers, deliberately.</b> Every {@code Size} in this API is a pixel extent — a capture
 * resolution, a template's dimensions, a region's span — and there is no producer of a fractional one:
 * the previous {@code double} fields were a vestige of the OpenCV type this class was copied from, and
 * every consumer cast them straight back to {@code int}, {@link #toString()} included.
 *
 * @param width  pixels across
 * @param height pixels down
 */
// The generated Activities declares one per size variable, rebuilt from the two stored numbers.
@Scaffolding
public record Size(int width, int height) {

    /**
     * Declared only to carry {@link Scaffolding}: the annotation targets a constructor, not a record
     * component, and the generated {@code Activities}' {@code size(String)} helper calls this one.
     */
    @Scaffolding
    public Size {
    }

    /** {@code 0 × 0} — what a value-typed variable defaults to before it is set. */
    public Size() {
        this(0, 0);
    }

    /** {@code width × height}. {@code long} because two screen dimensions can exceed an {@code int}. */
    public long area() {
        return (long) width * height;
    }

    /** Whether either dimension is zero or negative, so the size encloses nothing. */
    public boolean empty() {
        return width <= 0 || height <= 0;
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }
}
