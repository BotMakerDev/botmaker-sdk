package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.plugin.SdkPlugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Precision.TIGHT.minArea(400)} and its two siblings: each builds exactly what the method it names
 * does, gives its parts back, and is an instance factory, so the host reads it and never writes it.
 */
class PrecisionWithersTest {

    private static ComponentType<Precision> wither(String name) {
        return SdkTypes.PRECISION_WITHERS.stream()
                .filter(w -> w.factory().getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void eachBuildsWhatItsMethodDoes() {
        assertEquals(Precision.TIGHT.tolerance(7.5), wither("tolerance").build(List.of(Precision.TIGHT, 7.5)));
        assertEquals(Precision.TIGHT.minArea(400), wither("minArea").build(List.of(Precision.TIGHT, 400)));
        assertEquals(Precision.TIGHT.minCount(3), wither("minCount").build(List.of(Precision.TIGHT, 3)));
    }

    @Test
    void eachGivesItsPartsBackAndIsAnInstanceFactory() {
        Precision value = Precision.LOOSE.minArea(120);
        for (ComponentType<Precision> each : SdkTypes.PRECISION_WITHERS) {
            Method method = (Method) each.factory();
            assertFalse(Modifier.isStatic(method.getModifiers()), method::toString);
            assertEquals(Precision.class, each.componentTypes().getFirst());
            assertEquals(value, each.build(each.components(value)), method::toString);
        }
    }

    @Test
    void thePluginListsThemBesideItsOtherComponentTypes() {
        assertTrue(new SdkPlugin().componentTypes().containsAll(SdkTypes.PRECISION_WITHERS));
        assertTrue(new SdkPlugin().componentTypes().contains(CaptureTypes.REGION_CHAIN));
    }
}
