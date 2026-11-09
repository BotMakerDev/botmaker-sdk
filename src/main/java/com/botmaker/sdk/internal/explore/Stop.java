package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.geometry.Rect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A stop of a run exploring on its own (spec §2): everything Claude needs to fix what the bot does not know.
 *
 * @param id         the run's name for it, {@code <kind>-<n>}
 * @param kind       why it stopped
 * @param screen     the screen the game shows, or null when it shows none of the map
 * @param place      what it is about, so it is avoided once parked and told again when a fix did not take: an edge
 *                   ({@code "Home.shop→Shop"}), a screen's text box ({@code "Home#120,40"}), or a screen
 * @param framePath  the frame saved in the run's folder, or {@code ""} when none was saved
 * @param texts      every line read on the frame, with its box
 * @param elements   the screen tree's elements, as they read; empty without a tree
 * @param facts      the facts' values now, by name
 * @param lastPresses the last ten presses, oldest first
 * @param missing    one line saying what is missing
 * @param at         when, epoch milliseconds
 */
public record Stop(String id, StopKind kind, String screen, String place, String framePath, List<Text> texts,
                   List<String> elements, Map<String, String> facts, List<String> lastPresses, String missing,
                   long at) {

    /** How many presses a stop keeps. */
    public static final int PRESSES = 10;

    /** A line read on the frame and its box. */
    public record Text(String text, Rect box) {
        public Text {
            text = text == null ? "" : text;
        }
    }

    public Stop {
        Objects.requireNonNull(id, "id");
        kind = kind == null ? StopKind.UNKNOWN : kind;
        place = place == null ? "" : place;
        framePath = framePath == null ? "" : framePath;
        texts = texts == null ? List.of() : List.copyOf(texts);
        elements = elements == null ? List.of() : List.copyOf(elements);
        facts = facts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(facts));
        lastPresses = lastPresses == null ? List.of()
                : List.copyOf(lastPresses.subList(Math.max(0, lastPresses.size() - PRESSES), lastPresses.size()));
        missing = missing == null ? "" : missing;
    }

    /** Whether {@code other} is this stop again: the same kind on the same place. */
    public boolean same(Stop other) {
        return other != null && kind == other.kind && place.equals(other.place);
    }
}
