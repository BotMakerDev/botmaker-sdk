package com.botmaker.sdk.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Written on a deprecated type, method, constructor or field: <em>this is what to use instead</em>.
 *
 * <h2>What it is for</h2>
 *
 * <p>When a bot is upgraded to a newer SDK, Studio has both jars in hand and diffs them. A diff can see that
 * {@code ImageClicker#click} is gone and {@code IClicker#tap} has appeared; it cannot see that one became the
 * other. Read as a removal, a rename turns into hundreds of calls replaced by default values — the single
 * worst thing an upgrade can do to someone's project.
 *
 * <p>This annotation is the forward half of the answer, and it is read out of the <b>bot's own (older)</b>
 * jar: the bot still spells the member the old way, so the old jar is where the pointer to the new spelling
 * has to be. The backward half is {@link Replaces}, read out of the newer jar. Either one alone resolves a
 * single hop; the two <b>compose</b>, which is what lets a bot that skipped several releases follow a chain
 * of renames without any intermediate jar ever being fetched.
 *
 * <pre>{@code
 * // 1.2.0 — the window in which both spellings exist
 * @Deprecated(since = "1.2.0", forRemoval = true)
 * @ReplacedBy("com.botmaker.sdk.api.vision.IClicker#tap")
 * public boolean click(ImageTemplate t) { return tap(t); }
 *
 * @Replaces("com.botmaker.sdk.api.vision.ImageClicker#click@1.2.0")
 * public boolean tap(ImageTemplate t) { … }
 * }</pre>
 *
 * <h2>The grammar</h2>
 *
 * <p>A target is {@code fqn} for a type, {@code fqn#member} for a method or field, {@code fqn#<init>} for a
 * constructor. An enum constant <em>is</em> a static field, so {@code …interaction.Key#ENTER} names one.
 * There is <b>no arity</b> in the string: the annotation sits on one specific overload, so the parameter
 * count of both ends is already known from the bytecode.
 *
 * <h2>The rules that make a pointer safe</h2>
 *
 * <ul>
 *   <li><b>An empty value is an explicit statement</b>, not an omission: nothing takes this element's place,
 *       and Studio should replace its uses with a default value and mark them for review. The annotation is
 *       required on every deprecated public element precisely so that the author decides rather than forgets
 *       — a build gate checks it.
 *   <li><b>Write it at the moment of the change</b>, in the release that deprecates the element, never
 *       reconstructed later. That is also when the element it names is still compilable, which is what lets
 *       the gate verify the link with nothing but this build.
 *   <li><b>A pointer is a suggestion Studio checks, not an instruction it obeys.</b> The two return types are
 *       both in hand: in statement position the target's type cannot matter and the redirect is always taken;
 *       in expression position the redirect is taken only when the new type fits where the old one did, and
 *       otherwise the call falls back to a default value. A pointer can therefore be wrong without producing
 *       a bot that compiles and misbehaves.
 *   <li><b>A pointer can be corrected</b> in a later release — it is an ordinary annotation, not an identity.
 * </ul>
 *
 * <h2>Why {@link RetentionPolicy#CLASS}</h2>
 *
 * <p>Studio reads this from the published jar with the ClassGraph scan it already runs over the SDK's types,
 * so it has to survive compilation. It does not need to be visible at runtime: nothing in a running bot ever
 * asks what a member used to be called.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface ReplacedBy {

    /**
     * What to use instead — {@code fqn}, {@code fqn#member} or {@code fqn#<init>} — or the empty string,
     * meaning nothing takes this element's place.
     */
    String value() default "";

    /**
     * The author's own sentence about this move, shown to the user <b>verbatim</b>.
     *
     * <p>Everything else here is machine-readable: {@link #value()} is a target, {@link #behaviourChanged()}
     * is a flag. Neither can say <em>why</em>, and Studio's generated sentence ("{@code click} is now
     * {@code tap}") only restates what the diff already showed. This is the one channel through which the
     * person who made the change speaks to the person whose bot it lands on — "the new one measures from the
     * template's centre, not its top-left" — so Studio prefers it over its own wording and never paraphrases
     * it, truncates it or folds it into a list.
     *
     * <p>Write it in the second person and keep it to a sentence or two; it is read in a dialog beside the
     * call sites it applies to, not in a changelog. Empty means "the pointer says everything there is to say".
     */
    String note() default "";

    /**
     * True when the replacement <b>does something different</b>, not merely something with a different name.
     *
     * <p>This is the one gap the redirect model cannot see by construction. Studio decides whether to take a
     * pointer by comparing the two <em>shapes</em>: a same-shape redirect is always safe to apply, so a
     * rename lands silently and correctly, which is the whole point. But "same shape, different behaviour" —
     * a click that now targets the match's centre, a wait that now counts from a different instant — is
     * exactly a same-shape redirect, and the bot compiles and quietly does something else. That is the worst
     * outcome the model admits, and nothing in the bytecode reveals it.
     *
     * <p>So the author says it. Studio marks every redirected call site {@code @NeedsReview} when this is
     * set, even where the shape did not move, with {@link #note()} as the mark's text — which is why the gate
     * refuses {@code behaviourChanged = true} with a blank {@code note}: a flag with no sentence tells the
     * user their bot changed and nothing about how.
     */
    boolean behaviourChanged() default false;
}
