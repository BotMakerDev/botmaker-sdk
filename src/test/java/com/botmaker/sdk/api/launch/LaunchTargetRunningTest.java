package com.botmaker.sdk.api.launch;

import com.botmaker.sdk.api.Debug;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link LaunchTarget#startIfNotRunning()}'s "is it already up?" decision — the one that used to be
 * broken for every launcher, because a Steam/Heroic/Proton-wrapped game never runs under the process name in
 * its {@link LaunchTarget#spec()}. The decision now reads the ambient {@link Source capture source}'s window,
 * so it is driven here by a fake {@link NativeController} (nothing real is enumerated or launched).
 *
 * <p>The skip is asserted through the {@code [Target] … skipping cold launch} trace on stdout: it is the only
 * observable of a decision whose "no" branch launches a process, so the launching branch is exercised with a
 * command that does not exist (a failed {@code ProcessBuilder} is logged, not thrown).
 */
class LaunchTargetRunningTest {

    private static final String SKIP = "skipping cold launch";
    /** A command that cannot exist, so the "launch" branch is observable without starting anything. */
    private static final String BOGUS = "botmaker-nonexistent-command-9d3f";

    @AfterEach
    void reset() {
        NativeControllerFactory.setForTesting(null);
        Source.set(null);
    }

    @Test
    void skipsLaunchWhenTheTargetWindowIsAlreadyOpen() {
        NativeControllerFactory.setForTesting(new FakeController(List.of(window("Firestone Online Idle RPG"))));
        Source.set(CaptureSource.window("Firestone"));

        // Steam/Epic/Heroic take the shared default; a hit here means start() (a real launch) is never reached.
        assertTrue(trace(() -> LaunchTarget.parse("heroic:43d4ef20").startIfNotRunning()).contains(SKIP));
        assertTrue(trace(() -> LaunchTarget.parse("steam:570").startIfNotRunning()).contains(SKIP));
    }

    @Test
    void launchesWhenTheTargetWindowIsAbsent() {
        NativeControllerFactory.setForTesting(new FakeController(List.of(window("Some Other App"))));
        Source.set(CaptureSource.window("Firestone"));

        assertFalse(trace(() -> LaunchTarget.parse("cli:" + BOGUS).startIfNotRunning()).contains(SKIP));
    }

    @Test
    void aSourceWithNoWindowIdentityNeverReportsRunning() {
        // The desktop is always "present", so trusting isPresent() here would skip the launch forever.
        NativeControllerFactory.setForTesting(new FakeController(List.of(window("Firestone"))));
        Source.set(CaptureSource.desktop());

        assertFalse(trace(() -> LaunchTarget.parse("cli:" + BOGUS).startIfNotRunning()).contains(SKIP));
        assertFalse(CaptureSource.desktop().hasWindowIdentity());
        assertTrue(CaptureSource.window("Firestone").hasWindowIdentity());
    }

    @Test
    void anExeStillFallsBackToItsProcessNameWhenTheSourceCannotAnswer() {
        NativeControllerFactory.setForTesting(new FakeController(List.of()));
        Source.set(CaptureSource.desktop());

        // Not running under that name either, so it launches (and fails harmlessly) rather than skipping.
        assertFalse(trace(() -> LaunchTarget.parse("exe:/opt/" + BOGUS).startIfNotRunning()).contains(SKIP));
    }

    /** Runs {@code action} with debug output on, returning everything it printed to stdout. */
    private static String trace(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        boolean debugWas = Debug.isEnabled();
        System.setOut(new PrintStream(captured, true));
        Debug.enable();
        try {
            action.run();
        } catch (RuntimeException expected) {
            // The launching branch is exercised with a command that cannot exist, so reaching a real
            // ProcessBuilder failure is itself the assertion that it did not skip.
        } finally {
            Debug.set(debugWas);
            System.setOut(original);
        }
        return captured.toString();
    }

    private static GenericWindow window(String title) {
        return new GenericWindow(new Object(), title, new Rectangle(0, 0, 100, 100));
    }

    /** Minimal {@link NativeController} that only answers {@code getAllWindows}; everything else is a no-op. */
    private record FakeController(List<GenericWindow> windows) implements NativeController {
        @Override public List<GenericWindow> getAllWindows() { return windows; }
        @Override public GenericWindow getForegroundWindow() { return null; }
        @Override public List<GenericWindow> getChildWindows(GenericWindow parent) { return List.of(); }
        @Override public BufferedImage captureWindow(GenericWindow window) { return null; }
        @Override public void postLeftClick(GenericWindow window, int relativeX, int relativeY) {}
        @Override public void postLeftClickScreen(int xAbs, int yAbs) {}
        @Override public void focusWindow(GenericWindow window) {}
        @Override public void moveWindow(GenericWindow window, int x, int y) {}
        @Override public void resizeWindow(GenericWindow window, int width, int height) {}
        @Override public void keyDown(int nativeKeyCode) {}
        @Override public void keyUp(int nativeKeyCode) {}
        @Override public void typeText(String text) {}
        @Override public void mouseMove(int xAbs, int yAbs) {}
        @Override public void mouseButton(int button, boolean press) {}
        @Override public void scroll(int amount) {}
    }
}
