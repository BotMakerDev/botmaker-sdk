package com.botmaker.sdk.api.interaction;


/**
 * Mouse buttons for the richer {@link Mouse} actions (down/up/drag).
 *
 * <p><b>These are the buttons by what they do, not by where they sit.</b> A mouse with two buttons under the
 * thumb, a left-handed mouse, a mouse whose extra buttons the vendor's driver has remapped: in every one of
 * them the OS reports the button the user has configured it to be, and the bot asks for that. So a bot that
 * says {@code BACK} keeps working on a mouse whose back button is somewhere else, and Studio never has to ask
 * which physical layout the machine running the bot happens to have.
 *
 * <p>The numbers are X11's button numbering, which the Linux backends pass through untouched; Windows
 * translates them (the side buttons become {@code MOUSEEVENTF_XDOWN} plus an {@code XBUTTON} selector).
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): <b>no methods offered</b>, {@link Key}'s verdict for
 * {@link Key}'s reason. {@link #code()} is X11's button numbering, which is precisely the "where it sits"
 * answer the paragraph above says this enum exists to replace with a "what it does" one. The constants are
 * untouched and are the entire surface a bot wants.
 */
// Scaffolding for the same reason as Direction: the generated Activities holds one per mouse-button variable.
public enum MouseButton {
    LEFT(1), MIDDLE(2), RIGHT(3),
    /** The thumb button that goes back — the browser's Back, and what most games bind to a side button. */
    BACK(8),
    /** The thumb button that goes forward. */
    FORWARD(9);

    private final int code;

    MouseButton(int code) {
        this.code = code;
    }

    /** Native button number (1=left, 2=middle, 3=right, 8=back, 9=forward) understood by the controllers. */
    public int code() {
        return code;
    }
}
