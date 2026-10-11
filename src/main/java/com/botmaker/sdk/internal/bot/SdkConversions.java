package com.botmaker.sdk.internal.bot;

import com.botmaker.plugin.api.source.Conversion;
import com.botmaker.plugin.api.source.Converted;
import com.botmaker.plugin.api.source.Converting;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Spot;
import com.botmaker.sdk.api.geometry.Zone;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a bot's managed values reach this release's shapes, each {@link Conversion} from {@link SdkValues}' retired
 * declarations; the plugin lists {@link #ALL} as its {@code conversions()}, and Studio runs them when a bot
 * upgrades the SDK (umbrella {@code docs/refactor/25-compatibility.md} §12).
 */
public final class SdkConversions {

    /**
     * {@code Points}/{@code Regions} into {@code Spots}/{@code Zones} (2026-10-11). The numbers are kept: a spot
     * saved on a private display or a full-screen game was already from the game's corner, and one saved on a
     * moved window is saved again.
     */
    public static final Conversion PLACES = Conversion.from(SdkValues.POINTS, SdkValues.REGIONS)
            .to(SdkValues.SPOTS, SdkValues.ZONES)
            .by(SdkConversions::places)
            .because("Named spots and areas are now places of the game, in the bot's pixels, that follow its window"
                    + " and scale with it: Points became Spots and Regions became Zones. One saved on a window"
                    + " away from the screen's corner is saved again with save_point or save_region.");

    /** Every conversion, in the order they run. */
    public static final List<Conversion> ALL = List.of(PLACES);

    private SdkConversions() {}

    static Converted places(Converting old) {
        Map<String, Spot> spots = new LinkedHashMap<>();
        old.members(SdkValues.POINTS).forEach((name, point) -> spots.put(name, spot(point)));
        Map<String, Zone> zones = new LinkedHashMap<>();
        old.members(SdkValues.REGIONS).forEach((name, rect) -> zones.put(name, zone(rect)));
        Converted written = Converted.empty();
        if (!spots.isEmpty()) written = written.members(SdkValues.SPOTS, spots);
        if (!zones.isEmpty()) written = written.members(SdkValues.ZONES, zones);
        return written;
    }

    private static Spot spot(Point point) {
        return new Spot(point.x(), point.y());
    }

    private static Zone zone(Rect rect) {
        return new Zone(rect.x(), rect.y(), rect.width(), rect.height());
    }
}
