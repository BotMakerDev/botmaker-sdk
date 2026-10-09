package com.botmaker.sdk.plugin.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.flow.Labels;

import java.util.Collection;
import java.util.List;

/**
 * The naming rules the Activity Flow enforces, in one place: what an activity or outcome may be called, why
 * a candidate is refused, and which constant a label becomes.
 *
 * <p>They live here rather than on the dialog because there are three ways to name an activity or an outcome
 * — the side panel, {@link NewActivityDialog} and the return slot's <i>+ New outcome…</i> — and three copies
 * of "is this a legal name" do not stay identical.
 *
 * <h2>A label is typed; its constant is derived, and the label shown is the constant's</h2>
 *
 * <p>An activity and an outcome are constants of the bot's own {@code Activities} and {@code Outcomes} enums,
 * and the label the canvas shows is the constant's name as words ({@code BAG_FULL} is "Bag full",
 * {@code Labels}). The user types a label; {@link #constantFor} is the one mechanical step from it to the
 * constant's name. So the rules are about the <em>constant</em>: a label that yields none is refused, and two
 * labels that yield the same one are one name twice — "Bag full" and "bag-full" would both be
 * {@code Outcomes.BAG_FULL}, shown as "Bag full".
 */
public final class FlowNames {

    private FlowNames() {
    }

    /** The class the activity constants live in, for sentences. */
    static final String ACTIVITIES = "Activities";

    /** The class the outcome constants live in, for sentences. */
    static final String OUTCOMES = "Outcomes";

    /**
     * A typed label as it is kept: the words of the constant it makes — "bag-full" is "Bag full", because a
     * card shows its constant's name and the constant is all the bot holds. A label that makes no constant is
     * kept trimmed, with runs of whitespace collapsed to one space, so the refusal can quote it.
     */
    public static String label(String typed) {
        if (typed == null) return "";
        String constant = constantFor(typed);
        return constant == null ? typed.strip().replaceAll("\\s+", " ") : Labels.of(constant);
    }

