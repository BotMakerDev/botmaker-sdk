package com.botmaker.sdk.plugin.source;

import com.botmaker.sdk.plugin.screen.OpenWindows;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.launch.WindowIdentity;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Which windows a launch opened, best guess first: what ▶ Launch and pick offers while the game comes up.
 *
 * <p>A window is new when its handle was not open before the launch. Each owner process is asked once: a window of a
 * launcher's own UI ({@code RunningProbe.launcherUi} — Steam's library, a login prompt, Heroic) goes last, since the
 * game is what was launched; among the rest the bigger goes first, a splash being smaller than the game. The first
 * window no launcher owns is the recommended one. One row per window identity: a splash and the game under the same
 * class are one pick.
 */
final class WindowWatch {

    /** One window a launch opened, and the identity a pick of it saves. */
    record Candidate(GenericWindow window, WindowIdentity identity, boolean launcher, boolean recommended) {}

    private WindowWatch() {}

    /** The handles of {@code windows}, what a later list is compared with. */
    static Set<Object> handles(List<GenericWindow> windows) {
        Set<Object> handles = new HashSet<>();
        for (GenericWindow window : windows) if (window != null) handles.add(window.getNativeHandle());
        return handles;
    }

    /**
     * The windows of {@code now} not in {@code before}, pickable and with a class to save, ranked; the first a
     * launcher does not own is {@linkplain Candidate#recommended recommended}. Pure.
     */
    static List<Candidate> ranked(Set<Object> before, List<GenericWindow> now, LongPredicate launcherUi) {
        record Seen(GenericWindow window, WindowIdentity identity, boolean launcher) {}
        List<Seen> fresh = new ArrayList<>();
        for (GenericWindow window : OpenWindows.pickable(now)) {
            if (before.contains(window.getNativeHandle())) continue;
            WindowIdentity identity = WindowIdentity.of(window);
            if (identity == null) continue;
            fresh.add(new Seen(window, identity, launcherUi.test(window.getPid())));
        }
        fresh.sort(Comparator.comparing(Seen::launcher)
                .thenComparing(Comparator.comparingLong((Seen seen) -> area(seen.window())).reversed()));
        Map<String, Seen> once = new LinkedHashMap<>();
        for (Seen seen : fresh) once.putIfAbsent(seen.identity().spec(), seen);
        List<Candidate> ranked = new ArrayList<>();
        boolean recommended = false;
        for (Seen seen : once.values()) {
            boolean first = !recommended && !seen.launcher();
            recommended |= first;
            ranked.add(new Candidate(seen.window(), seen.identity(), seen.launcher(), first));
        }
        return ranked;
    }

    /** Whether two rankings offer the same windows in the same order: nothing to redraw. */
    static boolean same(List<Candidate> a, List<Candidate> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i).window().getNativeHandle(), b.get(i).window().getNativeHandle())
                    || !a.get(i).window().getTitle().equals(b.get(i).window().getTitle())) {
                return false;
            }
        }
        return true;
    }

    private static long area(GenericWindow window) {
        Rectangle rect = window.getRect();
        return rect == null ? 0 : (long) Math.max(0, rect.width) * Math.max(0, rect.height);
    }
}
