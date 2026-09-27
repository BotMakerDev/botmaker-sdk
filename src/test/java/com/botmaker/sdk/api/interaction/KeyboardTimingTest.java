package com.botmaker.sdk.api.interaction;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.internal.capture.core.RecordingNativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The order a held combo and a key sequence reach the controller in, with the waits recorded between the key
 * events instead of slept: the recording controller is the seam the routing tests use, and {@link Keyboard#pause}
 * is swapped for one that writes {@code wait(ms)} into the same list.
 */
class KeyboardTimingTest {

    private RecordingNativeController fake;
    private Consumer<Duration> realPause;

    @BeforeEach
    void setUp() {
        fake = new RecordingNativeController();
        NativeControllerFactory.setForTesting(fake);
        realPause = Keyboard.pause;
        Keyboard.pause = wait -> fake.events.add("wait(" + wait.toMillis() + ")");
    }

    @AfterEach
    void tearDown() {
        Keyboard.pause = realPause;
        NativeControllerFactory.setForTesting(null);
    }

    private List<String> keyEvents() {
        return fake.events.stream().filter(e -> e.startsWith("key") || e.startsWith("wait")).toList();
    }

    private static String down(Key key) {
        return "keyDown(" + key.nativeCode() + ")";
    }

    private static String up(Key key) {
        return "keyUp(" + key.nativeCode() + ")";
    }

    @Test
    void a_held_combo_waits_between_the_presses_and_the_releases() {
        Keyboard.combo(CaptureSource.desktop(), Combo.of(Key.CTRL, Key.S).held(Duration.ofMillis(200)));
        assertEquals(List.of(down(Key.CTRL), down(Key.S), "wait(200)", up(Key.S), up(Key.CTRL)), keyEvents());
    }

    @Test
    void a_combo_with_no_hold_does_not_wait() {
        Keyboard.combo(CaptureSource.desktop(), Combo.of(Key.CTRL, Key.S));
        assertEquals(List.of(down(Key.CTRL), down(Key.S), up(Key.S), up(Key.CTRL)), keyEvents());
    }

    @Test
    void a_sequence_presses_each_combo_then_waits_its_step() {
        KeySequence sequence = KeySequence.of(
                KeySequence.step(Combo.of(Key.CTRL, Key.A), Duration.ofMillis(100)),
                KeySequence.step(Combo.of(Key.CTRL, Key.C).held(Duration.ofMillis(50)), Duration.ZERO));
        Keyboard.sequence(CaptureSource.desktop(), sequence);
        assertEquals(List.of(
                down(Key.CTRL), down(Key.A), up(Key.A), up(Key.CTRL), "wait(100)",
                down(Key.CTRL), down(Key.C), "wait(50)", up(Key.C), up(Key.CTRL)), keyEvents());
    }
}
