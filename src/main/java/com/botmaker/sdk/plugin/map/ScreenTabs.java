package com.botmaker.sdk.plugin.map;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Which screens the Screens window has open as tabs, and where its ◀ ▶ go (2026-10-30, the user's pick: a preview tab
 * plus a jump bar). A screen opened goes into the one preview tab, which the next one opened replaces; a tab is kept
 * once its screen is edited or its tab double-clicked. Inputs never get a tab. Plain state, forgotten with the window;
 * on the FX thread.
 */
final class ScreenTabs {

    /** How many screens the jump bar's <em>recent</em> names. */
    static final int RECENT = 5;

    /** One screen tab: its constant, and whether it is kept (else it is the preview). */
    record Open(String constant, boolean kept) {}

    private final List<Open> open = new ArrayList<>();
    /** The screens looked at, oldest first; {@link #at} is the one in front. */
    private final List<String> visited = new ArrayList<>();
    private int at = -1;

    /** The tabs, left to right. */
    List<Open> tabs() {
        return List.copyOf(open);
    }

    /**
     * Opens {@code constant}: its tab when it has one, else the preview, replacing the screen it showed. With
     * {@code visit}, it is a new step of ◀ ▶ (a step back or forward is not), and the steps ahead of it go.
     */
    void open(String constant, boolean visit) {
        if (find(constant) < 0) {
            int preview = preview();
            if (preview >= 0) open.set(preview, new Open(constant, false));
            else open.add(new Open(constant, false));
        }
        if (!visit || at >= 0 && visited.get(at).equals(constant)) return;
        visited.subList(at + 1, visited.size()).clear();
        visited.add(constant);
        at = visited.size() - 1;
    }

    /** {@code constant}'s tab kept: the next screen opened goes into a tab of its own. */
    void keep(String constant) {
        int i = find(constant);
        if (i >= 0) open.set(i, new Open(constant, true));
    }

    /** Whether {@code constant}'s tab is the preview. */
    boolean isPreview(String constant) {
        int i = find(constant);
        return i >= 0 && !open.get(i).kept();
    }

    /** {@code constant}'s tab closed; ◀ ▶ still reach its screen. */
    void close(String constant) {
        int i = find(constant);
        if (i >= 0) open.remove(i);
    }

    /** Only the screens still in the map: a tab, or a step of ◀ ▶, of one deleted goes. */
    void retain(Set<String> constants) {
        open.removeIf(tab -> !constants.contains(tab.constant()));
        List<String> left = new ArrayList<>();
        int now = -1;
        for (int i = 0; i < visited.size(); i++) {
            String step = visited.get(i);
            // Two steps side by side once the one between them went are one.
            if (constants.contains(step) && (left.isEmpty() || !left.getLast().equals(step))) left.add(step);
            if (i == at) now = left.size() - 1;
        }
        visited.clear();
        visited.addAll(left);
        at = left.isEmpty() ? -1 : Math.max(now, 0);
    }

    boolean canBack() {
        return at > 0;
    }

    boolean canForward() {
        return at >= 0 && at < visited.size() - 1;
    }

    /** One step back through the screens looked at: the screen to open, without a new step. */
    Optional<String> back() {
        return canBack() ? Optional.of(visited.get(--at)) : Optional.empty();
    }

    /** One step forward again. */
    Optional<String> forward() {
        return canForward() ? Optional.of(visited.get(++at)) : Optional.empty();
    }

    /** The last {@link #RECENT} screens looked at, latest first, {@code current} left out. */
    List<String> recent(String current) {
        Set<String> recent = new LinkedHashSet<>();
        for (int i = visited.size() - 1; i >= 0 && recent.size() < RECENT; i--) {
            if (!visited.get(i).equals(current)) recent.add(visited.get(i));
        }
        return List.copyOf(recent);
    }

    private int preview() {
        for (int i = 0; i < open.size(); i++) if (!open.get(i).kept()) return i;
        return -1;
    }

    private int find(String constant) {
        for (int i = 0; i < open.size(); i++) if (open.get(i).constant().equals(constant)) return i;
        return -1;
    }
}
