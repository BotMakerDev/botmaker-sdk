package com.botmaker.sdk.api.launch;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.launch.RunningProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The layers behind {@link LaunchTarget#isRunning()}. The defect they exist for: the probe used to ask only the
 * ambient {@link Source capture source}, so a project capturing the <em>desktop</em> (no window identity) always
 * answered "not running" and relaunched the game on every run.
 */
class LaunchTargetProbeTest {

    @AfterEach
    void reset() {
        NativeControllerFactory.setForTesting(null);
        Source.set(null);
        RunningProbe.clearSpawned();
    }

    @Test
    void aDesktopCaptureSourceNoLongerBlindsTheProbe() {
        // The exact reported setup: capture the whole desktop, so the source cannot answer — but the game is up.
        NativeControllerFactory.setForTesting(new FakeController(List.of(window("Firestone Online Idle RPG"))));
        Source.set(CaptureSource.desktop());

        assertTrue(LaunchTarget.parse("heroic:Firestone").isRunning(),
                "the window is open; enumerating the OS's windows must see it even with no window source");
        assertFalse(LaunchTarget.parse("heroic:SomethingElseEntirely").isRunning());
    }

    @Test
    void aSteamTargetMatchesItsWrapperNotItsBareId() {
        // `570` alone would match any command line carrying that number by accident; Steam's own reaper spells
        // it `SteamLaunch AppId=570 --`, which is what makes the command-line layer usable at all.
        assertEquals("AppId=570", LaunchTarget.parse("steam:570").runningToken());
    }

    @Test
    void aLiveProcessIsFoundByItsCommandLine() throws Exception {
        String marker = "botmaker-probe-" + UUID.randomUUID();
        Process p = new ProcessBuilder("sleep", "30").start();
        try {
            assertTrue(RunningProbe.commandLineMentions("sleep 30"),
                    "a process this test just started must show up in the scan");
            assertFalse(RunningProbe.commandLineMentions(marker),
                    "a token nothing carries must not match");
        } finally {
            p.destroyForcibly();
        }
    }

    @Test
    void aProcessWeSpawnedOurselvesCountsUntilItExits() throws Exception {
        Process p = new ProcessBuilder("sleep", "30").start();
        RunningProbe.record("exe:/opt/thing", p);
        assertTrue(RunningProbe.spawnedAlive("exe:/opt/thing"));

        p.destroyForcibly();
        p.waitFor();
        assertFalse(RunningProbe.spawnedAlive("exe:/opt/thing"),
                "a dead handle must stop counting — no timer, no cooldown, just liveness");
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
