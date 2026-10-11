package com.botmaker.sdk.api.flow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where each card sits on the Activity Flow canvas, one arrangement per flow, as a value (2026-09-27; per flow
 * 2026-10-11).
 *
 * <p>It is written in {@code plugins/sdk/Sdk.java} beside the flows, and the flow editor rewrites the one
 * expression the {@code @SdkValue(FLOW_LAYOUT)} method returns:
 *
 * <pre>{@code
 * @SdkValue(SdkValue.Id.FLOW_LAYOUT)
 * public static FlowLayout flowLayout() {
 *     return FlowLayout.of(Map.ofEntries(
 *             Map.entry("MAIN", FlowLayout.cards(Map.ofEntries(
 *                     Map.entry("Collect", FlowLayout.at(40, 120)),
 *                     Map.entry("Rest", FlowLayout.at(320, 80)))))), true);
 * }
 * }</pre>
 *
 * <p><b>Nothing at runtime reads it.</b> A bot runs the same wherever its cards are drawn; the value is in the
 * bot's Java so that a clone opens on the canvas its author laid out, with no second file beside the source to
 * keep, ignore or lose. A method with no layout, or a project with no such method, opens on an arranged canvas.
 *
 * <p>Arrangements are keyed by the <b>flow's constant name</b> in {@code Flows}, and each card by its
 * <b>activity name</b>, the name the canvas draws and the edges route on, so the editor writes the flows and
 * their layout together whenever a flow or a card is renamed. Two flows running the same activity may place
 * its card differently.
 *
 * @param flows           each flow's cards, keyed by the flow's constant name; empty means "lay it out for me"
 * @param goHomeByDefault whether a newly added card starts with its "go home first" tick on
 */
public record FlowLayout(Map<String, Cards> flows, boolean goHomeByDefault) {

    /** Nothing placed, and new cards go home first: what a bot nobody has laid out reads as. */
    public static final FlowLayout NONE = new FlowLayout(Map.of(), true);

    /** Keeps the order the flows were given in, so the Java the editor writes does not reshuffle on every save. */
    public FlowLayout {
        flows = flows == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(flows));
    }

    /**
     * One flow's cards, keyed by activity name.
     *
     * @param spots each card's place; empty means "lay it out for me"
     */
    public record Cards(Map<String, Spot> spots) {

        /** No card placed. */
        public static final Cards NONE = new Cards(Map.of());

        /** Keeps the order the cards were given in, as {@link FlowLayout} keeps its flows'. */
        public Cards {
            spots = spots == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(spots));
        }

        /** Where {@code activity}'s card was left, or null. */
        public Spot spot(String activity) {
            return spots.get(activity);
        }
    }

    /** One card's place on the canvas, in unscaled canvas pixels. */
    public record Spot(int x, int y) {
    }

    /** The layout {@code Sdk.flowLayout()} returns. */
    public static FlowLayout of(Map<String, Cards> flows, boolean goHomeByDefault) {
        return new FlowLayout(flows, goHomeByDefault);
    }

    /** One flow's cards. */
    public static Cards cards(Map<String, Spot> spots) {
        return new Cards(spots);
    }

    /** A card's place. */
    public static Spot at(int x, int y) {
        return new Spot(x, y);
    }

    /** The cards of the flow {@code flow} names, or {@link Cards#NONE}. Never null. */
    public Cards cardsOf(String flow) {
        return flows.getOrDefault(flow, Cards.NONE);
    }

    /** This layout with {@code flow}'s cards replaced by {@code cards}, every other flow's kept where it is. */
    public FlowLayout with(String flow, Cards cards) {
        Map<String, Cards> next = new LinkedHashMap<>(flows);
        next.put(flow, cards == null ? Cards.NONE : cards);
        return new FlowLayout(next, goHomeByDefault);
    }

    /** This layout with new cards going home first or not. */
    public FlowLayout goingHomeByDefault(boolean goHome) {
        return new FlowLayout(flows, goHome);
    }
}
