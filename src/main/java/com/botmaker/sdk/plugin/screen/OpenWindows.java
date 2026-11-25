// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.screen;

import com.botmaker.sdk.internal.capture.NewWindows;
import com.botmaker.shared.capture.GenericWindow;

import java.util.List;

/**
 * The open windows a person may pick as the game, the one filter the source picker, the overlay's point-here, the
 * assistant's window tools and a run's watch of its launch share ({@link NewWindows#pickable}).
 */
public final class OpenWindows {

    private OpenWindows() {}

    /** {@link NewWindows#pickable}: titled windows, not BotMaker's own nor a compositor's, the newest first. */
    public static List<GenericWindow> pickable(List<GenericWindow> windows) {
        return NewWindows.pickable(windows);
    }
}
