package com.botmaker.sdk.api.meta;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Written on the surviving type, method, constructor or field: <em>these older spellings became this</em>.
 *
 * <h2>What it is for</h2>
 *
 * <p>This is the backward half of the redirect {@link ReplacedBy} declares forward, and it is read out of the
 * <b>newer</b> jar. Both halves are needed because a bot being upgraded holds only two jars — its own and the
 * one it is moving to — and the pointer may have been written in either of them:
 *
 * <ul>
 *   <li>the bot's old jar carries {@code @ReplacedBy} on the member the bot still calls;
 *   <li>the new jar carries {@code @Replaces} on the member that took it over, which is the only place the
 *       answer survives once the deprecated member is finally deleted.
 * </ul>
 *
 * <p>Composed, they resolve a chain: {@code a}→{@code b} announced in 2.0 and {@code b}→{@code c} in 3.0
 * lets a bot still spelling it {@code a} land on {@code c} with no intermediate jar fetched.
 *
 * <h2>The grammar</h2>
 *
 * <p>Each entry is {@code fqn[#member][(arity)]@<version>}: the old spelling — {@code fqn} for a type,
 * {@code fqn#member} for a method or field, {@code fqn#<init>} for a constructor — an optional parameter
 * count, and <b>the last release in which that spelling existed</b>.
 *
 * <pre>{@code
 * @Replaces({"com.botmaker.sdk.api.vision.ImageClicker#click@1.2.0",
 *            "com.botmaker.sdk.api.vision.IClicker#hit@2.4.1"})
 * public boolean tap(ImageTemplate t) { … }
 * }</pre>
 *
 * <p><b>The arity is optional, and it is here for the case {@link ReplacedBy} does not have.</b> A forward
 * pointer sits <em>on</em> one overload, so its parameter count is in the bytecode beside it and needs no
 * spelling out. This end names an overload that may already be deleted — nothing is left to count — so when
 * a member had several and only one of them was taken over, {@code …#click(2)@1.2.0} is how this element
 * says which. Omit it when the old member had a single overload, which is the ordinary case; an entry with no
 * arity claims the name, not a signature.
 *
 * <h2>Two elements may claim one entry — when the other end says so</h2>
 *
 * <p>A <b>split</b> ({@link ReplacedBy#value()} naming two targets) surfaces here as two survivors carrying
 * the same {@code name@version}, and that is the only place the split still exists once the old member is
 * deleted. So a double claim is legal <em>exactly</em> when the claimed element's own {@code @ReplacedBy}
 * lists precisely those claimants — a condition checkable inside one build, while both ends are compilable.
 * Every other double claim is still refused by the gate and still read by Studio as an ambiguous claim, which
 * is no claim: the old name is treated as unpaired rather than guessed at.
 *
 * <p>Neither {@code #} nor {@code @} occurs in a Java fully-qualified name, and a {@code (} cannot begin a
 * version, so the parse is unambiguous.
 * The version is <b>mandatory</b>: it tells Studio which era an entry belongs to (an entry is consulted only
 * for a bot pinned at or below it), and it distinguishes two entries that name the same type or member at
 * different points in the API's history.
 *
 * <h2>The rules</h2>
 *
 * <ul>
 *   <li><b>Entries accumulate and are never pruned.</b> History costs a string; losing it costs a rename.
 *       A stale entry cannot win, because a redirect only ever fires for a break the two-jar diff actually
 *       found — a name that is still live in the new jar is resolved by the live element, not by an entry.
 *   <li><b>An <em>undeclared</em> ambiguous claim is no claim.</b> If two surviving elements claim the same
 *       {@code name@version} without the claimed element declaring them as a split (see below), Studio treats
 *       the old name as unpaired (default value plus a review mark) rather than guessing, and the build gate
 *       refuses it outright.
 *   <li><b>Write it in the release that makes the change</b>, while the deprecated element it names is still
 *       present and compilable — that is what lets the gate verify both ends from a single build.
 * </ul>
 *
 * <h2>Why {@link RetentionPolicy#CLASS}</h2>
 *
 * <p>Same reason as {@link ReplacedBy}: Studio reads it from the published jar, and nothing at runtime cares.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Replaces {

    /** The older spellings this element took over, each {@code fqn[#member][(arity)]@<version>}. */
    String[] value();

    /**
     * The author's own sentence about the move, shown <b>verbatim</b> — {@link ReplacedBy#note()} written on
     * this end instead.
     *
     * <p>It is duplicated rather than shared because the two ends are read out of two different jars and only
     * one of them survives. A bot upgrading <em>through</em> the deprecation release reads the note on the
     * element it still calls; a bot that skipped that release finds the element gone and reads this one. The
     * forward note wins when both are present — it is the author speaking on the element the bot actually
     * names — and this is the fallback for everyone who arrived late.
     */
    String note() default "";

    /**
     * True when this element <b>does something different</b> from the one it took over, not merely something
     * with a different name — {@link ReplacedBy#behaviourChanged()} on this end, and for the same reason
     * {@link #note()} is here: the flag has to outlive the element it describes.
     *
     * <p>Studio takes the logical OR of the two ends, so either one asserting it marks every redirected call
     * site for review.
     */
    boolean behaviourChanged() default false;
}
