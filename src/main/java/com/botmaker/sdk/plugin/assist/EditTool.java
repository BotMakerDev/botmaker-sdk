// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

/**
 * A tool an explore worker's queued edit is carried out by ({@link EditWriter}), by its name on the server: the map's
 * edits and the picture cuts, each on its stop's saved frame.
 */
enum EditTool {
    /** An edit of the screen map: {@code edit_map}'s arguments, with its action. */
    EDIT_MAP("edit_map", "Edit the map"),
    /** Pictures cut from the stop's frame: {@code crop_pictures}' arguments, with its boxes. */
    CROP_PICTURES("crop_pictures", "Cut pictures"),
    /** A tool no worker writes by. */
    UNKNOWN("unknown", "Unknown");

    private final String id;
    private final String displayName;

    EditTool(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** The tool's name on the server, as a queued edit records it. */
    String id() {
        return id;
    }

    String displayName() {
        return displayName;
    }

    /** The tool named {@code id}, blank meaning {@link #EDIT_MAP}; {@link #UNKNOWN} for any other. */
    static EditTool fromId(String id) {
        String name = id == null ? "" : id.strip();
        if (name.isEmpty()) return EDIT_MAP;
        for (EditTool tool : values()) {
            if (tool != UNKNOWN && tool.id.equals(name)) return tool;
        }
        return UNKNOWN;
    }
}
