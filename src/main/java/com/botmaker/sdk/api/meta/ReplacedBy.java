package com.botmaker.sdk.api.meta;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Written on a deprecated element: <em>this is what to use instead</em>.
 *
 * <p>The annotation on this declaration is spelled in full rather than imported, because the simple name
 * here is this very type.
 *
 * @deprecated moved to {@link com.botmaker.plugin.api.meta.ReplacedBy}, where the whole documentation now
 * lives — the grammar, the split mechanism, and the rules that make a pointer safe. The compatibility
 * vocabulary belongs to the plugin contract rather than to one plugin: a second plugin renaming its own types
 * needs the same machinery, and it cannot be made to depend on {@code botmaker-sdk} to get it. Nothing about
 * the annotation itself changed, and both spellings are read for the length of this deprecation window, by
 * the processor, by the release gate and by Studio.
 */
@Deprecated(since = "1.2.0", forRemoval = true)
@com.botmaker.plugin.api.meta.ReplacedBy(
        value = "com.botmaker.plugin.api.meta.ReplacedBy",
        note = "The compatibility vocabulary moved to the plugin contract. Change the import to "
                + "com.botmaker.plugin.api.meta.ReplacedBy; nothing else about it changed.")
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
@Palette(category = "meta", categoryLabel = "Metadata", order = 108)
@Hidden("an annotation: written in source, never inserted as a call")
public @interface ReplacedBy {

    /**
     * What to use instead — each {@code fqn}, {@code fqn#member} or {@code fqn#<init>} — in preference order.
     * Empty means nothing takes this element's place.
     */
    String[] value() default {};

    /** One sentence per {@link #value() candidate}, in the same order and the same length. */
    String[] whens() default {};

    /** The author's own sentence about this move, shown to the user verbatim. */
    String note() default "";

    /** True when the replacement does something different, not merely something with a different name. */
    boolean behaviourChanged() default false;
}
