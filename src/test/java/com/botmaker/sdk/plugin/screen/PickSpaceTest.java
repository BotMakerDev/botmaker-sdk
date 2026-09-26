package com.botmaker.sdk.plugin.screen;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.interaction.Mouse;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.lang.reflect.Executable;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which numbers a pick writes: the call decides, because a bare Point means different things in different calls. */
class PickSpaceTest {

    private static Optional<Executable> method(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        return Optional.of(owner.getMethod(name, parameters));
    }

    @Test
    void aCallTakingASourceIsRelative() throws Exception {
        assertEquals(PickSpace.RELATIVE,
                PickSpace.of(method(Mouse.class, "click", CaptureSource.class, int.class, int.class), true));
    }

    @Test
    void aCallTakingDesktopPixelsIsAbsolute() throws Exception {
        assertEquals(PickSpace.ABSOLUTE, PickSpace.of(method(Mouse.class, "click", Point.class), true));
    }

    @Test
    void aMethodOnASourceIsRelative() {
        Executable onSource = CaptureSource.class.getMethods()[0];
        assertEquals(PickSpace.RELATIVE, PickSpace.of(Optional.of(onSource), true));
    }

    @Test
    void anUnresolvedCallIsAbsoluteAndARowIsRelative() {
        assertEquals(PickSpace.ABSOLUTE, PickSpace.of(Optional.empty(), true));
        assertEquals(PickSpace.RELATIVE, PickSpace.of(Optional.empty(), false));
    }

    @Test
    void absoluteAddsTheFramesOriginEvenWhenItIsNegative() {
        Rectangle leftMonitor = new Rectangle(-1920, 0, 1920, 1080);

        assertArrayEquals(new int[]{-1800, 40}, PickSpace.ABSOLUTE.point(new int[]{120, 40}, leftMonitor));
        assertArrayEquals(new int[]{120, 40}, PickSpace.RELATIVE.point(new int[]{120, 40}, leftMonitor));
        assertArrayEquals(new int[]{-1800, 40, 30, 20},
                PickSpace.ABSOLUTE.region(new int[]{120, 40, 30, 20}, leftMonitor));
    }
}
