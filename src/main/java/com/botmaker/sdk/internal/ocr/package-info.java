/**
 * The Tesseract engine, its native loading and the preprocessing applied before it runs.
 *
 * <p><b>Not versioned surface.</b> The split made when this package moved out of {@code botmaker-shared} on
 * 2026-08-26 was exactly the boundary rule: {@code OcrOptions}, {@code OcrLanguage} and {@code TextResult}
 * are names a bot writes and so became {@code com.botmaker.sdk.api.vision}; the engine behind them is
 * something a bot only ever receives results from, and stayed here, uncatalogued by construction.
 */
@Internal("a bot writes OcrOptions and reads a TextResult; the engine between them is plumbing")
package com.botmaker.sdk.internal.ocr;

import com.botmaker.plugin.api.meta.Internal;
