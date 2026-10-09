package com.botmaker.sdk.api.flow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where each card sits on the Activity Flow canvas, as a value (2026-09-27).
 *
 * <p>It is written in {@code plugins/sdk/Sdk.java} beside the flow, and the flow editor rewrites the one
 * expression the {@code @SdkValue(FLOW_LAYOUT)} method returns:
 *
 * <pre>{@code
 * @SdkValue(SdkValue.Id.FLOW_LAYOUT)
 * public static FlowLayout flowLayout() {
 *     return FlowLayout.of(Map.ofEntries(
 *             Map.entry("Collect", FlowLayout.at(40, 120)),
 *             Map.entry("Rest", FlowLayout.at(320, 80))), true);
 * }
 * }</pre>
 *
 * <p><b>Nothing at runtime reads it.</b> A bot runs the same wherever its cards are drawn; the value is in the
 * bot's Java so that a clone opens on the canvas its author laid out, with no second file beside the source to
 * keep, ignore or lose. A method with no layout, or a project with no such method, opens on an arranged canvas.
 *
 * <p>Positions are keyed by <b>activity name</b>, the name the canvas draws and the edges route on, so the
 * editor writes the flow and its layout together whenever a card is renamed.
 *
 * @param spots           each card's place, keyed by activity name; empty means "lay it out for me"
 * @param goHomeByDefault whether a newly added card starts with its "go home first" tick on
 */
public record FlowLayout(Map<String, Spot> spots, boolean goHomeByDefault) {

    /** Nothing placed, and new cards go home first: what a flow nobody has laid out reads as. */
    public static final FlowLayout NONE = new FlowLayout(Map.of(), true);

    /** Keeps the order the cards were given in, so the Java the editor writes does not reshuffle on every save. */
    public FlowLayout {
        spots = spots == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(spots));
    }

    /** One card's place on the canvas, in unscaled canvas pixels. */
    public record Spot(int x, int y) {
    }

    /** The layout {@code Sdk.flowLayout()} returns. */
    public static FlowLayout of(Map<String, Spot> spots, boolean goHomeByDefault) {
        return new FlowLayout(spots, goHomeByDefault);
    }

    /** A card's place. */
    public static Spot at(int x, int y) {
        return new Spot(x, y);
    }

    /** Where {@code activity}'s card was left, or null. */
    public Spot spot(String activity) {
        return spots.get(activity);
    }
}
