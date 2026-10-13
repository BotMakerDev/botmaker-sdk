package com.botmaker.sdk.plugin.screen;

import com.botmaker.session.display.GamescopeHost;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.WindowMatch;
import com.botmaker.shared.platform.Os;

import java.util.List;

/**
 * The open windows a person may pick as the game, the one filter the source picker, the overlay's point-here and the
 * assistant's window tools share.
 */
public final class OpenWindows {

    private OpenWindows() {}

    /**
     * {@code windows} without the untitled ones, BotMaker's own (Studio is never the game, 2026-10-13) and a
     * compositor's output window — whatever is inside that is listed under its own name, as an emulator or the
     * session — the most recently opened first: the game just launched is the one being picked.
     *
     * <p>X11 lists windows oldest first ({@code _NET_CLIENT_LIST}, in the order they were mapped), so they are
     * turned round there; Windows and macOS list them front to back already.
     */
    public static List<GenericWindow> pickable(List<GenericWindow> windows) {
        List<GenericWindow> kept = windows.stream()
                .filter(w -> w != null && w.getTitle() != null && !w.getTitle().isBlank())
                .filter(w -> !WindowMatch.isOwn(w) && !GamescopeHost.isHost(w))
                .toList();
        return Os.current() == Os.LINUX ? kept.reversed() : kept;
    }
}
