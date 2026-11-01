package com.botmaker.sdk.api.text;

import com.botmaker.plugin.api.value.Link;

/**
 * Immutable OCR tuning knobs. Start from {@link #defaults()} and derive variants with the copy methods, each named
 * as the part it sets:
 *
 * <pre>{@code
 * OcrOptions opts = OcrOptions.defaults()
 *         .languages(OcrLanguage.ENGLISH)
 *         .upscale(3.0)                 // small HUD font — upscale more
 *         .binarize(BinarizeMode.OTSU)
 *         .invert(true)                 // light text on a dark panel
 *         .charWhitelist("0123456789"); // a numeric counter
 * }</pre>
 *
 * <p>The {@code with*} names they had until 2026-11-01 are deprecated; Studio's upgrade rewrites them.
 *
 * @param languages    Tesseract language spec, e.g. {@code "eng"} or {@code "eng+chi_sim"}. It stays a
 *                     {@code String} because Tesseract parses the {@code +}-joined form and a bot may name a
 *                     language it installed system-wide — but the bundled ones are {@link OcrLanguage}, so
 *                     build it with {@link #languages(OcrLanguage...)} rather than by hand.
 * @param pageSegMode  Tesseract Page Segmentation Mode (PSM). {@code 3} = fully automatic (default);
 *                     {@code 7} = single line; {@code 8} = single word; {@code 6} = a uniform block.
 * @param ocrEngineMode Tesseract OCR Engine Mode (OEM). {@code 1} = LSTM only (default).
 * @param grayscale    convert to grayscale before recognition (usually helps).
 * @param upscale      linear upscale factor applied before recognition ({@code 1.0} = none). {@code 2–3}
 *                     markedly improves accuracy on small game fonts. Bounding boxes are mapped back down.
 * @param binarize     thresholding mode applied after grayscale — the main accuracy win for game text.
 * @param invert       invert after binarizing, so light-on-dark text becomes dark-on-light for Tesseract.
 * @param charWhitelist restrict recognition to these characters ({@code null}/empty = all).
 * @param level        granularity of the recognized {@link TextResult}s: {@link TextResult.Level#WORD}
 *                     or {@link TextResult.Level#LINE}.
 */
public record OcrOptions(
        String languages,
        int pageSegMode,
        int ocrEngineMode,
        boolean grayscale,
        double upscale,
        BinarizeMode binarize,
        boolean invert,
        String charWhitelist,
        TextResult.Level level) {

    /** How the preprocessing pass turns a grayscale image into black-and-white for Tesseract. */
    public enum BinarizeMode {
        /** Leave the (grayscale) image as-is. */
        NONE,
        /** Global Otsu threshold — best for even lighting / flat UI panels. The default. */
        OTSU,
        /** Adaptive (local) threshold — better under uneven lighting / gradients. */
        ADAPTIVE
    }

    /** Sensible general-purpose defaults: English, automatic layout, grayscale + 2× upscale + Otsu. */
    public static OcrOptions defaults() {
        return new OcrOptions(OcrLanguage.ENGLISH.code(), 3, 1, true, 2.0,
                BinarizeMode.OTSU, false, null, TextResult.Level.WORD);
    }

    @Link
    public OcrOptions languages(String languages) {
        return new OcrOptions(languages, pageSegMode, ocrEngineMode, grayscale, upscale, binarize, invert, charWhitelist, level);
    }

    /** The same, from the bundled set: {@code languages(ENGLISH, SIMPLIFIED_CHINESE)} → {@code eng+chi_sim}. */
    public OcrOptions languages(OcrLanguage... languages) {
        return languages(OcrLanguage.spec(languages));
    }

    @Link
    public OcrOptions pageSegMode(int pageSegMode) {
        return new OcrOptions(languages, pageSegMode, ocrEngineMode, grayscale, upscale, binarize, invert, charWhitelist, level);
    }

    @Link
    public OcrOptions ocrEngineMode(int ocrEngineMode) {
        return new OcrOptions(languages, pageSegMode, ocrEngineMode, grayscale, upscale, binarize, invert, charWhitelist, level);
    }

    @Link
    public OcrOptions grayscale(boolean grayscale) {
        return new OcrOptions(languages, pageSegMode, ocrEngineMode, grayscale, upscale, binarize, invert, charWhitelist, level);
    }

    @Link
    public OcrOptions upscale(double upscale) {
        return new OcrOptions(languages, pageSegMode, ocrEngineMode, grayscale, upscale, binarize, invert, charWhitelist, level);
    }

    @Link
    public OcrOptions binarize(BinarizeMode binarize) {
        return new OcrOptions(languages, pageSegMode, ocrEngineMode, grayscale, upscale, binarize, invert, charWhitelist, level);
    }

    @Link
    public OcrOptions invert(boolean invert) {
        return new OcrOptions(languages, pageSegMode, ocrEngineMode, grayscale, upscale, binarize, invert, charWhitelist, level);
    }

    @Link
    public OcrOptions charWhitelist(String charWhitelist) {
        return new OcrOptions(languages, pageSegMode, ocrEngineMode, grayscale, upscale, binarize, invert, charWhitelist, level);
    }

    @Link
    public OcrOptions level(TextResult.Level level) {
        return new OcrOptions(languages, pageSegMode, ocrEngineMode, grayscale, upscale, binarize, invert, charWhitelist, level);
    }

    /** Was {@link #languages(String)}. */
    @Deprecated(forRemoval = true)
    public OcrOptions withLanguages(String languages) {
        return languages(languages);
    }

    /** Was {@link #languages(OcrLanguage...)}. */
    @Deprecated(forRemoval = true)
    public OcrOptions withLanguages(OcrLanguage... languages) {
        return languages(languages);
    }

    /** Was {@link #pageSegMode(int)}. */
    @Deprecated(forRemoval = true)
    public OcrOptions withPageSegMode(int pageSegMode) {
        return pageSegMode(pageSegMode);
    }

    /** Was {@link #ocrEngineMode(int)}. */
    @Deprecated(forRemoval = true)
    public OcrOptions withOcrEngineMode(int ocrEngineMode) {
        return ocrEngineMode(ocrEngineMode);
    }

    /** Was {@link #grayscale(boolean)}. */
    @Deprecated(forRemoval = true)
    public OcrOptions withGrayscale(boolean grayscale) {
        return grayscale(grayscale);
    }

    /** Was {@link #upscale(double)}. */
    @Deprecated(forRemoval = true)
    public OcrOptions withUpscale(double upscale) {
        return upscale(upscale);
    }

    /** Was {@link #binarize(BinarizeMode)}. */
    @Deprecated(forRemoval = true)
    public OcrOptions withBinarize(BinarizeMode binarize) {
        return binarize(binarize);
    }

    /** Was {@link #invert(boolean)}. */
    @Deprecated(forRemoval = true)
    public OcrOptions withInvert(boolean invert) {
        return invert(invert);
    }

    /** Was {@link #charWhitelist(String)}. */
    @Deprecated(forRemoval = true)
    public OcrOptions withCharWhitelist(String charWhitelist) {
        return charWhitelist(charWhitelist);
    }

    /** Was {@link #level(TextResult.Level)}. */
    @Deprecated(forRemoval = true)
    public OcrOptions withLevel(TextResult.Level level) {
        return level(level);
    }
}
