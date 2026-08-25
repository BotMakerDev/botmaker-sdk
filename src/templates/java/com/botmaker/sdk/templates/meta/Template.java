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
 * kind, and the file the bot ends up calling it. A hand-written manifest is a second place to be right — a
 * template could be added and never listed, or a role misspelled — and none of that is visible until a project
 * is generated. Written here it is <em>javac's</em>: {@link Kind} is an enum so the closed set is enforced, and
 * the annotation cannot be attached to a file that does not exist.
 *
 * <p>The manifest still exists — it is what Studio reads out of the jar — but it is <b>generated</b> from
 * these annotations at build time, into {@code target/classes/botmaker-templates/manifest.txt}. There is one
 * source of truth and it is compiled.
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
}
