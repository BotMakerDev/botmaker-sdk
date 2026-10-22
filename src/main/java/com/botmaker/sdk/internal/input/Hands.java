package com.botmaker.sdk.internal.input;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.input.MouseButton;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.session.PointerPolicy;
import com.botmaker.session.PrivateSession;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;

import java.awt.Rectangle;
import java.util.List;

/**
 * Input on a game outside a run, in the pixels of the frame read from it: the assistant's click, key and scroll.
 * Each kind of place takes it as a run's {@code Mouse} and {@code Keyboard} would there: a private display through
 * its own controller and {@link PointerPolicy}, an emulator by touch, this desktop with the cursor put back.
 */
public sealed interface Hands permits Hands.InSession, Hands.OnEmulator, Hands.OnDesktop {

    /** How long a key is held between press and release: a game polling its keys misses a tap with none. */
    long KEY_HOLD_MS = 40;

    void click(int x, int y);

    /** At ({@code x}, {@code y}) by {@code gesture}, a hold held {@code holdMs}: what a map button's gesture does. */
    void press(int x, int y, Gesture gesture, long holdMs);

    /** {@code combo}'s keys down in order, held its hold (a key's tap when none), and up in reverse. */
    void keys(Combo combo);

    default void key(Key key) {
        keys(Combo.of(key));
    }

    /** {@code text} typed into the focused field as it reads, no key of it held. */
    void text(String text);

    /** {@code text} typed into the field just clicked, after Ctrl+A when it {@code clears}, then Enter when it submits. */
    default void type(String text, boolean clears, boolean submits) {
        if (clears) keys(Combo.of(Key.CTRL, Key.A));
        text(text);
        if (submits) key(Key.ENTER);
    }

    void scroll(int x, int y, int amount);

    /** Presses at ({@code fromX}, {@code fromY}), glides to ({@code toX}, {@code toY}) over {@code ms}, and lets go. */
    void drag(int fromX, int fromY, int toX, int toY, long ms);

    /** The private display's own pointer and keyboard, as a run's {@code Mouse} and {@code Keyboard} use them. */
    record InSession(PrivateSession session) implements Hands {

        @Override
        public void click(int x, int y) {
            Point o = origin();
            PointerPolicy.click(session.controller(), session, o.x() + x, o.y() + y, MouseButton.LEFT.code());
        }

        @Override
        public void press(int x, int y, Gesture gesture, long holdMs) {
            if (gesture == Gesture.CLICK || gesture == Gesture.UNKNOWN) {
                click(x, y);
                return;
            }
            Point o = origin();
            pressed(session.controller(), o.x() + x, o.y() + y, gesture, holdMs);
        }

        @Override
        public void keys(Combo combo) {
            NativeController controller = session.controller();
            GenericWindow window = session.attached();
            List<Key> keys = combo.keys();
            for (Key key : keys) {
                if (window == null) controller.keyDown(key.nativeCode());
                else controller.keyDown(window, key.nativeCode());
            }
            hold(combo);
            for (Key key : keys.reversed()) {
                if (window == null) controller.keyUp(key.nativeCode());
                else controller.keyUp(window, key.nativeCode());
            }
        }

        @Override
        public void text(String text) {
            GenericWindow window = session.attached();
            if (window == null) session.controller().typeText(text);
            else session.controller().typeText(window, text);
        }

        @Override
        public void scroll(int x, int y, int amount) {
            Point o = origin();
            session.controller().mouseMove(o.x() + x, o.y() + y);
            session.controller().scroll(amount);
        }

        @Override
        public void drag(int fromX, int fromY, int toX, int toY, long ms) {
            Point o = origin();
            glide(session.controller(), o.x() + fromX, o.y() + fromY, o.x() + toX, o.y() + toY, ms);
        }

        /** Where the game's window sits in the display: the frame's (0, 0), as {@code SessionSource} says. */
        private Point origin() {
            GenericWindow window = session.attached();
            Rectangle r = window == null ? null : window.getRect();
            return r == null ? new Point(0, 0) : new Point(r.x, r.y);
        }
    }

    /** The emulator's touch: a click, a double tap, a long press, and a swipe for a drag. */
    record OnEmulator(CaptureSource emulator) implements Hands {

        /** Between a double tap's two taps: well inside a phone's double-tap time. */
        private static final long DOUBLE_TAP_MS = 80;

        @Override
        public void click(int x, int y) {
            emulator.click(new Point(x, y));
        }

        @Override
        public void press(int x, int y, Gesture gesture, long holdMs) {
            Point at = new Point(x, y);
            // As a run presses it there: a touch screen has no right or middle button, and no hover but a press.
            switch (gesture) {
                case CLICK, UNKNOWN, RIGHT, MIDDLE -> emulator.click(at);
                case DOUBLE -> {
                    emulator.click(at);
                    sleep(DOUBLE_TAP_MS);
                    emulator.click(at);
                }
                // A swipe that stays put is a long press.
                case HOLD -> emulator.drag(at, at, holdMs);
                case HOVER -> {
                }
            }
        }

        @Override
        public void keys(Combo combo) {
            throw new UnsupportedOperationException("The emulator takes clicks here; a key needs try_statement.");
        }

        @Override
        public void text(String text) {
            if (!(emulator.base() instanceof EmulatorSource device)) {
                throw new UnsupportedOperationException("This emulator takes no text here; a field needs a run.");
            }
            device.text(text);
        }

