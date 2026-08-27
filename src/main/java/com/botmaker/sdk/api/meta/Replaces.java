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
 * Written on the surviving element: <em>these older spellings became this</em>.
 *
 * @deprecated nothing takes its place. It first moved to {@code com.botmaker.plugin.api.meta.Replaces}; that
 * type is gone too. The back edge existed because a bot skipping the deprecation release could not see a
 * forward pointer on an element that had since been deleted — and under the never-delete rule on
 * {@code com.botmaker.sdk.api.**} the deprecated element is still there, still carrying its own
 * {@code @ReplacedBy}, so the forward pointer alone answers every upgrade including a skipped one. Delete the
 * annotation and the import; keep the {@code @ReplacedBy} on the old element instead.
 */
@Deprecated(since = "1.2.0", forRemoval = true)
@ReplacedBy(value = {},
        note = "Nothing takes its place. Under never-delete the old element survives carrying its own "
                + "@ReplacedBy, so the forward pointer alone answers the upgrade.")
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
@Palette(category = "meta", categoryLabel = "Metadata", order = 109)
@Hidden("an annotation: written in source, never inserted as a call")
public @interface Replaces {

    /** The older spellings this element took over, each {@code fqn[#member][(arity)]@<version>}. */
    String[] value();

    /** The author's own sentence about the move, shown verbatim. */
    String note() default "";

    /** True when this element does something different from the one it took over. */
    boolean behaviourChanged() default false;
}
