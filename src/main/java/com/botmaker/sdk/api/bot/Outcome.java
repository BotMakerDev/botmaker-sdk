package com.botmaker.sdk.api.bot;


/**
 * What an activity reports when it finishes — {@code Outcomes.BAG_FULL}, {@code Outcomes.WON} — and what the
 * flow drawn in BotMaker Studio routes on.
 *
 * <p><b>An outcome is a constant of your bot's {@code Outcomes} class</b> (2026-10-02), the file BotMaker
 * keeps beside {@code Sdk.java}:
 *
 * <pre>{@code
 * @Managed("outcomes")
 * public final class Outcomes {
 *     public static final Outcome BAG_FULL = Outcome.named("Bag full");
 * }
 *
 * public static Outcome body() {
 *     if (bagFull()) return Outcomes.BAG_FULL;
 *     mineOnce();
 *     return Outcome.NEXT;
 * }
 * }</pre>
 *
 * <p>A body, a wire and the list of outcomes an activity declares all name the same constant, so a typo is a
 * compile error, and renaming an outcome on the Activity Flow canvas renames the constant and every use of it
 * by binding. The outcome used to be a string written in three places that nothing checked against each other.
 *
 * <p>The label is what the canvas draws on the wire and what the run's trace prints. Two outcomes are equal
 * when their labels are, compared exactly, because the label is the value a constant holds.
 */
public final class Outcome {

    /**
     * "Nothing special to report, carry on" — the outcome every activity has whether it declares one or not,
     * and the plain output wire on its card.
     */
    public static final Outcome NEXT = new Outcome("NEXT");

    /**
     * "This activity is switched off, go here instead" — the other outcome every activity has. A body never
     * reports it, since it did not run: the flow takes this wire for an activity switched off or with no body
     * yet. Unwired, it ends the run.
     */
    public static final Outcome DISABLED = new Outcome("DISABLED");

    private final String label;

    private Outcome(String label) {
        this.label = label;
    }

    /**
     * The outcome with this label — what an {@code Outcomes} constant is initialised with.
     *
     * <p>A blank or {@code null} label is {@link #NEXT} rather than an error, for the reason nothing else in a
     * bot's own configuration throws: an activity that answered badly should follow the wire it would have
     * followed with nothing to say, not stop the bot.
     */
    public static Outcome named(String label) {
        if (label == null || label.isBlank() || label.equals(NEXT.label)) return NEXT;
        if (label.equals(DISABLED.label)) return DISABLED;
        return new Outcome(label);
    }

    /** What the canvas draws on the wire and the trace prints. */
    public String label() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Outcome other && label.equals(other.label);
    }

    @Override
    public int hashCode() {
        return label.hashCode();
    }

    @Override
    public String toString() {
        return label;
    }
}