        /** Its own text input and Enter key: an emulator has no select-all, so a field there keeps what it held. */
        @Override
        public void type(String text, boolean clears, boolean submits) {
            text(text);
            if (submits && emulator.base() instanceof EmulatorSource device) device.key(EmulatorSource.KEYCODE_ENTER);
        }

        @Override
        public void scroll(int x, int y, int amount) {
            throw new UnsupportedOperationException("The emulator takes clicks here; a scroll needs try_statement.");
        }

        @Override
        public void drag(int fromX, int fromY, int toX, int toY, long ms) {
            emulator.drag(new Point(fromX, fromY), new Point(toX, toY), ms);
        }
    }

    /** This desktop's pointer and keyboard, the frame's (0, 0) at {@code origin}, the cursor put back after. */
    record OnDesktop(Point origin) implements Hands {

        @Override
        public void click(int x, int y) {
            PointerPolicy.click(NativeControllerFactory.get(), null, origin.x() + x, origin.y() + y,
                    MouseButton.LEFT.code());
        }

        @Override
        public void press(int x, int y, Gesture gesture, long holdMs) {
            if (gesture == Gesture.CLICK || gesture == Gesture.UNKNOWN) {
                click(x, y);
                return;
            }
            NativeController controller = NativeControllerFactory.get();
            java.awt.Point back = controller.cursorPosition();
            pressed(controller, origin.x() + x, origin.y() + y, gesture, holdMs);
            // A hover leaves the pointer on what it opened.
            if (back != null && gesture != Gesture.HOVER) controller.mouseMove(back.x, back.y);
        }

        /** Never Alt or Meta with others: on the user's desktop they are the window manager's (Alt+F4, Meta+L). */
        @Override
        public void keys(Combo combo) {
            if (combo.keys().size() > 1 && (combo.keys().contains(Key.ALT) || combo.keys().contains(Key.META))) {
                throw new UnsupportedOperationException("On this desktop keys with Alt or Meta reach the window "
                        + "manager, not only the game: run the game in a private display to press " + combo.caps()
                        + ".");
            }
            NativeController controller = NativeControllerFactory.get();
            for (Key key : combo.keys()) controller.keyDown(key.nativeCode());
            hold(combo);
            for (Key key : combo.keys().reversed()) controller.keyUp(key.nativeCode());
        }

        @Override
        public void text(String text) {
            NativeControllerFactory.get().typeText(text);
        }

        @Override
        public void scroll(int x, int y, int amount) {
            NativeController controller = NativeControllerFactory.get();
            java.awt.Point back = controller.cursorPosition();
            controller.mouseMove(origin.x() + x, origin.y() + y);
            controller.scroll(amount);
            if (back != null) controller.mouseMove(back.x, back.y);
        }

        @Override
        public void drag(int fromX, int fromY, int toX, int toY, long ms) {
            NativeController controller = NativeControllerFactory.get();
            java.awt.Point back = controller.cursorPosition();
            glide(controller, origin.x() + fromX, origin.y() + fromY, origin.x() + toX, origin.y() + toY, ms);
            if (back != null) controller.mouseMove(back.x, back.y);
        }
    }

    /**
     * A gesture other than a click at the display's ({@code x}, {@code y}): the pointer moved there, then a double
     * click's two presses, a right or middle press, the left button held {@code holdMs}, or nothing for a hover.
     */
    private static void pressed(NativeController controller, int x, int y, Gesture gesture, long holdMs) {
        controller.mouseMove(x, y);
        switch (gesture) {
            case DOUBLE -> {
                tap(controller, MouseButton.LEFT, controller.pressHoldMs());
                tap(controller, MouseButton.LEFT, controller.pressHoldMs());
            }
            case RIGHT -> tap(controller, MouseButton.RIGHT, controller.pressHoldMs());
            case MIDDLE -> tap(controller, MouseButton.MIDDLE, controller.pressHoldMs());
            case HOLD -> tap(controller, MouseButton.LEFT, holdMs);
            case CLICK, UNKNOWN -> tap(controller, MouseButton.LEFT, controller.pressHoldMs());
            case HOVER -> {
            }
        }
    }

    /** {@code button} down, held {@code ms}, and up, up even when interrupted. */
    private static void tap(NativeController controller, MouseButton button, long ms) {
        controller.mouseButton(button.code(), true);
        try {
            sleep(ms);
        } finally {
            controller.mouseButton(button.code(), false);
        }
    }

    /**
     * The left button pressed at the start, the pointer moved in steps of about 15 ms to the end, as {@code Mouse.drag}
     * glides, and let go there; an interrupt ends the glide early, kept for what follows.
     */
    private static void glide(NativeController controller, int fromX, int fromY, int toX, int toY, long ms) {
        controller.mouseMove(fromX, fromY);
        controller.mouseButton(MouseButton.LEFT.code(), true);
        try {
            int steps = Math.max(1, (int) (ms / 15));
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                controller.mouseMove((int) Math.round(fromX + (toX - fromX) * t),
                        (int) Math.round(fromY + (toY - fromY) * t));
                if (ms > 0) Thread.sleep(ms / steps);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            controller.mouseButton(MouseButton.LEFT.code(), false);
        }
    }

    /** {@code combo}'s hold between its keys' press and release, a key's tap when it has none. */
    private static void hold(Combo combo) {
        sleep(combo.hold().isZero() ? KEY_HOLD_MS : combo.hold().toMillis());
    }

    /** A pause of {@code ms}; an interrupt is kept for what follows. */
    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
