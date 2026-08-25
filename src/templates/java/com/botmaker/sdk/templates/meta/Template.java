package com.botmaker.sdk.templates.meta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * What a scaffold template is, said on the template itself.
 *
 * <h2>Why the declaration moved onto the class</h2>
 *
 * <p>Everything here used to be a column in a hand-written {@code manifest.txt}: the role Studio asks for, the
 * kind, the file the bot ends up calling it, and the list of holes. A hand-written manifest is a second place
 * to be right — a template could be added and never listed, a hole renamed in the Java and not in the text,
 * a role misspelled — and none of that is visible until a project is generated. Written here it is
 * <em>javac's</em>: {@link Kind} is an enum so the closed set is enforced, the annotation cannot be attached
 * to a file that does not exist, and the processor that reads it fails the SDK's own build when the
 * {@link #holes()} it declares and the fences actually in the file disagree.
 *
 * <p>The manifest still exists — it is what Studio reads out of the jar — but it is <b>generated</b> from
 * these annotations at build time, into {@code target/classes/botmaker-templates/manifest.txt}. There is one
 * source of truth and it is compiled.
 *
 * <h2>Holes carry a generation, and that is the whole point</h2>
 *
 * <p>An entry of {@link #holes()} is {@code NAME:generation} — {@code "FLOW:1"} — and the fences in the file
 * carry the same number: {@code /*<STUDIO:FLOW:1>*}{@code / … /*</STUDIO:FLOW:1>*}{@code /}. The number is not
 * a version of the template; it is a version of <em>that one hole's shape</em>. Bump it when what belongs
 * between the fences changes — different arguments, a different arrangement, a different meaning — and leave
 * it alone when only the surrounding frame moved.
 *
 * <p>It exists because the API pointer pair ({@code @ReplacedBy} / {@code @Replaces}) covers a member being
 * <em>renamed</em> inside a fragment and nothing else. Change {@code FlowGraph.of(String, Node…)} to
 * {@code FlowGraph.of(Node, Node…)} and every name a fragment mentions still resolves, so every check passes
 * and an older Studio writes last year's arrangement into this year's frame. Silently. A generation makes
 * that a refusal: Studio fills a hole only on an <b>exact</b> {@code name:generation} match, so a Studio that
 * has never produced {@code FLOW:2} says so by name instead of guessing.
 *
 * <p>Per hole and not per template, deliberately. A project pins one SDK and therefore sees exactly one key
 * set, so no range is ever needed; and one number for the whole scaffold would make every producer a range
 * whose gaps and overlaps fill silently wrong — which is the failure this exists to end.
 *
 * <h2>It never reaches a bot, or even the jar</h2>
 *
 * <p>Templates compile to {@code target/template-classes} and are shipped as <em>text</em>, so this
 * annotation is not on any classpath a bot ever sees. It is not in the shipped text either: the processor
 * writes the template out with the annotation and its import removed, so what an older Studio extracts is the
 * same plain Java it has always extracted. {@code RetentionPolicy.CLASS} because nothing reads it at runtime —
 * the processor reads it during the build and the manifest carries the answer from there.
 *
 * @see com.botmaker.sdk.apt.TemplateProcessor
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Template {

    /** Whether Studio writes this file once or rewrites it on every model change. */
    enum Kind {
        /** Written at creation and the user's thereafter: the entry point, GoHome, Popups, the stubs. */
        SEED,
        /** Rewritten wholesale on every model change: Activities, ActivityRegistry, FlowDriver. */
        REGENERATED
    }

    /** The role Studio asks for — {@code TemplateStore.require("FLOW_DRIVER")}. Unique across templates. */
    String id();

    /** Written once, or rewritten. */
    Kind kind();

    /**
     * The file name in the generated bot, with {@code ${CLASS}} standing for the project's main class and
     * {@code ${ACTIVITY}} for an activity's name.
     */
    String target();

    /**
     * Every hole in this file, as {@code NAME:generation}. Must match the fences in the file exactly — the
     * processor fails the build on a fence that is not declared, a declaration with no fence, an unpaired
     * fence, or a name declared twice.
     */
    String[] holes() default {};
}
