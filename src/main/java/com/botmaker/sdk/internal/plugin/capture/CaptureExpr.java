package com.botmaker.sdk.internal.plugin.capture;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.emulator.EmulatorSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.capture.Monitor;
import com.botmaker.sdk.internal.capture.NamedWindow;

import java.awt.Rectangle;

/**
 * A {@link CaptureSource} as the inline Java expression that produces it, and back.
 *
 * <p>Expressions are <b>fully qualified</b> against the SDK's {@code api.capture} facades so they compile
 * wherever the user has moved the method to, with no import management and no generated sidecar. A capture
 * source is one of four things — {@code CaptureSource.desktop()}, {@code CaptureSource.monitor(i)},
 * {@code CaptureSource.window("t")} or {@code new EmulatorSource("n")} — optionally narrowed to a rectangle
 * of that source with a trailing {@code .region(new Rect(x, y, w, h))}.
 *
 * <h2>One vocabulary, and this is the change that made it one (2026-09-22)</h2>
 *
 * <p>This class used to map a {@code CaptureTargetModel} — a {@code (spec, label)} pair whose spec was
 * {@code desktop} / {@code monitor:0} / {@code window:Diablo IV} in shared's {@code CaptureSourceKind}
 * grammar. That record and that grammar existed for one reason: the project's capture source was stored as
 * text, in {@code botmaker-project.properties}' {@code capture.source} and in {@code capture.json}. Both
 * files are deleted, so the stored form of a capture source is <b>Java</b>, and a third spelling of a type
 * the SDK already publishes was a second vocabulary to keep in step with nothing left to keep it in step
 * with.
 *
 * <p>So the pickers, the thumbnails, the frame grabber, the pilot's router and this class all speak
 * {@link CaptureSource} — the same type the bot's own {@code Sdk.captureSource()} returns, the same type
 * every vision call takes. {@link #of} writes one down and {@link #parse} reads one back, and they are
 * inverses over everything {@link #of} can write.
 */
public final class CaptureExpr {

    /** {@code com.botmaker.sdk.api.capture.} — the package the source factories live in, from the type itself. */
    private static final String PKG = CaptureSource.class.getPackageName() + ".";
    private static final String RECT = Rect.class.getName();
    private static final String EMULATOR_SOURCE = EmulatorSource.class.getName();

    private CaptureExpr() {}

    /**
     * The expression for "the project default", emitted as the SDK's <em>live</em> ambient source rather than a
     * snapshot of today's default source: it keeps following the project's configured source when that default is
     * changed later. Every path that fills a {@code CaptureSource} slot with "project default" — the in-block
     * picker, the expression menu, and the overload-switch seeding in {@code InitializerFactory} — must use this,
     * or the slot silently freezes into a {@code CaptureSource.window("…")} literal.
     */
    public static String projectDefault() {
        return PKG + "Source.current()";
    }

    /** The inline expression for {@code source}, or the whole-desktop source when {@code source} is null. */
    public static String of(CaptureSource source) {
        return of(source, null);
    }

    /**
     * The inline expression for {@code source}, narrowed to {@code region} when it is a positive-area
     * rectangle of that source.
     *
     * <p>{@code region} is in the source's <em>own</em> pixel coordinates — {@code (0,0)} is its top-left, so
     * the narrowing survives the window moving — and it is a plain {@link Rectangle} since the picker that
     * produces one moved to the SDK on 2026-08-31. It was a {@code CaptureRegion} record whose only job was to
     * say that, which the javadoc says as well and without a type nobody else names.
     */
    public static String of(CaptureSource source, Rectangle region) {
        String base = baseOf(source);
        if (region != null && region.width > 0 && region.height > 0) {
            return base + ".region(new " + RECT + "(" + region.x + ", " + region.y + ", "
                    + region.width + ", " + region.height + "))";
        }
        return base;
    }

    /**
     * The source {@code expression} produces, or {@code null} when it names none this can read.
     *
     * <p><b>The inverse of {@link #of}, and it is what made {@code capture.json} unnecessary.</b> Until it
     * existed, every surface that needed the project's capture source read a JSON file the same code path
     * also wrote — a second copy of an answer the bot's own Java already gave. Reading the Java back leaves
     * one author.
     *
     * <p>It matches the four shapes {@link #of} writes, tolerating whatever qualification the user's file
     * carries: {@code CaptureSource.window("Game")} and the fully-qualified form are the same source. A
     * trailing {@code .region(…)} is <b>kept</b>, because a region is part of which pixels the bot reads.
     *
     * <p><b>Anything else is {@code null}, and that is an ordinary answer.</b> The method is Java the user
     * owns: they may have written a helper call, a field reference, or a source this plugin has never heard
     * of. A caller shows "set by hand" and declines to replace it — the editor refuses to respell an
     * expression it cannot read rather than respelling it wrongly, which is the rule
     * {@link com.botmaker.plugin.api.source.PluginValues#open} states for a body that is not a single
     * {@code return}.
     */
    public static CaptureSource parse(String expression) {
        if (expression == null) return null;
        String text = expression.trim();

        Rect region = null;
        int at = text.lastIndexOf(".region(");
        if (at > 0 && text.endsWith(")")) {
            region = parseRect(text.substring(at + ".region(".length(), text.length() - 1));
            if (region == null) return null;
            text = text.substring(0, at).trim();
        }

        CaptureSource base = parseBase(text);
        if (base == null) return null;
        return region == null ? base : base.region(region);
    }

