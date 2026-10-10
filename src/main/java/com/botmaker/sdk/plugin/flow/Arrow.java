package com.botmaker.sdk.plugin.flow;

import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.flow.Labels;

/**
 * One arrow on the canvas, between two cards by label: what the canvas draws, moves and undoes, and what a
 * save turns into a {@link Flow.Edge} on one of the activity's outcomes — or, for {@link #DISABLED}, the step's
 * {@code .whenOff(…)}.
 *
 * <p>Labels and not constants on purpose. A rename on the canvas is undoable until the save, and a
 * snapshot of the arrows has to survive one: kept as labels, the canvas renames an arrow by rewriting two
 * strings. The constants are the save's to resolve, by binding, once.
 *
 * @param outcome the outcome's label, as {@link FlowNames#label} keeps it — "next" is {@link #NEXT} — or
 *                {@link #DISABLED} for the switched-off arrow
 */
public record Arrow(String from, String to, String outcome) {

    /**
     * The outcome Studio seeds every new activity's enum with, as the canvas labels it. An ordinary outcome
     * since 2026-10-10: renamed or removed like any other, once the activity has another.
     */
    public static final String NEXT = Labels.of("NEXT");

    /** The switched-off port: where the flow goes when the activity is off or has no body. */
    public static final String DISABLED = "DISABLED";

    public Arrow {
        from = from == null ? "" : from;
        to = to == null ? "" : to;
        // "Next", "NEXT" and "next" are one constant, so one port.
        outcome = outcome == null ? "" : DISABLED.equals(outcome) ? outcome : FlowNames.label(outcome);
    }

    /** The arrow a stored edge is drawn as. */
    public static Arrow of(Flow.Edge edge) {
        return new Arrow(edge.from().label(), edge.to().label(), edge.on() == null ? "" : edge.on().label());
    }

    /** The switched-off arrow a stored step is drawn with, or {@code null} when it has none. */
    public static Arrow off(Flow.Step step) {
        return step.whenOff().isNone() ? null : new Arrow(step.label(), step.whenOff().label(), DISABLED);
    }

    /** Whether this is the "switched off, go here instead" arrow. */
    public boolean isDisabled() {
        return DISABLED.equals(outcome);
    }

    /** The edge this arrow is saved as: its outcome as {@code from}'s constant. Not for {@link #isDisabled}. */
    public Flow.Edge toEdge() {
        return new Flow.Edge(FlowNames.outcome(from, outcome), FlowNames.activity(to));
    }
}
