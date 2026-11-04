package com.botmaker.sdk.api.params;

import com.botmaker.sdk.api.screens.Screen;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Where a {@link GameState} fact is read: a run that sees {@link #screen()} while the fact is unknown or stale reads
 * the readout named {@link #field()} off that look (2026-11-04). The fact's type says how: an {@code int} takes the
 * digits shown, a {@code String} the text, an {@code enum} the constant it names, and a {@code boolean} whether
 * anything shows there at all.
 *
 * <p>Without {@link StaleAfter} a value read is stale after an hour.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ReadOn {

    /** The screen showing it, a class of the bot's map. */
    Class<? extends Screen> screen();

    /** The name of the {@code Readout} field of {@link #screen()}. A name it does not have is warned of and never read. */
    String field();
}