    private static CaptureSource parseBase(String text) {
        String desktop = call(text, PKG + "CaptureSource.desktop");
        if (desktop != null) return CaptureSource.desktop();

        String monitor = call(text, PKG + "CaptureSource.monitor");
        if (monitor != null) {
            Integer index = number(monitor);
            return index == null ? null : CaptureSource.monitor(index);
        }

        String window = call(text, PKG + "CaptureSource.window");
        if (window != null) {
            String title = unquote(window);
            return title == null ? null : CaptureSource.window(title);
        }

        // The emulator is a constructor rather than a factory: EmulatorSource is not one of CaptureSource's
        // three static forms. It is still correct Java, it compiles, and the bot captures from the emulator.
        if (text.startsWith("new ")) {
            String emulator = call(text.substring(4).trim(), EMULATOR_SOURCE);
            if (emulator != null) {
                String name = unquote(emulator);
                return name == null ? null : new EmulatorSource(name);
            }
        }
        return null;
    }

    private static String baseOf(CaptureSource source) {
        // A region is written as its surface plus one .region(…). base() already unwraps every level and
        // subRegion() already composes the offsets, so this is one step rather than a recursion — and
        // writing the composed rect is what makes of/parse inverses over a region of a region.
        if (source != null && source.subRegion() != null) {
            Rect sub = source.subRegion();
            return baseOf(source.base()) + ".region(new " + RECT + "(" + sub.x() + ", " + sub.y() + ", "
                    + sub.width() + ", " + sub.height() + "))";
        }
        if (source instanceof Monitor monitor) {
            return PKG + "CaptureSource.monitor(" + monitor.index() + ")";
        }
        if (source instanceof NamedWindow window && notBlank(window.titleSubstring())) {
            return PKG + "CaptureSource.window(\"" + escape(window.titleSubstring()) + "\")";
        }
        if (source instanceof EmulatorSource emulator && notBlank(emulator.instanceName())) {
            return "new " + EMULATOR_SOURCE + "(\"" + escape(emulator.instanceName()) + "\")";
        }
        // The desktop, a null source, and a source this does not recognise all map to the whole virtual
        // desktop — which is what every caller has always meant by "nothing chosen".
        return PKG + "CaptureSource.desktop()";
    }

    /**
     * The argument text of {@code text} when it is a call to {@code name}, or {@code null}.
     *
     * <p>{@code name} is the fully-qualified spelling {@link #of} writes; a file that imported the type and
     * wrote the short form is the same call, so the callee matches when it is a <b>suffix of {@code name} at
     * a dot boundary</b>. That accepts {@code CaptureSource.window} and the fully-qualified form, and
     * rejects a {@code MyCaptureSource} whose name merely ends the same way.
     */
    private static String call(String text, String name) {
        if (!text.endsWith(")")) return null;
        int open = text.indexOf('(');
        if (open < 0) return null;
        String callee = text.substring(0, open).trim();
        if (!callee.equals(name) && !name.endsWith("." + callee)) return null;
        return text.substring(open + 1, text.length() - 1);
    }

    /** The four ints of a {@code new Rect(x, y, w, h)}, or {@code null} for anything else. */
    private static Rect parseRect(String text) {
        String inner = call(text.trim().startsWith("new ") ? text.trim().substring(4).trim() : text.trim(),
                Rect.class.getName());
        if (inner == null) return null;
        String[] parts = inner.split(",");
        if (parts.length != 4) return null;
        Integer x = number(parts[0]);
        Integer y = number(parts[1]);
        Integer w = number(parts[2]);
        Integer h = number(parts[3]);
        if (x == null || y == null || w == null || h == null) return null;
        return new Rect(x, y, w, h);
    }

    private static Integer number(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** The contents of a single double-quoted literal, unescaped, or {@code null} for anything else. */
    private static String unquote(String argument) {
        String text = argument.trim();
        if (text.length() < 2 || !text.startsWith("\"") || !text.endsWith("\"")) return null;
        String body = text.substring(1, text.length() - 1);
        // A quote that is not the last character would be a concatenation or a second argument, neither of
        // which this writes — so it reads as "somebody else wrote this" rather than being guessed at.
        if (body.replace("\\\\", "").replace("\\\"", "").contains("\"")) return null;
        return body.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
