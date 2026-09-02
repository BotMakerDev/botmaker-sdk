package com.botmaker.sdk.internal.plugin.record;

import com.botmaker.plugin.toolkit.Source;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.Keyboard;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.shared.input.InputEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure translation of a recorded {@link InputEvent} stream into <b>Java source lines</b> — no JavaFX, no
 * native, no side effects, so it is fully unit-testable. {@link MacroRecorderDialog} buffers events while
 * recording and calls {@link #translate} on stop.
 *
 * <p><b>It was {@code com.botmaker.studio.services.record.MacroTranslator} until 2026-09-02, and it produced
 * Studio's {@code List<BlockType>}.</b> That was the last SDK vocabulary left inside the editor: five class
 * literals — {@code Mouse}, {@code Keyboard}, {@code Wait}, {@code CaptureSource}, {@code Key} — spelled by
 * the host on this plugin's behalf, in a class whose whole subject is <em>what a recorded click is written
 * down as</em>. Nothing about it was ever host work; a host does not know that a click is a {@code Mouse}.
 *
 * <p>What changed on the way across is the output type, and it changed for a reason worth keeping: a
 * {@code BlockType} is the editor's own model and no plugin may build one, while <b>Java source text is the
 * wire every other contract surface already uses</b> — {@code SlotContext.replaceWith}, {@code Sources},
 * {@code SourceSeed}. So the translator emits the statements a person would have typed, and
 * {@link Macro#java()} spells them out imports and all.
 *
 * <p>v1 scope (leaf actions only, all coordinates <b>window-relative</b> or position-independent):
 * <ul>
 *   <li>Left click inside the window → {@code Mouse.click(CaptureSource.window("title"), relX, relY)}.</li>
 *   <li>Printable keys → coalesced into one {@code Keyboard.type("…")} per burst (Backspace edits the buffer).</li>
 *   <li>Named keys (Enter/Tab/arrows/F-keys/…) → {@code Keyboard.tap(Key.NAME)}.</li>
 *   <li>Wheel → {@code Mouse.scroll(±n)} (accumulated per direction).</li>
 *   <li>An idle gap ≥ {@value #GAP_MS} ms between gestures → a {@code Wait.milliseconds(n)} in between.</li>
 * </ul>
 * Right/middle/double clicks and drags are intentionally deferred (they lack a window-relative overload);
 * a left-button drag is detected only to <em>suppress</em> a spurious click, not to emit a drag. Standalone
 * modifier keys (Shift/Ctrl/Alt/…) are dropped. Clicks outside the window bounds are dropped.
 */
public final class MacroTranslator {

    /** Idle gap between gestures that produces a Wait statement. */
    static final long GAP_MS = 400;
    /** Waits are rounded to this granularity for tidy values. */
    private static final long WAIT_ROUND_MS = 100;
    /** Pointer travel (px) between a left press and release beyond which we treat it as a drag, not a click. */
    private static final int DRAG_THRESHOLD_PX = 5;

    /** The window a recording targets: its title substring and current absolute origin + size. */
    public record WindowRef(String title, int originX, int originY, int width, int height) {}

    /**
     * A translated recording: the statements, in order, and the types they name.
     *
     * <p>The statements are written with <b>simple</b> type names, which is what a person reading them
     * expects and what the {@link #imports} are for. Fully-qualified statements would need no imports and
     * would be unreadable, and the recorder's whole delivery is text a user reads before pasting.
     *
     * @param statements one Java statement per line, semicolon included
     * @param imports    fully-qualified type names, in the order first used
     */
    public record Macro(List<String> statements, List<String> imports) {

        /** True when nothing recognisable was recorded. */
        public boolean isEmpty() {
            return statements.isEmpty();
        }

        /** The import block, a blank line, then the statements — the text the user copies. */
        public String java() {
            StringBuilder out = new StringBuilder();
            for (String type : imports) out.append("import ").append(type).append(";\n");
            if (!imports.isEmpty()) out.append('\n');
            for (String statement : statements) out.append(statement).append('\n');
            return out.toString();
        }
    }

    private MacroTranslator() {}

    /** X keysym → {@code Key} enum constant name for non-printable named keys. */
    private static final Map<Long, String> NAMED_KEYS = buildNamedKeys();

    // X keysyms for modifiers we drop (keysymdef.h). Also covers the *_Lock keys.
    private static final long[] MODIFIER_KEYSYMS = {
            0xFFE1L, 0xFFE2L,           // Shift_L/R
            0xFFE3L, 0xFFE4L,           // Control_L/R
            0xFFE9L, 0xFFEAL,           // Alt_L/R
            0xFFE7L, 0xFFE8L,           // Meta_L/R
            0xFFEBL, 0xFFECL,           // Super_L/R
            0xFFEDL, 0xFFEEL,           // Hyper_L/R
            0xFFE5L, 0xFFE6L, 0xFF7FL,  // Caps_Lock, Shift_Lock, Num_Lock
            0xFE03L                     // ISO_Level3_Shift (AltGr)
    };

    /** Translates the buffered events for a recording of {@code window} into Java statements. */
    public static Macro translate(List<InputEvent> events, WindowRef window) {
        Set<Class<?>> used = new LinkedHashSet<>();
        List<Timed> gestures = collectGestures(events, window, used);
        List<String> statements = withWaits(gestures, used);
        List<String> imports = new ArrayList<>();
        for (Class<?> type : used) imports.add(Source.type(type));
        return new Macro(List.copyOf(statements), List.copyOf(imports));
    }

    /** A statement paired with the wall-clock time it happened, for later gap→Wait insertion. */
    private record Timed(String statement, long time) {}

    private static List<Timed> collectGestures(List<InputEvent> events, WindowRef window, Set<Class<?>> used) {
        List<Timed> out = new ArrayList<>();
        StringBuilder typing = new StringBuilder();
        long[] typingStart = {0};

        // Pending wheel run, accumulated per direction.
        int[] scrollAccum = {0};
        long[] scrollTime = {0};

        // Left-button drag detection: press coords + whether the pointer travelled far while held.
        boolean[] leftDown = {false};
        int[] downX = {0}, downY = {0};
        boolean[] dragged = {false};

        for (InputEvent e : events) {
            switch (e) {
                case InputEvent.KeyPress k -> {
                    long sym = k.keysym();
                    if (isModifier(sym)) continue;
                    if (sym == 0xFF08L && typing.length() > 0) { // Backspace edits the in-progress text
                        typing.deleteCharAt(typing.length() - 1);
                        continue;
                    }
                    Character ch = printableChar(sym);
                    if (ch != null) {
                        flushScroll(out, scrollAccum, scrollTime, used);
                        if (typing.length() == 0) typingStart[0] = k.timestampMs();
                        typing.append(ch.charValue());
                    } else {
                        String name = NAMED_KEYS.get(sym);
                        if (name == null) continue; // unknown / unsupported key — skip rather than emit garbage
                        flushTyping(out, typing, typingStart, used);
                        flushScroll(out, scrollAccum, scrollTime, used);
                        out.add(new Timed(tap(name, used), k.timestampMs()));
                    }
                }
                case InputEvent.ButtonPress b -> {
                    switch (b.button()) {
                        case 1 -> { leftDown[0] = true; downX[0] = b.x(); downY[0] = b.y(); dragged[0] = false; }
                        case 4, 5 -> { // wheel up / down — one notch per press
                            int dir = b.button() == 4 ? 1 : -1;
                            flushTyping(out, typing, typingStart, used);
                            if (scrollAccum[0] != 0 && Integer.signum(scrollAccum[0]) != dir) {
                                flushScroll(out, scrollAccum, scrollTime, used);
                            }
                            if (scrollAccum[0] == 0) scrollTime[0] = b.timestampMs();
                            scrollAccum[0] += dir;
                        }
                        default -> { // middle/right — deferred in v1
                            flushTyping(out, typing, typingStart, used);
                            flushScroll(out, scrollAccum, scrollTime, used);
                        }
                    }
                }
                case InputEvent.Motion m -> {
                    if (leftDown[0] && travelledFar(downX[0], downY[0], m.x(), m.y())) dragged[0] = true;
                }
                case InputEvent.ButtonRelease b -> {
                    if (b.button() == 1 && leftDown[0]) {
                        leftDown[0] = false;
                        if (dragged[0]) continue;           // a drag, not a click — suppress (drag deferred)
                        int relX = b.x() - window.originX();
                        int relY = b.y() - window.originY();
                        if (!inside(relX, relY, window)) continue; // e.g. a click on the recorder window
                        flushTyping(out, typing, typingStart, used);
                        flushScroll(out, scrollAccum, scrollTime, used);
                        out.add(new Timed(click(window.title(), relX, relY, used), b.timestampMs()));
                    }
                }
                case InputEvent.KeyRelease ignored -> { /* shift state tracked natively; nothing to emit */ }
            }
        }
        flushTyping(out, typing, typingStart, used);
        flushScroll(out, scrollAccum, scrollTime, used);
        return out;
    }

    /** Inserts a Wait between gestures separated by a gap ≥ {@link #GAP_MS}. */
    private static List<String> withWaits(List<Timed> gestures, Set<Class<?>> used) {
        List<String> out = new ArrayList<>();
        long prevTime = -1;
        for (Timed g : gestures) {
            if (prevTime >= 0) {
                long gap = g.time() - prevTime;
                if (gap >= GAP_MS) out.add(waitMs(roundWait(gap), used));
            }
            out.add(g.statement());
            prevTime = g.time();
        }
        return out;
    }

    private static void flushTyping(List<Timed> out, StringBuilder typing, long[] typingStart, Set<Class<?>> used) {
        if (typing.length() == 0) return;
        out.add(new Timed(type(typing.toString(), used), typingStart[0]));
        typing.setLength(0);
    }

    private static void flushScroll(List<Timed> out, int[] scrollAccum, long[] scrollTime, Set<Class<?>> used) {
        if (scrollAccum[0] == 0) return;
        out.add(new Timed(scroll(scrollAccum[0], used), scrollTime[0]));
        scrollAccum[0] = 0;
    }

    // ── Statement builders ──────────────────────────────────────────────────────────────────────────────
    //
    // Written with simple names rather than through Source.call, which fully-qualifies its type: the whole
    // delivery of a recording is text somebody reads before pasting it, and a wall of
    // com.botmaker.sdk.api.interaction.Keyboard.type(…) is not that. What Source is used for is the two
    // things a hand-rolled builder gets wrong — escaping a typed string, and vouching that the method name
    // exists on the class rather than discovering it at the user's next compile.

    private static String click(String title, int relX, int relY, Set<Class<?>> used) {
        used.add(Mouse.class);
        used.add(CaptureSource.class);
        Source.requireMethod(Mouse.class, "click");
        Source.requireMethod(CaptureSource.class, "window");
        return "Mouse.click(CaptureSource.window(" + Source.string(title).source() + "), "
                + relX + ", " + relY + ");";
    }

    private static String type(String text, Set<Class<?>> used) {
        used.add(Keyboard.class);
        Source.requireMethod(Keyboard.class, "type");
        return "Keyboard.type(" + Source.string(text).source() + ");";
    }

    private static String tap(String keyName, Set<Class<?>> used) {
        used.add(Keyboard.class);
        used.add(Key.class);
        Source.requireMethod(Keyboard.class, "tap");
        return "Keyboard.tap(Key." + keyName + ");";
    }

    private static String scroll(int notches, Set<Class<?>> used) {
        used.add(Mouse.class);
        Source.requireMethod(Mouse.class, "scroll");
        return "Mouse.scroll(" + notches + ");";
    }

    private static String waitMs(long ms, Set<Class<?>> used) {
        used.add(Wait.class);
        Source.requireMethod(Wait.class, "milliseconds");
        return "Wait.milliseconds(" + ms + ");";
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────────────

    private static boolean inside(int relX, int relY, WindowRef w) {
        return relX >= 0 && relY >= 0 && relX < w.width() && relY < w.height();
    }

    private static boolean travelledFar(int x0, int y0, int x1, int y1) {
        return Math.abs(x1 - x0) > DRAG_THRESHOLD_PX || Math.abs(y1 - y0) > DRAG_THRESHOLD_PX;
    }

    private static long roundWait(long gap) {
        long rounded = Math.round((double) gap / WAIT_ROUND_MS) * WAIT_ROUND_MS;
        return Math.max(WAIT_ROUND_MS, rounded);
    }

    private static boolean isModifier(long keysym) {
        for (long m : MODIFIER_KEYSYMS) if (m == keysym) return true;
        return false;
    }

    /** The character to type for a keysym, or {@code null} when it isn't a printable Latin-1 symbol. */
    private static Character printableChar(long keysym) {
        // X keysyms for Latin-1 printable characters equal their Unicode code point.
        if ((keysym >= 0x20 && keysym <= 0x7E) || (keysym >= 0xA0 && keysym <= 0xFF)) {
            return (char) keysym;
        }
        return null;
    }

    private static Map<Long, String> buildNamedKeys() {
        Map<Long, String> m = new java.util.HashMap<>();
        m.put(0xFF0DL, "ENTER");
        m.put(0xFF8DL, "ENTER");   // KP_Enter
        m.put(0xFF1BL, "ESCAPE");
        m.put(0xFF09L, "TAB");
        m.put(0xFF08L, "BACKSPACE");
        m.put(0xFFFFL, "DELETE");
        m.put(0xFF51L, "LEFT");
        m.put(0xFF52L, "UP");
        m.put(0xFF53L, "RIGHT");
        m.put(0xFF54L, "DOWN");
        // Function keys F1..F12 (XK_F1 = 0xFFBE).
        for (int i = 0; i < 12; i++) m.put(0xFFBEL + i, "F" + (i + 1));
        // Keypad digits with NumLock on (XK_KP_0 = 0xFFB0) → the NUM0..NUM9 constants.
        for (int i = 0; i < 10; i++) m.put(0xFFB0L + i, "NUM" + i);
        return Map.copyOf(m);
    }
}
