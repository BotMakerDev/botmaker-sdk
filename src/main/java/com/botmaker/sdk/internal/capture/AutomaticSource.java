// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.CaptureSource;

/**
 * {@link CaptureSource#automatic()}: the bot reads what its game's launch target says — an emulator app's device, a
 * website's own browser, the window a desktop game's launch opened — and the whole desktop with no target
 * ({@link AutoCapture}, 2026-11-25). A run sets {@code Source.current()} to that answer, which this then follows.
 */
public final class AutomaticSource extends AmbientSource {

    @Override
    public String toString() {
        return "CaptureSource.automatic()";
    }
}
