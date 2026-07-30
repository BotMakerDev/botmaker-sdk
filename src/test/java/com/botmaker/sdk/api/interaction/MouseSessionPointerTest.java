package com.botmaker.sdk.api.interaction;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.internal.capture.core.RecordingNativeController;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.session.ActiveSession;
import com.botmaker.session.Capability;
import com.botmaker.session.DesktopSession;
import com.botmaker.session.SessionKeyboard;
import com.botmaker.session.SessionPointer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bug this class exists for: a bot in a private session found its template, clicked it, and the game showed
 * only the <em>hover</em> effect. {@code Mouse} was calling {@code clickRestoringCursor} unconditionally, so the
 * pointer was warped off the target microseconds after the release and a UI sampling the pointer per frame
 * applied the click somewhere else entirely.
 *
 * <p>So these assert the absence of one call: no trailing {@code mouseMove} back to the origin while a session
 * owns the pointer. The mirror case on {@code :0} — where handing the user's cursor back is the entire reason
 * synthesized input is acceptable — is asserted just as explicitly, because "fixing" this by never restoring
 * would be a different regression.
 */
class MouseSessionPointerTest {

	private static final java.awt.Point ORIGIN = new java.awt.Point(7, 9);

	@AfterEach
	void detach() {
		ActiveSession.clear();
		NativeControllerFactory.setForTesting(null);
	}

	@Test
	void aClickInASessionLeavesThePointerOnTheTarget() {
		Recording controller = new Recording();
		ActiveSession.set(new StubSession(EnumSet.of(Capability.BACKGROUND_CLICK), controller));

		Mouse.click(new Point(100, 120));

		assertEquals(List.of("mouseMove(100,120)", "mouseButton(1,true)", "mouseButton(1,false)"),
			controller.events);
	}

	@Test
	void aClickOnTheUsersDesktopStillHandsTheCursorBack() {
		Recording controller = new Recording();
		NativeControllerFactory.setForTesting(controller);

		Mouse.click(new Point(100, 120));

		assertEquals("mouseMove(7,9)", controller.events.get(controller.events.size() - 1),
			"the :0 path must put the user's cursor back: " + controller.events);
	}

	@Test
	void aDragInASessionDoesNotWarpBackEither() {
		// Same defect one gesture over: the drag read the origin up front and restored it after the release.
		Recording controller = new Recording();
		ActiveSession.set(new StubSession(EnumSet.of(Capability.BACKGROUND_CLICK), controller));

		Mouse.drag(new Point(10, 10), new Point(50, 50));

		assertFalse(controller.events.contains("mouseMove(7,9)"),
			"a drag in a session must end where it ended: " + controller.events);
		assertEquals("mouseButton(1,false)", controller.events.get(controller.events.size() - 1));
	}

	@Test
	void aDoubleClickHoldsEachPressLongEnoughToBeSampled() {
		// It used to be down/up/down/up with no pause at all — under one frame at 60 fps, which a game that reads
		// input once per frame can drop entirely. Asserts the elapsed time, since only a real wait counts.
		Recording controller = new Recording();
		controller.hold = 12;
		ActiveSession.set(new StubSession(EnumSet.of(Capability.BACKGROUND_CLICK), controller));

		long start = System.nanoTime();
		Mouse.doubleClick(new Point(30, 40));
		long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

		assertTrue(elapsedMs >= 2 * controller.hold,
			"double-click returned in " + elapsedMs + "ms, too fast to have held either press");
		assertEquals(List.of("mouseMove(30,40)",
				"mouseButton(1,true)", "mouseButton(1,false)",
				"mouseButton(1,true)", "mouseButton(1,false)"),
			controller.events);
	}

	/** {@link RecordingNativeController} plus a readable cursor, so a restoring warp would show up in the list. */
	private static final class Recording extends RecordingNativeController {
		int hold;

		@Override public java.awt.Point cursorPosition() { return ORIGIN; }
		@Override public int pressHoldMs() { return hold; }
	}

	/** A session that is only its capability set and its controller — all {@code Mouse} reads. */
	private record StubSession(Set<Capability> capabilities, NativeController controller) implements DesktopSession {
		@Override public Rectangle screen() { return new Rectangle(); }
		@Override public SessionPointer pointer() { return null; }
		@Override public SessionKeyboard keyboard() { return null; }
		@Override public void attach(GenericWindow window) { }
		@Override public GenericWindow attached() { return null; }
		@Override public void launch(LaunchSpec spec) { }
		@Override public BufferedImage capture() { return null; }
		@Override public void close() { }
	}
}
