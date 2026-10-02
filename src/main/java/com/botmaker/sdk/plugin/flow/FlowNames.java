package com.botmaker.sdk.plugin.flow;

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
     * Whether {@code s} is a method reference — {@code Collect::body}, or {@code com.mybot.Collect::body}.
     *
     * <p>Purely syntactic, and that is the whole of what this editor is entitled to say about it: it has no
     * classpath for the bot being drawn, so whether the class exists and whether the method has the right
     * shape are javac's to answer, in the user's own file. What this stops is a text that could not be a
     * reference at all going into that file and breaking the build in a way nobody typed.
     *
     * <p>It checks what a person types into the field, never what the file holds: the host reads a body
     * back as whatever the file writes.
     */
    public static boolean isMethodReference(String s) {
        if (s == null) return false;
        int arrow = s.indexOf("::");
        if (arrow <= 0 || arrow + 2 >= s.length()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ':' && c != '.' && !Character.isJavaIdentifierPart(c)) return false;
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
     * itself, always: {@link Arrow#NEXT} for the implicit one, its own name for a declared one. One
     * spelling, so the word on a wire and the word in the bot's Java are visibly the same thing.
     */
    public static String outcomeLabel(String outcome) {
        return outcome == null || outcome.isBlank() ? Arrow.NEXT : outcome;
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
        if (Arrow.NEXT.equals(candidate)) {
            return "Every activity already has a NEXT outcome — it is always there.";
        }
        if (Arrow.DISABLED.equals(candidate)) {
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

    /**
     * Why {@code candidate} can't name an activity given the names already {@code taken}, or null when it can.
     *
     * <p>A name that differs from a taken one only in case is refused here too, since saving refuses it
     * ({@code ActivityFlowDialog.validate}): until 2026-09-28 this let it through, and the flow then sat
     * unsaved with the reason on the status line. {@code taken} holds the <em>other</em> activities, so a
     * card renamed to its own name in another case is allowed.
     */
    public static String activityNameProblem(String candidate, Collection<String> taken) {
        if (candidate == null || candidate.isEmpty()) return "Give the activity a name.";
        if (!isValidIdentifier(candidate)) {
            return "Enter a valid activity name (letters, digits, _; not starting with a digit).";
        }
        if (taken.contains(candidate)) return "Activity '" + candidate + "' already exists.";
        for (String other : taken) {
            if (other.equalsIgnoreCase(candidate)) {
                return "'" + candidate + "' differs from " + other + " only in case — pick another name.";
            }
        }
        return null;
    }

    /**
     * Why {@code candidate} can't name a saved preset, or null when it can. The two built-ins are derived
     * from the canvas and never saved, so a saved preset of either name would sit beside them in the list,
     * spelled the same and meaning something else.
     */
    public static String presetNameProblem(String candidate, Collection<String> builtIns) {
        if (candidate == null || candidate.isBlank()) return "A preset needs a name.";
        for (String builtIn : builtIns) {
            if (builtIn.equalsIgnoreCase(candidate.trim())) {
                return "'" + builtIn + "' is built in — pick another name.";
            }
        }
        return null;
    }

    // What both activity dialogs say about the three things a card carries beyond its name. One copy, because
    // both named a GoHome.run() and a Popups.run() that no project has had since the flow became Java.

    /** The outcomes section's explanation. */
    public static final String OUTCOMES_HINT = "What this activity can report. Its body returns one of the "
            + "Outcomes constants, and each gets an arrow on the canvas. Every activity also has a NEXT "
            + "outcome (Outcome.NEXT), and any outcome you leave without an arrow ends the run.";

    /** The go-home tick's tooltip. */
    public static final String GO_HOME_TIP = "Run the home method handed to Bot.run(…) immediately before "
            + "this activity, so it starts from a known screen.";

    /** The popup tick's tooltip. */
    public static final String POPUP_TIP = "Let the popup handler installed with PopupGuard dismiss popups "
            + "before each vision step of this activity. Turn it off for an activity that works through a "
            + "popup itself — otherwise the guard closes it underneath.";
}
