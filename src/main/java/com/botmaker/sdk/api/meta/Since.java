package com.botmaker.sdk.api.meta;

import com.botmaker.plugin.api.meta.ReplacedBy;
import com.botmaker.plugin.api.palette.Facade;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The release this element first shipped in.
 *
 * @deprecated moved to {@link com.botmaker.plugin.api.meta.Since}, where the whole documentation now lives.
 * The compatibility vocabulary belongs to the plugin contract rather than to one plugin: a second plugin
 * renaming its own types needs the same machinery, and it cannot be made to depend on {@code botmaker-sdk}
 * to get it. Nothing about the annotation itself changed — same element, same grammar, same
 * {@code CLASS} retention — and both spellings are read for the length of this deprecation window, by the
 * processor, by the release gate and by Studio.
 */
@Deprecated(since = "1.2.0", forRemoval = true)
@ReplacedBy(value = "com.botmaker.plugin.api.meta.Since",
        note = "The compatibility vocabulary moved to the plugin contract. Change the import to "
                + "com.botmaker.plugin.api.meta.Since; nothing else about it changed.")
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
@Facade(category = "meta", categoryLabel = "Metadata", role = "VALUE", order = 107)
public @interface Since {

    /** The release this element first shipped in, as {@code major.minor.patch} with no leading {@code v}. */
    String value();
}
