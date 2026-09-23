package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.emulator.EmulatorSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.capture.CurrentSource;
import com.botmaker.sdk.internal.capture.Monitor;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.capture.RegionSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The six calls a {@link CaptureSource} is written as, each taken apart and put back.
 *
 * <p>{@code build(components(v))} giving the same parts back is the law every {@link ComponentType} owes; the
 * host's spelling of those parts is asserted where the host lives.
 */
class CaptureTypesTest {

    @SuppressWarnings("unchecked")
    private static <T> List<Object> again(ComponentType<T> shape, Object value) {
        T built = shape.build(shape.components((T) value));
        return shape.components(built);
    }

    @Test
    void eachShapeGivesItsPartsBack() {
        assertEquals(List.of(), again(CaptureTypes.CURRENT, new CurrentSource()));
        assertEquals(List.of(), again(CaptureTypes.DESKTOP, CaptureSource.desktop()));
        assertEquals(List.of(2), again(CaptureTypes.MONITOR, CaptureSource.monitor(2)));
        assertEquals(List.of("Game"), again(CaptureTypes.WINDOW, CaptureSource.window("Game")));
        assertEquals(List.of("Pixel_7"), again(CaptureTypes.EMULATOR, new EmulatorSource("Pixel_7")));
    }

    @Test
    void aRegionIsItsSourceAndItsRectangle() {
        RegionSource region = (RegionSource) CaptureSource.region(CaptureSource.window("Game"),
                new Rect(1, 2, 30, 40));

        List<Object> parts = CaptureTypes.REGION.components(region);

        assertInstanceOf(NamedWindow.class, parts.get(0));
        assertEquals(new Rect(1, 2, 30, 40), parts.get(1));
        assertEquals(parts.get(1), CaptureTypes.REGION.components(CaptureTypes.REGION.build(parts)).get(1));
    }

    @Test
    void eachIsWrittenAsTheCallThatMakesIt() {
        assertEquals(Source.class, CaptureTypes.CURRENT.factoryOwner());
        assertEquals("current", CaptureTypes.CURRENT.factory());
        assertEquals(CaptureSource.class, CaptureTypes.REGION.factoryOwner());
        assertEquals("region", CaptureTypes.REGION.factory());
        assertTrue(CaptureTypes.EMULATOR.factory().isEmpty(), "an emulator is a constructor");
        assertEquals(Monitor.class, CaptureTypes.MONITOR.type());
    }

    @Test
    void aFreshCaptureSourceIsTheAmbientOne() {
        assertInstanceOf(CurrentSource.class, new CaptureTypes.CaptureSourceType().fresh());
    }
}
