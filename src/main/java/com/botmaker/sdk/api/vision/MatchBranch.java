package com.botmaker.sdk.api.vision;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;

import java.util.function.Predicate;

/**
 * A chain of branches over one frame — what runs when a group of templates was found in this combination.
 *
 * {@snippet lang=java :
 * ImageFinder.whileFindAny(POPUPS, found -> {
 *     found.when(m -> m.hasAny(MAIL, GIFT),                    () -> ImageClicker.click(CLAIM))
 *          .when(m -> m.hasAll(CHEST) && !m.hasAny(AD),        () -> ImageClicker.click(CHEST))
 *          .otherwise(                                          () -> Debug.log("nothing to do"));
 * });
 * }
 *
 * <h2>Why this and not a guarded switch</h2>
 *
 * <p>The same branching used to be written as a Java 21 pattern switch —
 * {@code case Matches m when m.hasAny(…) -> { … }} — built for the user by the editor. It worked, and it was
 * the wrong shape for one reason that outweighs how it reads: <b>a switch is a language construct, and an
 * editor cannot compose one out of a catalogue of methods.</b> Everything about it had to be spelled by
 * whoever wrote the editor — the type name, the pattern variable, the guard's method, the mandatory
 * {@code default} — so the host ended up holding this library's vocabulary on its behalf, and no second
 * library could ever have contributed a branching shape of its own.
 *
 * <p>These are ordinary methods. The editor offers them because they are in the palette, renders them
 * because it already renders calls that take a lambda, and edits the predicate because it is an ordinary
 * boolean expression. Three compile-time traps go with the switch: a statement switch over patterns must be
 * exhaustive, an unguarded {@code case Matches m} silently dominates every branch after it, and the pattern
 * variable is a second name for a frame the enclosing lambda has already named.
 *
 * <h2>Two guarantees the chain makes</h2>
 *
 * <p><b>At most one branch runs.</b> Once a predicate has matched, later {@link #when} predicates are not
 * evaluated at all and later actions do not run — the same first-match-wins reading a switch or an
 * {@code if}/{@code else if} chain has. That is why the predicate is a {@link Predicate} rather than a
 * {@code boolean}: an argument would be evaluated before the call, so every branch's test would run even
 * after one had already matched.
 *
 * <p><b>The frame never changes underneath it.</b> Every predicate sees the same {@link Matches}, so two
 * branches asking about the same instant cannot disagree — which is the property {@code Matches} exists for
 * in the first place, extended across a whole chain.
 *
 * <p>{@link #otherwise} is optional. A chain that ends without one simply does nothing when no branch
 * matched, and the returned value may be discarded.
 */
@Palette(category = "vision", categoryLabel = "Vision", order = 96)
@Hidden("a value type: a chain is started by calling when(...) on a Matches, never built from a menu")
public final class MatchBranch {

    private final Matches frame;
    private final boolean settled;

    MatchBranch(Matches frame, boolean settled) {
        this.frame = frame;
        this.settled = settled;
    }

    /**
     * Runs {@code action} when {@code test} passes and no earlier branch has already matched.
     *
     * <p>{@code test} is handed the same frame the chain started from. A {@code null} test or action is
     * skipped rather than thrown on: a half-filled branch is an ordinary state in an editor, and a bot that
     * refuses to run because one branch is unfinished is worse than one that does the rest.
     *
     * @return the chain, so branches read top to bottom
     */
    public MatchBranch when(Predicate<Matches> test, Runnable action) {
        if (settled || test == null || action == null || !test.test(frame)) {
            return this;
        }
        action.run();
        return new MatchBranch(frame, true);
    }

    /**
     * Runs {@code action} when no branch matched — the {@code else} of the chain.
     *
     * <p>Terminal by returning nothing, so a chain cannot accidentally continue past its own fallback and
     * read as though a later branch could still win.
     */
    public void otherwise(Runnable action) {
        if (!settled && action != null) {
            action.run();
        }
    }

    /** Whether some branch has already matched — the question every later {@link #when} asks. */
    @Hidden("plumbing: a bot reads the chain by writing branches, not by inspecting it")
    public boolean settled() {
        return settled;
    }
}
