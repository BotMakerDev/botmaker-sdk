// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.session.display.GamescopeHost;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.WindowMatch;
import com.botmaker.shared.launch.WindowIdentity;
import com.botmaker.shared.platform.Os;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Which windows a launch opened, best guess first: what ▶ Launch and pick offers while the game comes up, and what a
 * run with an automatic capture reads once its game's window is up (2026-11-25).
 *
 * <p>A window is new when its handle was not open before the launch. A window of a launcher's own UI
 * ({@code RunningProbe.launcherUi} — Steam's library, a login prompt, Heroic) goes last, since the game is what was
 * launched, even when the launch started the launcher; among the rest a window the launched process or one of its
 * children owns goes first, then the bigger, a splash being smaller than the game. The first window no launcher owns is the recommended one.
 * One row per window identity: a splash and the game under the same class are one pick.
 */
public final class NewWindows {

    /** One window a launch opened, and the identity a pick of it saves. */
    public record Candidate(GenericWindow window, WindowIdentity identity, boolean launcher, boolean ofLaunch,
                            boolean recommended) {}

    private NewWindows() {}

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

    /** The handles of {@code windows}, what a later list is compared with. */
    public static Set<Object> handles(List<GenericWindow> windows) {
        Set<Object> handles = new HashSet<>();
        for (GenericWindow window : windows) if (window != null) handles.add(window.getNativeHandle());
        return handles;
    }

    /**
     * The windows of {@code now} not in {@code before}, pickable and with a class to save, ranked: those no
     * {@code launcherUi} owns first, among them one whose owner {@code ofLaunch} accepts, then the bigger. The first a launcher does
     * not own is {@linkplain Candidate#recommended recommended}. Pure but for {@link #pickable}'s system check.
     */
    public static List<Candidate> ranked(Set<Object> before, List<GenericWindow> now, LongPredicate launcherUi,
                                         LongPredicate ofLaunch) {
        List<Candidate> fresh = new ArrayList<>();
        for (GenericWindow window : pickable(now)) {
            if (before.contains(window.getNativeHandle())) continue;
            WindowIdentity identity = WindowIdentity.of(window);
            if (identity == null) continue;
            // A launch that starts the launcher itself (Steam closed) has the launcher's library in its tree: still last.
            fresh.add(new Candidate(window, identity, launcherUi.test(window.getPid()), ofLaunch.test(window.getPid()),
                    false));
        }
        fresh.sort(Comparator.comparing(Candidate::launcher)
                .thenComparing((Candidate c) -> !c.ofLaunch())
                .thenComparing(Comparator.comparingLong((Candidate c) -> area(c.window())).reversed()));
        Map<String, Candidate> once = new LinkedHashMap<>();
        for (Candidate candidate : fresh) once.putIfAbsent(candidate.identity().spec(), candidate);
        List<Candidate> ranked = new ArrayList<>();
        boolean recommended = false;
        for (Candidate c : once.values()) {
            boolean first = !recommended && !c.launcher();
            recommended |= first;
            ranked.add(new Candidate(c.window(), c.identity(), c.launcher(), c.ofLaunch(), first));
        }
        return ranked;
    }

    /** The recommended window of {@code ranked}, or {@code null} when only launchers opened one. */
    public static Candidate recommended(List<Candidate> ranked) {
        for (Candidate candidate : ranked) if (candidate.recommended()) return candidate;
        return null;
    }

    private static long area(GenericWindow window) {
        Rectangle rect = window.getRect();
        return rect == null ? 0 : (long) Math.max(0, rect.width) * Math.max(0, rect.height);
    }
}
