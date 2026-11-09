package com.botmaker.sdk.api.params;

import com.botmaker.plugin.api.meta.Migrate;
import com.botmaker.sdk.api.screens.Screen;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Where a {@link Facts} fact is read: a run that sees {@link #screen()} while the fact is unknown or stale reads the
 * readout named {@link #field()} off that look (2026-11-04). The fact's type says how: an {@code int} takes the digits
 * shown, a {@code String} the text, an {@code enum} the constant it names, and a {@code boolean} whether anything shows
 * there at all.
 *
 * <p>Replaced by the readout itself (2026-11-06): {@code Readout.text(Area.of(Pictures.GOLD_ICON, 1.2, 0, 3, 1)).into(Progress::gold)}. A readout
 * that names the fact wins over this.
 */
@Deprecated(forRemoval = true)
@Migrate.Dropped("a fact is read by the screen's readout now: Readout.into(Group::fact).staleAfter(…)")
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ReadOn {

    /** The screen showing it, a class of the bot's map. */
    Class<? extends Screen> screen();

    /** The name of the {@code Readout} field of {@link #screen()}. A name it does not have is warned of and never read. */
    String field();
}
