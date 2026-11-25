// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.source;

import com.botmaker.sdk.internal.capture.NewWindows;
import com.botmaker.sdk.internal.capture.NewWindows.Candidate;
import com.botmaker.shared.capture.GenericWindow;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Which windows a launch opened, best guess first: what ▶ Launch and pick offers while the game comes up. The ranking
 * is {@link NewWindows}', which a run's own watch of its launch shares; the picker knows no launch process, so no
 * window counts as the launch's own.
 */
final class WindowWatch {

    private WindowWatch() {}

    /** The handles of {@code windows}, what a later list is compared with. */
    static Set<Object> handles(List<GenericWindow> windows) {
        return NewWindows.handles(windows);
    }

    /** {@link #ranked(Set, List, LongPredicate, String)} with no game class known. */
    static List<Candidate> ranked(Set<Object> before, List<GenericWindow> now, LongPredicate launcherUi) {
        return ranked(before, now, launcherUi, null);
    }

    /** {@link NewWindows#ranked} with no window of the launch's own process tree. */
    static List<Candidate> ranked(Set<Object> before, List<GenericWindow> now, LongPredicate launcherUi,
                                  String gameClass) {
        return NewWindows.ranked(before, now, launcherUi, pid -> false, gameClass);
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
}
