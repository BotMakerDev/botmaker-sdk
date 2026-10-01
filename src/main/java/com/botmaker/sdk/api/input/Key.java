package com.botmaker.sdk.api.input;

import com.botmaker.plugin.api.palette.Hidden;
import com.sun.jna.Platform;

/**
 * OS-neutral keyboard keys for {@link Keyboard}. Each constant carries both an X11 keysym (Linux)
 * and a Windows virtual-key code; {@link #nativeCode()} resolves to the right one for the current
 * platform so bot code never hard-codes platform key codes.
 *
 * <p>Letter keys map to the physical key (lowercase keysym on Linux, VK letter on Windows); use
 * {@link Keyboard#type(String)} when you need shifted/uppercase characters produced for you.
 *
 * <p>Since 2026-09-26 it also has the US punctuation keys, Home/End/Page Up/Page Down/Insert, Caps and Num Lock
 * and the numpad, appended after the first sixty-two so no earlier constant changes place.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): <b>no methods offered</b> — the only one there is,
 * {@link #nativeCode()}, is the platform translation this enum exists to spare a bot from doing, so a menu
 * entry handing back {@code 0xFFBE} would be an invitation to write exactly the hard-coded key code the class
 * javadoc above promises it never has to. The <em>constants</em> are unaffected and remain the whole point:
 * fields are never curated (that is Phase 3.12's standing decision), and enum constants reach the argument
 * pickers through Studio's {@code SdkType.enumConstantNames()}, which reads the class directly and never
 * consults the palette index at all.
 */
public enum Key {
    // Letters (Linux lowercase keysym == ASCII 'a'..'z'; Windows VK 'A'..'Z')
    A(0x61, 0x41), B(0x62, 0x42), C(0x63, 0x43), D(0x64, 0x44), E(0x65, 0x45),
    F(0x66, 0x46), G(0x67, 0x47), H(0x68, 0x48), I(0x69, 0x49), J(0x6A, 0x4A),
    K(0x6B, 0x4B), L(0x6C, 0x4C), M(0x6D, 0x4D), N(0x6E, 0x4E), O(0x6F, 0x4F),
    P(0x70, 0x50), Q(0x71, 0x51), R(0x72, 0x52), S(0x73, 0x53), T(0x74, 0x54),
    U(0x75, 0x55), V(0x76, 0x56), W(0x77, 0x57), X(0x78, 0x58), Y(0x79, 0x59), Z(0x7A, 0x5A),

    // Digits (both platforms: ASCII '0'..'9')
    NUM0(0x30, 0x30), NUM1(0x31, 0x31), NUM2(0x32, 0x32), NUM3(0x33, 0x33), NUM4(0x34, 0x34),
    NUM5(0x35, 0x35), NUM6(0x36, 0x36), NUM7(0x37, 0x37), NUM8(0x38, 0x38), NUM9(0x39, 0x39),

    // Function keys (Linux XK_F1=0xFFBE..; Windows VK_F1=0x70..)
    F1(0xFFBE, 0x70), F2(0xFFBF, 0x71), F3(0xFFC0, 0x72), F4(0xFFC1, 0x73),
    F5(0xFFC2, 0x74), F6(0xFFC3, 0x75), F7(0xFFC4, 0x76), F8(0xFFC5, 0x77),
    F9(0xFFC6, 0x78), F10(0xFFC7, 0x79), F11(0xFFC8, 0x7A), F12(0xFFC9, 0x7B),

    // Common controls
    ENTER(0xFF0D, 0x0D), ESCAPE(0xFF1B, 0x1B), SPACE(0x20, 0x20), TAB(0xFF09, 0x09),
    BACKSPACE(0xFF08, 0x08), DELETE(0xFFFF, 0x2E),

    // Modifiers (left variants)
    SHIFT(0xFFE1, 0x10), CTRL(0xFFE3, 0x11), ALT(0xFFE9, 0x12), META(0xFFEB, 0x5B),

    // Arrows
    LEFT(0xFF51, 0x25), UP(0xFF52, 0x26), RIGHT(0xFF53, 0x27), DOWN(0xFF54, 0x28),

