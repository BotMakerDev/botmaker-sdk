package com.botmaker.sdk.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>BotMaker Studio writes this element into the files it generates.</b> Renaming or removing it does not
 * only break bots that call it — it breaks bots that never mentioned it.
 *
 * <h2>What it is for</h2>
 *
 * <p>Studio does not merely edit a bot's source; it <em>emits</em> some of it. A new project's entry point
 * calls {@code Bot.start} and {@code PopupGuard.install}; the generated {@code FlowDriver} calls
 * {@code Bot.stop}, {@code Watchdog.checkpoint} and {@code PopupGuard.enabled}; every activity stub extends
 * {@code Activity}. None of that text was typed by the user, and none of it is in the SDK's own sources —
 * it lives in text blocks inside Studio.
 *
 * <p>That makes these elements different from the rest of the API in one specific way: <b>a bot's own code
 * can be migrated, and generated code is regenerated.</b> When a normal member is removed with nothing to
 * take its place, Studio substitutes a default value at each call site and marks the enclosing function for
 * review — the bot compiles, the user fixes it. Generated files have no such fallback: they hold no user
 * code, their shape is entirely ours, and a defaulted value inside one is not a repair, it is a broken
 * feature nobody asked for. So when a scaffolding element moves with no way across, Studio's answer is to
 * refuse — an upgrade it will not apply, or an Activity Flow edit it will not save — until Studio itself is
 * updated. That is a much sharper consequence than a review mark, and it is what this annotation exists to
 * make visible at the declaration.
 *
 * <h2>What it obliges</h2>
 *
 * <ul>
 *   <li><b>Deprecating one requires a real replacement.</b> The build gate refuses a {@code @Deprecated}
 *       {@code @Scaffolding} element whose {@link ReplacedBy} is empty. An empty pointer means "default the
 *       value and mark it for review", which is precisely the outcome generated code cannot take; declaring
 *       a dead end here is declaring that the next Studio release must ship before this one can.
 *   <li><b>The set is checked against Studio's, not maintained by eye.</b> Studio declares the same list
 *       from its side, derived from the generators themselves, and the two are reconciled by the build —
 *       a member Studio emits but that is not annotated here, or annotated here and no longer emitted, is
 *       a build failure naming the element. Two hand-written copies would drift; this pair cannot.
 *   <li><b>The annotation is a warning to the author, not a freeze.</b> Nothing forbids changing one. It
 *       says: this change also needs a Studio release, so land them together.
 * </ul>
 *
 * <h2>Why the SDK carries it at all, when the fact belongs to Studio</h2>
 *
 * <p>Because it has to be visible where the decision is made. An SDK author renaming {@code Watchdog#checkpoint}
 * has this module open, not Studio's; a fact recorded only in Studio would be found the release after it
 * mattered. The dependency still runs the right way — the SDK knows nothing <em>about</em> Studio here, only
 * that something generates this member, and nothing in this module reads the annotation.
 *
 * <h2>Why {@link RetentionPolicy#CLASS}</h2>
 *
 * <p>Same reason as the pointer pair: Studio reads it from the published jar — including a jar newer than
 * itself — with the ClassGraph scan it already runs. Nothing at runtime asks.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Scaffolding {
}
