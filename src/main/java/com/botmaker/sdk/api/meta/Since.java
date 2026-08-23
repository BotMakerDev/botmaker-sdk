package com.botmaker.sdk.api.meta;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The release this element first shipped in — readable from the jar, unlike the Javadoc {@code @since} tag.
 *
 * <h2>What it is for</h2>
 *
 * <p>Studio's upgrade dialog lists what a release adds, and today that list is a flat alphabetical diff of
 * two jars: every name that appeared, in one heap, with no way to tell "this is what 1.3.0 brought you" from
 * "this arrived two releases ago and you never noticed". A bot that skipped four releases gets one long list
 * and no shape. With this, the additions group by the version that introduced them, newest first, and the
 * dialog can say <em>new in 1.3.0</em> beside a name.
 *
 * <p>It is not a substitute for the diff. Two jars still answer "what is in the new one and not the old one"
 * exactly, and that stays the source of truth for <em>whether</em> something is new. This answers the
 * question the diff cannot: <em>when</em>.
 *
 * <h2>It is written once, at the moment of the addition, and never back-filled</h2>
 *
 * <p>The value is a fact about the past, and after the release it is unrecoverable — a jar diff can tell you
 * an element exists in 1.4.0 and not in 1.3.0, but only if both jars are in hand, and nothing in the API
 * carries that history. So it goes on the element in the same commit that introduces the element, and a
 * guessed value is worse than none: a wrong {@code @Since} makes the dialog assert something false about a
 * release the user can no longer check.
 *
 * <p>That is why <b>the surface as of 1.1.0 carries no {@code @Since} at all</b> and this is not an
 * oversight. The elements that were here when the contract began have no recorded introduction version —
 * there was no changelog then to read one out of — and inventing 818 identical tags would say nothing true.
 * Studio treats an absent value as "older than the oldest release in this span", so the pre-contract surface
 * simply does not appear in a per-version grouping, and every reader degrades to today's flat list rather
 * than breaking. <b>Every element added from 1.1.0 onward carries one.</b>
 *
 * <h2>The grammar</h2>
 *
 * <p>A semver — {@code 1.2.0} — with <b>no leading {@code v}</b>. The git tag carries the {@code v}; nothing
 * inside the API does, and {@link Replaces} entries are spelled the same way for the same reason. The build
 * gate checks the shape, and at release time also checks that no element claims a version newer than the one
 * being cut.
 *
 * <pre>{@code
 * @Since("1.2.0")
 * public static boolean findBest(ImageTemplateGroup group) { … }
 * }</pre>
 *
 * <h2>Why {@link RetentionPolicy#CLASS}</h2>
 *
 * <p>Same reason as {@link ReplacedBy} and {@link Replaces}: Studio reads it out of the published jar with
 * the ClassGraph scan it already runs, and nothing in a running bot ever asks how old a method is. The
 * Javadoc {@code @since} tag cannot serve here — it is stripped at compile time, and Studio has the jar, not
 * the sources.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Since {

    /** The release this element first shipped in, as {@code major.minor.patch} with no leading {@code v}. */
    String value();
}
