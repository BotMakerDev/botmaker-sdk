package com.botmaker.sdk.api.flow;

/**
 * One activity of the bot's flow, by name — a constant of your bot's {@code Activities} class (2026-10-02),
 * the file BotMaker keeps beside {@code Sdk.java}:
 *
 * <pre>{@code
 * @Managed("activities")
 * public final class Activities {
 *     public static final Activity COLLECT = Activity.named("Collect");
 * }
 *
 * ActivitySwitch.disable(Activities.COLLECT);
 * }</pre>
 *
 * <p>The flow's steps, wires, presets and start all name the constant, so renaming a card on the Activity Flow
 * canvas renames the constant and every use of it by binding, and a typo is a compile error. An activity used to
 * be its label, spelled again at every wire and every {@code disable("…")}.
 *
 * <p>The label is what the canvas draws on the card and what the run's trace prints. Two activities are equal
 * when their labels are, compared exactly, because the label is the value a constant holds.
 */
public final class Activity {

    /** No activity: the start of a flow with nothing in it. */
    public static final Activity NONE = new Activity("");

    private final String label;

    private Activity(String label) {
        this.label = label;
    }

    /** The activity with this label — what an {@code Activities} constant is initialised with. */
    public static Activity named(String label) {
        return label == null || label.isEmpty() ? NONE : new Activity(label);
    }

    /** What the canvas draws on the card and the trace prints. */
    public String label() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Activity other && label.equals(other.label);
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
