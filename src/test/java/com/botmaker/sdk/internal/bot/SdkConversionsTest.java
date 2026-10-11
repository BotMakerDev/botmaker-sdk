package com.botmaker.sdk.internal.bot;

import com.botmaker.plugin.api.source.Converted;
import com.botmaker.plugin.api.source.Converting;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Spot;
import com.botmaker.sdk.api.geometry.Zone;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A bot's old values carried into this release's shapes, as Studio runs it on upgrade. */
class SdkConversionsTest {

    @Test
    void pointsBecomeSpotsAndRegionsZonesWithTheirNumbers() {
        Converted written = SdkConversions.PLACES.convert(Converting.of(Map.of(
                SdkValues.POINTS.id(), Map.of("CLAIM", new Point(412, 230)),
                SdkValues.REGIONS.id(), Map.of("BAG", new Rect(10, 20, 200, 120)))));

        assertEquals(Map.of("CLAIM", new Spot(412, 230)), written.written().get(SdkValues.SPOTS.id()));
        assertEquals(Map.of("BAG", new Zone(10, 20, 200, 120)), written.written().get(SdkValues.ZONES.id()));
    }

    @Test
    void aBotWithNoPlacesGetsNoEmptyClass() {
        assertTrue(SdkConversions.PLACES.convert(Converting.of(Map.of())).written().isEmpty());
    }
}
