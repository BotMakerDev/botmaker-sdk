package com.botmaker.sdk.internal.input;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.input.MouseButton;
import com.botmaker.session.PointerPolicy;
import com.botmaker.session.PrivateSession;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;

import java.awt.Rectangle;

/**
 * Input on a game outside a run, in the pixels of the frame read from it: the assistant's click, key and scroll.
 * Each kind of place takes it as a run's {@code Mouse} and {@code Keyboard} would there: a private display through
 * its own controller and {@link PointerPolicy}, an emulator by touch, this desktop with the cursor put back.
 */
public sealed interface Hands permits Hands.InSession, Hands.OnEmulator, Hands.OnDesktop {

    /** How long a key is held between press and release: a game polling its keys misses a tap with none. */
    long KEY_HOLD_MS = 40;

    void click(int x, int y);

    void key(Key key);

    void scroll(int x, int y, int amount);

    /** The private display's own pointer and keyboard, as a run's {@code Mouse} and {@code Keyboard} use them. */
    record InSession(PrivateSession session) implements Hands {

        @Override
        public void click(int x, int y) {
            Point o = origin();
            PointerPolicy.click(session.controller(), session, o.x() + x, o.y() + y, MouseButton.LEFT.code());
        }

        @Override
        public void key(Key key) {
            NativeController controller = session.controller();
            GenericWindow window = session.attached();
            if (window == null) controller.keyDown(key.nativeCode());
            else controller.keyDown(window, key.nativeCode());
            hold();
            if (window == null) controller.keyUp(key.nativeCode());
            else controller.keyUp(window, key.nativeCode());
        }

        @Override
        public void scroll(int x, int y, int amount) {
            Point o = origin();
            session.controller().mouseMove(o.x() + x, o.y() + y);
            session.controller().scroll(amount);
        }

        /** Where the game's window sits in the display: the frame's (0, 0), as {@code SessionSource} says. */
        private Point origin() {
            GenericWindow window = session.attached();
            Rectangle r = window == null ? null : window.getRect();
            return r == null ? new Point(0, 0) : new Point(r.x, r.y);
        }
    }

    /** The emulator's touch: a click only. */
    record OnEmulator(CaptureSource emulator) implements Hands {

        @Override
        public void click(int x, int y) {
            emulator.click(new Point(x, y));
        }

        @Override
        public void key(Key key) {
            throw new UnsupportedOperationException("The emulator takes clicks here; a key needs try_statement.");
        }

        @Override
        public void scroll(int x, int y, int amount) {
            throw new UnsupportedOperationException("The emulator takes clicks here; a scroll needs try_statement.");
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
        public void key(Key key) {
            NativeController controller = NativeControllerFactory.get();
            controller.keyDown(key.nativeCode());
            hold();
            controller.keyUp(key.nativeCode());
        }

        @Override
        public void scroll(int x, int y, int amount) {
            NativeController controller = NativeControllerFactory.get();
            java.awt.Point back = controller.cursorPosition();
            controller.mouseMove(origin.x() + x, origin.y() + y);
            controller.scroll(amount);
            if (back != null) controller.mouseMove(back.x, back.y);
        }
    }

    /** A key's hold between press and release; an interrupt is kept for what follows. */
    private static void hold() {
        try {
            Thread.sleep(KEY_HOLD_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
