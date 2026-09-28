package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.record.Gesture;
import com.botmaker.plugin.api.record.Records;
import com.botmaker.sdk.api.interaction.Combo;
import com.botmaker.sdk.api.interaction.Keyboard;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.api.vision.ImageClicker;
import com.botmaker.sdk.api.vision.ImageWaiter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which SDK call the host writes for each recorded gesture. The host reads {@code @Records} and fills the
 * parameters (botmaker-plugin-host's {@code Recordings}); what is held here is the SDK's half: every gesture
 * has a writer, and where two compete the richer one ranks higher.
 */
class SdkRecordsTest {

    /** The classes that carry the SDK's writers. A gesture added to the contract fails here until one does. */
    private static final List<Class<?>> WRITERS =
            List.of(Mouse.class, Keyboard.class, Wait.class, ImageClicker.class, ImageWaiter.class);

    @Test
    void everyGestureHasAPublicStaticWriter() {
        for (Gesture gesture : Gesture.values()) {
            assertTrue(best(gesture).isPresent(), gesture + " has no @Records writer in the SDK");
        }
        for (Method m : writers()) {
            assertTrue(Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()),
                    m + " carries @Records but the host can only call a public static method");
        }
    }

    @Test
    void theRicherWriterWinsWhereTwoCompete() {
        // A combination is written as the value the Combo editor draws, not as a row of keys.
        assertEquals(List.of(Combo.class), List.of(best(Gesture.COMBO).orElseThrow().getParameterTypes()));
        // A click on a project picture outranks a click at a spot; the host falls back when there is none.
        assertEquals(ImageClicker.class, best(Gesture.CLICK).orElseThrow().getDeclaringClass());
    }

    private static Optional<Method> best(Gesture gesture) {
        return writers().stream().filter(m -> m.getAnnotation(Records.class).value() == gesture)
                .max(Comparator.comparingInt(m -> m.getAnnotation(Records.class).rank()));
    }

    private static List<Method> writers() {
        return WRITERS.stream().flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
                .filter(m -> m.isAnnotationPresent(Records.class)).toList();
    }
}
