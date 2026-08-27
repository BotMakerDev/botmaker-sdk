package com.botmaker.sdk.api.meta;

import com.botmaker.plugin.api.meta.ReplacedBy;
import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The release this element first shipped in.
 *
 * @deprecated nothing takes its place. It first moved to {@code com.botmaker.plugin.api.meta.Since}; that
 * type is gone too, deleted with the annotation processor that read it. What it recorded — the release an
 * element first shipped in — is now read off the jar the bot actually resolves, so an annotation restating it
 * by hand was a second answer to a question bytecode already answers. Delete the annotation and the import;
 * this element stays public forever under the never-delete rule, and reading it is harmless.
 */
@Deprecated(since = "1.2.0", forRemoval = true)
@ReplacedBy(value = {},
        note = "Nothing takes its place. The release an element first shipped in is read off the resolved "
                + "jar; delete the annotation and its import.")
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
@Palette(category = "meta", categoryLabel = "Metadata", order = 107)
@Hidden("an annotation: written in source, never inserted as a call")
public @interface Since {

    /** The release this element first shipped in, as {@code major.minor.patch} with no leading {@code v}. */
    String value();
}
