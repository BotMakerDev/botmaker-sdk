package com.botmaker.sdk.plugin.screen;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.interaction.Mouse;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Executable;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void aSubRegionOfASourceIsRelative() throws Exception {
        assertEquals(PickSpace.RELATIVE, PickSpace.of(method(CaptureSource.class, "region", Rect.class), true));
        assertEquals(PickSpace.RELATIVE, PickSpace.of(method(Mouse.class, "drag",
                CaptureSource.class, Point.class, Point.class, long.class), true));
    }

    /** {@code source.click(p)} takes the absolute point a matcher produced, whatever it is declared on. */
    @Test
    void aSourcesOwnClickIsAbsolute() throws Exception {
        assertEquals(PickSpace.ABSOLUTE, PickSpace.of(method(CaptureSource.class, "click", Point.class), true));
    }

    /** An emulator's frame is only placed on screen to be drawn: its bounds are no desktop origin to add. */
    @Test
    void anOffScreenFrameHasNoOriginToAdd() {
        BufferedImage pixels = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        Rectangle placed = new Rectangle(400, 200, 4, 4);

        assertEquals(placed, PickSpace.origin(new EditorFrame(pixels, "game", placed, true)));
        assertNull(PickSpace.origin(new EditorFrame(pixels, "emulator", placed, false)));
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