    /**
     * The constant a label is written as — {@code "Bag full"} to {@code BAG_FULL}, {@code "HandleFullInventory"}
     * to {@code HANDLE_FULL_INVENTORY} — or null when it can be none (no letter, or a digit first).
     *
     * <p>Letters and digits are kept, upper-cased; every run of anything else is one {@code _}, and so is a
     * step from a lower-case letter or digit to an upper-case one. So an identifier label that is already a
     * constant ({@code NOTHING_LEFT}) is its own constant.
     */
    public static String constantFor(String label) {
        if (label == null) return null;
        StringBuilder out = new StringBuilder();
        boolean gap = false;
        char previous = 0;
        for (char c : label.strip().toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                gap = out.length() > 0;
                continue;
            }
            boolean camelStep = Character.isUpperCase(c)
                    && (Character.isLowerCase(previous) || Character.isDigit(previous));
            if (out.length() > 0 && (gap || camelStep)) out.append('_');
            out.append(Character.toUpperCase(c));
            gap = false;
            previous = c;
        }
        if (out.isEmpty() || !Character.isJavaIdentifierStart(out.charAt(0))) return null;
        return out.toString();
    }

    /**
     * The activity a typed label names: the bot's constant {@link #constantFor} makes of it, held by name, and
     * {@link Activity#NONE} for a label that makes none.
     */
    public static Activity activity(String label) {
        return BotConstants.activity(constantFor(label));
    }

    /**
     * The outcome a typed label names, as {@link #activity} does: {@link Outcome#NEXT} for blank, "next" or a
     * label that makes no constant, {@link Outcome#DISABLED} for "disabled".
     */
    public static Outcome outcome(String label) {
        return BotConstants.outcome(constantFor(label));
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
     * How an outcome is written for the user — a port chip, a tooltip, a dialog row: its label, and
     * {@link Arrow#NEXT} for the implicit one.
     */
    public static String outcomeLabel(String outcome) {
        return outcome == null || outcome.isBlank() ? Arrow.NEXT : outcome;
    }

    /**
     * Why {@code candidate} can't be an outcome of the activity called {@code owner}, or null when it can.
     *
     * <p>{@code own} is that activity's outcomes and {@code elsewhere} every other activity's. An outcome is
     * one {@code Outcomes} constant however many activities report it, so adding one another activity already
     * declares is fine — it is the same outcome — but a second spelling of it is not, and neither is
     * <em>renaming</em> onto it ({@code replacing} non-null), which would merge two constants.
     */
    public static String outcomeProblem(List<String> own, Collection<String> elsewhere, String owner,
                                        String candidate, String replacing) {
        if (candidate == null || candidate.isEmpty()) return "Give the outcome a name.";
        String constant = constantFor(candidate);
        if (constant == null) return noConstant(candidate, OUTCOMES);
        if (Arrow.NEXT.equals(constant)) {
            return "Every activity already has a NEXT outcome — it is always there.";
        }
        if (Arrow.DISABLED.equals(constant)) {
            return "DISABLED is the port for this activity being switched off — it is always there, "
                    + "and an activity can't report it because it didn't run.";
        }
        for (String existing : own) {
            if (existing.equals(replacing) || !constant.equals(constantFor(existing))) continue;
            return existing.equals(candidate) ? "'" + candidate + "' is already an outcome of " + owner + "."
                    : sameConstant(candidate, existing, OUTCOMES, constant);
        }
        for (String existing : elsewhere) {
            if (existing.equals(replacing) || !constant.equals(constantFor(existing))) continue;
            if (replacing != null) {
                return "'" + existing + "' is already an outcome (" + OUTCOMES + "." + constant + "), and "
                        + "renaming onto it would merge the two. Remove this one and add '" + existing
                        + "' instead.";
            }
            if (!existing.equals(candidate)) return sameConstant(candidate, existing, OUTCOMES, constant);
        }
        return null;
    }

    /**
     * Why {@code candidate} can't name an activity given the names already {@code taken}, or null when it can.
     * {@code taken} holds the <em>other</em> activities, so a card renamed to its own name in another
     * spelling is allowed.
     */
    public static String activityNameProblem(String candidate, Collection<String> taken) {
        if (candidate == null || candidate.isEmpty()) return "Give the activity a name.";
        String constant = constantFor(candidate);
        if (constant == null) return noConstant(candidate, ACTIVITIES);
        if (taken.contains(candidate)) return "Activity '" + candidate + "' already exists.";
        for (String other : taken) {
            if (constant.equals(constantFor(other))) return sameConstant(candidate, other, ACTIVITIES, constant);
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

    private static String noConstant(String candidate, String holder) {
        return "'" + candidate + "' can't become a constant in " + holder + ".java — start it with a letter.";
    }

    private static String sameConstant(String candidate, String existing, String holder, String constant) {
        return "'" + candidate + "' and '" + existing + "' would both be " + holder + "." + constant
                + " — use one spelling.";
    }

    // What both activity dialogs say about the three things a card carries beyond its name. One copy, because
    // both named a GoHome.run() and a Popups.run() that no project has had since the flow became Java.

    /** The outcomes section's explanation. */
    public static final String OUTCOMES_HINT = "What this activity can report. Its body returns one of the "
            + "Outcomes constants, and each gets an arrow on the canvas. An outcome is one constant however "
            + "many activities report it, so renaming one renames it everywhere. Every activity also has a "
            + "NEXT outcome (Outcome.NEXT), and any outcome you leave without an arrow ends the run.";

    /** The go-home tick's tooltip. */
    public static final String GO_HOME_TIP = "Run the home method handed to Bot.run(…) immediately before "
            + "this activity, so it starts from a known screen.";

    /** The popup tick's tooltip. */
    public static final String POPUP_TIP = "Let the popup handler installed with PopupGuard dismiss popups "
            + "before each vision step of this activity. Turn it off for an activity that works through a "
            + "popup itself — otherwise the guard closes it underneath.";
}
