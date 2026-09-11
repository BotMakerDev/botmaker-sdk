package com.botmaker.sdk.internal.plugin.flow;

import com.botmaker.sdk.authoring.FlowEdgeModel;

import java.util.Collection;
import java.util.List;

/**
 * The naming rules the Activity Flow enforces, in one place: what makes a valid activity or outcome name, why
 * a candidate is rejected, and how an outcome is written for a user.
 *
 * <p>They live here rather than on the dialog because there are two ways to name an activity — the side
 * panel's rename field and {@link NewActivityDialog} — and two ways to name an outcome. Two copies of "is
 * this a legal name" do not stay identical, and the failure is silent: the lenient copy admits a name that
 * only breaks later, when the generator writes it into Java.
 */
public final class FlowNames {

    private FlowNames() {
    }

    /** Whether {@code s} can appear as-is in generated Java (a class name, a field, an enum constant). */
    public static boolean isValidIdentifier(String s) {
        if (s == null || s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }

    /**
     * An outcome name in the shape Java wants: trimmed, upper-cased, with runs of spaces, dots and dashes
     * collapsed to {@code _}. "bag full" becomes {@code BAG_FULL} rather than being rejected — the user is
     * naming a result, not writing an enum constant, and the one mechanical step between the two is ours to
     * take. {@link #isValidIdentifier} still guards what this can't fix (a leading digit, punctuation).
     */
    public static String normalizeOutcome(String typed) {
        if (typed == null) return "";
        String cleaned = typed.trim().replaceAll("[\\s.\\-]+", "_");
        return cleaned.toUpperCase();
    }

    /**
     * How an outcome is written for the user — a port chip, a tooltip, a dialog row. It is the constant
     * itself, always: {@link FlowEdgeModel#NEXT_OUTCOME} for the implicit one, its own name for a declared
     * one.
     *
     * <p>The implicit outcome used to read {@code "then"} on a wire and {@code "then (NEXT)"} in the forms,
     * while the return block's picker showed the bare constant — one outcome with three spellings, so the
     * user had to work out that the word on the wire and the constant in their Java were the same thing.
     * There is one name now, and it is the one that appears in the generated source. It is here rather than
     * on {@link FlowEdgeModel} because it is a sentence for a screen, and that record is read by things with
     * no screen.
     */
    public static String outcomeLabel(String outcome) {
        return outcome == null || outcome.isBlank() ? FlowEdgeModel.NEXT_OUTCOME : outcome;
    }

    /**
     * Why {@code candidate} can't be an outcome of the activity called {@code owner}, or null when it can.
     * {@code replacing} is the outcome being renamed, so a rename to its own name isn't a duplicate.
     */
    public static String outcomeProblem(List<String> outcomes, String owner, String candidate, String replacing) {
        if (candidate.isEmpty()) return "Give the outcome a name.";
        if (!isValidIdentifier(candidate)) {
            return "'" + candidate + "' isn't a valid name — it becomes an enum constant in Java.";
        }
        if (FlowEdgeModel.NEXT_OUTCOME.equals(candidate)) {
            return "Every activity already has a NEXT outcome — it is always there.";
        }
        if (FlowEdgeModel.DISABLED_OUTCOME.equals(candidate)) {
            return "DISABLED is the port for this activity being switched off — it is always there, "
                    + "and an activity can't report it because it didn't run.";
        }
        for (String existing : outcomes) {
            if (existing.equals(candidate) && !existing.equals(replacing)) {
                return "'" + candidate + "' is already an outcome of " + owner + ".";
            }
        }
        return null;
    }

    /** Why {@code candidate} can't name an activity given the names already {@code taken}, or null when it can. */
    public static String activityNameProblem(String candidate, Collection<String> taken) {
        if (candidate == null || candidate.isEmpty()) return "Give the activity a name.";
        if (!isValidIdentifier(candidate)) {
            return "Enter a valid activity name (letters, digits, _; not starting with a digit).";
        }
        if (taken.contains(candidate)) return "Activity '" + candidate + "' already exists.";
        return null;
    }
}
