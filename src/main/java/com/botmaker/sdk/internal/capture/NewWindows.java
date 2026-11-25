// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.session.display.GamescopeHost;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.WindowMatch;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.RunningProbe;
import com.botmaker.shared.launch.SteamIds;
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
 * ({@code RunningProbe.launcherUi}, or {@code launcherClass} for one the launch does not own — Steam's library, a
 * login prompt, Heroic) goes last, since the game is what was launched, even when the launch started the launcher; among the rest a window of the class the target names goes first
 * (a Steam game's {@code steam_app_<appid>}, 2026-11-25), then one the launched process or one of its children owns,
 * then the bigger, a splash being smaller than the game. The first window no launcher owns is the recommended one.
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
     * launcher ({@code launcherUi}, or a launcher's class) owns first, among them one whose owner {@code ofLaunch} accepts, then the bigger. The first a launcher does
     * not own is {@linkplain Candidate#recommended recommended}. Pure but for {@link #pickable}'s system check.
     */
    public static List<Candidate> ranked(Set<Object> before, List<GenericWindow> now, LongPredicate launcherUi,
                                         LongPredicate ofLaunch) {
        return ranked(before, now, launcherUi, ofLaunch, null);
    }

    /**
     * {@link #ranked(Set, List, LongPredicate, LongPredicate)}, a window of {@code gameClass} — the one the target
     * names, {@link #gameClass} — first among those no launcher owns; {@code null} names none.
     */
    public static List<Candidate> ranked(Set<Object> before, List<GenericWindow> now, LongPredicate launcherUi,
                                         LongPredicate ofLaunch, String gameClass) {
        List<Candidate> fresh = new ArrayList<>();
        for (GenericWindow window : pickable(now)) {
            if (before.contains(window.getNativeHandle())) continue;
            WindowIdentity identity = WindowIdentity.of(window);
            if (identity == null) continue;
            // A launch that starts the launcher itself (Steam closed) has the launcher's library in its tree: still last.
            boolean launched = ofLaunch.test(window.getPid());
            // The class says it too: under Wine a launcher's process is Wine's, and Windows may not name a process.
            // Not for a window the launch owns: a target that is the store itself (exe:…\Battle.net.exe) is its game.
            boolean launcher = launcherUi.test(window.getPid())
                    || !launched && RunningProbe.launcherClass(identity.windowClass());
            fresh.add(new Candidate(window, identity, launcher, launched, false));
        }
        fresh.sort(Comparator.comparing(Candidate::launcher)
                .thenComparing((Candidate c) -> !c.identity().windowClass().equalsIgnoreCase(
                        gameClass == null ? "" : gameClass))
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

    /**
     * The window class {@code target}'s game is known to open, or {@code null}: Proton names a Steam game's window
     * {@code steam_app_<appid>}, the appid Steam's launch carries ({@link SteamIds#appId}).
     */
    public static String gameClass(LaunchSpec target) {
        if (target == null || target.kind() != LaunchKind.STEAM) return null;
        String appId = SteamIds.appId(target.token());
        return appId.isEmpty() ? null : "steam_app_" + appId;
    }

    /**
     * The window of {@code gameClass} that was open in {@code before} and still is in {@code now}, or {@code null}: a
     * launch that only brings the running game to the front opens no window, and its game's own class names the one
     * it refocused (2026-11-25). The biggest of several, a settings dialog being smaller than the game. Recommended,
     * so a watch waits on it as on a new one.
     */
    public static Candidate refocused(Set<Object> before, List<GenericWindow> now, String gameClass) {
        if (gameClass == null) return null;
        Candidate biggest = null;
        for (GenericWindow window : pickable(now)) {
            if (!before.contains(window.getNativeHandle()) || window.getWindowClass() == null
                    || !window.getWindowClass().trim().equalsIgnoreCase(gameClass)) {
                continue;
            }
            WindowIdentity identity = WindowIdentity.of(window);
            if (identity != null && (biggest == null || area(window) > area(biggest.window()))) {
                biggest = new Candidate(window, identity, false, false, true);
            }
        }
        return biggest;
    }

    /**
     * The candidates of {@code ranked} the ranking cannot tell apart (2026-11-25): at least two no launcher owns, the
     * recommended one first, each as far up as it by the launch's tree, so only size put it on top. Empty when the
     * recommended one won by its owner or is alone, and when it is of the target's class ({@code gameClass}): every
     * window of that class is the game's, and the biggest is right, as {@link #refocused} takes it.
     */
    public static List<Candidate> doubt(List<Candidate> ranked, String gameClass) {
        Candidate top = recommended(ranked);
        if (top == null || ofClass(top, gameClass)) return List.of();
        List<Candidate> tied = new ArrayList<>();
        for (Candidate candidate : ranked) {
            if (!candidate.launcher() && candidate.ofLaunch() == top.ofLaunch()) tied.add(candidate);
        }
        return tied.size() < 2 ? List.of() : List.copyOf(tied);
    }

    private static boolean ofClass(Candidate candidate, String gameClass) {
        return gameClass != null && candidate.identity().windowClass().equalsIgnoreCase(gameClass);
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
