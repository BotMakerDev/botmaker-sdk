package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.launch.LaunchSpec;

/**
 * What the toolbar's game button says and shows: the name and cover of what this computer launches for the open
 * bot, or an invitation to pick one.
 *
 * <p>A toolbar label is a supplier with no arguments, called on the JavaFX thread at every refresh, so it may
 * only read a field. The open project's services are that field: {@code SdkPlugin} hands them over when a
 * project is bound and takes them back when it closes. The name and cover come from {@link RecentTargets},
 * which the game dialog writes with every pick, so nothing here scans a library. Links no JavaFX: a headless
 * host builds the toolbar list too.
 */
public final class GameButton {

    private static final String GLYPH = "🎮 ";

    /** The open project's services, or {@code null} between projects. */
    private static volatile StudioServices services;

    private GameButton() {}

    /** The project {@code bound} is open now. */
    public static void bind(StudioServices bound) {
        services = bound;
    }

    /** No project is open. */
    public static void unbind() {
        services = null;
    }

    /** The button's text: {@code 🎮 Hades II}, or {@code 🎮 Choose game} when nothing is picked. */
    public static String label() {
        StudioServices open = services;
        String spec = LaunchTargetValue.current(open);
        return GLYPH + (spec == null ? "Choose game" : nameOf(open, spec));
    }

    /** The button's picture: the current target's cover as a {@code file:} URI, or {@code null}. */
    public static String icon() {
        StudioServices open = services;
        return RecentTargets.find(open, LaunchTargetValue.current(open))
                .map(RecentTargets.Recent::artwork)
                .map(art -> art.toUri().toString())
                .orElse(null);
    }

    /** What {@code spec} is called: the name it was picked under, else the spec's short label. */
    static String nameOf(StudioServices services, String spec) {
        return RecentTargets.find(services, spec).map(RecentTargets.Recent::name)
                .orElseGet(() -> LaunchSpec.shortLabel(spec, null));
    }
}
