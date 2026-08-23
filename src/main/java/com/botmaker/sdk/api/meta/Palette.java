package com.botmaker.sdk.api.meta;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>BotMaker Studio offers this element in its block palette.</b> On a facade type it means "this class is
 * curated"; on a method it means "this exact overload earns a menu entry".
 *
 * <h2>The problem it solves</h2>
 *
 * <p>Studio's statement menu enumerates <em>every public static method</em> of every facade, resolved from the
 * bot's own jar. So until this annotation existed, "which methods exist" and "which methods Studio offers"
 * were the same question, and a method could only leave the menu by leaving the API. The method audit
 * (<i>docs/refactor/22-api-audit.md</i>) hit that wall repeatedly: it kept finding methods that are worth
 * having and not worth browsing — a lower-level form of an operation the menu already lists, one shape of a
 * family the user picks between with the ⚙ overload picker, a passthrough that only makes sense typed by
 * hand. Its only lever was deletion, so it deleted nothing and wrote the cases down.
 *
 * <h2>Hiding is not deprecating</h2>
 *
 * <p>This is the distinction the whole annotation rests on. An element without {@code @Palette} is
 * <b>public, supported, and under the same compatibility contract as everything else</b> — a bot that calls
 * it compiles, keeps compiling, and is migrated across renames exactly like any other call. It simply is not
 * <em>proposed</em>. {@link Deprecated} says "stop using this"; the absence of {@code @Palette} says "we do
 * not lead with this", which is an editorial judgement about a menu and carries no promise about the future.
 *
 * <p>That is also why this lever outlasts the others. Removing or renaming a method costs a major version
 * from 1.1.0 onwards; <b>adding an annotation costs nothing, ever</b>. Curation stays available for the whole
 * life of the API, long after the window in which the surface could be reshaped has closed.
 *
 * <h2>Strict, and per overload</h2>
 *
 * <p><b>Strict:</b> in a curated jar nothing is offered without the annotation. The default for a newly added
 * method is therefore <em>not offered</em> — someone decides it earns a menu entry, rather than 300 methods
 * arriving in the menus because nobody said otherwise.
 *
 * <p><b>Per overload:</b> {@code @Palette} gates the exact signature it sits on. A method <em>name</em>
 * appears in the menu when any of its overloads carries it, and the ⚙ picker then offers only those. This is
 * the granularity that matters, because the surface's size is mostly one systematic pattern — most matchers
 * exist in four shapes (with and without a {@code CaptureSource}, with and without a threshold) — and a
 * per-name switch could not touch it.
 *
 * <h2>How an uncurated jar is told from an empty one</h2>
 *
 * <p>Strict mode has one hazard: an SDK released before this annotation existed carries no {@code @Palette}
 * anywhere, and a strict reader would empty every menu. Studio does not guess or compare versions — it looks
 * for <b>this annotation class itself</b> in the index it already builds of {@code com.botmaker.sdk.api}.
 * Absent means the jar predates curation, and every public static method is offered exactly as before;
 * present means strict mode. A facade that is not itself {@code @Palette} is likewise uncurated and offers
 * everything, which is what lets the annotation be rolled out one facade at a time.
 *
 * <h2>Records, and why {@link ElementType#RECORD_COMPONENT} is in the target set</h2>
 *
 * <p>A record's accessors are generated, so there is no declaration to annotate — and a curated record would
 * therefore report its own components as <em>not offered</em>, which for {@code Rect} would mean
 * {@code x()}, {@code y()}, {@code width()} and {@code height()}: the four things anyone actually asks a
 * rectangle. Writing {@code @Palette} on the component instead is the language's own answer. An annotation on
 * a record component propagates to whichever of the field, the accessor and the constructor parameter its
 * {@code @Target} admits (JLS 8.10.3), and this one admits {@code METHOD} — so it lands on the accessor,
 * exactly where the reader looks, and nowhere else.
 *
 * <p>Constructors remain outside the target set, and deliberately: Studio inserts {@code new Rect(…)} through
 * a dedicated arg picker rather than from the method menu this annotation curates, so a per-constructor
 * verdict would gate nothing. {@code BotSettings}'s {@code public static final} defaults are outside it for
 * the same reason — a field is not a menu entry.
 *
 * <h2>Why {@link RetentionPolicy#CLASS}</h2>
 *
 * <p>Same reason as the pointer pair and {@link Scaffolding}: Studio reads it straight from the published jar
 * — including a jar newer than Studio itself — with the ClassGraph scan it already runs. Nothing at runtime
 * asks, and a bot never sees it.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface Palette {
}
