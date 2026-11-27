// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.plugin.api.params.Claims;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.launch.GameWindows;
import com.botmaker.shared.launch.WindowIdentity;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One window per run on this desktop (spec {@code 2026-11-26-run-places-design.md}, phase 6): several runs of one game
 * share this desktop, each in its own window. The run claims {@code window:<pc>/<id>} for the window it picks, and a
 * window another run claimed is no pick, so the next one the identity names is tried.
 *
 * <p>A pick is kept while its window stays open, so the claim is asked once, not on every capture. Only this desktop's
 * windows are claimed: a private display's or a game VM's are the run's own, and their ids repeat across displays.
 */
final class WindowClaims {

    private WindowClaims() {}

    private static volatile Predicate<String> claims = Claims::claim;
    private static volatile Consumer<String> releases = Claims::release;

    /** The id of the window each identity picked, while it stays open. */
    private static final Map<WindowIdentity, String> PICKED = new ConcurrentHashMap<>();

    /** The key of this desktop's window {@code id}, {@code window:<pc>/<id>}. */
    static String key(String id) {
        return "window:" + Place.desktop().pc() + "/" + id;
    }

    /**
     * The window of {@code windows} that {@code game} names and this run holds: the one picked before while it is open,
     * else the first another run has not claimed, claimed now. Empty when every window it names is another run's.
     */
    static Optional<GenericWindow> pick(WindowIdentity game, List<GenericWindow> windows) {
        String kept = PICKED.get(game);
        if (kept != null) {
            Optional<GenericWindow> same = GameWindows.named(game,
                    windows.stream().filter(w -> w != null && w.id().filter(kept::equals).isPresent()).toList());
            if (same.isPresent()) return same;
            // Its window closed: freed now, since X11 reuses ids and another run's new window may get this one.
            PICKED.remove(game, kept);
            releases.accept(key(kept));
        }
        Set<String> taken = new HashSet<>();
        while (true) {
            Optional<GenericWindow> hit = GameWindows.named(game, windows, taken);
            Optional<String> id = hit.flatMap(GenericWindow::id);
            if (id.isEmpty()) return hit;   // none left, or a window with no id: nothing to claim it by
            if (claims.test(key(id.get()))) {
                PICKED.put(game, id.get());
                return hit;
            }
            taken.add(id.get());
        }
    }

    /** Claims through {@code claimed} and frees through {@code released}, and forgets every pick; for tests. */
    static void claimWithForTesting(Predicate<String> claimed, Consumer<String> released) {
        claims = claimed;
        releases = released;
        PICKED.clear();
    }

    /** Back to the run's claims, no pick remembered. */
    static void resetForTesting() {
        claimWithForTesting(Claims::claim, Claims::release);
    }
}
