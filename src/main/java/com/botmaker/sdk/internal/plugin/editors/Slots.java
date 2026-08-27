package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.SlotContext;
import com.botmaker.plugin.api.ValueContext;
import com.botmaker.plugin.toolkit.Values;

import java.util.ArrayList;
import java.util.List;

/**
 * Reading and writing a value that may be either a Java expression or a row of stored strings — the one
 * thing every editor in this package needs and the contract deliberately does not do for it.
 *
 * <p>The host edits values in two places, and the same editor serves both (see
 * {@link com.botmaker.plugin.api.SlotEditor}). But the two <em>spell</em> a value differently: a slot in a
 * bot's source holds one string that happens to be Java — {@code new Rect(12, 40, 300, 80)} — while a row of
 * the Parameters window holds the four numbers as four strings. An editor that knew only one of those would
 * work in only one of the two places, which is exactly the limitation {@code ValueContext} was added to
 * remove.
 *
 * <p>So every method here asks {@link ValueContext#asSlot()} first and takes the other branch when the answer
 * is {@code null}. That question is asked in this class and, as far as possible, nowhere else.
 *
 * <p><b>Nothing here throws.</b> A value may have been typed by hand, written by a newer version of this
 * plugin, or left blank; every read degrades to a default, which is the toolkit's rule 2 restated for source
 * text.
 */
public final class Slots {

    private Slots() {}

    /** The raw text of the value — the slot's Java expression, or the first stored string. */
    public static String raw(ValueContext ctx) {
        SlotContext slot = ctx.asSlot();
        return slot != null ? slot.currentSource().trim() : ctx.single().trim();
    }

    /** Whether there is nothing there yet — a slot never filled in, or an empty row. */
    public static boolean isEmpty(ValueContext ctx) {
        return raw(ctx).isBlank();
    }

    /**
     * The {@code n} numeric arguments of the value, however it is spelled.
     *
     * <p>For a slot, that means the arguments of a constructor call — {@code new Rect(12, 40, 300, 80)} — read
     * positionally and without caring which type is being constructed, since the editor already decided that
     * by matching on the slot's type. For a stored row it is simply the first {@code n} items. A missing or
     * unparseable argument reads as {@code 0}, which is the value a numeric field would show anyway.
     */
    public static int[] ints(ValueContext ctx, int n) {
        SlotContext slot = ctx.asSlot();
        if (slot == null) return Values.ints(ctx.value(), n);
        List<String> args = arguments(slot.currentSource());
        List<String> normalised = new ArrayList<>(args.size());
        for (String arg : args) normalised.add(literal(arg));
        return Values.ints(normalised, n);
    }

    /**
     * A Java integer literal as the plain digits {@code Integer.parseInt} accepts — {@code 100L} is 100 and
     * {@code 1_000} is 1000.
     *
     * <p>Needed here and not in the toolkit because the toolkit reads a project file's stored strings, where
     * a value is already plain, and this class reads Java source, where it is a literal a person wrote. The
     * leniency is deliberate: it is fed whatever is in the slot, and a suffix must not cost the user their
     * label in the middle of rendering a block.
     */
    private static String literal(String source) {
        String s = source == null ? "" : source.trim().replace("_", "");
        return s.endsWith("L") || s.endsWith("l") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * The argument list of a call or constructor in {@code source}, as written.
     *
     * <p>A brace-and-quote-aware split rather than a parser: the contract hands over source text and this
     * module depends on no parsing library, deliberately (see the contract's rule 3). It handles the shapes
     * that actually occur in a slot — nested calls, string literals containing commas — and returns nothing
     * for text it cannot make sense of, which the callers all treat as "no current value".
     */
    public static List<String> arguments(String source) {
        String s = source == null ? "" : source.trim();
        int open = s.indexOf('(');
        int close = s.lastIndexOf(')');
        if (open < 0 || close <= open) return List.of();

        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = open + 1; i < close; i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\' && inString) {
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') depth--;
                else if (c == ',' && depth == 0) {
                    out.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }
        String last = current.toString().trim();
        if (!last.isEmpty() || !out.isEmpty()) out.add(last);
        return out;
    }

    /** The one string literal in {@code source}, unescaped, or {@code null} when there is none. */
    public static String stringLiteral(String source) {
        String s = source == null ? "" : source;
        int open = s.indexOf('"');
        if (open < 0) return null;
        StringBuilder out = new StringBuilder();
        for (int i = open + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                out.append(switch (next) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    default -> next;
                });
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
            }
        }
        return null;
    }

    /** {@code text} as a Java string literal, quotes and backslashes escaped. */
    public static String quote(String text) {
        String s = text == null ? "" : text;
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /**
     * Writes {@code numbers} as a constructor call on {@code type} in a slot, or as the numbers themselves in
     * a stored row.
     *
     * <p>The type is named fully-qualified in the expression and passed again as the import, which is the
     * combination the contract documents as always safe: the host adds the import if it is missing, and then
     * the fully-qualified name it shortens is already correct if it is not.
     */
    public static void writeConstructor(ValueContext ctx, Class<?> type, int... numbers) {
        SlotContext slot = ctx.asSlot();
        if (slot == null) {
            ctx.set(Values.of(numbers));
            return;
        }
        StringBuilder expression = new StringBuilder("new ").append(type.getName()).append('(');
        for (int i = 0; i < numbers.length; i++) {
            if (i > 0) expression.append(", ");
            expression.append(numbers[i]);
        }
        slot.replaceWith(expression.append(')').toString(), type.getName());
    }

    /**
     * Writes a Java expression into a slot, or {@code storedForm} into a row.
     *
     * <p>The two are separate arguments because they are genuinely different answers to the same question:
     * a slot wants {@code CaptureSource.window("Diablo IV")} and the project file wants {@code Diablo IV}.
     * An editor that has only one of them passes it twice.
     */
    public static void write(ValueContext ctx, String javaExpression, String storedForm, String... imports) {
        SlotContext slot = ctx.asSlot();
        if (slot == null) {
            ctx.set(storedForm);
        } else {
            slot.replaceWith(javaExpression, imports);
        }
    }

    /** Writes plain text — a string literal in a slot, the text itself in a row. */
    public static void writeText(ValueContext ctx, String text) {
        write(ctx, quote(text), text);
    }
}
