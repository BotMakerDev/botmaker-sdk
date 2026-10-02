package com.botmaker.sdk.plugin.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;

/**
 * One arrow on the canvas, between two cards by label: what the canvas draws, moves and undoes, and what a
 * save turns into a {@link Flow.Edge} between the bot's {@code Activities} constants.
 *
 * <p>Labels and not constants on purpose (2026-10-02). A rename on the canvas is undoable until the save, and a
 * snapshot of the arrows has to survive one: kept as labels, the canvas renames an arrow by rewriting two
 * strings. The constants are the save's to resolve, by binding, once.
 *
 * @param outcome {@code ""} for the plain output arrow, {@link #DISABLED} for the switched-off one, else the
 *                outcome's label
 */
public record Arrow(String from, String to, String outcome) {

    /** The plain output port, as the canvas labels it. An arrow stores it blank. */
    public static final String NEXT = Outcome.NEXT.label();

    /** The switched-off port: where the flow goes when the activity is off or has no body. */
    public static final String DISABLED = Outcome.DISABLED.label();

    public Arrow {
        from = from == null ? "" : from;
        to = to == null ? "" : to;
        outcome = outcome == null || outcome.equals(NEXT) ? "" : outcome;
    }

    /** The arrow a stored edge is drawn as. */
    public static Arrow of(Flow.Edge edge) {
        return new Arrow(edge.from().label(), edge.to().label(), edge.outcome().label());
    }

    /** The outcome this arrow routes, with blank read as {@link #NEXT}. */
    public String outcomeOrNext() {
        return outcome.isBlank() ? NEXT : outcome;
    }

    /** Whether this is the "switched off, go here instead" arrow. */
    public boolean isDisabled() {
        return DISABLED.equals(outcome);
    }

    /** The edge this arrow is saved as: each label as the value its constant holds. */
    public Flow.Edge toEdge() {
        return Flow.edge(Activity.named(from), Activity.named(to), Outcome.named(outcome));
    }
}
