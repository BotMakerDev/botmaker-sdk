// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A tool an explore worker's queued edit ({@link ExploreEvent.EditQueued}) is carried out by, by its name on the
 * server: the map's edits and the picture cuts, each on its stop's saved frame. The journal reads it too: only a map
 * edit gives the run something new to press, a cut alone draws nothing.
 */
public enum EditTool {
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
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Whether {@code edit}, this tool's arguments as JSON, gives {@code screen} something new once applied: every map
     * edit, and a cut that names {@code screen} as the one its pictures are on (each noted there as an untried input).
     * A cut alone, or onto another screen, adds nothing to press there.
     */
    public boolean changesMap(String edit, String screen) {
        return switch (this) {
            case EDIT_MAP -> true;
            case CROP_PICTURES -> {
                try {
                    JsonNode on = JSON.readTree(edit == null || edit.isBlank() ? "{}" : edit).path("screen");
                    yield screen != null && !screen.isBlank() && on.isTextual() && on.asText().strip().equals(screen);
                } catch (JsonProcessingException unreadable) {
                    yield false;
                }
            }
            case UNKNOWN -> false;
        };
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The tool named {@code id}, blank meaning {@link #EDIT_MAP}; {@link #UNKNOWN} for any other. */
    public static EditTool fromId(String id) {
        String name = id == null ? "" : id.strip();
        if (name.isEmpty()) return EDIT_MAP;
        for (EditTool tool : values()) {
            if (tool != UNKNOWN && tool.id.equals(name)) return tool;
        }
        return UNKNOWN;
    }
}
