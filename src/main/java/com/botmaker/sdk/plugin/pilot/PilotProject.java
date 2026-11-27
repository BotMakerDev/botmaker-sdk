// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.pilot;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.plugin.settings.SettingsValue;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.sdk.plugin.screen.CaptureValue;

import java.nio.file.Path;

/**
 * What the pilot needs to know about the project it is serving, read from that project's own source.
 *
 * <p>This is the seam the move out of the editor turned on. The pilot used to hold a
 * {@code ProjectSettingsService} — the editor's live settings object — and ask it for the default capture
 * target and the reference resolution. A plugin has neither that class nor any way to be handed one, and the
 * contract deliberately does not grow a service for it: <b>the host is only the only possible source of
 * which project is open</b>, and the answers themselves are in the project.
 *
 * <h2>It reads the bot's own Java</h2>
 *
 * <p>The project's capture source is the parameter {@code Sdk.captureSource} — the
 * {@code @SdkValue(SdkValue.Id.CAPTURE)} value — so this asks
 * {@link com.botmaker.plugin.api.source.PluginValues} for it, as a value the host read. One author, and it is
 * the one the user can see in their own editor.
 *
 * <p><b>Read on demand, never cached.</b> The user changes the source in another window while the pilot is
 * streaming, and a cache is how the pilot ends up pointing at the previous one.
 *
 * <p>Every answer is best-effort, because the pilot is a live stream: a project mid-save, a hand-edited
 * expression or a directory that has gone away all yield "nothing configured", which every caller already
 * handles.
 */
public final class PilotProject {

    private final StudioServices services;

    public PilotProject(StudioServices services) {
        this.services = services;
    }

    /** The project's resources directory, or {@code null} when the pilot is serving nothing. */
    public Path resourcesDir() {
        return services == null ? null : services.resourcesDir();
    }

    /**
     * The project's capture source, or {@code null} when there is nothing to show yet: a desktop game whose window
     * has not been picked, a website read only in a run's own browser, or a source a run would stop on. The pilot
     * then streams nothing rather than the whole desktop ({@link CaptureValue#shown}).
     */
    public CaptureSource defaultSource() {
        return CaptureValue.shown(services);
    }

    /** What this machine launches for the bot, or {@code null} when none is set. */
    public String launchTarget() {
        return LaunchTargetValue.played(services);
    }

    /** The bot's settings as its Java says them, {@link BotSettings#DEFAULTS} when it says none or no project. */
    public BotSettings settings() {
        return services == null ? BotSettings.DEFAULTS : SettingsValue.current(services);
    }

    /** The services the pilot was opened with, for a dialog it shows; {@code null} when it serves no project. */
    public StudioServices services() {
        return services;
    }
}
