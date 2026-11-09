package com.botmaker.sdk.plugin.map;

/** How 🗺 Screens lays its map out: the Layout ▾ picker's six modes (spec 2026-11-09 goal 5). */
enum LayoutMode {

    /** Left to right by depth from the entrance: ELK's layered algorithm. */
    LAYERED("layered", "Layered"),
    /** The same, its gaps cut so the map fits. */
    COMPACT("compact", "Compact"),
    /** The entrances' tree from the first screen down, ways back left out of the layout. */
    TREE("tree", "Tree"),
    /** Rings round the screen with the most ways. */
    HUB("hub", "Hub"),
    /** Screens that link pulled together, the rest pushed apart: ELK's stress layout. */
    ORGANIC("organic", "Organic"),
    /** Layered, with screens boxed in the groups their ways make ({@link ScreenClusters}). */
    GROUPS("groups", "Groups");

    private final String id;
    private final String displayName;

    LayoutMode(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    String id() {
        return id;
    }

    String displayName() {
        return displayName;
    }

    /** Whether the mode lays a map out rightward or downward; Hub and Organic give the same places either way. */
    boolean turns() {
        return this != HUB && this != ORGANIC;
    }

    /** The mode of {@code id}; {@link #LAYERED} for none or one no longer known. */
    static LayoutMode fromId(String id) {
        for (LayoutMode mode : values()) if (mode.id.equals(id)) return mode;
        return LAYERED;
    }
}
