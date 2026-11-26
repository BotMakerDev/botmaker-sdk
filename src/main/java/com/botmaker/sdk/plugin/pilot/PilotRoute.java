// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.pilot;

import com.botmaker.session.DesktopSession;
import com.botmaker.shared.emulator.EmulatorSurface;

/**
 * <b>Which surface the pilot is streaming and touching</b> — the one question {@link TargetCapture} and
 * {@link PilotInputService} must always answer the same way, since a gesture is only meaningful in the
 * coordinate space of the frame the user actually tapped.
 *
 * <p>This was a nullable {@code DesktopSession} read in both places: session or {@code :0}. An emulator is a
 * third answer that is neither, so the question stopped being a null check and became a closed set — typed
 * here, switched exhaustively there, so a fourth surface has to be decided rather than defaulted.
 *
 * <p>The three differ in exactly the way that matters: {@link Session} and {@link Emulator} are background-safe
 * (a bot-owned {@code :N} pointer, and ADB, which has no host cursor to move at all), while {@link Desktop}
 * mirrors the user's real screen and drives their real cursor.
 */
public sealed interface PilotRoute {

    /** Mirror the user's real {@code :0} desktop and drive its cursor — the fallback, never background-safe. */
    record Desktop() implements PilotRoute {}

    /** Stream and drive a bot-owned nested {@code :N} display. Wins over everything when one is live. */
    record Session(DesktopSession session) implements PilotRoute {}

    /** Stream and drive an Android emulator over ADB — off the user's desktop entirely. */
    record Emulator(EmulatorSurface surface) implements PilotRoute {}

    /**
     * The surface the project names is not there. Nothing is streamed and nothing is driven, and {@code reason}
     * is the sentence the phone is told: the user's own screen never stands in for it.
     */
    record Missing(String reason) implements PilotRoute {}

    /** The one instance of {@link Desktop} worth allocating. */
    PilotRoute DESKTOP = new Desktop();
}
