package com.botmaker.sdk.authoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One card on the flow canvas: which activity it is, and where it sits.
 *
 * <p>Position is purely presentational — it restores the visual layout when an editor reopens the flow. What
 * actually runs comes from the wiring and the start node, never from positions, and the generator reads
 * nothing here. It is stored because the file has one owner.
 *
 * <p>There is no terminal card. A run ends at an outcome with no wire leaving it, which is the same thing a
 * Stop card used to say and one fewer concept to draw. An older file may still hold the old {@code @stop}
 * node and edges into it; both fall away on load, because the node names no activity and every walk drops a
 * wire whose endpoint is not a placed activity.
 *
 * @param activity the {@link ActivityModel#name()} this node represents
 * @param x        canvas x of the node's top-left, in unscaled canvas coordinates
 * @param y        canvas y of the node's top-left, in unscaled canvas coordinates
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowNodeModel(String activity, double x, double y) {

    public FlowNodeModel {
        if (activity == null) activity = "";
    }
}
