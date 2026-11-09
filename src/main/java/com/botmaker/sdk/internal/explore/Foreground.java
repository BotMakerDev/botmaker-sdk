package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.capture.Window;
import com.botmaker.sdk.internal.emulator.Emulator;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.sdk.internal.launch.LaunchTarget;
import com.botmaker.sdk.internal.launch.Target;

/**
 * What is in front of the bot now and what should be: on Android the package of the window in focus against the
 * launch target's package, on a desktop the window in front against the one the capture source names. {@code inFront}
 * is {@code ""} when nothing could be read; {@code target} is {@code ""} when there is nothing to compare against (the
 * whole desktop, a private session, no launch target), and then nothing is in the way.
 *
 * @param inFront what is in front: a package name, or a window's label
 * @param target  what should be, in the same terms
 * @param dialog  how Android's own dialog in focus says itself ("isn't responding"), or {@code ""} for none
 */
public record Foreground(String inFront, String target, String dialog) {

    public Foreground {
        inFront = inFront == null ? "" : inFront;
        target = target == null ? "" : target;
        dialog = dialog == null ? "" : dialog;
    }

    public Foreground(String inFront, String target) {
        this(inFront, target, "");
    }

    /** Nothing to compare against: every foreground is the game's. */
    public static final Foreground UNKNOWN = new Foreground("", "");

    /** Whether something other than the target is in front. Unread, with a target to compare, is not the target. */
    public boolean foreign() {
        return !target.isEmpty() && !target.equals(inFront);
    }

    /** What is in front of the bot's capture source now; read again once when the first read finds nothing. */
    public static Foreground now() {
        Foreground read = read();
        return read.inFront().isEmpty() && !read.target().isEmpty() ? read() : read;
    }

    private static Foreground read() {
        return switch (Source.current()) {
            case EmulatorSource emulator -> new Foreground(emulator.focusedPackage(), targetPackage(),
                    emulator.systemDialog());
            case Emulator emulator -> new Foreground(emulator.focusedPackage(), targetPackage(), emulator.systemDialog());
            // Not ours is never the target's label, even when another window bears the same title.
            case NamedWindow window -> new Foreground(window.inFront() ? window.label()
                    : "another window" + Window.foreground().map(Window::title).filter(title -> !title.isBlank())
                            .map(title -> " (" + title + ")").orElse(""), window.label());
            case null, default -> UNKNOWN;
        };
    }

    /** The launch target's package when it is an app in an emulator, else {@code ""}. */
    private static String targetPackage() {
        return Target.current() instanceof LaunchTarget.EmulatorApp(String packageName, String ignored)
                ? packageName : "";
    }
}
