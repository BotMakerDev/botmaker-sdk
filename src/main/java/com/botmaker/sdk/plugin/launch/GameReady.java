package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.Readiness;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.launch.GameReadiness;
import com.botmaker.shared.launch.LaunchSpec;

/**
 * The SDK's answer to "can this bot start now?" (2026-10-25), shown in the bot's Run window: its game's window
 * open, else what its launcher's files say of the game ({@link GameReadiness}). Read from the bot's
 * {@code Sdk.launchTarget} and {@code Sdk.captureSource}, as a run would.
 */
public final class GameReady {

    /** The open project's services, or {@code null} between projects; {@code SdkPlugin} hands them over. */
    private static volatile StudioServices services;

    private GameReady() {}

    public static void bind(StudioServices bound) {
        services = bound;
    }

    public static void unbind() {
        services = null;
    }

    /** The plugin's {@link Readiness.Check}: blocking, a window listing and a launcher's files at most. */
    public static Readiness now() {
        StudioServices open = services;
        if (open == null) return null;
        LaunchSpec spec = LaunchTargetValue.spec(open);
        CaptureSource capture = CaptureValue.current(open);
        boolean windowKnown = capture != null && capture.hasWindowIdentity();
        boolean windowOpen = windowKnown && capture.isPresent();
        return of(spec, windowOpen ? null : spec == null ? null : GameReadiness.of(spec), windowKnown, windowOpen);
    }

    /**
     * The answer, pure: {@code game} is what the launcher's files say of {@code spec}'s game (unread, so
     * {@code null}, when the window is open or nothing launches). {@code null} when there is nothing to say: no
     * game to launch and no window to look for.
     */
    static Readiness of(LaunchSpec spec, GameReadiness game, boolean windowKnown, boolean windowOpen) {
        if (windowOpen) return Readiness.ready("game window found");
        if (spec == null || game == null) {
            return windowKnown ? Readiness.blocked("the game's window is not open: start the game first") : null;
        }
        return switch (game.state()) {
            case READY, UNKNOWN -> Readiness.ready(spec.describe() + " starts with the bot");
            case UPDATE_PENDING, UPDATING -> Readiness.waiting(game.reason());
            case SIGNED_OUT, NOT_INSTALLED -> Readiness.blocked(game.reason());
        };
    }
}
