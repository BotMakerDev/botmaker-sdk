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
 * <p>An activity is an enum nested in the bot's {@code Activities} class and an outcome a constant of it, and
 * the label the canvas shows is the name as words ({@code BAG_FULL} is "Bag full", {@code DailyReward} "Daily
 * reward", {@code Labels}). The user types a label; {@link #typeFor} and {@link #constantFor} are the one
 * mechanical step from it to the name. So the rules are about the <em>name</em>: a label that yields none is
 * refused, and two labels that yield the same one are one name twice — "Bag full" and "bag-full" would both be
 * {@code BAG_FULL}, shown as "Bag full".
 */
public final class FlowNames {

    private FlowNames() {
    }

    /** The class the activity enums live in, for sentences. */
    static final String ACTIVITIES = "Activities";

    /**
     * Names an activity's enum may not take: the class holding it, and the types its declaration and its
     * neighbours name ({@code enum X implements Outcome}), which a nested enum of that name would hide.
     */
    private static final java.util.Set<String> RESERVED = java.util.Set.of(ACTIVITIES, "Outcome", "Object",
            "String", "Class", "Enum", "Override", "SdkValue");

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
     * The enum an activity label is written as — {@code "Daily reward"} to {@code DailyReward},
     * {@code "DailyReward"} to itself — or null when it can be none (no letter, or a digit first).
     *
     * <p>Each run of letters and digits is a word, its first letter upper-cased; a word typed all in capitals
     * ({@code "BAG"}) is lower-cased after its first letter, and any other keeps its case, so a type name typed
     * as one is its own. {@code Labels.of} goes the other way.
     */
    public static String typeFor(String label) {
        if (label == null) return null;
        StringBuilder out = new StringBuilder();
        for (String word : label.strip().split("[^\\p{L}\\p{N}]+")) {
            if (word.isEmpty()) continue;
            String rest = word.substring(1);
            if (rest.equals(rest.toUpperCase(java.util.Locale.ROOT))) rest = rest.toLowerCase(java.util.Locale.ROOT);
            out.append(Character.toUpperCase(word.charAt(0))).append(rest);
        }
        if (out.isEmpty() || !Character.isJavaIdentifierStart(out.charAt(0))) return null;
        return out.toString();
    }

    /** An activity's label as it is kept: the words of the enum it makes, "daily-reward" as "Daily reward". */
    public static String activityLabel(String typed) {
        if (typed == null) return "";
        String type = typeFor(typed);
        return type == null ? typed.strip().replaceAll("\\s+", " ") : Labels.of(type);
    }

    /**
     * The activity a typed label names: the bot's enum {@link #typeFor} makes of it, held by name, and
     * {@link Activity#NONE} for a label that makes none.
     */
    public static Activity activity(String label) {
        return BotConstants.activity(typeFor(label));
    }

    /**
     * The outcome {@code label} of the activity labelled {@code activity}: its enum's constant {@link #constantFor}
     * makes of the label, held by the two names. Blank is {@link Arrow#NEXT}.
     */
    public static Outcome outcome(String activity, String label) {
        String constant = constantFor(label == null || label.isBlank() ? Arrow.NEXT : label);
        return BotConstants.outcome(typeFor(activity), constant);
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
     * <p>{@code own} is that activity's outcomes: an outcome is a constant of its activity's enum, so another
     * activity's outcome of the same name is another constant, and only a second spelling of one of its own is
     * refused — as is <em>renaming</em> onto one ({@code replacing} non-null), which would merge two.
     */
    public static String outcomeProblem(List<String> own, String owner, String candidate, String replacing) {
        if (candidate == null || candidate.isEmpty()) return "Give the outcome a name.";
        String constant = constantFor(candidate);
        String holder = ACTIVITIES + "." + (typeFor(owner) == null ? owner : typeFor(owner));
        if (constant == null) return noConstant(candidate, holder);
        if (constantFor(Arrow.NEXT).equals(constant) && replacing == null) {
            return "Every activity already has a " + constant + " outcome — it is always there.";
        }
        if (Arrow.DISABLED.equals(constant)) {
            return "DISABLED is the port for this activity being switched off — it is always there, "
                    + "and an activity can't report it because it didn't run.";
        }
        for (String existing : own) {
            if (existing.equals(replacing) || !constant.equals(constantFor(existing))) continue;
            return existing.equals(candidate) ? "'" + candidate + "' is already an outcome of " + owner + "."
                    : sameConstant(candidate, existing, holder, constant);
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
        String type = typeFor(candidate);
        if (type == null) return noConstant(candidate, ACTIVITIES);
        if (RESERVED.contains(type)) {
            return "'" + candidate + "' would be an enum named " + type + " inside " + ACTIVITIES + ".java, hiding the "
                    + "type of that name its code needs — pick another name.";
        }
        if (taken.contains(candidate)) return "Activity '" + candidate + "' already exists.";
        for (String other : taken) {
            if (type.equals(typeFor(other))) return sameConstant(candidate, other, ACTIVITIES, type);
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
        return "'" + candidate + "' can't become a name in " + holder + " — start it with a letter.";
    }

    private static String sameConstant(String candidate, String existing, String holder, String constant) {
        return "'" + candidate + "' and '" + existing + "' would both be " + holder + "." + constant
                + " — use one spelling.";
    }

    // What both activity dialogs say about the three things a card carries beyond its name. One copy, because
    // both named a GoHome.run() and a Popups.run() that no project has had since the flow became Java.

    /** The outcomes section's explanation. */
    public static final String OUTCOMES_HINT = "What this activity can report: the constants of its enum in "
            + "Activities.java. Its body returns one of them, and each gets an arrow on the canvas. Every "
            + "activity starts with NEXT, and any outcome you leave without an arrow ends the run.";

    /** The go-home tick's tooltip. */
    public static final String GO_HOME_TIP = "Run the flow's home method (Flow.home(…)) immediately before "
            + "this activity, so it starts from a known screen.";

    /** The popup tick's tooltip. */
    public static final String POPUP_TIP = "Let the popup handler installed with PopupGuard dismiss popups "
            + "before each vision step of this activity. Turn it off for an activity that works through a "
            + "popup itself — otherwise the guard closes it underneath.";
}
