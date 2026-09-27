package com.botmaker.sdk.plugin.pictures;

import java.util.List;

/**
 * The picture chooser's decisions, apart from its window: which pictures it offers, and which of the ones a
 * capture just saved it selects. A single-picture slot takes the last one saved — the picture the user was
 * looking at when they closed the tool; a group takes all of them.
 */
public final class ChooserSelection {

    private ChooserSelection() {}

    /** Whether {@code name} may be picked; {@code allowed == null} means every picture may. */
    public static boolean pickable(String name, List<String> allowed) {
        return allowed == null || allowed.contains(name);
    }

    /** The names to select once Capture Templates closes having saved {@code saved}, in that order. */
    public static List<String> afterCapture(List<String> saved, boolean multi, List<String> allowed) {
        List<String> usable = saved.stream().filter(name -> pickable(name, allowed)).toList();
        if (multi || usable.isEmpty()) return usable;
        return List.of(usable.get(usable.size() - 1));
    }
}
