package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueChoice;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Java source of a stored value — now a thin composition over {@link ValueCatalog}, and worth keeping
 * only for the two things the emitter actually asks of it.
 *
 * <h2>The switch is gone, and could never have been right</h2>
 *
 * <p>This class used to hold an exhaustive {@code switch} over seventeen enum constants, one arm per type,
 * beside a parallel one in {@code WireText}. Both were correct only while the vocabulary was closed. It is
 * open now: a type carries its own {@link com.botmaker.plugin.api.value.ValueCodec}, registered by whichever
 * plugin owns it, and composing shape over that codec is all that is left here. The seventeen arms live in
 * {@link SdkValueTypes}, as registrations rather than as cases.
 *
 * <h2>The value is written, not read</h2>
 *
 * <p>A generated field used to be a blank final assigned from a parser call — {@code Wire.duration(one("REST"))}
 * — so the bot re-read its own configuration file on every launch. It does not any more. Every value is
 * emitted as the literal the parser would have produced: if a reader already knows how to turn
 * {@code "1h30m"} into a {@code Duration}, the generator can do that conversion once, at generation time,
 * rather than shipping the text and the parser into every bot. The objection that used to block this — "a
 * re-run would then need a re-build" — turned out not to hold: every Run recompiles the project before
 * launching it.
 *
 * <h2>An unknown type declines, and the field is left out</h2>
 *
 * <p>{@link #initializer} answers {@code null} for a type nothing in the catalog registered, and the emitter
 * must skip that variable entirely. That state was unreachable while the vocabulary was an enum; it is the
 * normal state of a project whose plugin is not installed, and inventing a field for it would compile a
 * guess into the user's bot. The value itself is untouched on disk and comes back when the plugin does.
 */
public final class LiteralWriter {

    private LiteralWriter() {}

    /**
     * The initialiser for a field of this type holding this value, or {@code null} when the type is unknown
     * and the field must be left out — see the class note.
     */
    public static String initializer(ValueCatalog catalog, ValueChoice type, List<String> value) {
        return catalog.initializer(type, value).orElse(null);
    }

    /** The SDK classes a field of this type has to import — empty for a JDK, primitive or unknown type. */
    public static Set<String> imports(ValueCatalog catalog, ValueChoice type) {
        return Set.copyOf(catalog.imports(type));
    }

    /** Every class the given types need imported, in first-use order. */
    public static Set<String> imports(ValueCatalog catalog, List<ValueChoice> types) {
        Set<String> out = new LinkedHashSet<>();
        for (ValueChoice type : types) out.addAll(catalog.imports(type));
        return out;
    }

    /** Whether a field can be emitted for this type at all. */
    public static boolean canEmit(ValueCatalog catalog, ValueChoice type) {
        return type != null && catalog.knows(type.type().id());
    }

    /**
     * A Java string literal.
     *
     * <p>Kept here rather than in the contract for one more release: escaping is the first thing phase 14's
     * {@code emit} package takes, along with the Javadoc escaper and the indenter, and moving it twice would
     * be worse than moving it late. What is legal differs per position, which is why {@link #quoteChar} is
     * separate rather than a parameter.
     */
    public static String quote(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"';
    }

    /** A Java char literal, quotes included. */
    public static String quoteChar(char c) {
        return "'" + switch (c) {
            case '\'' -> "\\'";
            case '\\' -> "\\\\";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> String.valueOf(c);
        } + "'";
    }
}
