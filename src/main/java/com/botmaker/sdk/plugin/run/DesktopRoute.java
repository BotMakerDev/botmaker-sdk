// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.run;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.plugin.pictures.ResourceMove;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.session.launch.BackgroundLauncher;

import java.util.function.BooleanSupplier;

/**
 * Whether the run's pixels are the user's own desktop, where the layer draws. An emulator's matches and taps
 * are in the device's pixels and a private display session's in that display's, and both cross the wire with
 * no target to say so, the pilot streaming that route as it is. Drawn over the desktop they would land
 * somewhere unrelated, so the layer draws nothing for them.
 */
final class DesktopRoute {

    private DesktopRoute() {}

    /**
     * Answers, while the run lasts, whether its marks belong on the desktop. The project's capture source is
     * read once, at opening; the private session is asked each time, since the bot may start one mid-run.
     */
    static BooleanSupplier of(StudioServices services) {
        try {
            if (CaptureLabels.emulatorName(CaptureValue.shown(services)) != null) return () -> false;
        } catch (RuntimeException unreadable) {
            // the source is mid-save: the desktop, as the pilot assumes
        }
        BackgroundLauncher launcher = BackgroundLauncher.forProject(ResourceMove.resourcesRoot(services.resourcesDir()));
        return () -> {
            try {
                return launcher.session() == null;
            } catch (RuntimeException unknown) {
                return true;
            }
        };
    }
}
