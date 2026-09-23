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
    void eachIsWrittenAsTheCallThatMakesIt() throws NoSuchMethodException {
        assertEquals(Source.class.getMethod("current"), CaptureTypes.CURRENT.factory());
        assertEquals(CaptureSource.class.getMethod("region", CaptureSource.class, Rect.class),
                CaptureTypes.REGION.factory());
        assertEquals(EmulatorSource.class.getConstructor(String.class), CaptureTypes.EMULATOR.factory(),
                "an emulator is a constructor");
        assertEquals(List.of(CaptureSource.class, Rect.class), CaptureTypes.REGION.componentTypes(),
                "the parts are the factory's parameters");
        assertEquals(Monitor.class, CaptureTypes.MONITOR.type());
    }

    /** {@code window("Game").region(r)} builds what the chained call does; it is read and never written. */
    @Test
    void theChainedRegionBuildsWhatTheChainDoes() throws NoSuchMethodException {
        CaptureSource window = CaptureSource.window("Game");
        Rect sub = new Rect(1, 2, 30, 40);
        RegionSource chained = (RegionSource) window.region(sub);

        RegionSource built = CaptureTypes.REGION_CHAIN.build(List.of(window, sub));

        assertEquals(CaptureSource.class.getMethod("region", Rect.class), CaptureTypes.REGION_CHAIN.factory());
        assertEquals(List.of(CaptureSource.class, Rect.class), CaptureTypes.REGION_CHAIN.componentTypes());
        assertEquals(chained.sub(), built.sub());
        assertEquals(CaptureTypes.WINDOW.components((NamedWindow) chained.parent()),
                CaptureTypes.WINDOW.components((NamedWindow) built.parent()));
    }

    @Test
    void aFreshCaptureSourceIsTheAmbientOne() {
        assertInstanceOf(CurrentSource.class, new CaptureTypes.CaptureSourceType().fresh());
    }
}
