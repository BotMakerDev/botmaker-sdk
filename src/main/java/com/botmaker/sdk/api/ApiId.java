package com.botmaker.sdk.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A name for what this type <em>is</em>, which survives being renamed.
 *
 * <h2>The problem it solves</h2>
 *
 * <p>When a bot is upgraded to a newer SDK, Studio has both jars in hand and diffs them. A diff can see that
 * {@code ImageClicker} is gone and {@code IClicker} has appeared; it cannot see that they are the same class.
 * Read as a removal, a rename becomes hundreds of deleted statements across the bot — the single worst thing
 * an upgrade can do to someone's project, and the reason a rename cannot be treated like any other break.
 *
 * <p>An id makes the pairing a fact rather than a guess or a declaration. Both releases spell the id the same
 * way, so the diff matches them directly, and nothing has to be written down in a file that someone has to
 * remember to maintain on every release.
 *
 * <pre>{@code
 * @ApiId("image-clicker") public final class ImageClicker { }   // 1.1.0
 * @ApiId("image-clicker") public final class IClicker    { }    // 2.0.0 — paired, no file entry
 * }</pre>
 *
 * <h2>The one rule: an id is retired, never re-pointed</h2>
 *
 * <p>An id names a <em>role</em>. When the role disappears — the class is not renamed, it is gone, because the
 * design that needed it is gone — the id goes with it. Its absence from the newer jar is exactly the signal
 * that says "this is not coming back", and every call to it is then replaced with a default value and flagged
 * for the user to look at. Moving a retired id onto a different class to save typing tells the upgrade that
 * unrelated code is the same code.
 *
 * <p>Reusing one is nonetheless survivable, which is deliberate: an id pairs the <em>type name</em> and
 * nothing else. Every member is still checked one by one against the newer type, so an id kept across a
 * wholesale redesign degrades into "the type was renamed, and most of its members had no counterpart" —
 * defaults and review marks — rather than a rewrite that is silently wrong.
 *
 * <h2>Why {@link RetentionPolicy#CLASS}</h2>
 *
 * <p>Studio reads this out of the published jar with the same ClassGraph scan it already runs over the SDK's
 * types, so the id has to survive compilation. It does not need to be visible at runtime: nothing in a running
 * bot ever asks what a class used to be called.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface ApiId {

    /** The stable id, in kebab-case: {@code "image-clicker"}, {@code "precision"}. Unique across the API. */
    String value();
}
