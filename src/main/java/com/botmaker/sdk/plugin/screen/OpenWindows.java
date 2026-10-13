package com.botmaker.sdk.plugin.screen;

import com.botmaker.session.display.GamescopeHost;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.WindowMatch;

import java.util.List;

/**
 * The open windows a person may pick as the game, the one filter the source picker, the overlay's point-here and the
 * assistant's window tools share.
 */
public final class OpenWindows {

    private OpenWindows() {}

    /**
     * {@code windows} without the untitled ones, BotMaker's own (Studio is never the game, 2026-10-13) and a
     * compositor's output window: whatever is inside that is listed under its own name, as an emulator or the
     * session.
     */
    public static List<GenericWindow> pickable(List<GenericWindow> windows) {
        return windows.stream()
                .filter(w -> w != null && w.getTitle() != null && !w.getTitle().isBlank())
                .filter(w -> !WindowMatch.isOwn(w) && !GamescopeHost.isHost(w))
                .toList();
    }
}
