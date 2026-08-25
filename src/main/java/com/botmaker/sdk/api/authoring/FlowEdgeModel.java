package com.botmaker.sdk.api.authoring;

import com.botmaker.sdk.api.meta.Since;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One wire in the flow: "when {@code from} finishes reporting {@code outcome}, {@code to} runs next".
 *
 * <p>An activity may have one wire per outcome, so the flow branches; several wires may arrive at the same
 * node, and a wire may lead back to an earlier activity to loop. The pair that must be unique is
 * {@code (from, outcome)} — one outcome cannot lead to two places. The generator relies on that and does not
 * re-check it; an editor is where a second wire is refused, while the user is drawing it.
 *
 * @param from    source activity name
 * @param to      target activity name — the one that runs next
 * @param outcome the source outcome this wire is for; blank ⇒ {@link #NEXT_OUTCOME}
 */
@Since("1.2.0")
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowEdgeModel(String from, String to, String outcome) {

    /**
     * The outcome every activity has whether it declares one or not — "nothing special to report, carry on".
     * Emitted as the first constant of each activity's {@code Outcome} enum, and what a generated stub
     * returns, so a flow drawn without ever thinking about outcomes behaves like a plain linear one.
     *
     * <p>Stored as a <b>blank string</b> on the edge, never as the literal name: the constant was called
     * {@code DEFAULT} before it was called {@code NEXT}, and blank-means-implicit meant that rename cost no
     * migration at all. Keep it that way if it is ever renamed again.
     */
    public static final String NEXT_OUTCOME = "NEXT";

    public FlowEdgeModel {
        if (from == null) from = "";
        if (to == null) to = "";
        if (outcome == null) outcome = "";
    }

    /** The implicit-outcome wire — how every edge behaved before outcomes existed. */
    public FlowEdgeModel(String from, String to) {
        this(from, to, "");
    }

    /** The outcome constant this wire routes, resolving blank to {@link #NEXT_OUTCOME}. */
    @JsonIgnore
    public String outcomeOrNext() {
        return outcome.isBlank() ? NEXT_OUTCOME : outcome;
    }

    /** True when this is the plain "finished, carry on" wire rather than one for a named outcome. */
    @JsonIgnore
    public boolean isNext() {
        return outcome.isBlank() || NEXT_OUTCOME.equals(outcome);
    }
}
