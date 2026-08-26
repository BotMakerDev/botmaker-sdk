package com.botmaker.sdk.api.vision;

import com.botmaker.plugin.api.palette.Facade;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.meta.Since;

/**
 * One recognized piece of text from an OCR pass. Immutable.
 *
 * <p>The {@link #bounds() bounding box} is in <b>source-local</b> coordinates (top-left of the recognized
 * image is {@code (0,0)}), already corrected for any preprocessing upscale. {@link Text} shifts it to
 * absolute screen coordinates and hands back a {@link TextMatch}; this is the un-shifted form, and most bots
 * never see it.
 *
 * <p>It carries a {@link Rect} rather than a {@code java.awt.Rectangle}. That is new in 1.2.0 and is the
 * point of the move: while the OCR core lived in {@code botmaker-shared} it had no SDK geometry type to
 * name, so every caller converted at the boundary.
 *
 * @param text       the recognized text (a single word or a whole line, per {@link #level()})
 * @param bounds     the source-local bounding box of the text
 * @param confidence Tesseract's confidence, 0..100 (higher is better)
 * @param level      whether this result is a {@link Level#WORD} or a {@link Level#LINE}
 */
@Since("1.2.0")
@Facade(category = "vision", categoryLabel = "Vision", role = "VALUE", order = 103)
public record TextResult(String text, Rect bounds, float confidence, Level level) {

    /** Granularity of a {@link TextResult}: an individual word or a whole line of text. */
    public enum Level { WORD, LINE }
}
