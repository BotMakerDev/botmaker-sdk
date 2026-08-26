package com.botmaker.sdk.internal.plugin.catalog;

import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.sdk.api.authoring.SdkVersion;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The per-version palette catalogs, and the one lookup over them.
 *
 * <p>One entry per {@link SdkVersion}, each written as the previous one plus its deltas. That is the whole
 * mechanism by which a bot pinning an older SDK is offered <em>that</em> SDK's surface from an editor
 * bundling a newer one — the inversion's rule, applied to the palette exactly as {@code Authoring} applies
 * it to generation.
 *
 * <h2>Built once, and lazily</h2>
 *
 * <p>A catalog resolves every entry through {@code SerializedLambda}, so building one is reflection-heavy
 * and building all of them eagerly would cost a bot's startup something for a feature no bot uses. The map
 * holds suppliers; {@link #forVersion} memoises what it builds.
 *
 * <h2>Unknown versions</h2>
 *
 * <p>{@link #forVersion} is total and answers {@link PaletteCatalog#empty()} for a version with no class of
 * its own. Empty means <em>uncurated</em> — the editor widens its menus rather than emptying them, which is
 * the fail-open direction and the one the palette has always taken.
 */
public final class Catalogs {

    private static final Map<SdkVersion, Supplier<PaletteCatalog>> SOURCES = sources();

    private static final Map<SdkVersion, PaletteCatalog> BUILT = new EnumMap<>(SdkVersion.class);

    private Catalogs() {
    }

    private static Map<SdkVersion, Supplier<PaletteCatalog>> sources() {
        Map<SdkVersion, Supplier<PaletteCatalog>> map = new EnumMap<>(SdkVersion.class);
        map.put(SdkVersion.V1_1_0, V1_1_0::build);
        map.put(SdkVersion.V1_2_0, V1_2_0::build);
        return Map.copyOf(map);
    }

    /** The catalog for {@code version}, or {@link PaletteCatalog#empty()} when that version is uncurated. */
    public static synchronized PaletteCatalog forVersion(SdkVersion version) {
        if (version == null) {
            return PaletteCatalog.empty();
        }
        Supplier<PaletteCatalog> source = SOURCES.get(version);
        if (source == null) {
            return PaletteCatalog.empty();
        }
        return BUILT.computeIfAbsent(version, v -> source.get());
    }

    /** The versions this build carries a catalog for — what {@code ApiCatalogTest} sweeps. */
    public static Map<SdkVersion, Supplier<PaletteCatalog>> all() {
        return SOURCES;
    }
}