    // Punctuation, US layout (Linux keysym == ASCII; Windows VK_OEM_*). Appended: no earlier ordinal moves.
    BACKQUOTE(0x60, 0xC0), MINUS(0x2D, 0xBD), EQUALS(0x3D, 0xBB), LEFT_BRACKET(0x5B, 0xDB),
    RIGHT_BRACKET(0x5D, 0xDD), BACKSLASH(0x5C, 0xDC), SEMICOLON(0x3B, 0xBA), QUOTE(0x27, 0xDE),
    COMMA(0x2C, 0xBC), PERIOD(0x2E, 0xBE), SLASH(0x2F, 0xBF),

    // Navigation and locks
    HOME(0xFF50, 0x24), END(0xFF57, 0x23), PAGE_UP(0xFF55, 0x21), PAGE_DOWN(0xFF56, 0x22),
    INSERT(0xFF63, 0x2D), CAPS_LOCK(0xFFE5, 0x14), NUM_LOCK(0xFF7F, 0x90),

    // Numpad (Linux XK_KP_0=0xFFB0..; Windows VK_NUMPAD0=0x60..)
    NUMPAD_0(0xFFB0, 0x60), NUMPAD_1(0xFFB1, 0x61), NUMPAD_2(0xFFB2, 0x62), NUMPAD_3(0xFFB3, 0x63),
    NUMPAD_4(0xFFB4, 0x64), NUMPAD_5(0xFFB5, 0x65), NUMPAD_6(0xFFB6, 0x66), NUMPAD_7(0xFFB7, 0x67),
    NUMPAD_8(0xFFB8, 0x68), NUMPAD_9(0xFFB9, 0x69),
    NUMPAD_ADD(0xFFAB, 0x6B), NUMPAD_SUBTRACT(0xFFAD, 0x6D), NUMPAD_MULTIPLY(0xFFAA, 0x6A),
    NUMPAD_DIVIDE(0xFFAF, 0x6F), NUMPAD_DECIMAL(0xFFAE, 0x6E),
    /**
     * The numpad's Enter. Linux has a keysym of its own for it; <b>Windows has no virtual key for it</b> — the
     * two Enters differ only by an extended-key flag this SDK does not send — so on Windows this is
     * {@link #ENTER}.
     */
    NUMPAD_ENTER(0xFF8D, 0x0D);

    private final int linuxKeySym;
    private final int windowsVk;

    Key(int linuxKeySym, int windowsVk) {
        this.linuxKeySym = linuxKeySym;
        this.windowsVk = windowsVk;
    }

    /** The key code to feed the native controller on the current OS. */
    @Hidden("the platform translation this enum exists to spare a bot from doing; a menu offering it "
            + "invites a bot to reason about key codes, which is exactly what Key removes")
    public int nativeCode() {
        return Platform.isWindows() ? windowsVk : linuxKeySym;
    }

    int linuxKeySym() {
        return linuxKeySym;
    }

    int windowsVk() {
        return windowsVk;
    }

    /**
     * The key as it is printed on the cap: {@code Ctrl}, {@code F5}, {@code Page Up}, {@code [}, {@code Num 5}.
     * What an editor shows, and what {@link Combo#toString()} joins with {@code +}.
     */
    @Hidden("display text for an editor; a bot compares keys, never their labels")
    public String label() {
        return switch (this) {
            case ESCAPE -> "Esc";
            case BACKQUOTE -> "`";
            case MINUS -> "-";
            case EQUALS -> "=";
            case LEFT_BRACKET -> "[";
            case RIGHT_BRACKET -> "]";
            case BACKSLASH -> "\\";
            case SEMICOLON -> ";";
            case QUOTE -> "'";
            case COMMA -> ",";
            case PERIOD -> ".";
            case SLASH -> "/";
            case NUMPAD_ADD -> "Num +";
            case NUMPAD_SUBTRACT -> "Num -";
            case NUMPAD_MULTIPLY -> "Num *";
            case NUMPAD_DIVIDE -> "Num /";
            case NUMPAD_DECIMAL -> "Num .";
            case NUMPAD_ENTER -> "Num Enter";
            default -> {
                String name = name();
                if (name.startsWith("NUMPAD_")) yield "Num " + name.substring(7);
                if (name.length() == 4 && name.startsWith("NUM")) yield name.substring(3);
                yield words(name);
            }
        };
    }

    /** {@code PAGE_UP} → {@code Page Up}; a one-letter or F-key name stays as it is. */
    private static String words(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.split("_")) {
            if (!out.isEmpty()) out.append(' ');
            out.append(word.charAt(0)).append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return out.toString();
    }
}
